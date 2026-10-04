package bf.ambawbio.facturation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import bf.ambawbio.TestIntegration;
import bf.ambawbio.conformite.application.ServiceCertification;
import bf.ambawbio.conformite.application.SimulateurFec;
import bf.ambawbio.facturation.application.ServiceFacturation;
import bf.ambawbio.shared.domaine.Uuid7;
import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.socle.tenancy.Pack;
import tools.jackson.databind.json.JsonMapper;

/** Acceptation LOT 6 : numérotation sans trou, immuabilité (API et base), certification différée, avoirs (INV-04), RG-02. */
class FacturationTest extends TestIntegration {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JsonMapper json;
    @Autowired
    ServiceFacturation facturation;
    @Autowired
    ServiceCertification certification;
    @Autowired
    SimulateurFec simulateur;

    EntrepriseTest e;
    RequestPostProcessor admin;
    UUID client;
    int annee;

    @BeforeEach
    void entreprise() throws Exception {
        e = creerEntreprise("Quincaillerie Facture", Pack.BUSINESS, "00012345A");
        admin = comme(e.id(), e.adminKeycloakId());
        client = Uuid7.nouveau();
        mvc.perform(post("/api/v1/referentiel/tiers").with(admin).contentType(MediaType.APPLICATION_JSON).content("""
                {"id":"%s","tiers":{"code":"BATIR","nom":"Bâtir Faso SARL","regimeCode":"RNI","ifu":"00098765C","adresse":"Zone du bois",
                 "ville":"Ouagadougou","delaiPaiementJours":30}}""".formatted(client))).andExpect(status().isCreated());
        annee = LocalDate.now(java.time.ZoneId.of("Africa/Ouagadougou")).getYear();
    }

    @AfterEach
    void simulateurDisponible() {
        simulateur.changerMode(SimulateurFec.Mode.DISPONIBLE);
    }

    private UUID brouillon(long... prix) throws Exception {
        var id = Uuid7.nouveau();
        var lignes = new ArrayList<String>();
        for (int i = 0; i < prix.length; i++) {
            lignes.add("{\"designation\":\"Article %d\",\"unite\":\"U\",\"quantite\":3,\"prixUnitaire\":%d,\"prixTtc\":true,\"taxeCode\":\"TVA18\",\"taux\":18}"
                    .formatted(i, prix[i]));
        }
        mvc.perform(post("/api/v1/facturation/documents").with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"%s\",\"clientId\":\"%s\",\"lignes\":[%s]}".formatted(id, client, String.join(",", lignes))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.statut").value("BROUILLON"));
        return id;
    }

    /** La première tentative de certification part juste après la validation, hors du fil de la requête. */
    private void attendre(UUID id, String fecStatut) throws Exception {
        for (int i = 0; i < 100; i++) {
            var etat = json.readTree(mvc.perform(get("/api/v1/facturation/documents/{id}", id).with(admin)).andReturn().getResponse().getContentAsString());
            if (fecStatut.equals(etat.get("fecStatut").asString())) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Statut de certification attendu : " + fecStatut);
    }

    private void attendreTentatives(UUID id, int n) throws Exception {
        for (int i = 0; i < 100 && dans(() -> jdbc.queryForObject("select tentatives from conformite.certification where document_id = ?",
                Integer.class, id)) < n; i++) {
            Thread.sleep(100);
        }
    }

    private <T> T dans(Callable<T> c) {
        return ContexteTenant.executerPour(e.id(), () -> {
            try {
                return c.call();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    @Test
    void UC_FAC_01_factureValideeCertifieeEtImmuable() throws Exception {
        var id = brouillon(5500, 17500);
        mvc.perform(post("/api/v1/facturation/documents/{id}/validation", id).with(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("FA-%d-000001".formatted(annee)))
                .andExpect(jsonPath("$.statut").value("VALIDEE"))
                .andExpect(jsonPath("$.totalTtc").value(69_000))
                .andExpect(jsonPath("$.clientIfu").value("00098765C"))
                .andExpect(jsonPath("$.dateEcheance").value(LocalDate.now(java.time.ZoneId.of("Africa/Ouagadougou")).plusDays(30).toString()));
        attendre(id, "CERTIFIEE");
        mvc.perform(get("/api/v1/facturation/documents/{id}", id).with(admin)).andExpect(jsonPath("$.fecSimulee").value(true));

        // RG-01 par l'API
        mvc.perform(put("/api/v1/facturation/documents/{id}", id).with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"%s\",\"clientId\":\"%s\",\"lignes\":[]}".formatted(id, client))).andExpect(jsonPath("$.code").value("RG-01"));
        mvc.perform(delete("/api/v1/facturation/documents/{id}", id).with(admin)).andExpect(jsonPath("$.code").value("RG-01"));

        // RG-01 par la base : même une requête SQL de l'application est refusée
        assertThatThrownBy(() -> dans(() -> jdbc.update("update facturation.document_fiscal set total_ttc = 1 where id = ?", id)))
                .hasStackTraceContaining("RG-01");
        assertThatThrownBy(() -> dans(() -> jdbc.update("update facturation.ligne_document set prix_unitaire = 1 where document_id = ?", id)))
                .hasStackTraceContaining("RG-01");
        assertThatThrownBy(() -> dans(() -> jdbc.update("delete from facturation.document_fiscal where id = ?", id))).hasStackTraceContaining("RG-01");

        // PDF archivé : version de validation puis version certifiée
        var pdf = mvc.perform(get("/api/v1/facturation/documents/{id}/pdf", id).with(admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(dans(() -> jdbc.queryForList("select motif from facturation.archive_pdf where document_id = ? order by version", String.class, id)))
                .containsExactly("validation", "certification");
        assertThatThrownBy(() -> dans(() -> jdbc.update("update facturation.archive_pdf set contenu = '\\x00' where document_id = ?", id)))
                .hasStackTraceContaining("permission");
    }

    @Test
    void milleValidationsConcurrentesSansTrouNiDoublon() throws Exception {
        var ids = dans(() -> {
            var liste = new ArrayList<UUID>();
            for (int i = 0; i < 1000; i++) {
                var d = facturation.creerBrouillon(new ServiceFacturation.DemandeDocument(Uuid7.nouveau(), null, client, null,
                        List.of(new ServiceFacturation.DemandeLigne(null, "Ciment", "SAC", BigDecimal.ONE, 5500L, true, 0L, "TVA18", new BigDecimal("18")))));
                liste.add(d.getId());
            }
            return liste;
        });
        try (var executeur = Executors.newFixedThreadPool(24)) {
            var taches = ids.stream().map(id -> (Callable<Object>) () -> dans(() -> facturation.valider(id))).toList();
            for (var f : executeur.invokeAll(taches)) {
                f.get();
            }
        }
        var bornes = dans(() -> jdbc.queryForMap(
                "select count(*) n, count(distinct numero) distincts, min(sequence) mini, max(sequence) maxi "
                        + "from facturation.document_fiscal where type = 'FACTURE'"));
        assertThat(bornes).containsEntry("n", 1000L).containsEntry("distincts", 1000L).containsEntry("mini", 1L).containsEntry("maxi", 1000L);
    }

    @Test
    void certificationDiffereeQuandLeServiceEstIndisponiblePuisDisponible() throws Exception {
        simulateur.changerMode(SimulateurFec.Mode.INDISPONIBLE);
        var id = brouillon(1000);
        mvc.perform(post("/api/v1/facturation/documents/{id}/validation", id).with(admin)).andExpect(jsonPath("$.fecStatut").value("EN_FILE"));
        attendreTentatives(id, 1);
        var etat = dans(() -> jdbc.queryForMap("select tentatives, prochain_essai > now() + interval '50 seconds' as differe from conformite.certification "
                + "where document_id = ?", id));
        assertThat(etat).containsEntry("tentatives", 1).containsEntry("differe", true);

        // Essai suivant échu, service toujours indisponible : délai croissant (5 min) ; au-delà de 24 h en file, alerte.
        dans(() -> jdbc.update("update conformite.certification set prochain_essai = now(), soumis_le = now() - interval '25 hours' "
                + "where document_id = ?", id));
        assertThat(dans(certification::reprendre)).isEqualTo(1);
        etat = dans(() -> jdbc.queryForMap("select tentatives, alerte, prochain_essai > now() + interval '4 minutes' as differe from conformite.certification "
                + "where document_id = ?", id));
        assertThat(etat).containsEntry("tentatives", 2).containsEntry("alerte", true).containsEntry("differe", true);
        mvc.perform(get("/api/v1/conformite/file").with(admin)).andExpect(jsonPath("$[0].alerte").value(true));

        // Retour du service : la reprise certifie la pièce.
        simulateur.changerMode(SimulateurFec.Mode.DISPONIBLE);
        dans(() -> jdbc.update("update conformite.certification set prochain_essai = now() where document_id = ?", id));
        dans(certification::reprendre);
        attendre(id, "CERTIFIEE");
        mvc.perform(get("/api/v1/facturation/documents/{id}", id).with(admin))
                .andExpect(jsonPath("$.fecIdentifiant").value(org.hamcrest.Matchers.startsWith("SIM-")));
    }

    @Test
    void rejetDeCertificationSignale() throws Exception {
        simulateur.changerMode(SimulateurFec.Mode.REJET);
        var id = brouillon(1000);
        mvc.perform(post("/api/v1/facturation/documents/{id}/validation", id).with(admin)).andExpect(status().isOk());
        attendre(id, "REJETEE");
        mvc.perform(get("/api/v1/facturation/documents/{id}", id).with(admin))
                .andExpect(jsonPath("$.fecMessage").value(org.hamcrest.Matchers.containsString("Rejet simulé")));
    }

    @Test
    void SD_08_avoirsPartielEtTotalSansDepasserLeResteINV_04() throws Exception {
        var id = brouillon(1000);
        var facture = json.readTree(mvc.perform(post("/api/v1/facturation/documents/{id}/validation", id).with(admin))
                .andReturn().getResponse().getContentAsString());
        var ligne = facture.get("lignes").get(0).get("id").asString();

        mvc.perform(post("/api/v1/facturation/documents/{id}/avoirs", id).with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"%s\",\"motif\":\"Sac abîmé\",\"quantites\":{\"%s\":1}}".formatted(Uuid7.nouveau(), ligne)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.numero").value("AV-%d-000001".formatted(annee)))
                .andExpect(jsonPath("$.totalTtc").value(1000));
        mvc.perform(post("/api/v1/facturation/documents/{id}/avoirs", id).with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"%s\",\"motif\":\"Erreur\",\"quantites\":{\"%s\":3}}".formatted(Uuid7.nouveau(), ligne)))
                .andExpect(jsonPath("$.code").value("INV-04"));
        mvc.perform(post("/api/v1/facturation/documents/{id}/avoirs", id).with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"%s\",\"motif\":\"Annulation\",\"quantites\":{}}".formatted(Uuid7.nouveau())))
                .andExpect(jsonPath("$.totalTtc").value(2000));
        mvc.perform(get("/api/v1/facturation/documents/{id}", id).with(admin)).andExpect(jsonPath("$.statut").value("ANNULEE_PAR_AVOIR"))
                .andExpect(jsonPath("$.totalAvoirs").value(3000));
    }

    @Test
    void RG_02_clientAssujettiSansIfuRefuseEtNumeroNonConsomme() throws Exception {
        var id = brouillon(1000);
        try (var c = connexionProprietaire()) {
            c.createStatement().executeUpdate("update referentiel.tiers set ifu = null where id = '" + client + "'");
        }
        mvc.perform(post("/api/v1/facturation/documents/{id}/validation", id).with(admin)).andExpect(jsonPath("$.code").value("RG-02"));
        try (var c = connexionProprietaire()) {
            c.createStatement().executeUpdate("update referentiel.tiers set ifu = '00098765C' where id = '" + client + "'");
        }
        mvc.perform(post("/api/v1/facturation/documents/{id}/validation", id).with(admin))
                .andExpect(jsonPath("$.numero").value("FA-%d-000001".formatted(annee)));
    }
}
