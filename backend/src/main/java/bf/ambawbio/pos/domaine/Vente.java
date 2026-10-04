package bf.ambawbio.pos.domaine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import bf.ambawbio.shared.domaine.EntiteMetier;
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
 * Vente ou retour de caisse (F-POS-01, 06, 07) : pièce créée par un terminal, jamais modifiée (INV-01).
 * Numéro pris dans la plage du terminal ({@code TK-C01-2026-000501}, guide §6.7).
 */
@Entity
@Table(schema = "pos", name = "vente")
public class Vente extends EntiteMetier {

    public enum Type { VENTE, RETOUR }

    public enum Moyen { ESPECES, MOBILE_MONEY, CARTE }

    @Column(name = "session_id")
    private UUID sessionId;
    @Column(name = "societe_id")
    private UUID societeId;
    @Column(name = "etablissement_id")
    private UUID etablissementId;
    @Column(name = "terminal_id")
    private UUID terminalId;
    @Column(name = "caissier_id")
    private UUID caissierId;
    @Enumerated(EnumType.STRING)
    private Type type;
    @Column(name = "type_piece")
    private String typePiece;
    private int annee;
    private long sequence;
    private String numero;
    private Instant horodatage;
    @Column(name = "client_id")
    private UUID clientId;
    @Column(name = "vente_origine_id")
    private UUID venteOrigineId;
    @Column(name = "total_ht")
    private long totalHt;
    @Column(name = "total_taxes")
    private long totalTaxes;
    @Column(name = "total_ttc")
    private long totalTtc;
    private long remise;
    @Column(name = "facture_demandee")
    private boolean factureDemandee;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "vente_id", nullable = false, updatable = false)
    @OrderBy("rang")
    @org.hibernate.annotations.BatchSize(size = 100)
    private List<Ligne> lignes = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "vente_id", nullable = false, updatable = false)
    @org.hibernate.annotations.BatchSize(size = 100)
    private List<Encaissement> encaissements = new ArrayList<>();

    protected Vente() {
    }

    public record Entete(UUID id, UUID sessionId, UUID societeId, UUID etablissementId, UUID terminalId, UUID caissierId, Type type,
            String typePiece, int annee, long sequence, String numero, Instant horodatage, UUID clientId, UUID venteOrigineId, boolean factureDemandee) {
    }

    public Vente(Entete e, List<Ligne> lignes, List<Encaissement> encaissements) {
        super(e.id());
        this.sessionId = e.sessionId();
        this.societeId = e.societeId();
        this.etablissementId = e.etablissementId();
        this.terminalId = e.terminalId();
        this.caissierId = e.caissierId();
        this.type = e.type();
        this.typePiece = e.typePiece();
        this.annee = e.annee();
        this.sequence = e.sequence();
        this.numero = e.numero();
        this.horodatage = e.horodatage();
        this.clientId = e.clientId();
        this.venteOrigineId = e.venteOrigineId();
        this.factureDemandee = e.factureDemandee();
        this.lignes.addAll(lignes);
        this.encaissements.addAll(encaissements);
        this.totalHt = lignes.stream().mapToLong(Ligne::montantHt).sum();
        this.totalTaxes = lignes.stream().mapToLong(Ligne::montantTaxe).sum();
        this.totalTtc = lignes.stream().mapToLong(Ligne::montantTtc).sum();
        this.remise = lignes.stream().mapToLong(Ligne::remise).sum();
    }

    /** Espèces nettes de la pièce (encaissées pour une vente, remboursées pour un retour). */
    public long especes() {
        return encaissements.stream().filter(e -> e.moyen() == Moyen.ESPECES).mapToLong(Encaissement::montant).sum();
    }

    public UUID sessionId() { return sessionId; }
    public UUID societeId() { return societeId; }
    public UUID etablissementId() { return etablissementId; }
    public UUID caissierId() { return caissierId; }
    public Type type() { return type; }
    public String numero() { return numero; }
    public Instant horodatage() { return horodatage; }
    public UUID clientId() { return clientId; }
    public UUID venteOrigineId() { return venteOrigineId; }
    public long totalHt() { return totalHt; }
    public long totalTaxes() { return totalTaxes; }
    public long totalTtc() { return totalTtc; }
    public long remise() { return remise; }
    public boolean factureDemandee() { return factureDemandee; }
    public List<Ligne> lignes() { return List.copyOf(lignes); }
    public List<Encaissement> encaissements() { return List.copyOf(encaissements); }

    @Entity
    @Table(schema = "pos", name = "ligne_vente")
    public static class Ligne extends EntiteMetier {
        private int rang;
        @Column(name = "produit_id")
        private UUID produitId;
        private String libelle;
        private String conditionnement;
        private BigDecimal quantite;
        private BigDecimal facteur;
        @Column(name = "quantite_unite_stock")
        private BigDecimal quantiteUniteStock;
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

        public record Donnees(UUID id, int rang, UUID produitId, String libelle, String conditionnement, BigDecimal quantite, BigDecimal facteur,
                long prixUnitaire, boolean prixTtc, long remise, String taxeCode, BigDecimal taux, UUID ligneOrigineId) {
        }

        public Ligne(Donnees d) {
            super(d.id());
            var montants = CalculVente.ligne(d.quantite(), d.prixUnitaire(), d.prixTtc(), d.remise(), d.taux());
            this.rang = d.rang();
            this.produitId = d.produitId();
            this.libelle = d.libelle();
            this.conditionnement = d.conditionnement();
            this.quantite = d.quantite().setScale(3, RoundingMode.HALF_UP);
            this.facteur = (d.facteur() == null ? BigDecimal.ONE : d.facteur()).setScale(3, RoundingMode.HALF_UP);
            this.quantiteUniteStock = quantite.multiply(facteur).setScale(3, RoundingMode.HALF_UP);
            this.prixUnitaire = d.prixUnitaire();
            this.prixTtc = d.prixTtc();
            this.remise = d.remise();
            this.taxeCode = d.taxeCode();
            this.taux = d.taux();
            this.montantHt = montants.ht().valeur();
            this.montantTaxe = montants.taxe().valeur();
            this.montantTtc = montants.ttc().valeur();
            this.ligneOrigineId = d.ligneOrigineId();
        }

        public UUID produitId() { return produitId; }
        public String libelle() { return libelle; }
        public BigDecimal quantite() { return quantite; }
        public BigDecimal quantiteUniteStock() { return quantiteUniteStock; }
        public long prixUnitaire() { return prixUnitaire; }
        public long remise() { return remise; }
        public String taxeCode() { return taxeCode; }
        public BigDecimal taux() { return taux; }
        public long montantHt() { return montantHt; }
        public long montantTaxe() { return montantTaxe; }
        public long montantTtc() { return montantTtc; }
        public UUID ligneOrigineId() { return ligneOrigineId; }
    }

    @Entity
    @Table(schema = "pos", name = "encaissement")
    public static class Encaissement extends EntiteMetier {
        @Enumerated(EnumType.STRING)
        private Moyen moyen;
        private long montant;
        private Long recu;
        private Long rendu;
        private String operateur;
        private String reference;

        protected Encaissement() {
        }

        public Encaissement(UUID id, Moyen moyen, long montant, Long recu, Long rendu, String operateur, String reference) {
            super(id);
            this.moyen = moyen;
            this.montant = montant;
            this.recu = recu;
            this.rendu = rendu;
            this.operateur = operateur;
            this.reference = reference;
        }

        public Moyen moyen() { return moyen; }
        public long montant() { return montant; }
        public Long recu() { return recu; }
        public Long rendu() { return rendu; }
    }
}
