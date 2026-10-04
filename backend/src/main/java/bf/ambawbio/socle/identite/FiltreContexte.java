package bf.ambawbio.socle.identite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.socle.tenancy.StatutEntreprise;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Après validation du jeton : positionne l'entreprise (revendication {@code tenant_id}, jamais un paramètre de requête),
 * charge les permissions et établissements autorisés de l'utilisateur, bloque une entreprise suspendue.
 */
class FiltreContexte extends OncePerRequestFilter {

    private final ServiceIdentite identite;
    private final StatutEntreprise statutEntreprise;

    FiltreContexte(ServiceIdentite identite, StatutEntreprise statutEntreprise) {
        this.identite = identite;
        this.statutEntreprise = statutEntreprise;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
            throws ServletException, IOException {
        try {
            if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jeton
                    && !preparerContexte(jeton, requete, reponse)) {
                return;
            }
            chaine.doFilter(requete, reponse);
        } finally {
            ContexteTenant.effacer();
        }
    }

    private boolean preparerContexte(JwtAuthenticationToken jeton, HttpServletRequest requete, HttpServletResponse reponse) throws IOException {
        var chemin = requete.getRequestURI();
        var sujet = jeton.getToken().getSubject();
        if (chemin.startsWith("/api/v1/plateforme/")) {
            if (jeton.getAuthorities().stream().anyMatch(a -> "ROLE_admin-plateforme".equals(a.getAuthority()))) {
                ContexteTenant.definirPlateforme(uuidOuNull(sujet));
            }
            return true;
        }
        var tenant = jeton.getToken().getClaimAsString("tenant_id");
        if (tenant == null) {
            return true;
        }
        var tenantId = UUID.fromString(tenant);
        ContexteTenant.definir(tenantId, null);
        var profil = identite.profil(sujet);
        if (profil.isEmpty()) {
            return refuser(reponse, "UTILISATEUR_INCONNU", "Votre compte n'est rattaché à aucune entreprise. Contactez l'administrateur.");
        }
        if (!profil.get().actif()) {
            return refuser(reponse, "UTILISATEUR_DESACTIVE", "Votre compte est désactivé. Contactez l'administrateur de votre entreprise.");
        }
        if (statutEntreprise.estSuspendue(tenantId) && !chemin.equals("/api/v1/socle/contexte")) {
            return refuser(reponse, "ENTREPRISE_SUSPENDUE",
                    "L'abonnement de votre entreprise est suspendu. Renouvelez-le pour retrouver l'accès à vos données.");
        }
        ContexteTenant.definir(tenantId, profil.get().utilisateurId());
        requete.setAttribute(AccesEtablissementService.ATTRIBUT_PROFIL, profil.get());
        var autorites = new ArrayList<GrantedAuthority>(jeton.getAuthorities());
        profil.get().permissions().forEach(p -> autorites.add(new SimpleGrantedAuthority(p)));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jeton.getToken(), autorites, jeton.getName()));
        return true;
    }

    private static UUID uuidOuNull(String valeur) {
        try {
            return UUID.fromString(valeur);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private static boolean refuser(HttpServletResponse reponse, String code, String message) throws IOException {
        reponse.setStatus(HttpStatus.FORBIDDEN.value());
        reponse.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        reponse.setCharacterEncoding("UTF-8");
        reponse.getWriter().write("{\"type\":\"https://ambawbio.bf/erreurs/" + code.toLowerCase().replace('_', '-')
                + "\",\"title\":\"Forbidden\",\"status\":403,\"code\":\"" + code + "\",\"detail\":\"" + message.replace("\"", "\\\"") + "\"}");
        return false;
    }
}
