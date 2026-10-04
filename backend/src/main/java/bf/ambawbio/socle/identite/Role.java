package bf.ambawbio.socle.identite;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import bf.ambawbio.shared.domaine.EntiteMetier;
import bf.ambawbio.shared.domaine.RegleMetierException;
import bf.ambawbio.socle.api.Permissions;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Rôle : ensemble de permissions « module:action ». Les rôles système sont créés avec l'entreprise. */
@Entity
@Table(schema = "socle", name = "role")
public class Role extends EntiteMetier {

    private String code;
    private String libelle;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "permissions", columnDefinition = "text[]")
    private List<String> permissions = new ArrayList<>();
    private boolean systeme;

    protected Role() {
    }

    public Role(UUID id, String code, String libelle, List<String> permissions, boolean systeme) {
        super(id);
        this.code = code;
        this.libelle = libelle;
        this.systeme = systeme;
        definirPermissions(permissions);
    }

    public void modifier(String libelle, List<String> permissions) {
        if (systeme) {
            throw new RegleMetierException("ROLE_SYSTEME", "Les rôles fournis par Ambawbio ne sont pas modifiables. Créez un rôle personnalisé.");
        }
        this.libelle = libelle;
        definirPermissions(permissions);
    }

    private void definirPermissions(List<String> demandees) {
        var inconnues = demandees.stream().filter(p -> !Permissions.TOUTES.contains(p)).toList();
        if (!inconnues.isEmpty()) {
            throw new RegleMetierException("PERMISSION_INCONNUE", "Permissions inconnues : " + String.join(", ", inconnues) + ".");
        }
        this.permissions = new ArrayList<>(demandees.stream().distinct().sorted().toList());
    }

    public String code() { return code; }
    public String libelle() { return libelle; }
    public List<String> permissions() { return List.copyOf(permissions); }
    public boolean systeme() { return systeme; }
}
