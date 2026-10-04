package bf.ambawbio.facturation.domaine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import bf.ambawbio.shared.domaine.CalculLigne;
import bf.ambawbio.shared.domaine.EntiteMetier;
import bf.ambawbio.shared.domaine.RegleMetierException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Facture ou avoir (F-FAC-01, 05). Brouillon modifiable ; une fois validé (numéro attribué, mentions figées), plus rien
 * ne change que le statut de paiement ou d'annulation et la certification (RG-01, protégé aussi par la base).
 */
@Entity
@Table(schema = "facturation", name = "document_fiscal")
public class DocumentFiscal extends EntiteMetier {

    public enum Type { FACTURE, AVOIR }

    public enum Statut { BROUILLON, VALIDEE, PARTIELLEMENT_PAYEE, PAYEE, ANNULEE_PAR_AVOIR }

    public enum StatutFec { NON_SOUMISE, EN_FILE, CERTIFIEE, REJETEE }

    /** Mentions du client figées à la validation (RG-02). */
    public record Client(String nom, String adresse, String ifu, String rccm, String regime, boolean assujetti) {
    }

    @Column(name = "societe_id")
    private UUID societeId;
    @Column(name = "etablissement_id")
    private UUID etablissementId;
    @Enumerated(EnumType.STRING)
    private Type type;
    @Enumerated(EnumType.STRING)
    private Statut statut;
    private String numero;
    private Integer exercice;
    private Long sequence;
    @Column(name = "date_emission")
    private LocalDate dateEmission;
    @Column(name = "date_echeance")
    private LocalDate dateEcheance;
    @Column(name = "client_id")
    private UUID clientId;
    @Column(name = "client_nom")
    private String clientNom;
    @Column(name = "client_adresse")
    private String clientAdresse;
    @Column(name = "client_ifu")
    private String clientIfu;
    @Column(name = "client_rccm")
    private String clientRccm;
    @Column(name = "client_regime")
    private String clientRegime;
    @Column(name = "client_assujetti")
    private Boolean clientAssujetti;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> emetteur;
    @Column(name = "facture_origine_id")
    private UUID factureOrigineId;
    private String motif;
    @Column(name = "total_ht")
    private long totalHt;
    @Column(name = "total_taxes")
    private long totalTaxes;
    @Column(name = "total_ttc")
    private long totalTtc;
    @Column(name = "total_avoirs")
    private long totalAvoirs;
    private String origine;
    @Column(name = "origine_id")
    private UUID origineId;
    @Column(name = "valide_le")
    private Instant valideLe;
    @Column(name = "valide_par")
    private UUID validePar;
    @Enumerated(EnumType.STRING)
    @Column(name = "fec_statut")
    private StatutFec fecStatut = StatutFec.NON_SOUMISE;
    @Column(name = "fec_identifiant")
    private String fecIdentifiant;
    @Column(name = "fec_code_qr")
    private String fecCodeQr;
    @Column(name = "fec_horodatage")
    private Instant fecHorodatage;
    @Column(name = "fec_simulee")
    private boolean fecSimulee;
    @Column(name = "fec_message")
    private String fecMessage;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "document_id", nullable = false, updatable = false)
    @OrderBy("rang")
    @org.hibernate.annotations.BatchSize(size = 100)
    private List<Ligne> lignes = new ArrayList<>();

    protected DocumentFiscal() {
    }

    public DocumentFiscal(UUID id, Type type, UUID societeId, UUID etablissementId, UUID clientId) {
        super(id);
        this.type = type;
        this.societeId = societeId;
        this.etablissementId = etablissementId;
        this.clientId = clientId;
        this.statut = Statut.BROUILLON;
    }

    public void modifierBrouillon(UUID clientId, LocalDate dateEcheance, List<Ligne.Donnees> lignes) {
        exigerBrouillon();
        this.clientId = clientId;
        this.dateEcheance = dateEcheance;
        this.lignes.clear();
        int rang = 0;
        for (var d : lignes) {
            this.lignes.add(new Ligne(d, ++rang));
        }
        recalculer();
    }

    public void lierOrigine(String origine, UUID origineId, UUID factureOrigineId, String motif) {
        exigerBrouillon();
        this.origine = origine;
        this.origineId = origineId;
        this.factureOrigineId = factureOrigineId;
        this.motif = motif;
    }

    private void recalculer() {
        totalHt = lignes.stream().mapToLong(Ligne::montantHt).sum();
        totalTaxes = lignes.stream().mapToLong(Ligne::montantTaxe).sum();
        totalTtc = lignes.stream().mapToLong(Ligne::montantTtc).sum();
    }

    /** Validation (UC-FAC-01) : numéro sans trou, date, mentions figées ; contrôles RG-02. */
    public void valider(String numero, int exercice, long sequence, LocalDate date, LocalDate echeance, Client client, Map<String, Object> emetteur,
            UUID par, Instant le) {
        exigerBrouillon();
        if (lignes.isEmpty()) {
            throw new RegleMetierException("PIECE_VIDE", "Une facture comporte au moins une ligne.");
        }
        if (emetteur.get("ifu") == null) {
            throw new RegleMetierException("RG-02", "L'IFU de la société émettrice est obligatoire : renseignez-le dans les paramètres de la société.");
        }
        if (client.assujetti() && (client.ifu() == null || client.ifu().isBlank())) {
            throw new RegleMetierException("RG-02", "Le client « " + client.nom() + " » est assujetti : son IFU est obligatoire sur la facture (INV-03).");
        }
        this.numero = numero;
        this.exercice = exercice;
        this.sequence = sequence;
        this.dateEmission = date;
        this.dateEcheance = echeance;
        this.clientNom = client.nom();
        this.clientAdresse = client.adresse();
        this.clientIfu = client.ifu();
        this.clientRccm = client.rccm();
        this.clientRegime = client.regime();
        this.clientAssujetti = client.assujetti();
        this.emetteur = Map.copyOf(emetteur.entrySet().stream().filter(e -> e.getValue() != null)
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        this.validePar = par;
        this.valideLe = le;
        this.statut = type == Type.FACTURE && "vente".equals(origine) ? Statut.PAYEE : Statut.VALIDEE;
        this.fecStatut = StatutFec.EN_FILE;
    }

    /** Avoir validé sur cette facture : jamais au-delà du reste non annulé (INV-04). */
    public void imputerAvoir(long montant) {
        if (type != Type.FACTURE || statut == Statut.BROUILLON) {
            throw new RegleMetierException("AVOIR_IMPOSSIBLE", "Un avoir ne porte que sur une facture validée.");
        }
        if (montant <= 0 || totalAvoirs + montant > totalTtc) {
            throw new RegleMetierException("INV-04", "L'avoir dépasse le reste non annulé de la facture (" + (totalTtc - totalAvoirs) + " F).");
        }
        totalAvoirs += montant;
        if (totalAvoirs == totalTtc) {
            statut = Statut.ANNULEE_PAR_AVOIR;
        }
    }

    private void exigerBrouillon() {
        if (statut != Statut.BROUILLON) {
            throw new RegleMetierException("RG-01", "La pièce " + numero + " est validée : elle ne peut plus être modifiée. Corrigez-la par un avoir.");
        }
    }

    public UUID societeId() { return societeId; }
    public UUID etablissementId() { return etablissementId; }
    public Type type() { return type; }
    public Statut statut() { return statut; }
    public String numero() { return numero; }
    public LocalDate dateEmission() { return dateEmission; }
    public LocalDate dateEcheance() { return dateEcheance; }
    public UUID clientId() { return clientId; }
    public Client client() {
        return new Client(clientNom, clientAdresse, clientIfu, clientRccm, clientRegime, Boolean.TRUE.equals(clientAssujetti));
    }
    public Map<String, Object> emetteur() { return emetteur == null ? Map.of() : emetteur; }
    public UUID factureOrigineId() { return factureOrigineId; }
    public String motif() { return motif; }
    public long totalHt() { return totalHt; }
    public long totalTaxes() { return totalTaxes; }
    public long totalTtc() { return totalTtc; }
    public long totalAvoirs() { return totalAvoirs; }
    public String origine() { return origine; }
    public UUID origineId() { return origineId; }
    public Instant valideLe() { return valideLe; }
    public StatutFec fecStatut() { return fecStatut; }
    public String fecIdentifiant() { return fecIdentifiant; }
    public String fecCodeQr() { return fecCodeQr; }
    public Instant fecHorodatage() { return fecHorodatage; }
    public boolean fecSimulee() { return fecSimulee; }
    public String fecMessage() { return fecMessage; }
    public List<Ligne> lignes() { return List.copyOf(lignes); }

    @Entity
    @Table(schema = "facturation", name = "ligne_document")
    public static class Ligne extends EntiteMetier {
        private int rang;
        @Column(name = "produit_id")
        private UUID produitId;
        private String designation;
        private String unite;
        private BigDecimal quantite;
        @Column(name = "prix_unitaire")
        private long prixUnitaire;
        @Column(name = "prix_ttc")
        private boolean prixTtc;
        private long remise;
        @Column(name = "taxe_code")
        private String taxeCode;
        private BigDecimal taux;
        @Column(name = "montant_ht")
        private long montantHt;
        @Column(name = "montant_taxe")
        private long montantTaxe;
        @Column(name = "montant_ttc")
        private long montantTtc;
        @Column(name = "ligne_origine_id")
        private UUID ligneOrigineId;

        protected Ligne() {
        }

        public record Donnees(UUID id, UUID produitId, String designation, String unite, BigDecimal quantite, long prixUnitaire, boolean prixTtc,
                long remise, String taxeCode, BigDecimal taux, UUID ligneOrigineId) {
        }

        Ligne(Donnees d, int rang) {
            super(d.id());
            if (d.designation() == null || d.designation().isBlank()) {
                throw new RegleMetierException("DESIGNATION_OBLIGATOIRE", "Chaque ligne a une désignation.");
            }
            var taux = d.taux() == null ? BigDecimal.ZERO : d.taux();
            var m = CalculLigne.ligne(d.quantite(), d.prixUnitaire(), d.prixTtc(), d.remise(), taux);
            this.rang = rang;
            this.produitId = d.produitId();
            this.designation = d.designation().trim();
            this.unite = d.unite();
            this.quantite = d.quantite().setScale(3, RoundingMode.HALF_UP);
            this.prixUnitaire = d.prixUnitaire();
            this.prixTtc = d.prixTtc();
            this.remise = d.remise();
            this.taxeCode = d.taxeCode();
            this.taux = taux;
            this.montantHt = m.ht().valeur();
            this.montantTaxe = m.taxe().valeur();
            this.montantTtc = m.ttc().valeur();
            this.ligneOrigineId = d.ligneOrigineId();
        }

        public UUID produitId() { return produitId; }
        public String designation() { return designation; }
        public String unite() { return unite; }
        public BigDecimal quantite() { return quantite; }
        public long prixUnitaire() { return prixUnitaire; }
        public boolean prixTtc() { return prixTtc; }
        public long remise() { return remise; }
        public String taxeCode() { return taxeCode; }
        public BigDecimal taux() { return taux; }
        public long montantHt() { return montantHt; }
        public long montantTaxe() { return montantTaxe; }
        public long montantTtc() { return montantTtc; }
        public UUID ligneOrigineId() { return ligneOrigineId; }
    }
}
