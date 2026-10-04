package bf.ambawbio.referentiel.domaine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import bf.ambawbio.shared.domaine.EntiteMetier;
import bf.ambawbio.shared.domaine.Ifu;
import bf.ambawbio.shared.domaine.RegleMetierException;
import bf.ambawbio.shared.domaine.Telephone;
import bf.ambawbio.shared.domaine.Uuid7;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/** Tiers unique, client et/ou fournisseur, partagé par tous les modules (F-SOC-06). */
@Entity
@Table(schema = "referentiel", name = "tiers")
public class Tiers extends EntiteMetier {

    public enum Nature { PARTICULIER, ENTREPRISE, ADMINISTRATION, ONG }

    public enum Operateur { ORANGE_MONEY, MOOV_MONEY, WAVE }

    public record Identification(String nom, Nature nature, boolean estClient, boolean estFournisseur, String ifu, String rccm,
            UUID regimeFiscalId, boolean regimeAssujetti) {
    }

    public record Coordonnees(String telephone, String courriel, String adresse, String ville) {
    }

    public record ConditionsCommerciales(UUID listePrixId, int delaiPaiementJours, Long plafondCredit) {
    }

    public record DonneesContact(String nom, String fonction, String telephone, String courriel, boolean consentementProspection) {
    }

    public record DonneesCompte(Operateur operateur, String numero, String titulaire, boolean parDefaut) {
    }

    private String code;
    private String nom;
    @Enumerated(EnumType.STRING)
    private Nature nature;
    @Column(name = "est_client")
    private boolean estClient;
    @Column(name = "est_fournisseur")
    private boolean estFournisseur;
    private String ifu;
    private String rccm;
    @Column(name = "regime_fiscal_id")
    private UUID regimeFiscalId;
    private String telephone;
    private String courriel;
    private String adresse;
    private String ville;
    @Column(name = "liste_prix_id")
    private UUID listePrixId;
    @Column(name = "delai_paiement_jours")
    private int delaiPaiementJours;
    @Column(name = "plafond_credit")
    private Long plafondCredit;
    private boolean actif;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "tiers_id", nullable = false, updatable = false)
    @org.hibernate.annotations.BatchSize(size = 100)
    private List<Contact> contacts = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "tiers_id", nullable = false, updatable = false)
    @org.hibernate.annotations.BatchSize(size = 100)
    private List<CompteMobileMoney> comptesMobileMoney = new ArrayList<>();

    protected Tiers() {
    }

    public Tiers(UUID id, String code) {
        super(id);
        if (code == null || code.isBlank()) {
            throw new RegleMetierException("CODE_OBLIGATOIRE", "Le code du tiers est obligatoire.");
        }
        this.code = code.trim();
        this.actif = true;
    }

    public void modifier(Identification id, Coordonnees coordonnees, ConditionsCommerciales conditions, boolean actif, Map<String, Object> champsPerso) {
        if (id.nom() == null || id.nom().isBlank()) {
            throw new RegleMetierException("NOM_OBLIGATOIRE", "Le nom du tiers est obligatoire.");
        }
        if (!id.estClient() && !id.estFournisseur()) {
            throw new RegleMetierException("ROLE_TIERS_OBLIGATOIRE", "Un tiers est client, fournisseur, ou les deux.");
        }
        var ifuNormalise = id.ifu() == null || id.ifu().isBlank() ? null : Ifu.depuisSaisie(id.ifu()).valeur();
        // INV-03 : IFU obligatoire pour un client assujetti (régime fiscal soumis à la TVA).
        if (id.estClient() && id.regimeAssujetti() && ifuNormalise == null) {
            throw new RegleMetierException("INV-03", "L'IFU est obligatoire pour un client assujetti à la TVA.");
        }
        this.nom = id.nom().trim();
        this.nature = id.nature() == null ? (ifuNormalise == null ? Nature.PARTICULIER : Nature.ENTREPRISE) : id.nature();
        this.estClient = id.estClient();
        this.estFournisseur = id.estFournisseur();
        this.ifu = ifuNormalise;
        this.rccm = id.rccm();
        this.regimeFiscalId = id.regimeFiscalId();
        this.telephone = Telephone.normaliser(coordonnees.telephone());
        this.courriel = coordonnees.courriel() == null || coordonnees.courriel().isBlank() ? null : coordonnees.courriel().trim();
        this.adresse = coordonnees.adresse();
        this.ville = coordonnees.ville();
        if (conditions.delaiPaiementJours() < 0 || (conditions.plafondCredit() != null && conditions.plafondCredit() < 0)) {
            throw new RegleMetierException("CONDITIONS_INVALIDES", "Le délai de paiement et le plafond de crédit ne peuvent pas être négatifs.");
        }
        this.listePrixId = conditions.listePrixId();
        this.delaiPaiementJours = conditions.delaiPaiementJours();
        this.plafondCredit = conditions.plafondCredit();
        this.actif = actif;
        champsPerso().clear();
        champsPerso().putAll(champsPerso);
    }

    public void definirContacts(List<DonneesContact> donnees) {
        contacts.clear();
        donnees.forEach(d -> contacts.add(new Contact(Uuid7.nouveau(), d)));
    }

    public void definirComptesMobileMoney(List<DonneesCompte> donnees) {
        if (donnees.stream().filter(DonneesCompte::parDefaut).count() > 1) {
            throw new RegleMetierException("COMPTE_PAR_DEFAUT", "Un seul compte Mobile Money peut être marqué par défaut.");
        }
        comptesMobileMoney.clear();
        donnees.forEach(d -> comptesMobileMoney.add(new CompteMobileMoney(Uuid7.nouveau(), d)));
    }

    public String code() { return code; }
    public String nom() { return nom; }
    public Nature nature() { return nature; }
    public boolean estClient() { return estClient; }
    public boolean estFournisseur() { return estFournisseur; }
    public String ifu() { return ifu; }
    public String rccm() { return rccm; }
    public UUID regimeFiscalId() { return regimeFiscalId; }
    public String telephone() { return telephone; }
    public String courriel() { return courriel; }
    public String adresse() { return adresse; }
    public String ville() { return ville; }
    public UUID listePrixId() { return listePrixId; }
    public int delaiPaiementJours() { return delaiPaiementJours; }
    public Long plafondCredit() { return plafondCredit; }
    public boolean actif() { return actif; }
    public List<Contact> contacts() { return List.copyOf(contacts); }
    public List<CompteMobileMoney> comptesMobileMoney() { return List.copyOf(comptesMobileMoney); }

    /** Contact d'un tiers ; le consentement à la prospection est recueilli explicitement et daté (F-SOC-18). */
    @Entity
    @Table(schema = "referentiel", name = "contact")
    public static class Contact extends EntiteMetier {
        private String nom;
        private String fonction;
        private String telephone;
        private String courriel;
        @Column(name = "consentement_prospection")
        private boolean consentementProspection;
        @Column(name = "consentement_le")
        private Instant consentementLe;

        protected Contact() {
        }

        Contact(UUID id, DonneesContact d) {
            super(id);
            if (d.nom() == null || d.nom().isBlank()) {
                throw new RegleMetierException("NOM_OBLIGATOIRE", "Le nom du contact est obligatoire.");
            }
            this.nom = d.nom();
            this.fonction = d.fonction();
            this.telephone = Telephone.normaliser(d.telephone());
            this.courriel = d.courriel();
            this.consentementProspection = d.consentementProspection();
            this.consentementLe = d.consentementProspection() ? Instant.now() : null;
        }

        public String nom() { return nom; }
        public String fonction() { return fonction; }
        public String telephone() { return telephone; }
        public String courriel() { return courriel; }
        public boolean consentementProspection() { return consentementProspection; }
        public Instant consentementLe() { return consentementLe; }
    }

    /** Compte Mobile Money d'un tiers : opérateur en texte, jamais de logo (charte, brief §7.3). */
    @Entity
    @Table(schema = "referentiel", name = "compte_mobile_money")
    public static class CompteMobileMoney extends EntiteMetier {
        @Enumerated(EnumType.STRING)
        private Operateur operateur;
        private String numero;
        private String titulaire;
        @Column(name = "par_defaut")
        private boolean parDefaut;

        protected CompteMobileMoney() {
        }

        CompteMobileMoney(UUID id, DonneesCompte d) {
            super(id);
            if (d.operateur() == null) {
                throw new RegleMetierException("OPERATEUR_OBLIGATOIRE", "L'opérateur Mobile Money est obligatoire.");
            }
            this.operateur = d.operateur();
            this.numero = Telephone.normaliser(d.numero());
            if (this.numero == null) {
                throw new RegleMetierException("TELEPHONE_INVALIDE", "Le numéro Mobile Money est obligatoire.");
            }
            this.titulaire = d.titulaire();
            this.parDefaut = d.parDefaut();
        }

        public Operateur operateur() { return operateur; }
        public String numero() { return numero; }
        public String titulaire() { return titulaire; }
        public boolean parDefaut() { return parDefaut; }
    }
}
