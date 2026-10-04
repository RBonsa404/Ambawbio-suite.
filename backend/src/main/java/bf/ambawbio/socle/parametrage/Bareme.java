package bf.ambawbio.socle.parametrage;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import bf.ambawbio.shared.domaine.EntiteMetier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Barème versionné par date d'effet (RG-10, R-11) : taux de TVA, retenues, barèmes IUTS/CNSS, mentions…
 * Une version n'est jamais modifiée : on crée une nouvelle version avec une nouvelle date d'effet.
 */
@Entity
@Immutable
@Table(schema = "socle", name = "bareme")
public class Bareme extends EntiteMetier {

    private String code;
    @Column(name = "date_effet")
    private LocalDate dateEffet;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> valeur;
    private String description;
    @Column(name = "a_valider")
    private boolean aValider;

    protected Bareme() {
    }

    public Bareme(UUID id, String code, LocalDate dateEffet, Map<String, Object> valeur, String description, boolean aValider) {
        super(id);
        this.code = code;
        this.dateEffet = dateEffet;
        this.valeur = valeur;
        this.description = description;
        this.aValider = aValider;
    }

    public String code() { return code; }
    public LocalDate dateEffet() { return dateEffet; }
    public Map<String, Object> valeur() { return valeur; }
    public String description() { return description; }
    public boolean aValider() { return aValider; }
}
