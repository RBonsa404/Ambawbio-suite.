package bf.ambawbio.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import bf.ambawbio.TestIntegration;
import bf.ambawbio.shared.domaine.Uuid7;
import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.socle.tenancy.Pack;
import bf.ambawbio.sync.api.GestionnaireOperation;
import bf.ambawbio.sync.api.OperationTerminal;
import tools.jackson.databind.json.JsonMapper;

/** Acceptation LOT 4 : tests obligatoires du moteur de synchronisation (guide §8.8) et appairage (SD-11). */
@Import(MoteurSynchronisationTest.Cumul.class)
class MoteurSynchronisationTest extends TestIntegration {

    /**
     * Gestionnaire de test « cumul » : se comporte comme les mouvements de stock (RG-05) — chaque opération ajoute
     * sa quantité, jamais d'écrasement. Les vrais mouvements arrivent avec la caisse (LOT 5) et le stock (LOT 9).
     */
    @TestConfiguration
    static class Cumul {
        static final Map<String, AtomicLong> TOTAUX = new ConcurrentHashMap<>();

        @Bean
        GestionnaireOperation gestionnaireCumul() {
            return new GestionnaireOperation() {
                @Override
                public String type() {
                    return "TEST_CUMUL";
                }

                @Override
                public void appliquer(OperationTerminal op) {
                    TOTAUX.computeIfAbsent(op.charge().get("produit").asString(), k -> new AtomicLong()).addAndGet(op.charge().get("quantite").asLong());
                }
            };
        }
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JsonMapper json;

    EntrepriseTest e;
    RequestPostProcessor admin;
    UUID siege;

    @BeforeEach
    void entreprise() {
        e = creerEntreprise("Quincaillerie Synchro", Pack.BUSINESS);
        admin = comme(e.id(), e.adminKeycloakId());
        siege = ContexteTenant.executerPour(e.id(), () -> jdbc.queryForObject("select id from socle.etablissement where code = 'SIEGE'", UUID.class));
    }

    private TerminalDeTest appairer(String nom) throws Exception {
        var terminal = new TerminalDeTest(Uuid7.nouveau());
        var reponse = mvc.perform(post("/api/v1/socle/terminaux").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"%s\",\"etablissementId\":\"%s\",\"nom\":\"%s\"}".formatted(terminal.id, siege, nom)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var code = json.readTree(reponse).get("code").asString();
        mvc.perform(post("/api/v1/socle/terminaux/appairage").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"terminalId\":\"%s\",\"code\":\"%s\",\"clePublique\":\"%s\"}".formatted(terminal.id, code, terminal.clePublique())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.terminal.statut").value("ACTIF"));
        return terminal;
    }

    private String pousser(TerminalDeTest t, List<String> operations) throws Exception {
        return mvc.perform(post("/api/v1/sync/push").with(admin).contentType(MediaType.APPLICATION_JSON).content(t.envoi(operations)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private UUID creerClient(String code) throws Exception {
        var id = Uuid7.nouveau();
        mvc.perform(post("/api/v1/referentiel/tiers").with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"%s\",\"tiers\":{\"code\":\"%s\",\"nom\":\"Client %s\"}}".formatted(id, code, code))).andExpect(status().isCreated());
        return id;
    }

    private long nombre(String sql, Object... parametres) {
        return ContexteTenant.executerPour(e.id(), () -> jdbc.queryForObject(sql, Long.class, parametres));
    }

    @Test
    void SD_11_appairageParQrCodeEtPlagesSansChevauchement() throws Exception {
        var premier = appairer("Caisse 1");
        var second = appairer("Caisse 2");
        mvc.perform(get("/api/v1/sync/pull?terminalId={t}", premier.id).with(admin))
                .andExpect(jsonPath("$.plages", hasSize(3)))
                .andExpect(jsonPath("$.plages[?(@.typePiece=='TICKET')].debut").value(hasItem(1)))
                .andExpect(jsonPath("$.plages[?(@.typePiece=='TICKET')].prefixe").value(hasItem("TK-C01")));
        mvc.perform(get("/api/v1/sync/pull?terminalId={t}", second.id).with(admin))
                .andExpect(jsonPath("$.plages[?(@.typePiece=='TICKET')].debut").value(hasItem(2001)))
                .andExpect(jsonPath("$.plages[?(@.typePiece=='TICKET')].prefixe").value(hasItem("TK-C02")));
        // Un code ne sert qu'une fois.
        mvc.perform(post("/api/v1/socle/terminaux/appairage").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"terminalId\":\"%s\",\"code\":\"ABCD-EFGH\",\"clePublique\":\"%s\"}".formatted(premier.id, premier.clePublique())))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("APPAIRAGE_IMPOSSIBLE"));
    }

    @Test
    void INV_11_laBaseRefuseDeuxPlagesQuiSeChevauchent() throws Exception {
        var terminal = appairer("Caisse");
        var societe = ContexteTenant.executerPour(e.id(), () -> jdbc.queryForObject("select id from socle.societe", UUID.class));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ContexteTenant.executerPour(e.id(), () -> jdbc.update("""
                insert into sync.plage_numerotation (id, tenant_id, societe_id, terminal_id, type_piece, annee, debut, fin, prochain, statut)
                values (?, ?, ?, ?, 'TICKET', extract(year from now())::int, 250, 300, 250, 'ACTIVE')""", Uuid7.nouveau(), e.id(), societe, terminal.id)))
                .rootCause().hasMessageContaining("exclusion");
    }

    @Test
    void rejeuDuMemeLotTroisFoisAucunDoublon() throws Exception {
        var terminal = appairer("Caisse");
        var client = creerClient("C-REJEU");
        var operations = new ArrayList<String>();
        for (int i = 0; i < 10; i++) {
            operations.add(terminal.operation("FICHE_CLIENT_MODIFIEE", "{\"tiersId\":\"%s\",\"ville\":\"Ville %d\"}".formatted(client, i)));
        }
        var premiere = json.readTree(pousser(terminal, operations));
        assertThat(premiere.get("accuses").valueStream().map(a -> a.get("statut").asString())).containsOnly("APPLIQUEE");
        for (int essai = 0; essai < 2; essai++) {
            var rejeu = json.readTree(pousser(terminal, operations));
            assertThat(rejeu.get("accuses").valueStream().map(a -> a.get("statut").asString())).containsOnly("IGNOREE_DOUBLON");
        }
        assertThat(nombre("select count(*) from sync.operation_recue where terminal_id = ?", terminal.id)).isEqualTo(10);
        mvc.perform(get("/api/v1/referentiel/tiers/{id}", client).with(admin)).andExpect(jsonPath("$.ville").value("Ville 9"));
    }

    @Test
    void coupureAuMilieuDUnLotRepriseSansPerte() throws Exception {
        var terminal = appairer("Caisse");
        var operations = new ArrayList<String>();
        for (int i = 0; i < 20; i++) {
            operations.add(terminal.operation("TEST_CUMUL", "{\"produit\":\"coupure-%s\",\"quantite\":1}".formatted(e.id())));
        }
        pousser(terminal, operations.subList(0, 8));          // la connexion tombe : seules 8 opérations sont arrivées
        var reprise = json.readTree(pousser(terminal, operations)); // le terminal renvoie tout le lot
        var statuts = reprise.get("accuses").valueStream().map(a -> a.get("statut").asString()).toList();
        assertThat(statuts.subList(0, 8)).containsOnly("IGNOREE_DOUBLON");
        assertThat(statuts.subList(8, 20)).containsOnly("APPLIQUEE");
        assertThat(Cumul.TOTAUX.get("coupure-" + e.id()).get()).isEqualTo(20);
    }

    @Test
    void deuxTerminauxHorsLigneSurLeMemeProduitCumulCorrect() throws Exception {
        var t1 = appairer("Caisse 1");
        var t2 = appairer("Caisse 2");
        var produit = "ciment-" + e.id();
        try (var executeur = Executors.newFixedThreadPool(2)) {
            var envois = new ArrayList<java.util.concurrent.Future<?>>();
            for (var t : List.of(t1, t2)) {
                envois.add(executeur.submit(() -> {
                    for (int lot = 0; lot < 3; lot++) {
                        var operations = new ArrayList<String>();
                        for (int i = 0; i < 50; i++) {
                            operations.add(t.operation("TEST_CUMUL", "{\"produit\":\"%s\",\"quantite\":-2}".formatted(produit)));
                        }
                        pousser(t, operations);
                    }
                    return null;
                }));
            }
            for (var envoi : envois) {
                envoi.get();
            }
        }
        assertThat(Cumul.TOTAUX.get(produit).get()).isEqualTo(-600);
        assertThat(nombre("select count(*) from sync.operation_recue where statut = 'APPLIQUEE'")).isEqualTo(300);
    }

    @Test
    void septJoursDOperationsCinqMilleVentesPuisSynchronisation() throws Exception {
        var terminal = appairer("Caisse");
        var produit = "riz-" + e.id();
        long debut = System.nanoTime();
        for (int lot = 0; lot < 50; lot++) {
            var operations = new ArrayList<String>();
            for (int i = 0; i < 100; i++) {
                operations.add(terminal.operation("TEST_CUMUL", "{\"produit\":\"%s\",\"quantite\":1}".formatted(produit)));
            }
            var reponse = json.readTree(pousser(terminal, operations));
            assertThat(reponse.get("accuses").valueStream().map(a -> a.get("statut").asString())).containsOnly("APPLIQUEE");
        }
        var duree = (System.nanoTime() - debut) / 1_000_000_000.0;
        assertThat(Cumul.TOTAUX.get(produit).get()).isEqualTo(5000);
        assertThat(duree).as("5 000 opérations en secondes").isLessThan(120);
    }

    @Test
    void terminalRevoqueRefuseSesOperationsConserveesSurLAppareil() throws Exception {
        var terminal = appairer("Caisse volée");
        mvc.perform(post("/api/v1/socle/terminaux/{id}/revocation", terminal.id).with(admin)).andExpect(jsonPath("$.statut").value("REVOQUE"));
        mvc.perform(post("/api/v1/sync/push").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(terminal.envoi(List.of(terminal.operation("TEST_CUMUL", "{\"produit\":\"x\",\"quantite\":1}")))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TERMINAL_REVOQUE"));
        mvc.perform(get("/api/v1/sync/pull?terminalId={t}", terminal.id).with(admin)).andExpect(status().isForbidden());
        assertThat(nombre("select count(*) from sync.operation_recue where terminal_id = ?", terminal.id)).isZero();
        assertThat(nombre("select count(*) from sync.plage_numerotation where terminal_id = ? and statut = 'ACTIVE'", terminal.id)).isZero();
    }

    @Test
    void signatureInvalideRefusee() throws Exception {
        var terminal = appairer("Caisse");
        var imposteur = new TerminalDeTest(terminal.id);
        var reponse = json.readTree(pousser(terminal, List.of(imposteur.operation("TEST_CUMUL", "{\"produit\":\"y\",\"quantite\":1}"))));
        assertThat(reponse.get("accuses").get(0).get("statut").asString()).isEqualTo("REFUSEE");
        assertThat(nombre("select count(*) from sync.operation_recue where terminal_id = ?", terminal.id)).isZero();
    }

    @Test
    void RG_06_ficheClientCoordonneesSeulementEtConflitsTraces() throws Exception {
        var terminal = appairer("Caisse");
        var client = creerClient("C-RG06");
        var interdit = terminal.operation("FICHE_CLIENT_MODIFIEE", "{\"tiersId\":\"%s\",\"nom\":\"Autre nom\"}".formatted(client));
        var inconnu = terminal.operation("FICHE_CLIENT_MODIFIEE", "{\"tiersId\":\"%s\",\"ville\":\"Bobo\"}".formatted(Uuid7.nouveau()));
        var correct = terminal.operation("FICHE_CLIENT_MODIFIEE", "{\"tiersId\":\"%s\",\"telephone\":\"76 11 22 33\"}".formatted(client));
        var reponse = json.readTree(pousser(terminal, List.of(interdit, inconnu, correct)));
        assertThat(reponse.get("accuses").valueStream().map(a -> a.get("statut").asString())).containsExactly("EN_CONFLIT", "EN_CONFLIT", "APPLIQUEE");
        assertThat(reponse.get("accuses").get(0).get("motif").asString()).contains("coordonnées");
        assertThat(nombre("select count(*) from sync.operation_recue where statut = 'EN_CONFLIT'")).isEqualTo(2);
        // Le changement repart vers les terminaux par le flux.
        mvc.perform(get("/api/v1/sync/pull?terminalId={t}&curseur=0&limite=500", terminal.id).with(admin))
                .andExpect(jsonPath("$.changements[?(@.entite=='client' && @.entiteId=='%s')].donnees.telephone".formatted(client))
                        .value(hasItem("+226 76 11 22 33")));
    }

    @Test
    void receptionParPagesEtAlerteDePlageA80Pourcent() throws Exception {
        var terminal = appairer("Caisse");
        for (int i = 0; i < 12; i++) {
            creerClient("C-PAGE-" + i);
        }
        var premiere = json.readTree(mvc.perform(get("/api/v1/sync/pull?terminalId={t}&curseur=0&limite=5", terminal.id).with(admin))
                .andReturn().getResponse().getContentAsString());
        assertThat(premiere.get("changements").size()).isEqualTo(5);
        assertThat(premiere.get("encore").asBoolean()).isTrue();
        long curseur = premiere.get("curseur").asLong();
        long total = 5;
        var encore = true;
        while (encore) {
            var page = json.readTree(mvc.perform(get("/api/v1/sync/pull?terminalId={t}&curseur={c}&limite=5", terminal.id, curseur).with(admin))
                    .andReturn().getResponse().getContentAsString());
            total += page.get("changements").size();
            curseur = page.get("curseur").asLong();
            encore = page.get("encore").asBoolean();
        }
        assertThat(total).isEqualTo(nombre("select count(*) from sync.flux_changements"));

        // RG-04 : à 80 % de la plage des tickets, une plage suivante est préparée.
        var ticket = json.readTree(mvc.perform(get("/api/v1/sync/pull?terminalId={t}&curseur={c}", terminal.id, curseur).with(admin))
                .andReturn().getResponse().getContentAsString()).get("plages").valueStream()
                .filter(p -> p.get("typePiece").asString().equals("TICKET")).findFirst().orElseThrow();
        mvc.perform(post("/api/v1/sync/push").with(admin).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"terminalId\":\"%s\",\"operations\":[],\"plages\":[{\"id\":\"%s\",\"prochain\":1601}]}"
                                .formatted(terminal.id, ticket.get("id").asString())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/sync/pull?terminalId={t}&curseur={c}", terminal.id, curseur).with(admin))
                .andExpect(jsonPath("$.plages[?(@.typePiece=='TICKET')]", hasSize(2)));
    }

    @Test
    void envoiCompresseGzipAccepte() throws Exception {
        var terminal = appairer("Caisse");
        var corps = terminal.envoi(List.of(terminal.operation("TEST_CUMUL", "{\"produit\":\"gzip-%s\",\"quantite\":3}".formatted(e.id()))));
        var compresse = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(compresse)) {
            gzip.write(corps.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        mvc.perform(post("/api/v1/sync/push").with(admin).contentType(MediaType.APPLICATION_JSON).header("Content-Encoding", "gzip")
                        .content(compresse.toByteArray()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accuses[*].statut", everyItem(is("APPLIQUEE"))));
    }
}
