package bf.ambawbio.pos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import bf.ambawbio.TestIntegration;
import bf.ambawbio.pos.domaine.CalculVente;
import bf.ambawbio.shared.domaine.Uuid7;
import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.socle.tenancy.Pack;
import bf.ambawbio.sync.TerminalDeTest;
import tools.jackson.databind.json.JsonMapper;

/** Acceptation LOT 5 : caisse hors-ligne (SD-02, SD-06, SD-08 partie caisse ; RG-09 ; INV-14 ; cas sensibles §8.5). */
class CaisseHorsLigneTest extends TestIntegration {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JsonMapper json;

    EntrepriseTest e;
    RequestPostProcessor admin;
    UUID siege;
    UUID caissier;
    UUID pointDeVente;
    TerminalDeTest terminal;
    int annee;
    long prochainTicket;
    long prochainAvoir;

    @BeforeEach
    void caisse() throws Exception {
        e = creerEntreprise("Boutique Caisse", Pack.ESSENTIEL);
        admin = comme(e.id(), e.adminKeycloakId());
        siege = ContexteTenant.executerPour(e.id(), () -> jdbc.queryForObject("select id from socle.etablissement where code = 'SIEGE'", UUID.class));
        caissier = ContexteTenant.executerPour(e.id(),
                () -> jdbc.queryForObject("select id from socle.utilisateur where keycloak_id = ?", UUID.class, e.adminKeycloakId()));
        pointDeVente = Uuid7.nouveau();
        mvc.perform(post("/api/v1/pos/points-de-vente").with(admin).contentType(MediaType.APPLICATION_JSON).content("""
                {"id":"%s","etablissementId":"%s","code":"caisse1","nom":"Caisse comptoir","seuilEcart":500}""".formatted(pointDeVente, siege)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("CAISSE1"));
        terminal = appairer("Caisse comptoir");
        annee = Instant.now().atZone(java.time.ZoneOffset.UTC).getYear();
        prochainTicket = 1;
        prochainAvoir = 1;
    }

    private TerminalDeTest appairer(String nom) throws Exception {
        var t = new TerminalDeTest(Uuid7.nouveau());
        var reponse = mvc.perform(post("/api/v1/socle/terminaux").with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"%s\",\"etablissementId\":\"%s\",\"nom\":\"%s\"}".formatted(t.id, siege, nom)))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/v1/socle/terminaux/appairage").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"terminalId\":\"%s\",\"code\":\"%s\",\"clePublique\":\"%s\"}"
                                .formatted(t.id, json.readTree(reponse).get("code").asString(), t.clePublique())))
                .andExpect(status().isOk());
        return t;
    }

    private List<String> statuts(TerminalDeTest t, List<String> operations) throws Exception {
        var reponse = mvc.perform(post("/api/v1/sync/push").with(admin).contentType(MediaType.APPLICATION_JSON).content(t.envoi(operations)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var statuts = new ArrayList<String>();
        json.readTree(reponse).get("accuses").forEach(a -> statuts.add(a.get("statut").asString()
                + (a.get("motif").isNull() ? "" : " " + a.get("motif").asString())));
        return statuts;
    }

    private String ouverture(UUID session, long fonds, Instant le) {
        return terminal.operation("SESSION_OUVERTE", """
                {"sessionId":"%s","pointDeVenteId":"%s","fondsInitial":%d,"ouverteLe":"%s","caissierId":"%s"}"""
                .formatted(session, pointDeVente, fonds, le, caissier));
    }

    record Article(UUID ligneId, String quantite, long prix, long remise) {
        Article(String quantite, long prix) {
            this(Uuid7.nouveau(), quantite, prix, 0);
        }
    }

    /** Pièce de caisse calculée comme le terminal (prix TTC, TVA 18 %), payée en espèces avec rendu. */
    private String piece(String type, UUID id, UUID session, long numero, Instant le, List<Article> articles, UUID origine, List<UUID> lignesOrigine) {
        return terminal.operation(type, charge(type, id, session, numero, le, articles, origine, lignesOrigine));
    }

    private String charge(String type, UUID id, UUID session, long numero, Instant le, List<Article> articles, UUID origine, List<UUID> lignesOrigine) {
        var lignes = new ArrayList<String>();
        long total = 0;
        for (int i = 0; i < articles.size(); i++) {
            var a = articles.get(i);
            var m = CalculVente.ligne(new BigDecimal(a.quantite()), a.prix(), true, a.remise(), new BigDecimal("18"));
            total += m.ttc().valeur();
            lignes.add("""
                    {"id":"%s","produitId":"%s","libelle":"Article %d","quantite":"%s","facteur":"1","prixUnitaire":%d,"prixTtc":true,"remise":%d,
                     "taxeCode":"TVA18","taux":"18","montantHt":%d,"montantTaxe":%d,"montantTtc":%d%s}"""
                    .formatted(a.ligneId(), UUID.nameUUIDFromBytes(("p" + i).getBytes()), i, a.quantite(), a.prix(), a.remise(), m.ht().valeur(),
                            m.taxe().valeur(), m.ttc().valeur(), lignesOrigine == null ? "" : ",\"ligneOrigineId\":\"" + lignesOrigine.get(i) + "\""));
        }
        long recu = type.equals("VENTE_ENREGISTREE") ? ((total + 999) / 1000) * 1000 : total;
        return """
                {"venteId":"%s","sessionId":"%s","caissierId":"%s","numero":{"typePiece":"%s","annee":%d,"sequence":%d},"horodatage":"%s",
                 %s"lignes":[%s],"encaissements":[{"moyen":"ESPECES","montant":%d,"recu":%d,"rendu":%d}],"totalTtc":%d}"""
                .formatted(id, session, caissier, type.equals("VENTE_ENREGISTREE") ? "TICKET" : "AVOIR", annee, numero, le,
                        origine == null ? "" : "\"venteOrigineId\":\"" + origine + "\",", String.join(",", lignes), total, recu, recu - total, total);
    }

    private String vente(UUID session, Instant le, Article... articles) {
        return piece("VENTE_ENREGISTREE", Uuid7.nouveau(), session, prochainTicket++, le, List.of(articles), null, null);
    }

    private String cloture(UUID session, long comptees) {
        return terminal.operation("SESSION_CLOTUREE", """
                {"sessionId":"%s","especesComptees":%d,"clotureeLe":"%s","caissierId":"%s"}""".formatted(session, comptees, Instant.now(), caissier));
    }

    private Map<String, Object> ligne(String sql, Object... p) {
        return ContexteTenant.executerPour(e.id(), () -> jdbc.queryForMap(sql, p));
    }

    private long nombre(String sql, Object... p) {
        return ContexteTenant.executerPour(e.id(), () -> jdbc.queryForObject(sql, Long.class, p));
    }

    @Test
    void calculParLigneArrondiAuFrancDemiSuperieur() {
        var ttc = CalculVente.ligne(new BigDecimal("3"), 5500, true, 0, new BigDecimal("18"));
        assertThat(List.of(ttc.ht().valeur(), ttc.taxe().valeur(), ttc.ttc().valeur())).containsExactly(13983L, 2517L, 16500L);
        var ht = CalculVente.ligne(new BigDecimal("0.5"), 17500, false, 250, new BigDecimal("18"));
        assertThat(List.of(ht.ht().valeur(), ht.taxe().valeur(), ht.ttc().valeur())).containsExactly(8500L, 1530L, 10030L);
        assertThat(BigDecimal.valueOf(13983).add(BigDecimal.valueOf(2517)).setScale(0, RoundingMode.HALF_UP).longValue()).isEqualTo(16500);
    }

    @Test
    void SD_02_venteHorsLigneEnregistreeUneSeuleFoisAvecSonNumeroDePlage() throws Exception {
        var session = Uuid7.nouveau();
        var venteOp = vente(session, Instant.now(), new Article("2", 5500), new Article("1", 17500));
        assertThat(statuts(terminal, List.of(ouverture(session, 10_000, Instant.now()), venteOp))).containsExactly("APPLIQUEE", "APPLIQUEE");
        // Rejeu (réseau coupé avant l'accusé) : aucun doublon.
        assertThat(statuts(terminal, List.of(venteOp, venteOp))).containsOnly("IGNOREE_DOUBLON");
        var v = ligne("select numero, total_ttc, total_taxes from pos.vente");
        assertThat(v.get("numero")).isEqualTo("TK-C01-%d-000001".formatted(annee));
        assertThat(v.get("total_ttc")).isEqualTo(28_500L);
        assertThat(nombre("select count(*) from pos.ligne_vente")).isEqualTo(2);
    }

    @Test
    void numeroHorsPlageOuCalculDivergentMisEnConflit() throws Exception {
        var session = Uuid7.nouveau();
        statuts(terminal, List.of(ouverture(session, 0, Instant.now())));
        var horsPlage = piece("VENTE_ENREGISTREE", Uuid7.nouveau(), session, 999_999, Instant.now(), List.of(new Article("1", 1000)), null, null);
        var faussee = terminal.operation("VENTE_ENREGISTREE", charge("VENTE_ENREGISTREE", Uuid7.nouveau(), session, prochainTicket++, Instant.now(),
                List.of(new Article("1", 1000)), null, null).replace("\"montantTtc\":1000", "\"montantTtc\":900"));
        var sansDroit = vente(session, Instant.now(), new Article(Uuid7.nouveau(), "1", 10_000, 5_000));
        assertThat(statuts(terminal, List.of(horsPlage, faussee, sansDroit)))
                .satisfiesExactly(s -> assertThat(s).startsWith("EN_CONFLIT").contains("plages"),
                        s -> assertThat(s).startsWith("EN_CONFLIT").contains("calcul"),
                        s -> assertThat(s).startsWith("EN_CONFLIT").contains("maximum autorisé"));
        assertThat(nombre("select count(*) from pos.vente")).isZero();
    }

    @Test
    void INV_14_deuxOuverturesConcurrentesLaSecondeEstEnConflitSansPerdreSesVentes() throws Exception {
        var premiere = Uuid7.nouveau();
        var seconde = Uuid7.nouveau();
        assertThat(statuts(terminal, List.of(ouverture(premiere, 0, Instant.now()), ouverture(seconde, 0, Instant.now()),
                vente(seconde, Instant.now(), new Article("1", 2000))))).containsOnly("APPLIQUEE");
        assertThat(ligne("select statut from pos.session_caisse where id = ?", seconde).get("statut")).isEqualTo("EN_CONFLIT");
        assertThat(nombre("select count(*) from pos.vente where session_id = ?", seconde)).isEqualTo(1);
        assertThat(nombre("select count(*) from audit.journal where action = 'SESSION_CONCURRENTE'")).isEqualTo(1);
    }

    @Test
    void SD_08_retourLimiteALaQuantiteVendueRetoursPrecedentsDeduits() throws Exception {
        var session = Uuid7.nouveau();
        var venteId = Uuid7.nouveau();
        var ligneId = Uuid7.nouveau();
        statuts(terminal, List.of(ouverture(session, 0, Instant.now()),
                piece("VENTE_ENREGISTREE", venteId, session, prochainTicket++, Instant.now(), List.of(new Article(ligneId, "3", 1500, 0)), null, null)));
        var retour1 = piece("RETOUR_ENREGISTRE", Uuid7.nouveau(), session, prochainAvoir++, Instant.now(), List.of(new Article("2", 1500)), venteId,
                List.of(ligneId));
        var retour2 = piece("RETOUR_ENREGISTRE", Uuid7.nouveau(), session, prochainAvoir++, Instant.now(), List.of(new Article("2", 1500)), venteId,
                List.of(ligneId));
        assertThat(statuts(terminal, List.of(retour1, retour2)))
                .satisfiesExactly(s -> assertThat(s).isEqualTo("APPLIQUEE"), s -> assertThat(s).startsWith("EN_CONFLIT").contains("supérieur"));
        assertThat(ligne("select numero from pos.vente where type = 'RETOUR'").get("numero")).isEqualTo("AV-C01-%d-000001".formatted(annee));
    }

    @Test
    void RG_09_ecartAuDelaDuSeuilValideParUnResponsableAvecSonCodePin() throws Exception {
        var session = Uuid7.nouveau();
        // Fonds 10 000 + vente 5 500 payée 6 000 rendu 500 = 15 500 F attendus.
        statuts(terminal, List.of(ouverture(session, 10_000, Instant.now()), vente(session, Instant.now(), new Article("1", 5500))));
        assertThat(statuts(terminal, List.of(cloture(session, 14_000)))).containsExactly("APPLIQUEE");
        var s = ligne("select statut, especes_theoriques, ecart from pos.session_caisse where id = ?", session);
        assertThat(s).containsEntry("statut", "ECART_A_VALIDER").containsEntry("especes_theoriques", 15_500L).containsEntry("ecart", -1_500L);

        mvc.perform(put("/api/v1/socle/moi/code-pin").with(admin).contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"482916\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/pos/sessions/{id}/validation-ecart", session).with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"000000\",\"motif\":\"Erreur de rendu\"}")).andExpect(jsonPath("$.code").value("PIN_INCORRECT"));
        mvc.perform(post("/api/v1/pos/sessions/{id}/validation-ecart", session).with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"482916\",\"motif\":\"Erreur de rendu\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("ECART_VALIDE"));
        assertThat(nombre("select count(*) from audit.journal where action = 'ECART_VALIDE'")).isEqualTo(1);

        // Écart dans le seuil : clôture directe.
        var autre = Uuid7.nouveau();
        statuts(terminal, List.of(ouverture(autre, 5_000, Instant.now()), cloture(autre, 4_800)));
        assertThat(ligne("select statut from pos.session_caisse where id = ?", autre).get("statut")).isEqualTo("CLOTUREE");
    }

    @Test
    void septJoursDeVentesHorsLignePuisSynchronisesSansPerteNiTrou() throws Exception {
        var operations = new ArrayList<String>();
        var debut = Instant.now().minus(7, ChronoUnit.DAYS);
        long attendu = 0;
        for (int jour = 0; jour < 7; jour++) {
            var session = Uuid7.nouveau();
            var matin = debut.plus(jour, ChronoUnit.DAYS);
            operations.add(ouverture(session, 20_000, matin));
            long especes = 20_000;
            for (int n = 0; n < 150; n++) {
                var prix = 250L * (1 + (n % 40));
                operations.add(vente(session, matin.plusSeconds(60L * n), new Article(String.valueOf(1 + n % 3), prix)));
                especes += prix * (1 + n % 3);
                attendu += prix * (1 + n % 3);
            }
            operations.add(cloture(session, especes));
        }
        var statuts = new ArrayList<String>();
        for (int i = 0; i < operations.size(); i += 100) {
            statuts.addAll(statuts(terminal, operations.subList(i, Math.min(i + 100, operations.size()))));
        }
        assertThat(statuts).hasSize(7 * 152).containsOnly("APPLIQUEE");
        assertThat(nombre("select count(*) from pos.vente")).isEqualTo(1050);
        assertThat(nombre("select sum(total_ttc) from pos.vente")).isEqualTo(attendu);
        assertThat(nombre("select max(sequence) - min(sequence) + 1 from pos.vente")).isEqualTo(1050);
        assertThat(nombre("select count(*) from pos.session_caisse where statut = 'CLOTUREE'")).isEqualTo(7);
        mvc.perform(get("/api/v1/pos/sessions").with(admin)).andExpect(jsonPath("$.length()").value(7));
    }
}
