package bf.ambawbio.pos.domaine;

import java.util.UUID;

import bf.ambawbio.shared.domaine.EntiteMetier;
import bf.ambawbio.shared.domaine.RegleMetierException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Point de vente (caisse) d'un établissement (F-POS-03) et ses paramètres. */
@Entity
@Table(schema = "pos", name = "point_de_vente")
public class PointDeVente extends EntiteMetier {

    public enum VenteSansStock { AUTORISER_ALERTE, BLOQUER }

    @Column(name = "etablissement_id")
    private UUID etablissementId;
    private String code;
    private String nom;
    @Column(name = "seuil_ecart")
    private long seuilEcart;
    @Column(name = "comptage_aveugle")
    private boolean comptageAveugle;
    @Column(name = "remise_max_pourcent")
    private int remiseMaxPourcent;
    @Enumerated(EnumType.STRING)
    @Column(name = "vente_sans_stock")
    private VenteSansStock venteSansStock;
    private boolean actif;

    protected PointDeVente() {
    }

    public PointDeVente(UUID id, UUID etablissementId, String code) {
        super(id);
        this.etablissementId = etablissementId;
        if (code == null || code.isBlank()) {
            throw new RegleMetierException("CODE_OBLIGATOIRE", "Le code du point de vente est obligatoire.");
        }
        this.code = code.trim().toUpperCase();
    }

    public void parametrer(String nom, long seuilEcart, boolean comptageAveugle, int remiseMaxPourcent, VenteSansStock venteSansStock, boolean actif) {
        if (nom == null || nom.isBlank()) {
            throw new RegleMetierException("NOM_OBLIGATOIRE", "Le nom du point de vente est obligatoire.");
        }
        if (seuilEcart < 0 || remiseMaxPourcent < 0 || remiseMaxPourcent > 100) {
            throw new RegleMetierException("PARAMETRE_INVALIDE", "Le seuil d'écart est positif et la remise maximale comprise entre 0 et 100 %.");
        }
        this.nom = nom.trim();
        this.seuilEcart = seuilEcart;
        this.comptageAveugle = comptageAveugle;
        this.remiseMaxPourcent = remiseMaxPourcent;
        this.venteSansStock = venteSansStock == null ? VenteSansStock.AUTORISER_ALERTE : venteSansStock;
        this.actif = actif;
    }

    public UUID etablissementId() { return etablissementId; }
    public String code() { return code; }
    public String nom() { return nom; }
    public long seuilEcart() { return seuilEcart; }
    public boolean comptageAveugle() { return comptageAveugle; }
    public int remiseMaxPourcent() { return remiseMaxPourcent; }
    public VenteSansStock venteSansStock() { return venteSansStock; }
    public boolean actif() { return actif; }
}
