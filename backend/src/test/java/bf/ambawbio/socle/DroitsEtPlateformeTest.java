package bf.ambawbio.socle;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import bf.ambawbio.TestIntegration;
import bf.ambawbio.shared.domaine.Uuid7;
import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.socle.identite.AnnuaireSimule;
import bf.ambawbio.socle.tenancy.Pack;
import tools.jackson.databind.json.JsonMapper;

/** Permissions « module:action », établissements autorisés (RG-14), barèmes (RG-10, INV-13), plateforme (UC-SOC-13). */
class DroitsEtPlateformeTest extends TestIntegration {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    AnnuaireSimule annuaire;
    @Autowired
    JsonMapper json;

    private String creerCaissiereDuSiege(EntrepriseTest e) throws Exception {
        var admin = comme(e.id(), e.adminKeycloakId());
        var utilisateurId = Uuid7.nouveau();
        mvc.perform(post("/api/v1/socle/utilisateurs").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"%s\",\"nomUtilisateur\":\"awa.%s\",\"prenom\":\"Awa\",\"nom\":\"Kaboré\",\"courriel\":\"awa.%s@test.bf\"}"
                                .formatted(utilisateurId, utilisateurId.toString().substring(28), utilisateurId.toString().substring(28))))
                .andExpect(status().isCreated());
        UUID roleCaissier = ContexteTenant.executerPour(e.id(), () -> jdbc.queryForObject("select id from socle.role where code = 'caissier'", UUID.class));
        UUID siege = ContexteTenant.executerPour(e.id(), () -> jdbc.queryForObject("select id from socle.etablissement where code = 'SIEGE'", UUID.class));
        mvc.perform(post("/api/v1/socle/utilisateurs/{id}/affectations", utilisateurId).with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"%s\",\"roleId\":\"%s\",\"etablissementId\":\"%s\"}".formatted(Uuid7.nouveau(), roleCaissier, siege)))
                .andExpect(status().isCreated());
        return ContexteTenant.executerPour(e.id(), () -> identite.utilisateur(utilisateurId).keycloakId());
    }

    @Test
    void RG_14_uneCaissiereNAccedeQuASesDroitsEtASonEtablissement() throws Exception {
        var e = creerEntreprise("Droits", Pack.BUSINESS);
        var admin = comme(e.id(), e.adminKeycloakId());
        UUID societe = ContexteTenant.executerPour(e.id(), () -> jdbc.queryForObject("select id from socle.societe", UUID.class));
        mvc.perform(post("/api/v1/socle/etablissements").with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"%s\",\"societeId\":\"%s\",\"code\":\"BOBO\",\"nom\":\"Bobo\"}".formatted(Uuid7.nouveau(), societe)))
                .andExpect(status().isCreated());
        var caissiere = creerCaissiereDuSiege(e);

        mvc.perform(get("/api/v1/socle/utilisateurs").with(comme(e.id(), caissiere)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCES_REFUSE"));
        mvc.perform(get("/api/v1/socle/contexte").with(comme(e.id(), caissiere)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etablissements[*].code", contains("SIEGE")))
                .andExpect(jsonPath("$.permissions", hasItem("pos:vendre")))
                .andExpect(jsonPath("$.roles", contains("caissier")));
        // Rôles du royaume Keycloak alignés (utilisés pour la MFA conditionnelle).
        org.assertj.core.api.Assertions.assertThat(annuaire.compte(caissiere).roles()).containsExactly("caissier");
    }

    @Test
    void unUtilisateurDesactiveNAccedePlusALApi() throws Exception {
        var e = creerEntreprise("Désactivation", Pack.BUSINESS);
        var caissiere = creerCaissiereDuSiege(e);
        UUID id = ContexteTenant.executerPour(e.id(), () -> jdbc.queryForObject(
                "select id from socle.utilisateur where keycloak_id = ?", UUID.class, caissiere));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/socle/utilisateurs/{id}", id)
                        .with(comme(e.id(), e.adminKeycloakId())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prenom\":\"Awa\",\"nom\":\"Kaboré\",\"actif\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/socle/contexte").with(comme(e.id(), caissiere)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("UTILISATEUR_DESACTIVE"));
        org.assertj.core.api.Assertions.assertThat(annuaire.compte(caissiere).actif()).isFalse();
    }

    @Test
    void RG_10_INV_13_baremeVersionneParDateDEffet() throws Exception {
        var e = creerEntreprise("Barèmes", Pack.BUSINESS);
        var admin = comme(e.id(), e.adminKeycloakId());
        var version = "{\"id\":\"%s\",\"code\":\"TVA_TAUX_NORMAL\",\"dateEffet\":\"2099-01-01\",\"valeur\":{\"taux\":19}}";
        mvc.perform(post("/api/v1/socle/baremes").with(admin).contentType(MediaType.APPLICATION_JSON).content(version.formatted(Uuid7.nouveau())))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/socle/baremes/TVA_TAUX_NORMAL").with(admin)).andExpect(jsonPath("$.valeur.taux").value(18))
                .andExpect(jsonPath("$.aValider").value(true));
        mvc.perform(get("/api/v1/socle/baremes/TVA_TAUX_NORMAL?date=2099-06-01").with(admin)).andExpect(jsonPath("$.valeur.taux").value(19));
        mvc.perform(post("/api/v1/socle/baremes").with(admin).contentType(MediaType.APPLICATION_JSON).content(version.formatted(Uuid7.nouveau())))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INV-13"));
    }

    @Test
    void F_SOC_05_seulsLesModulesDuPackSontActivables() throws Exception {
        var e = creerEntreprise("Modules", Pack.ESSENTIEL);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/socle/modules")
                        .with(comme(e.id(), e.adminKeycloakId())).contentType(MediaType.APPLICATION_JSON).content("[\"pos\",\"comptabilite\"]"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("MODULE_HORS_PACK"));
    }

    @Test
    void UC_SOC_13_lEditeurCreeSuspendEtReactiveUneEntreprise() throws Exception {
        var id = Uuid7.nouveau();
        var corps = """
                {"id":"%s","nom":"Boutique Nouvelle","pack":"ESSENTIEL","villeSiege":"Koudougou",
                 "adminNomUtilisateur":"admin.nouvelle","adminPrenom":"Aminata","adminNom":"Zongo","adminCourriel":"aminata@test.bf"}
                """.formatted(id);
        mvc.perform(post("/api/v1/plateforme/entreprises").with(comme(id, "inconnu")).contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/plateforme/entreprises").with(commeEditeur()).contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.statut").value("ACTIVE"));
        mvc.perform(get("/api/v1/plateforme/entreprises").with(commeEditeur()))
                .andExpect(jsonPath("$[*].nom", hasItem("Boutique Nouvelle")));

        var admin = ContexteTenant.executerPour(id, () -> identite.utilisateurs().getFirst().keycloakId());
        mvc.perform(get("/api/v1/socle/contexte").with(comme(id, admin)))
                .andExpect(jsonPath("$.etablissements[0].nom").value("Siège"))
                .andExpect(jsonPath("$.roles", contains("administrateur")));

        mvc.perform(post("/api/v1/plateforme/entreprises/{id}/suspension", id).with(commeEditeur())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/socle/entreprise").with(comme(id, admin)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ENTREPRISE_SUSPENDUE"));
        mvc.perform(get("/api/v1/socle/contexte").with(comme(id, admin))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/plateforme/entreprises/{id}/reactivation", id).with(commeEditeur())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/socle/entreprise").with(comme(id, admin))).andExpect(status().isOk());
    }

    @Test
    void F_SOC_18_demandeDEffacementEnregistreeEtCloturee() throws Exception {
        var e = creerEntreprise("Données perso", Pack.BUSINESS);
        var admin = comme(e.id(), e.adminKeycloakId());
        var id = Uuid7.nouveau();
        mvc.perform(post("/api/v1/socle/donnees-personnelles/demandes").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"id\":\"%s\",\"type\":\"EFFACEMENT\",\"personneConcernee\":\"Client démo\","
                                + "\"contact\":\"+226 70 00 00 00\"}").formatted(id)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.statut").value("RECUE"));
        mvc.perform(post("/api/v1/socle/donnees-personnelles/demandes/{id}/cloture", id).with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptee\":true,\"reponse\":\"Données anonymisées.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("TRAITEE"));
        mvc.perform(get("/api/v1/socle/donnees-personnelles/registre").with(admin)).andExpect(jsonPath("$.length()").value(3));
    }
}
