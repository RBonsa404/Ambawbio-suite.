package bf.ambawbio;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/** Démarrage complet sur PostgreSQL (migrations Flyway, validation du schéma) et sécurité de l'API. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ConteneursDeTest.class)
class AmbawbioApplicationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void lApiRefuseUnAppelSansJeton() throws Exception {
        mvc.perform(get("/api/moi")).andExpect(status().isUnauthorized());
    }

    @Test
    void lApiRenvoieLUtilisateurConnecteEtSesRoles() throws Exception {
        mvc.perform(get("/api/moi").with(jwt()
                        .jwt(j -> j.subject("0199a1b2-0000-7000-8000-000000000001")
                                .claim("preferred_username", "awa")
                                .claim("name", "Awa Kaboré")
                                .claim("realm_access", Map.of("roles", List.of("caissier"))))
                        .authorities(new SimpleGrantedAuthority("ROLE_caissier"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomUtilisateur").value("awa"))
                .andExpect(jsonPath("$.nomComplet").value("Awa Kaboré"))
                .andExpect(jsonPath("$.roles[0]").value("caissier"));
    }

    @Test
    void lApplicationAndroidPeutAppelerLApi() throws Exception {
        mvc.perform(options("/api/moi")
                        .header("Origin", "https://localhost")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://localhost"));
    }

    @Test
    void laSondeDeSanteEstPublique() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
