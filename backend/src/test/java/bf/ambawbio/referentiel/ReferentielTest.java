package bf.ambawbio.referentiel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import bf.ambawbio.TestIntegration;
import bf.ambawbio.shared.domaine.Uuid7;
import bf.ambawbio.socle.tenancy.Pack;

/** Acceptation LOT 2 : import 1 000 produits et 500 clients avec rapport exact, recherche rapide, champs personnalisés. */
class ReferentielTest extends TestIntegration {

    EntrepriseTest e;
    RequestPostProcessor admin;

    @BeforeEach
    void entreprise() {
        e = creerEntreprise("Quincaillerie Import", Pack.BUSINESS);
        admin = comme(e.id(), e.adminKeycloakId());
    }

    private void champ(String entite, String code, String type, String options, boolean filtrable, boolean obligatoire) throws Exception {
        mvc.perform(post("/api/v1/studio/champs").with(admin).contentType(MediaType.APPLICATION_JSON).content("""
                {"id":"%s","entite":"%s","code":"%s","libelle":"%s","type":"%s","options":%s,"filtrable":%s,"obligatoire":%s}
                """.formatted(Uuid7.nouveau(), entite, code, code, type, options, filtrable, obligatoire))).andExpect(status().isCreated());
    }

    private MockMultipartFile fichier(String nom, String contenu) {
        return new MockMultipartFile("fichier", nom, "text/csv", contenu.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void lesDonneesDeDemarrageSontCreeesAvecLEntreprise() throws Exception {
        mvc.perform(get("/api/v1/referentiel/taxes").with(admin))
                .andExpect(jsonPath("$[*].code", contains("EXO", "TVA10", "TVA18")))
                .andExpect(jsonPath("$[?(@.code=='TVA18')].aValider", contains(true)));
        mvc.perform(get("/api/v1/referentiel/regimes-fiscaux").with(admin)).andExpect(jsonPath("$[*].code", contains("CME", "RNI", "RSI")));
        mvc.perform(get("/api/v1/referentiel/types-conditionnement").with(admin)).andExpect(jsonPath("$[*].code", hasItem("SAC")));
    }

    @Test
    void import1000ProduitsAvecRapportDErreursExact() throws Exception {
        champ("produit", "marque", "LISTE", "[\"Lafarge\",\"Cimaf\",\"Diamond\"]", true, false);
        var csv = new StringBuilder("﻿Code;Nom;Catégorie;Unité;Taxe;Prix vente;Code barre;Conditionnement;Conditionnement quantité;champ.marque\r\n");
        for (int i = 1; i <= 1006; i++) {
            int ligne = i + 1;
            String code = "P" + String.format("%04d", i);
            String nom = "Produit démo " + i;
            String prix = String.valueOf(500 + i);
            String taxe = "TVA18";
            String codeBarre = "6130000" + String.format("%06d", i);
            String marque = i % 2 == 0 ? "Cimaf" : "Lafarge";
            switch (ligne) {
                case 12 -> nom = "";
                case 25 -> prix = "12,5x";
                case 40 -> taxe = "TVA99";
                case 60 -> code = "P0001";
                case 80 -> codeBarre = "6130000000002";
                case 90 -> marque = "Inconnue";
                default -> { }
            }
            csv.append(String.join(";", code, nom, i <= 500 ? "Construction" : "Alimentation", "U", taxe, prix, codeBarre, "CARTON", "12", marque))
                    .append("\r\n");
        }
        var contenu = csv.toString();

        mvc.perform(multipart("/api/v1/referentiel/imports/produits").file(fichier("produits.csv", contenu)).with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REJETE"))
                .andExpect(jsonPath("$.lignesTotal").value(1006))
                .andExpect(jsonPath("$.lignesImportees").value(0))
                .andExpect(jsonPath("$.erreurs", hasSize(6)))
                .andExpect(jsonPath("$.erreurs[*].ligne", contains(12, 25, 40, 60, 80, 90)))
                .andExpect(jsonPath("$.erreurs[*].colonne", contains("nom", "prix_vente", "taxe", "code", "code_barre", "champ.marque")))
                .andExpect(jsonPath("$.erreurs[3].message").value("Code déjà présent à la ligne 2 du fichier."));
        mvc.perform(get("/api/v1/referentiel/produits?taille=1").with(admin)).andExpect(jsonPath("$.total").value(0));

        long debut = System.nanoTime();
        mvc.perform(multipart("/api/v1/referentiel/imports/produits").file(fichier("produits.csv", contenu))
                        .param("lignesValidesSeulement", "true").with(admin))
                .andExpect(jsonPath("$.statut").value("PARTIEL"))
                .andExpect(jsonPath("$.lignesImportees").value(1000));
        assertThat((System.nanoTime() - debut) / 1_000_000).as("durée de l'import en ms").isLessThan(30_000);
        mvc.perform(get("/api/v1/referentiel/produits?taille=1").with(admin)).andExpect(jsonPath("$.total").value(1000));
        // Recherche rapide : sans accent, sur 1 000 produits, en moins de 300 ms (requête chaude).
        mvc.perform(get("/api/v1/referentiel/produits?q=demo 77").with(admin));
        long debutRecherche = System.nanoTime();
        mvc.perform(get("/api/v1/referentiel/produits?q=PRODUIT DEMO 777").with(admin)).andExpect(jsonPath("$.elements[0].code").value("P0777"));
        assertThat((System.nanoTime() - debutRecherche) / 1_000_000).as("durée de la recherche en ms").isLessThan(300);

        // Rejouer le même fichier met à jour sans créer de doublon.
        mvc.perform(multipart("/api/v1/referentiel/imports/produits").file(fichier("produits.csv", contenu))
                .param("lignesValidesSeulement", "true").with(admin)).andExpect(jsonPath("$.lignesImportees").value(1000));
        mvc.perform(get("/api/v1/referentiel/produits?taille=1").with(admin)).andExpect(jsonPath("$.total").value(1000));
        mvc.perform(get("/api/v1/referentiel/categories").with(admin)).andExpect(jsonPath("$[*].nom", contains("Alimentation", "Construction")));

        // Champs personnalisés importés, visibles et filtrables ; code-barres du produit.
        mvc.perform(get("/api/v1/referentiel/produits?champ.marque=Cimaf&taille=1").with(admin)).andExpect(jsonPath("$.total").value(502));
        mvc.perform(get("/api/v1/referentiel/produits/code-barre/6130000000500").with(admin))
                .andExpect(jsonPath("$.code").value("P0500"))
                .andExpect(jsonPath("$.champsPerso.marque").value("Cimaf"))
                .andExpect(jsonPath("$.conditionnements[0].quantite").value(12.0));
    }

    @Test
    void import500ClientsAvecRapportDErreursExact() throws Exception {
        var csv = new StringBuilder("code;nom;client;fournisseur;ifu;regime;telephone;mobile_money_operateur;mobile_money_numero\n");
        for (int i = 1; i <= 503; i++) {
            int ligne = i + 1;
            String ifu = i % 3 == 0 ? "%08dA".formatted(i) : "";
            String regime = i % 3 == 0 ? "RNI" : "";
            String telephone = "70 %02d %02d %02d".formatted(i / 10000, (i / 100) % 100, i % 100);
            switch (ligne) {
                case 101 -> ifu = "12AB";
                case 201 -> { regime = "RNI"; ifu = ""; }
                case 301 -> telephone = "123";
                default -> { }
            }
            csv.append(String.join(";", "C%04d".formatted(i), "Client démo " + i, "oui", "non", ifu, regime, telephone,
                    i % 2 == 0 ? "Orange Money" : "moov", telephone)).append('\n');
        }
        mvc.perform(multipart("/api/v1/referentiel/imports/tiers").file(fichier("clients.csv", csv.toString())).param("mode", "VERIFICATION")
                        .with(admin))
                .andExpect(jsonPath("$.statut").value("REJETE"))
                .andExpect(jsonPath("$.erreurs[*].ligne", contains(101, 201, 301)))
                .andExpect(jsonPath("$.erreurs[*].colonne", contains("ifu", "ifu", "telephone")))
                .andExpect(jsonPath("$.erreurs[1].message").value("L'IFU est obligatoire pour un client assujetti à la TVA."));
        mvc.perform(multipart("/api/v1/referentiel/imports/tiers").file(fichier("clients.csv", csv.toString()))
                        .param("lignesValidesSeulement", "true").with(admin))
                .andExpect(jsonPath("$.lignesImportees").value(500));
        mvc.perform(get("/api/v1/referentiel/tiers?client=true&taille=1").with(admin)).andExpect(jsonPath("$.total").value(500));
        mvc.perform(get("/api/v1/referentiel/tiers?q=70 00 01 50").with(admin))
                .andExpect(jsonPath("$.elements[0].code").value("C0150"))
                .andExpect(jsonPath("$.elements[0].comptesMobileMoney[0].numero").value("+226 70 00 01 50"))
                .andExpect(jsonPath("$.elements[0].ifu").value("00000150A"));
    }

    @Test
    void importExcelEtRapportCsv() throws Exception {
        var sortie = new ByteArrayOutputStream();
        try (var classeur = new XSSFWorkbook()) {
            var feuille = classeur.createSheet("Produits");
            var lignes = List.of(List.of("code", "nom", "prix_vente", "taxe"), List.of("X1", "Tôle bac 3 m", "4750", "TVA18"),
                    List.of("X2", "Savon 400 g", "trois cents", "TVA18"));
            for (int i = 0; i < lignes.size(); i++) {
                var ligne = feuille.createRow(i);
                for (int j = 0; j < lignes.get(i).size(); j++) {
                    ligne.createCell(j).setCellValue(lignes.get(i).get(j));
                }
            }
            classeur.write(sortie);
        }
        var resultat = mvc.perform(multipart("/api/v1/referentiel/imports/produits")
                        .file(new MockMultipartFile("fichier", "produits.xlsx", "application/vnd.ms-excel", sortie.toByteArray())).with(admin))
                .andExpect(jsonPath("$.erreurs[0].ligne").value(3))
                .andExpect(jsonPath("$.erreurs[0].colonne").value("prix_vente"))
                .andReturn().getResponse().getContentAsString();
        var id = resultat.replaceAll(".*\"id\":\"([0-9a-f-]{36})\".*", "$1");
        var rapport = mvc.perform(get("/api/v1/referentiel/imports/{id}/rapport.csv", id).with(admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(rapport).contains("ligne;colonne;valeur;motif").contains("3;prix_vente;trois cents;Montant invalide");
    }

    @Test
    void rechercheInsensibleAuxAccentsParNomCodeEtCodeBarre() throws Exception {
        creerProduit("CIM50", "Ciment CPJ 45 — 50 kg", 5500, "[{\"valeur\":\"6131234500017\"}]", "{}");
        creerProduit("CAF1", "Café Touba 250 g", 1250, "[]", "{}");
        mvc.perform(get("/api/v1/referentiel/produits?q=cafe").with(admin)).andExpect(jsonPath("$.elements[*].code", contains("CAF1")));
        mvc.perform(get("/api/v1/referentiel/produits?q=CIMENT cpj").with(admin)).andExpect(jsonPath("$.elements[*].code", contains("CIM50")));
        mvc.perform(get("/api/v1/referentiel/produits?q=6131234500017").with(admin)).andExpect(jsonPath("$.elements[*].code", contains("CIM50")));
        // Une autre entreprise ne voit rien.
        var autre = creerEntreprise("Autre", Pack.BUSINESS);
        mvc.perform(get("/api/v1/referentiel/produits?q=ciment").with(comme(autre.id(), autre.adminKeycloakId())))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void champsPersonnalisesValidesEtFiltrables() throws Exception {
        champ("produit", "marque", "LISTE", "[\"Lafarge\",\"Cimaf\"]", true, false);
        champ("produit", "garantie_mois", "NOMBRE", "[]", false, false);
        champ("tiers", "secteur", "TEXTE", "[]", true, true);
        creerProduit("A1", "Ciment Lafarge", 5600, "[]", "{\"marque\":\"Lafarge\",\"garantie_mois\":\"6\"}");
        creerProduit("A2", "Ciment Cimaf", 5400, "[]", "{\"marque\":\"Cimaf\"}");
        mvc.perform(get("/api/v1/referentiel/produits?champ.marque=Cimaf").with(admin)).andExpect(jsonPath("$.elements[*].code", contains("A2")));
        mvc.perform(get("/api/v1/referentiel/produits/{id}", idProduit("A1")).with(admin)).andExpect(jsonPath("$.champsPerso.garantie_mois").value(6));
        mvc.perform(get("/api/v1/referentiel/produits?champ.garantie_mois=6").with(admin))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CHAMP_INVALIDE"));
        mvc.perform(post("/api/v1/referentiel/produits").with(admin).contentType(MediaType.APPLICATION_JSON).content(produit("A3", "X", 1, "[]",
                "{\"marque\":\"Autre\"}"))).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CHAMP_INVALIDE"));
        mvc.perform(post("/api/v1/referentiel/tiers").with(admin).contentType(MediaType.APPLICATION_JSON).content("""
                {"id":"%s","tiers":{"code":"T1","nom":"Client sans secteur"}}""".formatted(Uuid7.nouveau())))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.detail").value("Le champ « secteur » est obligatoire."));
        mvc.perform(get("/api/v1/studio/champs?entite=produit").with(admin)).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void INV_03_etPrixApplicableSelonLaListeDuClient() throws Exception {
        mvc.perform(post("/api/v1/referentiel/tiers").with(admin).contentType(MediaType.APPLICATION_JSON).content("""
                {"id":"%s","tiers":{"code":"BAT","nom":"Bâtir Faso SARL","regimeCode":"RNI"}}""".formatted(Uuid7.nouveau())))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INV-03"));

        creerProduit("SAV", "Savon 400 g", 300, "[]", "{}");
        var produit = idProduit("SAV");
        var liste = Uuid7.nouveau();
        mvc.perform(post("/api/v1/referentiel/listes-prix").with(admin).contentType(MediaType.APPLICATION_JSON).content("""
                {"id":"%s","liste":{"nom":"Grossistes","lignes":[{"produitId":"%s","quantiteMin":10,"prix":270},
                  {"produitId":"%s","quantiteMin":100,"prix":250}]}}
                """.formatted(liste, produit, produit))).andExpect(status().isCreated());
        var client = Uuid7.nouveau();
        mvc.perform(post("/api/v1/referentiel/tiers").with(admin).contentType(MediaType.APPLICATION_JSON).content("""
                {"id":"%s","tiers":{"code":"GROS","nom":"Boutique Salimata","ifu":"00098765 c","regimeCode":"RSI","listePrixId":"%s",
                 "telephone":"76543210","comptesMobileMoney":[{"operateur":"ORANGE_MONEY","numero":"76543210","parDefaut":true}]}}
                """.formatted(client, liste)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.ifu").value("00098765C")).andExpect(jsonPath("$.telephone").value("+226 76 54 32 10"));
        mvc.perform(get("/api/v1/referentiel/prix?produit={p}&tiers={c}&quantite=5", produit, client).with(admin))
                .andExpect(jsonPath("$.prixUnitaire").value(300)).andExpect(jsonPath("$.source").value("PRIX_DE_BASE"));
        mvc.perform(get("/api/v1/referentiel/prix?produit={p}&tiers={c}&quantite=150", produit, client).with(admin))
                .andExpect(jsonPath("$.prixUnitaire").value(250)).andExpect(jsonPath("$.source").value("LISTE_PRIX"));
    }

    private String produit(String code, String nom, long prix, String codesBarres, String champs) {
        return """
                {"id":"%s","produit":{"code":"%s","nom":"%s","taxeCode":"TVA18","prixVente":%d,"codesBarres":%s,"champsPerso":%s}}
                """.formatted(Uuid7.nouveau(), code, nom, prix, codesBarres, champs);
    }

    private void creerProduit(String code, String nom, long prix, String codesBarres, String champs) throws Exception {
        mvc.perform(post("/api/v1/referentiel/produits").with(admin).contentType(MediaType.APPLICATION_JSON)
                .content(produit(code, nom, prix, codesBarres, champs))).andExpect(status().isCreated());
    }

    private String idProduit(String code) throws Exception {
        var json = mvc.perform(get("/api/v1/referentiel/produits?q=" + code).with(admin)).andReturn().getResponse().getContentAsString();
        return json.replaceAll("(?s).*?\"id\":\"([0-9a-f-]{36})\".*", "$1");
    }
}
