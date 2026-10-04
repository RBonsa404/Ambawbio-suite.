package bf.ambawbio.socle;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import bf.ambawbio.TestIntegration;
import bf.ambawbio.shared.domaine.Uuid7;
import bf.ambawbio.socle.tenancy.Pack;

/** Acceptation LOT 1 : INV-15 / RG-16 — une seule société hors pack Enterprise. */
class SocietesTest extends TestIntegration {

    private static String societe(String nom) {
        return "{\"id\":\"%s\",\"nom\":\"%s\"}".formatted(Uuid7.nouveau(), nom);
    }

    @Test
    void RG_16_INV_15_horsPackEnterpriseUneSeuleSociete() throws Exception {
        var e = creerEntreprise("Commerce Business", Pack.BUSINESS);
        mvc.perform(post("/api/v1/socle/societes").with(comme(e.id(), e.adminKeycloakId()))
                        .contentType(MediaType.APPLICATION_JSON).content(societe("Deuxième société")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INV-15"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("pack Enterprise")));
    }

    @Test
    void RG_16_lePackEnterpriseAutorisePlusieursSocietes() throws Exception {
        var e = creerEntreprise("Groupe Enterprise", Pack.ENTERPRISE);
        mvc.perform(post("/api/v1/socle/societes").with(comme(e.id(), e.adminKeycloakId()))
                        .contentType(MediaType.APPLICATION_JSON).content(societe("Filiale Bobo")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.principale").value(false));
    }

    @Test
    void uneCreationRejoueeNeCreePasDeDoublon() throws Exception {
        var e = creerEntreprise("Groupe Rejeu", Pack.ENTERPRISE);
        var corps = societe("Filiale rejouée");
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/socle/societes").with(comme(e.id(), e.adminKeycloakId()))
                    .contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().isCreated());
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/socle/societes")
                        .with(comme(e.id(), e.adminKeycloakId())))
                .andExpect(jsonPath("$.length()").value(2));
    }
}
