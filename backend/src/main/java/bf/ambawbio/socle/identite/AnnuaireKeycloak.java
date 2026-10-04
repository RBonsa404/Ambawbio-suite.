package bf.ambawbio.socle.identite;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import bf.ambawbio.shared.domaine.RegleMetierException;

/**
 * Adaptateur Keycloak (API d'administration REST) avec le compte de service {@code ambawbio-serveur}.
 * Actif si {@code ambawbio.annuaire.mode=keycloak} (valeur par défaut).
 */
@Component
@ConditionalOnProperty(name = "ambawbio.annuaire.mode", havingValue = "keycloak", matchIfMissing = true)
class AnnuaireKeycloak implements PortAnnuaire {

    /** Rôles du royaume gérés par l'application (les autres, comme admin-plateforme, ne sont pas touchés). */
    private static final Set<String> ROLES_GERES = Set.of("administrateur", "dirigeant", "gerant", "comptable", "caissier", "magasinier", "commercial");

    private final RestClient client;
    private final String urlJeton;
    private final String clientId;
    private final String secret;

    @org.springframework.beans.factory.annotation.Autowired
    AnnuaireKeycloak(ProprietesAnnuaire proprietes) {
        this(proprietes, RestClient.builder());
    }

    AnnuaireKeycloak(ProprietesAnnuaire proprietes, RestClient.Builder fabrique) {
        this.client = fabrique.baseUrl(proprietes.url() + "/admin/realms/" + proprietes.royaume()).build();
        this.urlJeton = proprietes.url() + "/realms/" + proprietes.royaume() + "/protocol/openid-connect/token";
        this.clientId = proprietes.clientId();
        this.secret = proprietes.secret();
    }

    private String jeton() {
        var formulaire = new LinkedMultiValueMap<String, String>();
        formulaire.add("grant_type", "client_credentials");
        formulaire.add("client_id", clientId);
        formulaire.add("client_secret", secret);
        Map<?, ?> reponse = RestClient.create().post().uri(urlJeton).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formulaire).retrieve().body(Map.class);
        return String.valueOf(reponse.get("access_token"));
    }

    @Override
    public String creerCompte(NouveauCompte compte) {
        var jeton = jeton();
        var representation = Map.of(
                "username", compte.nomUtilisateur(),
                "firstName", compte.prenom(),
                "lastName", compte.nom(),
                "email", compte.courriel(),
                "enabled", true,
                "emailVerified", false,
                "attributes", Map.of("tenant_id", List.of(compte.tenantId().toString())),
                "requiredActions", List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"));
        var reponse = client.post().uri("/users").headers(h -> h.setBearerAuth(jeton)).body(representation)
                .exchange((requete, r) -> {
                    if (r.getStatusCode().value() == 409) {
                        throw new RegleMetierException("COMPTE_EXISTANT", "Un compte existe déjà avec ce nom d'utilisateur ou ce courriel.");
                    }
                    return r.getHeaders().getLocation();
                });
        URI emplacement = reponse;
        var id = emplacement.getPath().substring(emplacement.getPath().lastIndexOf('/') + 1);
        client.put().uri("/users/{id}/execute-actions-email", id).headers(h -> h.setBearerAuth(jeton))
                .body(List.of("UPDATE_PASSWORD", "VERIFY_EMAIL")).retrieve().toBodilessEntity();
        return id;
    }

    @Override
    public void definirActif(String keycloakId, boolean actif) {
        client.put().uri("/users/{id}", keycloakId).headers(h -> h.setBearerAuth(jeton()))
                .body(Map.of("enabled", actif)).retrieve().toBodilessEntity();
    }

    @Override
    public void synchroniserRoles(String keycloakId, Set<String> codesRoles) {
        var jeton = jeton();
        List<Map<String, Object>> actuels = client.get().uri("/users/{id}/role-mappings/realm", keycloakId)
                .headers(h -> h.setBearerAuth(jeton)).retrieve().body(List.class);
        var aRetirer = actuels.stream().filter(r -> ROLES_GERES.contains(r.get("name")) && !codesRoles.contains(r.get("name"))).toList();
        if (!aRetirer.isEmpty()) {
            client.method(org.springframework.http.HttpMethod.DELETE).uri("/users/{id}/role-mappings/realm", keycloakId)
                    .headers(h -> h.setBearerAuth(jeton)).body(aRetirer).retrieve().toBodilessEntity();
        }
        var aAjouter = codesRoles.stream().filter(ROLES_GERES::contains)
                .filter(code -> actuels.stream().noneMatch(r -> code.equals(r.get("name"))))
                .map(code -> (Map<String, Object>) client.get().uri("/roles/{nom}", code).headers(h -> h.setBearerAuth(jeton))
                        .retrieve().body(Map.class))
                .toList();
        if (!aAjouter.isEmpty()) {
            client.post().uri("/users/{id}/role-mappings/realm", keycloakId).headers(h -> h.setBearerAuth(jeton))
                    .body(aAjouter).retrieve().toBodilessEntity();
        }
    }
}
