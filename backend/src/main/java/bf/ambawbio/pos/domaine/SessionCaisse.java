package bf.ambawbio.pos.domaine;

import java.time.Instant;
import java.util.UUID;

import bf.ambawbio.shared.domaine.EntiteMetier;
import bf.ambawbio.shared.domaine.RegleMetierException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Session de caisse (F-POS-02) : ouverture avec fonds, clôture avec comptage, écart validé au-delà du seuil (RG-09). */
@Entity
@Table(schema = "pos", name = "session_caisse")
public class SessionCaisse extends EntiteMetier {

    public enum Statut { OUVERTE, EN_CONFLIT, CLOTUREE, ECART_A_VALIDER, ECART_VALIDE }

    @Column(name = "point_de_vente_id")
    private UUID pointDeVenteId;
    @Column(name = "etablissement_id")
    private UUID etablissementId;
    @Column(name = "terminal_id")
    private UUID terminalId;
    @Column(name = "caissier_id")
    private UUID caissierId;
    @Enumerated(EnumType.STRING)
    private Statut statut;
    @Column(name = "ouverte_le")
    private Instant ouverteLe;
    @Column(name = "fonds_initial")
    private long fondsInitial;
    @Column(name = "cloturee_le")
    private Instant clotureeLe;
    @Column(name = "especes_comptees")
    private Long especesComptees;
    @Column(name = "especes_theoriques")
    private Long especesTheoriques;
    private Long ecart;
    @Column(name = "validee_par")
    private UUID valideePar;
    @Column(name = "validee_le")
    private Instant valideeLe;
    @Column(name = "motif_validation")
    private String motifValidation;

    protected SessionCaisse() {
    }

    /**
     * @param concurrente vrai si une autre session est déjà ouverte sur ce point de vente : la session est conservée
     *                    (ses ventes ne sont jamais perdues) mais marquée en conflit pour le responsable (INV-14, D-30).
     */
    public SessionCaisse(UUID id, UUID pointDeVenteId, UUID etablissementId, UUID terminalId, UUID caissierId, Instant ouverteLe, long fondsInitial,
            boolean concurrente) {
        super(id);
        if (fondsInitial < 0) {
            throw new RegleMetierException("FONDS_INVALIDE", "Le fonds de caisse ne peut pas être négatif.");
        }
        this.pointDeVenteId = pointDeVenteId;
        this.etablissementId = etablissementId;
        this.terminalId = terminalId;
        this.caissierId = caissierId;
        this.ouverteLe = ouverteLe;
        this.fondsInitial = fondsInitial;
        this.statut = concurrente ? Statut.EN_CONFLIT : Statut.OUVERTE;
    }

    /** Clôture : l'écart au-delà du seuil doit être validé par un responsable (RG-09). */
    public void cloturer(long comptees, long theoriques, long seuil, Instant le) {
        if (statut != Statut.OUVERTE && statut != Statut.EN_CONFLIT) {
            throw new RegleMetierException("SESSION_DEJA_CLOTUREE", "Cette session de caisse est déjà clôturée.");
        }
        if (comptees < 0) {
            throw new RegleMetierException("COMPTAGE_INVALIDE", "Le montant compté ne peut pas être négatif.");
        }
        this.especesComptees = comptees;
        this.especesTheoriques = theoriques;
        this.ecart = comptees - theoriques;
        this.clotureeLe = le;
        this.statut = Math.abs(ecart) > seuil ? Statut.ECART_A_VALIDER : Statut.CLOTUREE;
    }

    public void validerEcart(UUID responsable, String motif, Instant le) {
        if (statut != Statut.ECART_A_VALIDER) {
            throw new RegleMetierException("AUCUN_ECART_A_VALIDER", "Cette session n'a pas d'écart en attente de validation.");
        }
        if (motif == null || motif.isBlank()) {
            throw new RegleMetierException("MOTIF_OBLIGATOIRE", "Indiquez la raison de l'écart.");
        }
        this.valideePar = responsable;
        this.motifValidation = motif.trim();
        this.valideeLe = le;
        this.statut = Statut.ECART_VALIDE;
    }

    public UUID pointDeVenteId() { return pointDeVenteId; }
    public UUID etablissementId() { return etablissementId; }
    public UUID terminalId() { return terminalId; }
    public UUID caissierId() { return caissierId; }
    public Statut statut() { return statut; }
    public Instant ouverteLe() { return ouverteLe; }
    public long fondsInitial() { return fondsInitial; }
    public Instant clotureeLe() { return clotureeLe; }
    public Long especesComptees() { return especesComptees; }
    public Long especesTheoriques() { return especesTheoriques; }
    public Long ecart() { return ecart; }
    public UUID valideePar() { return valideePar; }
    public Instant valideeLe() { return valideeLe; }
    public String motifValidation() { return motifValidation; }
}
