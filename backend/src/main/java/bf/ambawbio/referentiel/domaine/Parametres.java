package bf.ambawbio.referentiel.domaine;

import java.math.BigDecimal;
import java.util.UUID;

import bf.ambawbio.shared.domaine.EntiteMetier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Paramètres simples du référentiel, regroupés ici : régime fiscal, taxe, unité, type de conditionnement, catégorie. */
public final class Parametres {

    private Parametres() {
    }

    /** Régime d'imposition (Q-03 : RNI, RSI, CME par défaut, paramétrables). */
    @Entity
    @Table(schema = "referentiel", name = "regime_fiscal")
    public static class RegimeFiscal extends EntiteMetier {
        private String code;
        private String libelle;
        @Column(name = "assujetti_tva")
        private boolean assujettiTva;
        private boolean systeme;

        protected RegimeFiscal() {
        }

        public RegimeFiscal(UUID id, String code, String libelle, boolean assujettiTva, boolean systeme) {
            super(id);
            this.code = code;
            this.libelle = libelle;
            this.assujettiTva = assujettiTva;
            this.systeme = systeme;
        }

        public String code() { return code; }
        public String libelle() { return libelle; }
        public boolean assujettiTva() { return assujettiTva; }
        public boolean systeme() { return systeme; }
    }

    /** Taxe applicable à un produit (taux en %, arrondi au franc lors du calcul, guide §11.3). */
    @Entity
    @Table(schema = "referentiel", name = "taxe")
    public static class Taxe extends EntiteMetier {
        private String code;
        private String libelle;
        private BigDecimal taux;
        private boolean actif;
        @Column(name = "a_valider")
        private boolean aValider;

        protected Taxe() {
        }

        public Taxe(UUID id, String code, String libelle, BigDecimal taux, boolean aValider) {
            super(id);
            this.code = code;
            this.actif = true;
            this.aValider = aValider;
            modifier(libelle, taux, true);
        }

        /** RG-06 : les paramètres fiscaux se modifient en ligne uniquement (jamais depuis un terminal hors-ligne). */
        public final void modifier(String libelle, BigDecimal taux, boolean actif) {
            if (taux == null || taux.signum() < 0 || taux.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new bf.ambawbio.shared.domaine.RegleMetierException("TAUX_INVALIDE", "Le taux doit être compris entre 0 et 100 %.");
            }
            this.libelle = libelle;
            this.taux = taux;
            this.actif = actif;
        }

        public String code() { return code; }
        public String libelle() { return libelle; }
        public BigDecimal taux() { return taux; }
        public boolean actif() { return actif; }
        public boolean aValider() { return aValider; }
    }

    @Entity
    @Table(schema = "referentiel", name = "unite_mesure")
    public static class UniteMesure extends EntiteMetier {
        public enum Categorie { UNITE, POIDS, VOLUME, LONGUEUR, SURFACE, TEMPS }

        private String code;
        private String libelle;
        @Enumerated(EnumType.STRING)
        private Categorie categorie;

        protected UniteMesure() {
        }

        public UniteMesure(UUID id, String code, String libelle, Categorie categorie) {
            super(id);
            this.code = code;
            this.libelle = libelle;
            this.categorie = categorie;
        }

        public String code() { return code; }
        public String libelle() { return libelle; }
        public Categorie categorie() { return categorie; }
    }

    @Entity
    @Table(schema = "referentiel", name = "type_conditionnement")
    public static class TypeConditionnement extends EntiteMetier {
        private String code;
        private String libelle;

        protected TypeConditionnement() {
        }

        public TypeConditionnement(UUID id, String code, String libelle) {
            super(id);
            this.code = code;
            this.libelle = libelle;
        }

        public String code() { return code; }
        public String libelle() { return libelle; }
    }

    @Entity
    @Table(schema = "referentiel", name = "categorie")
    public static class Categorie extends EntiteMetier {
        private String nom;
        @Column(name = "parent_id")
        private UUID parentId;

        protected Categorie() {
        }

        public Categorie(UUID id, String nom, UUID parentId) {
            super(id);
            this.nom = nom;
            this.parentId = parentId;
        }

        public void modifier(String nom, UUID parentId) {
            this.nom = nom;
            this.parentId = parentId;
        }

        public String nom() { return nom; }
        public UUID parentId() { return parentId; }
    }
}
