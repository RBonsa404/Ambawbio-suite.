package bf.ambawbio.referentiel.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import bf.ambawbio.referentiel.domaine.Parametres.Categorie;
import bf.ambawbio.referentiel.domaine.Parametres.Taxe;
import bf.ambawbio.referentiel.domaine.Parametres.UniteMesure;
import bf.ambawbio.referentiel.domaine.Produit;
import bf.ambawbio.referentiel.domaine.Tiers;
import bf.ambawbio.sync.api.FluxChangements;

/**
 * Publie vers les terminaux les données du référentiel utiles hors-ligne (guide §8.4) : catalogue, prix, taxes,
 * unités, catégories, clients. Appelé dans la transaction de la modification. Les fiches sont réduites à ce
 * dont le terminal a besoin (pas de prix d'achat, pas de contacts).
 */
@Component
class PublicationReferentiel {

    private final FluxChangements flux;
    private final TaxeDepot taxes;
    private final UniteDepot unites;
    private final CategorieDepot categories;
    private final ProduitDepot produits;
    private final TiersDepot tiers;

    PublicationReferentiel(FluxChangements flux, TaxeDepot taxes, UniteDepot unites, CategorieDepot categories, ProduitDepot produits,
            TiersDepot tiers) {
        this.flux = flux;
        this.taxes = taxes;
        this.unites = unites;
        this.categories = categories;
        this.produits = produits;
        this.tiers = tiers;
    }

    void produit(Produit p) {
        var donnees = new LinkedHashMap<String, Object>();
        donnees.put("code", p.code());
        donnees.put("nom", p.nom());
        donnees.put("type", p.type().name());
        donnees.put("categorieId", p.categorieId());
        donnees.put("uniteId", p.uniteId());
        donnees.put("taxeId", p.taxeId());
        donnees.put("prixVente", p.prixVente());
        donnees.put("prixVenteTtc", p.prixVenteTtc());
        donnees.put("suiviStock", p.suiviStock());
        donnees.put("actif", p.actif());
        donnees.put("conditionnements", p.conditionnements().stream()
                .map(c -> Map.of("code", c.code(), "libelle", c.libelle(), "quantite", c.quantite(), "prixVente", c.prixVente() == null ? "" : c.prixVente()))
                .toList());
        donnees.put("codesBarres", p.codesBarres().stream()
                .map(c -> Map.of("valeur", c.valeur(), "conditionnement", c.conditionnementCode() == null ? "" : c.conditionnementCode())).toList());
        flux.publier("produit", p.getId(), donnees, null);
    }

    void tiers(Tiers t) {
        if (!t.estClient()) {
            return;
        }
        var donnees = new LinkedHashMap<String, Object>();
        donnees.put("code", t.code());
        donnees.put("nom", t.nom());
        donnees.put("ifu", t.ifu());
        donnees.put("telephone", t.telephone());
        donnees.put("courriel", t.courriel());
        donnees.put("adresse", t.adresse());
        donnees.put("ville", t.ville());
        donnees.put("regimeFiscalId", t.regimeFiscalId());
        donnees.put("listePrixId", t.listePrixId());
        donnees.put("actif", t.actif());
        donnees.put("comptesMobileMoney", t.comptesMobileMoney().stream()
                .map(c -> Map.of("operateur", c.operateur().name(), "numero", c.numero(), "parDefaut", c.parDefaut())).toList());
        flux.publier("client", t.getId(), donnees, null);
    }

    void taxe(Taxe t) {
        flux.publier("taxe", t.getId(), Map.of("code", t.code(), "libelle", t.libelle(), "taux", t.taux(), "actif", t.actif()), null);
    }

    void unite(UniteMesure u) {
        flux.publier("unite", u.getId(), Map.of("code", u.code(), "libelle", u.libelle()), null);
    }

    void categorie(Categorie c) {
        var donnees = new LinkedHashMap<String, Object>();
        donnees.put("nom", c.nom());
        donnees.put("parentId", c.parentId());
        flux.publier("categorie", c.getId(), donnees, null);
    }

    /** Publication complète de l'entreprise du contexte (données antérieures au moteur de synchronisation). */
    @Transactional
    public void toutPublier() {
        taxes.findAll().forEach(this::taxe);
        unites.findAll().forEach(this::unite);
        categories.findAll().forEach(this::categorie);
        produits.findAll().forEach(this::produit);
        tiers.findAll().forEach(this::tiers);
    }

    boolean dejaPublie() {
        return flux.contient("taxe");
    }

    static <T> Map<Object, T> parId(Iterable<T> elements, Function<T, Object> id) {
        return java.util.stream.StreamSupport.stream(elements.spliterator(), false).collect(Collectors.toMap(id, Function.identity()));
    }
}
