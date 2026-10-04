package bf.ambawbio.socle.identite;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

/**
 * Test de contrat de l'adaptateur Keycloak, contre le royaume de démonstration (infra/docker-compose.dev.yml).
 * Lancement : {@code AMBAWBIO_TEST_KEYCLOAK=http://localhost:8180 ./mvnw test -Dtest=AnnuaireKeycloakTest}
 */
@EnabledIfEnvironmentVariable(named = "AMBAWBIO_TEST_KEYCLOAK", matches = "http.*")
class AnnuaireKeycloakTest {

    @Test
    void creeUnCompteAlignéSesRolesEtLeDesactive() {
        var url = System.getenv("AMBAWBIO_TEST_KEYCLOAK");
        var annuaire = new AnnuaireKeycloak(new ProprietesAnnuaire("keycloak", url, "ambawbio", "ambawbio-serveur", "ambawbio-serveur-dev"),
                RestClient.builder());
        var suffixe = UUID.randomUUID().toString().substring(0, 8);
        var tenant = UUID.randomUUID();
        var id = annuaire.creerCompte(new PortAnnuaire.NouveauCompte("test-" + suffixe, "Test", "Contrat", "test-" + suffixe + "@demo.ambawbio.bf", tenant));

        annuaire.synchroniserRoles(id, Set.of("comptable", "caissier"));
        annuaire.synchroniserRoles(id, Set.of("comptable"));
        annuaire.definirActif(id, false);

        var admin = RestClient.builder().baseUrl(url + "/admin/realms/ambawbio").build();
        Map<?, ?> compte = admin.get().uri("/users/{id}", id).headers(h -> h.setBearerAuth(jetonService(url))).retrieve().body(Map.class);
        assertThat(compte.get("enabled")).isEqualTo(false);
        assertThat(((Map<?, ?>) compte.get("attributes")).get("tenant_id")).isEqualTo(List.of(tenant.toString()));
        List<Map<String, Object>> roles = admin.get().uri("/users/{id}/role-mappings/realm", id)
                .headers(h -> h.setBearerAuth(jetonService(url))).retrieve().body(List.class);
        assertThat(roles).extracting(r -> r.get("name")).contains("comptable").doesNotContain("caissier");
    }

    private static String jetonService(String url) {
        Map<?, ?> reponse = RestClient.create().post().uri(url + "/realms/ambawbio/protocol/openid-connect/token")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials&client_id=ambawbio-serveur&client_secret=ambawbio-serveur-dev")
                .retrieve().body(Map.class);
        return String.valueOf(reponse.get("access_token"));
    }
}
