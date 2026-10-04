package bf.ambawbio.sync.plages;

import java.util.UUID;

import bf.ambawbio.shared.domaine.EntiteMetier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Plage de numéros réservée à un terminal pour les pièces émises hors-ligne (F-SOC-13, guide §8.6). */
@Entity
@Table(schema = "sync", name = "plage_numerotation")
public class PlageNumerotation extends EntiteMetier {

    public enum TypePiece {
        TICKET("TK"), FACTURE("FA"), AVOIR("AV");

        private final String prefixe;

        TypePiece(String prefixe) {
            this.prefixe = prefixe;
        }

        public String prefixe() {
            return prefixe;
        }
    }

    public enum Statut { ACTIVE, EPUISEE, CLOTUREE }

    @Column(name = "societe_id")
    private UUID societeId;
    @Column(name = "terminal_id")
    private UUID terminalId;
    @Enumerated(EnumType.STRING)
    @Column(name = "type_piece")
    private TypePiece typePiece;
    private int annee;
    private long debut;
    private long fin;
    private long prochain;
    @Enumerated(EnumType.STRING)
    private Statut statut;

    protected PlageNumerotation() {
    }

    PlageNumerotation(UUID id, UUID societeId, UUID terminalId, TypePiece type, int annee, long debut, long fin) {
        super(id);
        this.societeId = societeId;
        this.terminalId = terminalId;
        this.typePiece = type;
        this.annee = annee;
        this.debut = debut;
        this.fin = fin;
        this.prochain = debut;
        this.statut = Statut.ACTIVE;
    }

    /** Le terminal signale le prochain numéro qu'il utilisera ; la valeur ne recule jamais. */
    void signalerProchain(long valeur) {
        prochain = Math.max(prochain, Math.min(valeur, fin + 1));
        if (prochain > fin) {
            statut = Statut.EPUISEE;
        }
    }

    void cloturer() {
        statut = Statut.CLOTUREE;
    }

    /** RG-04 : alerte à 80 % de consommation. */
    public boolean seuilAlerteAtteint() {
        return (prochain - debut) * 100 >= (fin - debut + 1) * 80L;
    }

    public UUID societeId() { return societeId; }
    public UUID terminalId() { return terminalId; }
    public TypePiece typePiece() { return typePiece; }
    public int annee() { return annee; }
    public long debut() { return debut; }
    public long fin() { return fin; }
    public long prochain() { return prochain; }
    public Statut statut() { return statut; }
}
