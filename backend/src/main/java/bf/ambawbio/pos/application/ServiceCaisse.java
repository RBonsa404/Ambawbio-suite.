package bf.ambawbio.pos.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bf.ambawbio.pos.api.RetourEnregistre;
import bf.ambawbio.pos.api.SessionCloturee;
import bf.ambawbio.pos.api.VenteEnregistree;
import bf.ambawbio.pos.domaine.SessionCaisse;
import bf.ambawbio.pos.domaine.Vente;
import bf.ambawbio.shared.domaine.RegleMetierException;
import bf.ambawbio.shared.domaine.RessourceIntrouvableException;
import bf.ambawbio.shared.domaine.Uuid7;
import bf.ambawbio.socle.api.Habilitations;
import bf.ambawbio.socle.api.JournalAudit;
import bf.ambawbio.socle.api.Organisation;
import bf.ambawbio.socle.api.Permissions;
import bf.ambawbio.sync.api.OperationTerminal;
import bf.ambawbio.sync.api.PlagesNumerotation;
import tools.jackson.databind.JsonNode;

/**
 * Application des opérations de caisse reçues des terminaux (guide §8.5) : créations uniquement, l'identifiant
 * empêche les doublons ; une règle violée met l'opération EN_CONFLIT avec son motif (le terminal la garde).
 */
@Service
public class ServiceCaisse {

    private final PointDeVenteDepot pointsDeVente;
    private final SessionDepot sessions;
    private final VenteDepot ventes;
    private final PlagesNumerotation plages;
    private final Organisation organisation;
    private final Habilitations habilitations;
    private final JournalAudit audit;
    private final PublicationCaisse publication;
    private final ApplicationEventPublisher evenements;

    ServiceCaisse(PointDeVenteDepot pointsDeVente, SessionDepot sessions, VenteDepot ventes, PlagesNumerotation plages, Organisation organisation,
            Habilitations habilitations, JournalAudit audit, PublicationCaisse publication, ApplicationEventPublisher evenements) {
        this.pointsDeVente = pointsDeVente;
        this.sessions = sessions;
        this.ventes = ventes;
        this.plages = plages;
        this.organisation = organisation;
        this.habilitations = habilitations;
        this.audit = audit;
        this.publication = publication;
        this.evenements = evenements;
    }

    /** {@code SESSION_OUVERTE} : {@code {sessionId, pointDeVenteId, fondsInitial, ouverteLe}}. */
    @Transactional
    public void ouvrir(OperationTerminal op) {
        var c = op.charge();
        var sessionId = uuid(c, "sessionId");
        if (sessions.existsById(sessionId)) {
            return;
        }
        var pdv = pointsDeVente.findById(uuid(c, "pointDeVenteId"))
                .filter(p -> p.etablissementId().equals(op.etablissementId()))
                .orElseThrow(() -> new RegleMetierException("POINT_DE_VENTE_INCONNU", "Point de vente inconnu pour cet établissement."));
        exigerPermission(op, Permissions.POS_VENDRE);
        boolean concurrente = sessions.existsByPointDeVenteIdAndStatut(pdv.getId(), SessionCaisse.Statut.OUVERTE);
        var session = sessions.saveAndFlush(new SessionCaisse(sessionId, pdv.getId(), op.etablissementId(), op.terminalId(), caissier(op),
                instant(c, "ouverteLe", op.horodatageLocal()), c.path("fondsInitial").asLong(0), concurrente));
        if (concurrente) {
            audit.enregistrer("SESSION_CONCURRENTE", "session_caisse", sessionId, null,
                    Map.of("pointDeVente", pdv.code(), "terminal", op.terminalId().toString()));
        }
        publication.session(session);
    }

    /** {@code VENTE_ENREGISTREE} ou {@code RETOUR_ENREGISTRE} (voir {@code docs/lots/LOT-05.md} pour la charge). */
    @Transactional
    public void enregistrer(OperationTerminal op, Vente.Type type) {
        var c = op.charge();
        var id = uuid(c, "venteId");
        if (ventes.existsById(id)) {
            return;
        }
        var session = sessions.findById(uuid(c, "sessionId"))
                .orElseThrow(() -> new RegleMetierException("SESSION_INCONNUE", "Session de caisse inconnue."));
        var pdv = pointsDeVente.findById(session.pointDeVenteId()).orElseThrow();
        exigerPermission(op, Permissions.POS_VENDRE);
        var societe = organisation.societeDe(op.etablissementId())
                .orElseThrow(() -> new RegleMetierException("ETABLISSEMENT_INCONNU", "Établissement inconnu."));

        var numero = c.path("numero");
        var typePiece = type == Vente.Type.VENTE ? "TICKET" : "AVOIR";
        int annee = numero.path("annee").asInt();
        long sequence = numero.path("sequence").asLong();
        if (!typePiece.equals(numero.path("typePiece").asString()) || !plages.couvre(op.terminalId(), typePiece, annee, sequence)) {
            throw new RegleMetierException("NUMERO_HORS_PLAGE", "Le numéro de pièce n'appartient pas aux plages de ce terminal.");
        }
        if (ventes.existsBySocieteIdAndTypePieceAndAnneeAndSequence(societe, typePiece, annee, sequence)) {
            throw new RegleMetierException("NUMERO_DEJA_UTILISE", "Ce numéro de pièce est déjà utilisé par une autre pièce.");
        }
        var libelleNumero = "%s-%d-%06d".formatted(plages.prefixe(op.terminalId(), typePiece), annee, sequence);
        String numeroFacture = null;
        boolean factureDemandee = c.path("factureDemandee").asBoolean(false);
        if (type == Vente.Type.VENTE && factureDemandee) {
            // RG-03 : numéro de facture pris hors-ligne dans la plage FACTURE du terminal ; client obligatoire.
            var f = c.path("facture");
            if (uuidOuNull(c, "clientId") == null || f.isMissingNode()
                    || !plages.couvre(op.terminalId(), "FACTURE", f.path("annee").asInt(), f.path("sequence").asLong())) {
                throw new RegleMetierException("FACTURE_INVALIDE", "Facture demandée sans client ou sans numéro de facture du terminal.");
            }
            numeroFacture = "%s-%d-%06d".formatted(plages.prefixe(op.terminalId(), "FACTURE"), f.path("annee").asInt(), f.path("sequence").asLong());
        }

        Vente origine = null;
        if (type == Vente.Type.RETOUR) {
            origine = ventes.findById(uuid(c, "venteOrigineId")).filter(v -> v.type() == Vente.Type.VENTE)
                    .orElseThrow(() -> new RegleMetierException("VENTE_ORIGINE_INCONNUE", "Le ticket d'origine du retour est introuvable."));
        }

        var lignes = lignes(c, op, pdv.remiseMaxPourcent());
        if (origine != null) {
            verifierQuantitesRetournables(origine, lignes);
        }
        var encaissements = encaissements(c);
        var vente = new Vente(new Vente.Entete(id, session.getId(), societe, op.etablissementId(), op.terminalId(), caissier(op), type, typePiece,
                annee, sequence, libelleNumero, instant(c, "horodatage", op.horodatageLocal()), uuidOuNull(c, "clientId"),
                origine == null ? null : origine.getId(), factureDemandee, numeroFacture), lignes, encaissements);
        verifierTotaux(c, vente);
        ventes.saveAndFlush(vente);

        var resume = vente.lignes().stream()
                .map(l -> new VenteEnregistree.Ligne(l.getId(), l.produitId(), l.libelle(), l.quantite(), l.quantiteUniteStock(), l.prixUnitaire(),
                        l.prixTtc(), l.remise(), l.taxeCode(), l.taux(), l.montantHt(), l.montantTaxe(), l.montantTtc(), l.ligneOrigineId())).toList();
        if (type == Vente.Type.VENTE) {
            evenements.publishEvent(new VenteEnregistree(1, id, societe, op.etablissementId(), session.getId(), libelleNumero, vente.horodatage(),
                    vente.totalTtc(), vente.totalTaxes(), vente.clientId(), vente.factureDemandee(), numeroFacture, resume));
        } else {
            evenements.publishEvent(new RetourEnregistre(1, id, origine.getId(), societe, op.etablissementId(), session.getId(), libelleNumero,
                    vente.horodatage(), vente.totalTtc(), resume));
        }
    }

    /** {@code SESSION_CLOTUREE} : {@code {sessionId, especesComptees, clotureeLe}}. */
    @Transactional
    public void cloturer(OperationTerminal op) {
        var c = op.charge();
        var session = sessions.findById(uuid(c, "sessionId"))
                .orElseThrow(() -> new RegleMetierException("SESSION_INCONNUE", "Session de caisse inconnue."));
        exigerPermission(op, Permissions.POS_CLOTURER);
        var pdv = pointsDeVente.findById(session.pointDeVenteId()).orElseThrow();
        session.cloturer(c.path("especesComptees").asLong(), especesTheoriques(session), pdv.seuilEcart(), instant(c, "clotureeLe", op.horodatageLocal()));
        sessions.flush();
        audit.enregistrer("SESSION_CLOTUREE", "session_caisse", session.getId(), null,
                Map.of("theoriques", session.especesTheoriques(), "comptees", session.especesComptees(), "ecart", session.ecart()));
        publication.session(session);
        evenements.publishEvent(new SessionCloturee(1, session.getId(), session.etablissementId(), session.especesTheoriques(),
                session.especesComptees(), session.ecart(), session.statut() == SessionCaisse.Statut.ECART_A_VALIDER));
    }

    /** Validation d'un écart par un responsable, avec son code PIN (RG-09, UC-POS-08). */
    @Transactional
    public SessionCaisse validerEcart(UUID sessionId, String identifiantResponsable, String pin, String motif) {
        var session = sessions.findById(sessionId).orElseThrow(() -> new RessourceIntrouvableException("Session de caisse introuvable."));
        var responsable = identifiantResponsable == null || identifiantResponsable.isBlank() ? habilitations.utilisateurCourant()
                : habilitations.parNomUtilisateur(identifiantResponsable)
                        .orElseThrow(() -> new RegleMetierException("RESPONSABLE_INCONNU", "Identifiant de responsable inconnu."));
        if (!habilitations.possede(responsable, session.etablissementId(), Permissions.POS_VALIDER_ECART)) {
            throw new RegleMetierException("RESPONSABLE_NON_HABILITE", "Cette personne n'est pas habilitée à valider un écart de caisse.");
        }
        if (!habilitations.verifierCodePin(responsable, pin)) {
            throw new RegleMetierException("PIN_INCORRECT", "Code PIN incorrect.");
        }
        session.validerEcart(responsable, motif, Instant.now());
        sessions.flush();
        audit.enregistrer("ECART_VALIDE", "session_caisse", sessionId, null,
                Map.of("ecart", session.ecart(), "responsable", responsable.toString(), "motif", session.motifValidation()));
        publication.session(session);
        return session;
    }

    /** Espèces attendues : fonds initial + espèces des ventes − espèces remboursées (retours). */
    long especesTheoriques(SessionCaisse session) {
        long total = session.fondsInitial();
        for (var v : ventes.findBySessionIdOrderByHorodatageAsc(session.getId())) {
            total += v.type() == Vente.Type.VENTE ? v.especes() : -v.especes();
        }
        return total;
    }

    private List<Vente.Ligne> lignes(JsonNode c, OperationTerminal op, int remiseMax) {
        var resultat = new ArrayList<Vente.Ligne>();
        int rang = 0;
        boolean remise = false;
        for (var l : c.path("lignes")) {
            var quantite = new BigDecimal(l.path("quantite").asString());
            long prix = l.path("prixUnitaire").asLong();
            long remiseLigne = l.path("remise").asLong(0);
            if (remiseLigne > 0) {
                remise = true;
                if (remiseLigne * 100 > bf.ambawbio.shared.domaine.CalculLigne.brut(quantite, prix).valeur() * remiseMax) {
                    throw new RegleMetierException("REMISE_EXCESSIVE",
                            "Remise supérieure au maximum autorisé pour ce point de vente (" + remiseMax + " %).");
                }
            }
            var idLigne = uuidOuNull(l, "id");
            var ligne = new Vente.Ligne(new Vente.Ligne.Donnees(idLigne == null ? Uuid7.nouveau() : idLigne, ++rang, uuid(l, "produitId"),
                    l.path("libelle").asString(),
                    l.path("conditionnement").asString(null), quantite, l.has("facteur") ? new BigDecimal(l.path("facteur").asString()) : BigDecimal.ONE,
                    prix, l.path("prixTtc").asBoolean(true), remiseLigne, l.path("taxeCode").asString(null),
                    new BigDecimal(l.path("taux").asString("0")), uuidOuNull(l, "ligneOrigineId")));
            if (ligne.montantTtc() != l.path("montantTtc").asLong() || ligne.montantTaxe() != l.path("montantTaxe").asLong()) {
                throw new RegleMetierException("CALCUL_DIVERGENT", "Les montants de la ligne « " + ligne.libelle() + " » ne correspondent pas au calcul.");
            }
            resultat.add(ligne);
        }
        if (resultat.isEmpty()) {
            throw new RegleMetierException("PIECE_VIDE", "Une pièce de caisse comporte au moins une ligne.");
        }
        if (remise && !habilitations.possede(caissier(op), op.etablissementId(), Permissions.POS_REMISER)) {
            throw new RegleMetierException("REMISE_NON_AUTORISEE", "Remise accordée par un utilisateur qui n'y est pas autorisé.");
        }
        return resultat;
    }

    private List<Vente.Encaissement> encaissements(JsonNode c) {
        var resultat = new ArrayList<Vente.Encaissement>();
        for (var e : c.path("encaissements")) {
            var moyen = Vente.Moyen.valueOf(e.path("moyen").asString());
            long montant = e.path("montant").asLong();
            Long recu = e.hasNonNull("recu") ? e.get("recu").asLong() : null;
            Long rendu = e.hasNonNull("rendu") ? e.get("rendu").asLong() : null;
            if (montant <= 0 || (recu != null && recu - (rendu == null ? 0 : rendu) != montant)) {
                throw new RegleMetierException("ENCAISSEMENT_INVALIDE", "Encaissement incohérent (montant, reçu, rendu).");
            }
            resultat.add(new Vente.Encaissement(Uuid7.nouveau(), moyen, montant, recu, rendu, e.path("operateur").asString(null),
                    e.path("reference").asString(null)));
        }
        return resultat;
    }

    private static void verifierTotaux(JsonNode c, Vente vente) {
        long encaisse = vente.encaissements().stream().mapToLong(Vente.Encaissement::montant).sum();
        if (encaisse != vente.totalTtc()) {
            throw new RegleMetierException("ENCAISSEMENT_INCOMPLET",
                    "Les encaissements (" + encaisse + " F) ne correspondent pas au total (" + vente.totalTtc() + " F).");
        }
        if (c.path("totalTtc").asLong() != vente.totalTtc()) {
            throw new RegleMetierException("CALCUL_DIVERGENT", "Le total de la pièce ne correspond pas au calcul.");
        }
    }

    /** Cas sensible (guide §8.5) : on ne rend jamais plus que ce qui a été vendu, retours précédents déduits. */
    private void verifierQuantitesRetournables(Vente origine, List<Vente.Ligne> retour) {
        Map<UUID, BigDecimal> restant = new HashMap<>();
        origine.lignes().forEach(l -> restant.merge(l.getId(), l.quantiteUniteStock(), BigDecimal::add));
        for (var precedent : ventes.findByVenteOrigineId(origine.getId())) {
            precedent.lignes().forEach(l -> restant.merge(l.ligneOrigineId(), l.quantiteUniteStock().negate(), BigDecimal::add));
        }
        for (var l : retour) {
            var dispo = l.ligneOrigineId() == null ? null : restant.get(l.ligneOrigineId());
            if (dispo == null || l.quantiteUniteStock().compareTo(dispo) > 0) {
                throw new RegleMetierException("RETOUR_SUPERIEUR_VENTE", "Retour de « " + l.libelle() + " » supérieur à la quantité vendue.");
            }
            restant.put(l.ligneOrigineId(), dispo.subtract(l.quantiteUniteStock()));
        }
    }

    private void exigerPermission(OperationTerminal op, String permission) {
        if (!habilitations.possede(caissier(op), op.etablissementId(), permission)) {
            throw new RegleMetierException("UTILISATEUR_NON_HABILITE",
                    "L'utilisateur du terminal n'a pas le droit « " + permission + " » sur cet établissement.");
        }
    }

    /** Le caissier est lu dans la charge signée (l'en-tête {@code utilisateurId} n'est pas couvert par la signature). */
    private static UUID caissier(OperationTerminal op) {
        var id = uuidOuNull(op.charge(), "caissierId");
        if (id == null) {
            throw new RegleMetierException("CHARGE_INVALIDE", "Champ obligatoire absent : caissierId.");
        }
        return id;
    }

    private static UUID uuid(JsonNode c, String champ) {
        var v = c.path(champ).asString(null);
        if (v == null) {
            throw new RegleMetierException("CHARGE_INVALIDE", "Champ obligatoire absent : " + champ + ".");
        }
        return UUID.fromString(v);
    }

    private static UUID uuidOuNull(JsonNode c, String champ) {
        var v = c.path(champ).asString(null);
        return v == null || v.isBlank() ? null : UUID.fromString(v);
    }

    private static Instant instant(JsonNode c, String champ, Instant defaut) {
        var v = c.path(champ).asString(null);
        return v == null ? defaut : Instant.parse(v);
    }
}
