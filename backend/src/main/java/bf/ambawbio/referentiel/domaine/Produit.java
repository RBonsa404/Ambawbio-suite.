package bf.ambawbio.referentiel.domaine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import bf.ambawbio.shared.domaine.EntiteMetier;
import bf.ambawbio.shared.domaine.RegleMetierException;
import bf.ambawbio.shared.domaine.Uuid7;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Produit ou service (F-SOC-07). Prix en francs entiers (R-06) ; {@code prixVenteTtc} indique si le prix saisi
 * inclut les taxes (pratique de la caisse) ou non (devis entre entreprises) — décision D-17.
 */
@Entity
@Table(schema = "referentiel", name = "produit")
public class Produit extends EntiteMetier {

    public enum Type { BIEN, SERVICE }

    public record DonneesConditionnement(String code, String libelle, BigDecimal quantite, Long prixVente) {
    }

    public record DonneesCodeBarre(String valeur, String conditionnementCode) {
    }

    private String code;
    private String nom;
    @Enumerated(EnumType.STRING)
    private Type type;
    @Column(name = "categorie_id")
    private UUID categorieId;
    @Column(name = "unite_id")
    private UUID uniteId;
    @Column(name = "taxe_id")
    private UUID taxeId;
    @Column(name = "prix_vente")
    private long prixVente;
    @Column(name = "prix_vente_ttc")
    private boolean prixVenteTtc;
    @Column(name = "prix_achat")
    private Long prixAchat;
    @Column(name = "suivi_stock")
    private boolean suiviStock;
    private boolean actif;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "produit_id", nullable = false, updatable = false)
    @org.hibernate.annotations.BatchSize(size = 100)
    private List<Conditionnement> conditionnements = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "produit_id", nullable = false, updatable = false)
    @org.hibernate.annotations.BatchSize(size = 100)
    private List<CodeBarre> codesBarres = new ArrayList<>();

    protected Produit() {
    }

    public Produit(UUID id, String code) {
        super(id);
        if (code == null || code.isBlank()) {
            throw new RegleMetierException("CODE_OBLIGATOIRE", "Le code du produit est obligatoire.");
        }
        this.code = code.trim();
        this.actif = true;
    }

    public void modifier(String nom, Type type, UUID categorieId, UUID uniteId, UUID taxeId, long prixVente, boolean prixVenteTtc,
            Long prixAchat, boolean suiviStock, boolean actif, Map<String, Object> champsPerso) {
        if (nom == null || nom.isBlank()) {
            throw new RegleMetierException("NOM_OBLIGATOIRE", "Le nom du produit est obligatoire.");
        }
        if (prixVente < 0 || (prixAchat != null && prixAchat < 0)) {
            throw new RegleMetierException("PRIX_INVALIDE", "Les prix ne peuvent pas être négatifs.");
        }
        this.nom = nom.trim();
        this.type = type == null ? Type.BIEN : type;
        this.categorieId = categorieId;
        this.uniteId = uniteId;
        this.taxeId = taxeId;
        this.prixVente = prixVente;
        this.prixVenteTtc = prixVenteTtc;
        this.prixAchat = prixAchat;
        this.suiviStock = this.type == Type.BIEN && suiviStock;
        this.actif = actif;
        champsPerso().clear();
        champsPerso().putAll(champsPerso);
    }

    /** Mise à jour en place (par code) pour respecter l'unicité en base sans suppression préalable. */
    public void definirConditionnements(List<DonneesConditionnement> donnees) {
        var codes = new HashSet<String>();
        for (var d : donnees) {
            if (!codes.add(d.code())) {
                throw new RegleMetierException("CONDITIONNEMENT_DOUBLON", "Le conditionnement « " + d.code() + " » est saisi deux fois.");
            }
        }
        conditionnements.removeIf(c -> !codes.contains(c.code()));
        for (var d : donnees) {
            conditionnements.stream().filter(c -> c.code().equals(d.code())).findFirst()
                    .ifPresentOrElse(c -> c.modifier(d.libelle(), d.quantite(), d.prixVente()),
                            () -> conditionnements.add(new Conditionnement(Uuid7.nouveau(), d.code(), d.libelle(), d.quantite(), d.prixVente())));
        }
    }

    public void definirCodesBarres(List<DonneesCodeBarre> donnees) {
        var valeurs = new HashSet<String>();
        for (var d : donnees) {
            if (!valeurs.add(d.valeur())) {
                throw new RegleMetierException("CODE_BARRE_DOUBLON", "Le code-barres « " + d.valeur() + " » est saisi deux fois.");
            }
            if (d.conditionnementCode() != null && conditionnements.stream().noneMatch(c -> c.code().equals(d.conditionnementCode()))) {
                throw new RegleMetierException("CONDITIONNEMENT_INCONNU", "Le code-barres « " + d.valeur() + " » vise un conditionnement absent.");
            }
        }
        codesBarres.removeIf(c -> !valeurs.contains(c.valeur()));
        for (var d : donnees) {
            codesBarres.stream().filter(c -> c.valeur().equals(d.valeur())).findFirst()
                    .ifPresentOrElse(c -> c.rattacher(d.conditionnementCode()),
                            () -> codesBarres.add(new CodeBarre(Uuid7.nouveau(), d.valeur(), d.conditionnementCode())));
        }
    }

    public String code() { return code; }
    public String nom() { return nom; }
    public Type type() { return type; }
    public UUID categorieId() { return categorieId; }
    public UUID uniteId() { return uniteId; }
    public UUID taxeId() { return taxeId; }
    public long prixVente() { return prixVente; }
    public boolean prixVenteTtc() { return prixVenteTtc; }
    public Long prixAchat() { return prixAchat; }
    public boolean suiviStock() { return suiviStock; }
    public boolean actif() { return actif; }
    public List<Conditionnement> conditionnements() { return List.copyOf(conditionnements); }
    public List<CodeBarre> codesBarres() { return List.copyOf(codesBarres); }

    /** Conditionnement (sac, carton…) exprimé en unités de base du produit (F-STK-03). */
    @Entity
    @Table(schema = "referentiel", name = "conditionnement")
    public static class Conditionnement extends EntiteMetier {
        private String code;
        private String libelle;
        private BigDecimal quantite;
        @Column(name = "prix_vente")
        private Long prixVente;

        protected Conditionnement() {
        }

        Conditionnement(UUID id, String code, String libelle, BigDecimal quantite, Long prixVente) {
            super(id);
            this.code = code;
            modifier(libelle, quantite, prixVente);
        }

        final void modifier(String libelle, BigDecimal quantite, Long prixVente) {
            if (quantite == null || quantite.signum() <= 0) {
                throw new RegleMetierException("QUANTITE_INVALIDE", "La quantité d'un conditionnement doit être positive.");
            }
            this.libelle = libelle == null || libelle.isBlank() ? code : libelle;
            this.quantite = quantite.setScale(3, java.math.RoundingMode.HALF_UP);
            this.prixVente = prixVente;
        }

        public String code() { return code; }
        public String libelle() { return libelle; }
        public BigDecimal quantite() { return quantite; }
        public Long prixVente() { return prixVente; }
    }

    @Entity
    @Table(schema = "referentiel", name = "code_barre")
    public static class CodeBarre extends EntiteMetier {
        private String valeur;
        @Column(name = "conditionnement_code")
        private String conditionnementCode;

        protected CodeBarre() {
        }

        CodeBarre(UUID id, String valeur, String conditionnementCode) {
            super(id);
            this.valeur = valeur;
            this.conditionnementCode = conditionnementCode;
        }

        void rattacher(String conditionnementCode) {
            this.conditionnementCode = conditionnementCode;
        }

        public String valeur() { return valeur; }
        public String conditionnementCode() { return conditionnementCode; }
    }
}
