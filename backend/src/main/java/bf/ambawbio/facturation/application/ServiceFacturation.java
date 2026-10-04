package bf.ambawbio.facturation.application;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bf.ambawbio.conformite.api.ControleIfu;
import bf.ambawbio.conformite.api.FileCertification;
import bf.ambawbio.conformite.api.PieceACertifier;
import bf.ambawbio.facturation.api.AvoirValide;
import bf.ambawbio.facturation.api.FactureValidee;
import bf.ambawbio.facturation.domaine.DocumentFiscal;
import bf.ambawbio.referentiel.api.CatalogueFiscal;
import bf.ambawbio.shared.domaine.RegleMetierException;
import bf.ambawbio.shared.domaine.RessourceIntrouvableException;
import bf.ambawbio.shared.domaine.Uuid7;
import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.socle.api.JournalAudit;
import bf.ambawbio.socle.api.Organisation;

/** Factures et avoirs (UC-FAC-01, 02 ; SD-05, SD-08). */
@Service
public class ServiceFacturation {

    static final ZoneId FUSEAU = ZoneId.of("Africa/Ouagadougou");

    /** Ligne demandée : produit du catalogue (prix et taxe repris s'ils sont absents) ou ligne libre. */
    public record DemandeLigne(UUID produitId, String designation, String unite, BigDecimal quantite, Long prixUnitaire, Boolean prixTtc, Long remise,
            String taxeCode, BigDecimal taux) {
    }

    public record DemandeDocument(UUID id, UUID etablissementId, UUID clientId, LocalDate dateEcheance, List<DemandeLigne> lignes) {
    }

    /** Avoir partiel (quantités par ligne de la facture) ou total ({@code lignes} vide). */
    public record DemandeAvoir(UUID id, String motif, Map<UUID, BigDecimal> quantites) {
    }

    private final DocumentDepot documents;
    private final NumerotationSansTrou numerotation;
    private final CatalogueFiscal catalogue;
    private final Organisation organisation;
    private final FileCertification file;
    private final ControleIfu controleIfu;
    private final GenerateurPdf pdf;
    private final JdbcTemplate jdbc;
    private final JournalAudit audit;
    private final ApplicationEventPublisher evenements;

    ServiceFacturation(DocumentDepot documents, NumerotationSansTrou numerotation, CatalogueFiscal catalogue, Organisation organisation,
            FileCertification file, ControleIfu controleIfu, GenerateurPdf pdf, JdbcTemplate jdbc, JournalAudit audit,
            ApplicationEventPublisher evenements) {
        this.documents = documents;
        this.numerotation = numerotation;
        this.catalogue = catalogue;
        this.organisation = organisation;
        this.file = file;
        this.controleIfu = controleIfu;
        this.pdf = pdf;
        this.jdbc = jdbc;
        this.audit = audit;
        this.evenements = evenements;
    }

    @Transactional(readOnly = true)
    public List<DocumentFiscal> liste() {
        return documents.findTop300ByOrderByCreeLeDesc();
    }

    @Transactional(readOnly = true)
    public DocumentFiscal document(UUID id) {
        return documents.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Pièce introuvable."));
    }

    /** Lecture d'une pièce et de ses lignes dans une transaction (vues de l'API). */
    @Transactional(readOnly = true)
    public <T> T lire(UUID id, java.util.function.Function<DocumentFiscal, T> vue) {
        return vue.apply(document(id));
    }

    @Transactional(readOnly = true)
    public <T> List<T> lireTout(java.util.function.Function<DocumentFiscal, T> vue) {
        return liste().stream().map(vue).toList();
    }

    @Transactional
    public DocumentFiscal creerBrouillon(DemandeDocument d) {
        var existant = documents.findById(d.id());
        if (existant.isPresent()) {
            return existant.get();
        }
        var societe = (d.etablissementId() == null ? organisation.societePrincipale() : organisation.societeDe(d.etablissementId()))
                .orElseThrow(() -> new RegleMetierException("SOCIETE_INCONNUE", "Société émettrice introuvable."));
        var doc = new DocumentFiscal(d.id(), DocumentFiscal.Type.FACTURE, societe, d.etablissementId(), exigerClient(d.clientId()).id());
        doc.modifierBrouillon(d.clientId(), d.dateEcheance(), lignes(d.lignes()));
        return documents.saveAndFlush(doc);
    }

    @Transactional
    public DocumentFiscal modifierBrouillon(UUID id, DemandeDocument d) {
        var doc = document(id);
        doc.modifierBrouillon(exigerClient(d.clientId()).id(), d.dateEcheance(), lignes(d.lignes()));
        return documents.saveAndFlush(doc);
    }

    @Transactional
    public void supprimerBrouillon(UUID id) {
        var doc = document(id);
        if (doc.statut() != DocumentFiscal.Statut.BROUILLON) {
            throw new RegleMetierException("RG-01", "La pièce " + doc.numero() + " est validée : elle ne peut pas être supprimée.");
        }
        documents.delete(doc);
    }

    /** UC-FAC-01 : numéro sans trou, mentions figées, IFU contrôlé, mise en file de certification, PDF archivé. */
    @Transactional
    public DocumentFiscal valider(UUID id) {
        var doc = document(id);
        if (doc.statut() != DocumentFiscal.Statut.BROUILLON) {
            return doc;
        }
        var date = LocalDate.now(FUSEAU);
        var numero = numerotation.attribuer(doc.societeId(), doc.type(), date.getYear());
        finaliser(doc, numero.texte(), numero.exercice(), numero.sequence(), date);
        return doc;
    }

    /** UC-FAC-02 / SD-08 : avoir total ou partiel, validé immédiatement ; jamais au-delà du reste non annulé (INV-04). */
    @Transactional
    public DocumentFiscal emettreAvoir(UUID factureId, DemandeAvoir demande) {
        var existant = documents.findById(demande.id());
        if (existant.isPresent()) {
            return existant.get();
        }
        var facture = document(factureId);
        var avoir = preparerAvoir(facture, demande.id(), demande.motif(), demande.quantites());
        var date = LocalDate.now(FUSEAU);
        var numero = numerotation.attribuer(facture.societeId(), DocumentFiscal.Type.AVOIR, date.getYear());
        finaliser(avoir, numero.texte(), numero.exercice(), numero.sequence(), date);
        return avoir;
    }

    /** Facture demandée en caisse (RG-03) : numéro pris hors-ligne dans la plage FACTURE du terminal. */
    @Transactional
    public void facturerVente(UUID venteId, UUID etablissementId, UUID clientId, String numero, LocalDate date, List<DocumentFiscal.Ligne.Donnees> lignes) {
        if (documents.findFirstByOrigineAndOrigineIdAndType("vente", venteId, DocumentFiscal.Type.FACTURE).isPresent()) {
            return;
        }
        var societe = organisation.societeDe(etablissementId).orElseThrow();
        var doc = new DocumentFiscal(Uuid7.nouveau(), DocumentFiscal.Type.FACTURE, societe, etablissementId, clientId);
        doc.modifierBrouillon(clientId, date, lignes);
        doc.lierOrigine("vente", venteId, null, null);
        documents.saveAndFlush(doc);
        finaliser(doc, numero, date.getYear(), 0, date);
    }

    /** Retour en caisse d'une vente facturée : avoir numéroté comme la pièce de retour du terminal (RG-03). */
    @Transactional
    public void avoirSurRetour(UUID venteOrigineId, UUID retourId, String numero, LocalDate date, Map<UUID, BigDecimal> quantites) {
        var facture = documents.findFirstByOrigineAndOrigineIdAndType("vente", venteOrigineId, DocumentFiscal.Type.FACTURE);
        if (facture.isEmpty() || documents.findFirstByOrigineAndOrigineIdAndType("retour", retourId, DocumentFiscal.Type.AVOIR).isPresent()) {
            return;
        }
        var avoir = preparerAvoir(facture.get(), Uuid7.nouveau(), "Retour de marchandise en caisse", quantites);
        avoir.lierOrigine("retour", retourId, facture.get().getId(), "Retour de marchandise en caisse");
        finaliser(avoir, numero, date.getYear(), 0, date);
    }

    private DocumentFiscal preparerAvoir(DocumentFiscal facture, UUID id, String motif, Map<UUID, BigDecimal> quantites) {
        if (facture.type() != DocumentFiscal.Type.FACTURE || facture.statut() == DocumentFiscal.Statut.BROUILLON) {
            throw new RegleMetierException("AVOIR_IMPOSSIBLE", "Un avoir ne porte que sur une facture validée.");
        }
        Map<UUID, BigDecimal> restant = new HashMap<>();
        facture.lignes().forEach(l -> restant.put(l.getId(), l.quantite()));
        for (var precedent : documents.findByFactureOrigineId(facture.getId())) {
            precedent.lignes().forEach(l -> restant.merge(l.ligneOrigineId(), l.quantite().negate(), BigDecimal::add));
        }
        var lignes = new java.util.ArrayList<DocumentFiscal.Ligne.Donnees>();
        for (var l : facture.lignes()) {
            var q = quantites == null || quantites.isEmpty() ? restant.get(l.getId()) : quantites.getOrDefault(l.getId(), BigDecimal.ZERO);
            if (q == null || q.signum() <= 0) {
                continue;
            }
            if (q.compareTo(restant.get(l.getId())) > 0) {
                throw new RegleMetierException("INV-04", "Avoir de « " + l.designation() + " » supérieur à la quantité facturée restante.");
            }
            long remise = BigDecimal.valueOf(l.remise()).multiply(q).divide(l.quantite(), 0, java.math.RoundingMode.DOWN).longValue();
            lignes.add(new DocumentFiscal.Ligne.Donnees(Uuid7.nouveau(), l.produitId(), l.designation(), l.unite(), q, l.prixUnitaire(), l.prixTtc(),
                    remise, l.taxeCode(), l.taux(), l.getId()));
        }
        if (lignes.isEmpty()) {
            throw new RegleMetierException("AVOIR_VIDE", "Rien à annuler : la facture est déjà entièrement couverte par des avoirs.");
        }
        if (motif == null || motif.isBlank()) {
            throw new RegleMetierException("MOTIF_OBLIGATOIRE", "Indiquez le motif de l'avoir.");
        }
        var avoir = new DocumentFiscal(id, DocumentFiscal.Type.AVOIR, facture.societeId(), facture.etablissementId(), facture.clientId());
        avoir.modifierBrouillon(facture.clientId(), null, lignes);
        avoir.lierOrigine(facture.origine() == null ? null : facture.origine(), facture.origineId(), facture.getId(), motif.trim());
        documents.saveAndFlush(avoir);
        return avoir;
    }

    private void finaliser(DocumentFiscal doc, String numero, int exercice, long sequence, LocalDate date) {
        var client = exigerClient(doc.clientId());
        if (client.ifu() != null && controleIfu.valide(client.ifu()).filter(v -> !v).isPresent()) {
            throw new RegleMetierException("IFU_INCONNU", "L'IFU " + client.ifu() + " du client n'est pas reconnu par l'administration fiscale.");
        }
        var emetteur = organisation.emetteur(doc.societeId()).orElseThrow();
        Map<String, Object> mentions = new LinkedHashMap<>();
        mentions.put("raisonSociale", emetteur.raisonSociale());
        mentions.put("ifu", emetteur.ifu());
        mentions.put("rccm", emetteur.rccm());
        mentions.put("regimeFiscal", emetteur.regimeFiscal());
        mentions.put("adresse", emetteur.adresse());
        mentions.put("ville", emetteur.ville());
        mentions.put("telephone", emetteur.telephone());
        mentions.put("courriel", emetteur.courriel());
        var echeance = doc.type() == DocumentFiscal.Type.FACTURE
                ? (doc.dateEcheance() != null ? doc.dateEcheance() : date.plusDays(client.delaiPaiementJours())) : null;
        doc.valider(numero, exercice, sequence, date, echeance,
                new DocumentFiscal.Client(client.nom(), adresse(client), client.ifu(), client.rccm(), client.regime(), client.assujetti()), mentions,
                ContexteTenant.utilisateurCourant().orElse(null), Instant.now());
        DocumentFiscal facture = null;
        if (doc.type() == DocumentFiscal.Type.AVOIR) {
            facture = document(doc.factureOrigineId());
            facture.imputerAvoir(doc.totalTtc());
        }
        documents.flush();
        audit.enregistrer(doc.type() == DocumentFiscal.Type.FACTURE ? "FACTURE_VALIDEE" : "AVOIR_VALIDE", "document_fiscal", doc.getId(), null,
                Map.of("numero", numero, "totalTtc", doc.totalTtc()));
        file.soumettre(pieceACertifier(doc, facture));
        archiver(doc, facture == null ? null : facture.numero(), "validation");
        if (doc.type() == DocumentFiscal.Type.FACTURE) {
            evenements.publishEvent(new FactureValidee(1, doc.getId(), doc.societeId(), numero, date, doc.clientId(), doc.totalHt(), doc.totalTaxes(),
                    doc.totalTtc(), doc.origine(), doc.origineId()));
        } else {
            evenements.publishEvent(new AvoirValide(1, doc.getId(), doc.factureOrigineId(), doc.societeId(), numero, date, doc.clientId(), doc.totalHt(),
                    doc.totalTaxes(), doc.totalTtc()));
        }
    }

    private static String adresse(CatalogueFiscal.ClientFiscal c) {
        return c.ville() == null ? c.adresse() : (c.adresse() == null ? c.ville() : c.adresse() + ", " + c.ville());
    }

    private static PieceACertifier pieceACertifier(DocumentFiscal d, DocumentFiscal facture) {
        Map<String, PieceACertifier.Taxe> taxes = new LinkedHashMap<>();
        for (var l : d.lignes()) {
            var cle = l.taxeCode() + "|" + l.taux().stripTrailingZeros().toPlainString();
            var t = taxes.get(cle);
            taxes.put(cle, new PieceACertifier.Taxe(l.taxeCode(), l.taux(), (t == null ? 0 : t.baseHt()) + l.montantHt(),
                    (t == null ? 0 : t.montant()) + l.montantTaxe()));
        }
        return new PieceACertifier(d.getId(), d.type().name(), d.numero(), d.dateEmission(), d.valideLe(), (String) d.emetteur().get("ifu"),
                (String) d.emetteur().get("raisonSociale"), d.client().ifu(), d.client().nom(), facture == null ? null : facture.numero(), d.totalHt(),
                d.totalTaxes(), d.totalTtc(), List.copyOf(taxes.values()));
    }

    /** F-FAC-06 : PDF archivé avec son empreinte, jamais remplacé (une nouvelle version à la certification). */
    void archiver(DocumentFiscal doc, String numeroOrigine, String motif) {
        var contenu = pdf.generer(doc, numeroOrigine);
        Integer version = jdbc.queryForObject("select coalesce(max(version), 0) + 1 from facturation.archive_pdf where document_id = ?", Integer.class,
                doc.getId());
        jdbc.update("insert into facturation.archive_pdf (id, tenant_id, document_id, version, motif, empreinte, contenu) values (?, ?, ?, ?, ?, ?, ?)",
                Uuid7.nouveau(), ContexteTenant.tenantObligatoire(), doc.getId(), version, motif, empreinte(contenu), contenu);
    }

    /** PDF à télécharger : dernière version archivée d'une pièce validée ; aperçu calculé pour un brouillon. */
    @Transactional(readOnly = true)
    public byte[] pdf(UUID id) {
        var doc = document(id);
        if (doc.statut() == DocumentFiscal.Statut.BROUILLON) {
            return pdf.generer(doc, null);
        }
        return jdbc.queryForObject("select contenu from facturation.archive_pdf where document_id = ? order by version desc limit 1", byte[].class, id);
    }

    @Transactional(readOnly = true)
    public String numero(UUID id) {
        return id == null ? null : documents.findById(id).map(DocumentFiscal::numero).orElse(null);
    }

    private CatalogueFiscal.ClientFiscal exigerClient(UUID clientId) {
        if (clientId == null) {
            throw new RegleMetierException("CLIENT_OBLIGATOIRE", "Choisissez le client à facturer.");
        }
        return catalogue.client(clientId).orElseThrow(() -> new RegleMetierException("CLIENT_INCONNU", "Client introuvable."));
    }

    private List<DocumentFiscal.Ligne.Donnees> lignes(List<DemandeLigne> demandes) {
        if (demandes == null) {
            return List.of();
        }
        return demandes.stream().map(l -> {
            var article = l.produitId() == null ? null : catalogue.article(l.produitId())
                    .orElseThrow(() -> new RegleMetierException("PRODUIT_INCONNU", "Produit introuvable."));
            return new DocumentFiscal.Ligne.Donnees(Uuid7.nouveau(), l.produitId(),
                    l.designation() != null ? l.designation() : article == null ? null : article.nom(),
                    l.unite() != null ? l.unite() : article == null ? null : article.unite(),
                    l.quantite() == null ? BigDecimal.ONE : l.quantite(),
                    l.prixUnitaire() != null ? l.prixUnitaire() : article == null ? 0 : article.prixVente(),
                    l.prixTtc() != null ? l.prixTtc() : article == null || article.prixTtc(),
                    l.remise() == null ? 0 : l.remise(),
                    l.taxeCode() != null ? l.taxeCode() : article == null ? null : article.taxeCode(),
                    l.taux() != null ? l.taux() : article == null ? BigDecimal.ZERO : article.taux(), null);
        }).toList();
    }

    private static String empreinte(byte[] contenu) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
