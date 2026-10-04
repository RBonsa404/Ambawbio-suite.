package bf.ambawbio.socle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import bf.ambawbio.TestIntegration;
import bf.ambawbio.shared.domaine.Uuid7;
import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.socle.tenancy.Pack;

/** Acceptation LOT 1 : isolation stricte entre deux entreprises (guide §6.4, INV-12, RG-14). */
class IsolationEntreprisesTest extends TestIntegration {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TransactionTemplate transaction;

    EntrepriseTest a;
    EntrepriseTest b;

    @BeforeEach
    void deuxEntreprises() {
        a = creerEntreprise("Quincaillerie A", Pack.BUSINESS);
        b = creerEntreprise("Pharmacie B", Pack.BUSINESS);
    }

    @Test
    void uneEntrepriseNeVoitQueSesPropresDonneesParLApi() throws Exception {
        var societeA = ContexteTenant.executerPour(a.id(), () -> jdbc.queryForObject("select id from socle.societe", UUID.class));
        mvc.perform(post("/api/v1/socle/etablissements").with(comme(a.id(), a.adminKeycloakId())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"%s\",\"societeId\":\"%s\",\"code\":\"BOBO\",\"nom\":\"Bobo\"}".formatted(Uuid7.nouveau(), societeA)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/socle/etablissements").with(comme(a.id(), a.adminKeycloakId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/v1/socle/etablissements").with(comme(b.id(), b.adminKeycloakId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[*].code", not(hasItem("BOBO"))));
        mvc.perform(get("/api/v1/socle/entreprise").with(comme(b.id(), b.adminKeycloakId())))
                .andExpect(jsonPath("$.nom").value("Pharmacie B"));
        mvc.perform(get("/api/v1/socle/utilisateurs").with(comme(b.id(), b.adminKeycloakId())))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/v1/audit/journal").with(comme(b.id(), b.adminKeycloakId())))
                .andExpect(jsonPath("$.elements[*].entiteId", not(hasItem(a.id().toString()))));
    }

    @Test
    void lUtilisateurDUneEntrepriseNePeutPasSeFairePasserPourUneAutre() throws Exception {
        // Jeton de A avec la revendication de B : le sujet n'existe pas dans B, accès refusé.
        mvc.perform(get("/api/v1/socle/entreprise").with(comme(b.id(), a.adminKeycloakId())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("UTILISATEUR_INCONNU"));
    }

    @Test
    void laBaseDeDonneesIsoleMemeSansHibernate() {
        // Troisième barrière seule (RLS) : SQL brut, aucun filtre applicatif.
        long vuesParA = ContexteTenant.executerPour(a.id(), () -> transaction.execute(s ->
                jdbc.queryForObject("select count(distinct tenant_id) from socle.utilisateur", Long.class)));
        assertThat(vuesParA).isEqualTo(1);
        long sansContexte = transaction.execute(s -> jdbc.queryForObject("select count(*) from socle.entreprise", Long.class));
        assertThat(sansContexte).isZero();
        long journalB = ContexteTenant.executerPour(a.id(), () -> transaction.execute(s ->
                jdbc.queryForObject("select count(*) from audit.journal where tenant_id = ?", Long.class, b.id())));
        assertThat(journalB).isZero();
    }

    @Test
    void laBaseRefuseDEcrirePourUneAutreEntreprise() {
        assertThatThrownBy(() -> ContexteTenant.executerPour(a.id(), () -> transaction.execute(s ->
                jdbc.update("insert into socle.module_active (tenant_id, module) values (?, 'test-rls')", b.id()))))
                .rootCause().hasMessageContaining("row-level security");
    }
}
