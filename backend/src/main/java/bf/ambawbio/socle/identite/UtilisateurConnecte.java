package bf.ambawbio.socle.identite;

import java.util.List;

/** Vue de l'utilisateur connecté, renvoyée par {@code GET /api/moi}. */
public record UtilisateurConnecte(String identifiant, String nomUtilisateur, String nomComplet, String courriel, List<String> roles) {
}
