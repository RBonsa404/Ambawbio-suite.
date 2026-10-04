package bf.ambawbio.socle.identite;

import java.util.Set;
import java.util.UUID;

/** Droits effectifs d'un utilisateur : permissions et établissements autorisés (RG-14). */
public record ProfilAcces(UUID utilisateurId, boolean actif, Set<String> roles, Set<String> permissions,
        boolean tousEtablissements, Set<UUID> etablissements) {

    public boolean autorise(UUID etablissementId) {
        return tousEtablissements || etablissements.contains(etablissementId);
    }
}
