package bf.ambawbio.socle.identite;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Informations sur l'utilisateur authentifié (vérifie la chaîne Keycloak → API au LOT 0). */
@RestController
@RequestMapping("/api/moi")
class MoiControleur {

    @GetMapping
    UtilisateurConnecte moi(@AuthenticationPrincipal Jwt jwt) {
        var roles = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .sorted()
                .toList();
        return new UtilisateurConnecte(
                jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("email"),
                roles);
    }
}
