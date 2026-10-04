package bf.ambawbio.referentiel.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import bf.ambawbio.referentiel.domaine.Produit;
import bf.ambawbio.referentiel.domaine.Tiers;
import bf.ambawbio.shared.tenant.ContexteTenant;

/** Catalogue et clients de démonstration de la Quincaillerie Wend-Panga (profil {@code dev}, données fictives). */
@Component
@Profile({"dev", "demo"})
@Order(20)
class DonneesDemonstrationReferentiel implements ApplicationRunner {

    private static final UUID WEND_PANGA = UUID.fromString("01920000-0000-7000-8000-000000000001");
    private static final Logger JOURNAL = LoggerFactory.getLogger(DonneesDemonstrationReferentiel.class);

    private record Article(String code, String nom, long prix, String unite, String codeBarre, String conditionnement, String quantite, Long prixCond) {
    }

    private final ServiceParametres parametres;
    private final ServiceProduits produits;
    private final ServiceTiers tiers;
    private final TransactionTemplate transaction;

    DonneesDemonstrationReferentiel(ServiceParametres parametres, ServiceProduits produits, ServiceTiers tiers, TransactionTemplate transaction) {
        this.parametres = parametres;
        this.produits = produits;
        this.tiers = tiers;
        this.transaction = transaction;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        ContexteTenant.executerPour(WEND_PANGA, () -> transaction.execute(s -> {
            parametres.initialiser();
            if (produits.rechercher(null, null, null, Map.of(), 0, 1).getTotalElements() > 0) {
                return null;
            }
            var articles = List.of(
                    new Article("CIM-50", "Ciment CPJ 45 — sac 50 kg", 5_500, "U", "6130001000014", null, null, null),
                    new Article("RIZ-25", "Riz brisé — sac 25 kg", 17_500, "U", "6130001000021", null, null, null),
                    new Article("HUI-20", "Huile végétale — bidon 20 L", 24_000, "U", "6130001000038", null, null, null),
                    new Article("TOL-3", "Tôle bac 3 m", 4_750, "U", "6130001000045", null, null, null),
                    new Article("SAV-400", "Savon 400 g", 300, "U", "6130001000052", "CARTON", "72", 20_000L),
                    new Article("KAR-1", "Beurre de karité 1 kg", 1_500, "KG", "6130001000069", null, null, null),
                    new Article("FER-10", "Fer à béton 10 mm — barre 12 m", 3_250, "U", "6130001000076", "PAQUET", "10", 31_000L),
                    new Article("POI-20", "Pointes 70 mm — 1 kg", 1_200, "KG", "6130001000083", null, null, null));
            int n = 1;
            for (var a : articles) {
                var conditionnements = a.conditionnement() == null ? List.<Produit.DonneesConditionnement>of()
                        : List.of(new Produit.DonneesConditionnement(a.conditionnement(), null, new BigDecimal(a.quantite()), a.prixCond()));
                produits.creer(UUID.fromString("01920000-0000-7000-8000-%012x".formatted(0xC00 + n++)), new ServiceProduits.Commande(a.code(), a.nom(),
                        Produit.Type.BIEN, null, a.unite(), "TVA18", a.prix(), true, null, true, true, conditionnements,
                        List.of(new Produit.DonneesCodeBarre(a.codeBarre(), null)), Map.of()));
            }
            tiers.creer(UUID.fromString("01920000-0000-7000-8000-000000000d01"), new ServiceTiers.Commande("CLI-BATIR", "Bâtir Faso SARL (démo)",
                    Tiers.Nature.ENTREPRISE, true, false, "00054321D", "BF-OUA-2019-B-1234", "RNI",
                    new Tiers.Coordonnees("70 11 22 33", "contact@batirfaso.demo", "Zone industrielle de Kossodo", "Ouagadougou"),
                    new Tiers.ConditionsCommerciales(null, 30, 2_000_000L), true, List.of(),
                    List.of(new Tiers.DonneesCompte(Tiers.Operateur.ORANGE_MONEY, "70 11 22 33", "Bâtir Faso", true)), Map.of()));
            tiers.creer(UUID.fromString("01920000-0000-7000-8000-000000000d02"), new ServiceTiers.Commande("CLI-AMINATA", "Aminata Zongo",
                    Tiers.Nature.PARTICULIER, true, false, null, null, null, new Tiers.Coordonnees("76 44 55 66", null, null, "Ouagadougou"),
                    new Tiers.ConditionsCommerciales(null, 0, null), true, List.of(),
                    List.of(new Tiers.DonneesCompte(Tiers.Operateur.MOOV_MONEY, "76 44 55 66", "Aminata Zongo", true)), Map.of()));
            tiers.creer(UUID.fromString("01920000-0000-7000-8000-000000000d03"), new ServiceTiers.Commande("FOU-CIMENT", "Cimenterie du Faso (démo)",
                    Tiers.Nature.ENTREPRISE, false, true, "00011122E", null, "RNI", new Tiers.Coordonnees("25 30 00 00", null, null, "Ouagadougou"),
                    new Tiers.ConditionsCommerciales(null, 45, null), true, List.of(), List.of(), Map.of()));
            JOURNAL.info("Catalogue de démonstration créé : Quincaillerie Wend-Panga");
            return null;
        }));
    }
}
