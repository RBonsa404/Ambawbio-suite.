package bf.ambawbio.sync.terminaux;

import java.time.Instant;
import java.util.UUID;

import bf.ambawbio.shared.domaine.EntiteMetier;
import bf.ambawbio.shared.domaine.RegleMetierException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Terminal de caisse ou de magasin, appairé par QR code et identifié par sa clé publique ECDSA (UC-SOC-04, SD-11). */
@Entity
@Table(schema = "sync", name = "terminal")
public class Terminal extends EntiteMetier {

    public enum Statut { EN_ATTENTE_APPAIRAGE, ACTIF, REVOQUE }

    @Column(name = "etablissement_id")
    private UUID etablissementId;
    private String code;
    private String nom;
    @Enumerated(EnumType.STRING)
    private Statut statut;
    @Column(name = "empreinte_code_appairage")
    private String empreinteCodeAppairage;
    @Column(name = "appairage_expire_le")
    private Instant appairageExpireLe;
    @Column(name = "cle_publique")
    private String clePublique;
    @Column(name = "appaire_le")
    private Instant appaireLe;
    @Column(name = "revoque_le")
    private Instant revoqueLe;
    @Column(name = "derniere_synchro")
    private Instant derniereSynchro;

    protected Terminal() {
    }

    public Terminal(UUID id, UUID etablissementId, String code, String nom) {
        super(id);
        this.etablissementId = etablissementId;
        this.code = code;
        this.nom = nom;
        this.statut = Statut.EN_ATTENTE_APPAIRAGE;
    }

    /** Nouveau code d'appairage à usage unique (affiché en QR code), valable 15 minutes. */
    void preparerAppairage(String empreinteCode, Instant expiration) {
        if (statut == Statut.REVOQUE) {
            throw new RegleMetierException("TERMINAL_REVOQUE", "Ce terminal est révoqué. Créez un nouveau terminal.");
        }
        this.empreinteCodeAppairage = empreinteCode;
        this.appairageExpireLe = expiration;
    }

    void appairer(String empreinteCode, String clePublique, Instant maintenant) {
        if (statut != Statut.EN_ATTENTE_APPAIRAGE || empreinteCodeAppairage == null) {
            throw new RegleMetierException("APPAIRAGE_IMPOSSIBLE", "Ce terminal n'attend pas d'appairage. Demandez un nouveau QR code à l'administrateur.");
        }
        if (appairageExpireLe.isBefore(maintenant)) {
            throw new RegleMetierException("CODE_EXPIRE", "Le QR code a expiré. Demandez-en un nouveau à l'administrateur.");
        }
        if (!java.security.MessageDigest.isEqual(empreinteCodeAppairage.getBytes(), empreinteCode.getBytes())) {
            throw new RegleMetierException("CODE_INCORRECT", "Code d'appairage incorrect.");
        }
        this.clePublique = clePublique;
        this.statut = Statut.ACTIF;
        this.appaireLe = maintenant;
        this.empreinteCodeAppairage = null;
        this.appairageExpireLe = null;
    }

    void revoquer(Instant maintenant) {
        this.statut = Statut.REVOQUE;
        this.revoqueLe = maintenant;
        this.empreinteCodeAppairage = null;
    }

    void synchronise(Instant maintenant) {
        this.derniereSynchro = maintenant;
    }

    public UUID etablissementId() { return etablissementId; }
    public String code() { return code; }
    public String nom() { return nom; }
    public Statut statut() { return statut; }
    public String clePublique() { return clePublique; }
    public Instant appaireLe() { return appaireLe; }
    public Instant revoqueLe() { return revoqueLe; }
    public Instant derniereSynchro() { return derniereSynchro; }
    public Instant appairageExpireLe() { return appairageExpireLe; }
}
