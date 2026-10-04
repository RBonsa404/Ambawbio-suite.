package bf.ambawbio.referentiel.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bf.ambawbio.referentiel.domaine.Produit;
import bf.ambawbio.shared.domaine.RegleMetierException;
import bf.ambawbio.shared.domaine.RessourceIntrouvableException;
import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.socle.api.ChampsPersonnalises;
import bf.ambawbio.socle.api.JournalAudit;
import tools.jackson.databind.json.JsonMapper;

/** Produits et services (F-SOC-07, UC-SOC-09). */
@Service
public class ServiceProduits {

    public record Commande(String code, String nom, Produit.Type type, UUID categorieId, String uniteCode, String taxeCode,
            long prixVente, boolean prixVenteTtc, Long prixAchat, boolean suiviStock, boolean actif,
            List<Produit.DonneesConditionnement> conditionnements, List<Produit.DonneesCodeBarre> codesBarres,
            Map<String, Object> champsPerso) {
    }

    private final ProduitDepot produits;
    private final UniteDepot unites;
    private final TaxeDepot taxes;
    private final CategorieDepot categories;
    private final ChampsPersonnalises champs;
    private final JournalAudit audit;
    private final JsonMapper json;
    private final PublicationReferentiel publication;

    ServiceProduits(ProduitDepot produits, UniteDepot unites, TaxeDepot taxes, CategorieDepot categories, ChampsPersonnalises champs,
            JournalAudit audit, JsonMapper json, PublicationReferentiel publication) {
        this.produits = produits;
        this.unites = unites;
        this.taxes = taxes;
        this.categories = categories;
        this.champs = champs;
        this.audit = audit;
        this.json = json;
        this.publication = publication;
    }

    @Transactional(readOnly = true)
    public Page<Produit> rechercher(String q, UUID categorie, Boolean actif, Map<String, String> filtresChamps, int page, int taille) {
        var filtre = champs.filtre("produit", filtresChamps);
        return produits.rechercher(ContexteTenant.tenantObligatoire(), q == null || q.isBlank() ? null : q.trim(), categorie, actif,
                json.writeValueAsString(filtre), PageRequest.of(Math.max(page, 0), Math.min(Math.max(taille, 1), 200)));
    }

    @Transactional(readOnly = true)
    public Produit produit(UUID id) {
        return produits.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Produit introuvable."));
    }

    /** Lecture d'un code-barres (scan en caisse ou au magasin). */
    @Transactional(readOnly = true)
    public Optional<Produit> parCodeBarre(String valeur) {
        return produits.parCodeBarre(ContexteTenant.tenantObligatoire(), valeur);
    }

    /** Création idempotente (identifiant fourni par le client). */
    @Transactional
    public Produit creer(UUID id, Commande c) {
        var existant = produits.findById(id);
        if (existant.isPresent()) {
            return existant.get();
        }
        if (produits.findByCode(c.code()).isPresent()) {
            throw new RegleMetierException("CODE_EXISTANT", "Un produit porte déjà le code « " + c.code() + " ».");
        }
        var produit = new Produit(id, c.code());
        appliquer(produit, c);
        produits.save(produit);
        publication.produit(produit);
        audit.enregistrer("PRODUIT_CREE", "produit", id, null, Map.of("code", c.code(), "prixVente", c.prixVente()));
        return produit;
    }

    /** Une modification du prix de vente est journalisée (guide §6.6). */
    @Transactional
    public Produit modifier(UUID id, Commande c) {
        var produit = produit(id);
        var ancienPrix = produit.prixVente();
        appliquer(produit, c);
        produits.flush();
        publication.produit(produit);
        if (ancienPrix != c.prixVente()) {
            audit.enregistrer("PRIX_MODIFIE", "produit", id, Map.of("prixVente", ancienPrix), Map.of("prixVente", c.prixVente()));
        }
        return produit;
    }

    void appliquer(Produit produit, Commande c) {
        var unite = unites.findByCode(c.uniteCode() == null ? "U" : c.uniteCode())
                .orElseThrow(() -> new RegleMetierException("UNITE_INCONNUE", "Unité inconnue : « " + c.uniteCode() + " »."));
        var taxe = taxes.findByCode(c.taxeCode())
                .orElseThrow(() -> new RegleMetierException("TAXE_INCONNUE", "Taxe inconnue : « " + c.taxeCode() + " »."));
        if (c.categorieId() != null && !categories.existsById(c.categorieId())) {
            throw new RessourceIntrouvableException("Catégorie introuvable.");
        }
        produit.modifier(c.nom(), c.type(), c.categorieId(), unite.getId(), taxe.getId(), c.prixVente(), c.prixVenteTtc(), c.prixAchat(),
                c.suiviStock(), c.actif(), champs.valider("produit", c.champsPerso()));
        produit.definirConditionnements(c.conditionnements() == null ? List.of() : c.conditionnements());
        var codes = c.codesBarres() == null ? List.<Produit.DonneesCodeBarre>of() : c.codesBarres();
        if (!codes.isEmpty()) {
            var pris = produits.codesBarresPrisAilleurs(ContexteTenant.tenantObligatoire(),
                    codes.stream().map(Produit.DonneesCodeBarre::valeur).toList(), produit.getId());
            if (!pris.isEmpty()) {
                throw new RegleMetierException("CODE_BARRE_EXISTANT", "Code-barres déjà attribué à un autre produit : " + String.join(", ", pris) + ".");
            }
        }
        produit.definirCodesBarres(codes);
    }
}
