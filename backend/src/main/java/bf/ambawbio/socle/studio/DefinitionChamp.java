package bf.ambawbio.socle.studio;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import bf.ambawbio.shared.domaine.EntiteMetier;
import bf.ambawbio.shared.domaine.RegleMetierException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Définition d'un champ personnalisé (UC-SOC-10). Le code et le type ne changent plus après création. */
@Entity
@Table(schema = "socle", name = "definition_champ")
public class DefinitionChamp extends EntiteMetier {

    public enum Type { TEXTE, NOMBRE, DATE, BOOLEEN, LISTE }

    public static final List<String> ENTITES = List.of("produit", "tiers");

    private String entite;
    private String code;
    private String libelle;
    @Enumerated(EnumType.STRING)
    private Type type;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> options = new ArrayList<>();
    private boolean obligatoire;
    private boolean filtrable;
    private int ordre;

    protected DefinitionChamp() {
    }

    public DefinitionChamp(UUID id, String entite, String code, String libelle, Type type, List<String> options,
            boolean obligatoire, boolean filtrable, int ordre) {
        super(id);
        if (!ENTITES.contains(entite)) {
            throw new RegleMetierException("ENTITE_INCONNUE", "Les champs personnalisés sont disponibles pour : " + String.join(", ", ENTITES) + ".");
        }
        if (code == null || !code.matches("[a-z][a-z0-9_]{1,39}")) {
            throw new RegleMetierException("CODE_INVALIDE", "Le code doit commencer par une lettre minuscule (lettres, chiffres, _).");
        }
        this.entite = entite;
        this.code = code;
        this.type = type;
        modifier(libelle, options, obligatoire, filtrable, ordre);
    }

    public void modifier(String libelle, List<String> options, boolean obligatoire, boolean filtrable, int ordre) {
        if (type == Type.LISTE && (options == null || options.isEmpty())) {
            throw new RegleMetierException("OPTIONS_OBLIGATOIRES", "Un champ de type liste doit proposer au moins une valeur.");
        }
        this.libelle = libelle;
        this.options = options == null ? new ArrayList<>() : new ArrayList<>(options);
        this.obligatoire = obligatoire;
        this.filtrable = filtrable;
        this.ordre = ordre;
    }

    public String entite() { return entite; }
    public String code() { return code; }
    public String libelle() { return libelle; }
    public Type type() { return type; }
    public List<String> options() { return List.copyOf(options); }
    public boolean obligatoire() { return obligatoire; }
    public boolean filtrable() { return filtrable; }
    public int ordre() { return ordre; }
}
