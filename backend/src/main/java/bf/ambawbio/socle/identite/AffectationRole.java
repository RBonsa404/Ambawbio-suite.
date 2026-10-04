package bf.ambawbio.socle.identite;

import java.util.UUID;

import bf.ambawbio.shared.domaine.EntiteMetier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Affectation d'un rôle à un utilisateur, pour un établissement ou pour tous ({@code etablissementId} nul). RG-14. */
@Entity
@Table(schema = "socle", name = "affectation_role")
public class AffectationRole extends EntiteMetier {

    @Column(name = "utilisateur_id")
    private UUID utilisateurId;
    @Column(name = "role_id")
    private UUID roleId;
    @Column(name = "etablissement_id")
    private UUID etablissementId;

    protected AffectationRole() {
    }

    public AffectationRole(UUID id, UUID utilisateurId, UUID roleId, UUID etablissementId) {
        super(id);
        this.utilisateurId = utilisateurId;
        this.roleId = roleId;
        this.etablissementId = etablissementId;
    }

    public UUID utilisateurId() { return utilisateurId; }
    public UUID roleId() { return roleId; }
    public UUID etablissementId() { return etablissementId; }
}
