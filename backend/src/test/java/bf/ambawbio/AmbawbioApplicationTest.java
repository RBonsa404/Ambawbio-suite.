package bf.ambawbio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** Démarrage complet (migrations, validation du schéma) et règles d'accès générales de l'API. */
class AmbawbioApplicationTest extends TestIntegration {

    @Test
    void lApiRefuseUnAppelSansJeton() throws Exception {
        mvc.perform(get("/api/v1/socle/contexte")).andExpect(status().isUnauthorized());
    }

    @Test
    void lApplicationAndroidPeutAppelerLApi() throws Exception {
        mvc.perform(options("/api/v1/socle/contexte")
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
