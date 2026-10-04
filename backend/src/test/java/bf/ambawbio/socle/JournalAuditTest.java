package bf.ambawbio.socle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import bf.ambawbio.TestIntegration;
import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.socle.audit.VerificationAudit;
import bf.ambawbio.socle.tenancy.Pack;

/** Acceptation LOT 1 : chaîne d'audit vérifiée, altération détectée, journal en insertion seule (RG-11). */
class JournalAuditTest extends TestIntegration {

    @Autowired
    VerificationAudit verification;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TransactionTemplate transaction;

    @Test
    void RG_11_lesOperationsSensiblesSontJourneliseesEtLaChaineEstIntegre() throws Exception {
        var e = creerEntreprise("Audit intègre", Pack.BUSINESS);
        mvc.perform(post("/api/v1/socle/connexions").with(comme(e.id(), e.adminKeycloakId()))).andExpect(status().isNoContent());
        mvc.perform(put("/api/v1/socle/entreprise").with(comme(e.id(), e.adminKeycloakId())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Audit intègre SARL\",\"ifu\":\"00012345 a\"}")).andExpect(status().isOk());

        mvc.perform(get("/api/v1/audit/journal").with(comme(e.id(), e.adminKeycloakId())))
                .andExpect(jsonPath("$.elements[0].action").value("ENTREPRISE_MODIFIEE"))
                .andExpect(jsonPath("$.elements[0].apres.ifu").value("00012345A"))
                .andExpect(jsonPath("$.elements[1].action").value("CONNEXION"));
        mvc.perform(get("/api/v1/audit/journal/verification").with(comme(e.id(), e.adminKeycloakId())))
                .andExpect(jsonPath("$.integre").value(true))
                .andExpect(jsonPath("$.entreesVerifiees").value(org.hamcrest.Matchers.greaterThan(5)));
    }

    @Test
    void RG_11_uneAlterationDirecteEnBaseEstDetectee() throws Exception {
        var e = creerEntreprise("Audit altéré", Pack.BUSINESS);
        long cible;
        try (var connexion = connexionProprietaire(); var requete = connexion.createStatement()) {
            var resultat = requete.executeQuery("select min(id) + 2 from audit.journal where tenant_id = '" + e.id() + "'");
            resultat.next();
            cible = resultat.getLong(1);
            requete.execute("alter table audit.journal disable trigger journal_insertion_seule");
            requete.execute("update audit.journal set apres = '{\"nom\":\"falsifié\"}' where id = " + cible);
            requete.execute("alter table audit.journal enable trigger journal_insertion_seule");
        }
        var resultat = ContexteTenant.executerPour(e.id(), verification::verifier);
        assertThat(resultat.integre()).isFalse();
        assertThat(resultat.premiereRupture()).isEqualTo(cible);
    }

    @Test
    void RG_11_leJournalEstEnInsertionSeule() {
        var e = creerEntreprise("Audit protégé", Pack.BUSINESS);
        assertThatThrownBy(() -> ContexteTenant.executerPour(e.id(), () -> transaction.execute(s ->
                jdbc.update("update audit.journal set action = 'X'"))))
                .rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> {
            try (var connexion = connexionProprietaire(); var requete = connexion.createStatement()) {
                requete.execute("delete from audit.journal where tenant_id = '" + e.id() + "'");
            }
        }).hasMessageContaining("insertion seule");
    }
}
