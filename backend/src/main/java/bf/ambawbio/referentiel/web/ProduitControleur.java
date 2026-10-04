package bf.ambawbio.referentiel.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import bf.ambawbio.referentiel.application.ServiceProduits;
import bf.ambawbio.referentiel.domaine.Produit;
import bf.ambawbio.shared.domaine.RessourceIntrouvableException;
import bf.ambawbio.shared.web.PageResultat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@RestController
@org.springframework.transaction.annotation.Transactional(readOnly = true)
@RequestMapping("/api/v1/referentiel/produits")
@Tag(name = "Référentiel — produits", description = "Produits et services, conditionnements, codes-barres (F-SOC-07, UC-SOC-09)")
class ProduitControleur {

    record ConditionnementVue(String code, String libelle, BigDecimal quantite, Long prixVente) {
    }

    record CodeBarreVue(String valeur, String conditionnementCode) {
    }

    record ProduitVue(UUID id, String code, String nom, Produit.Type type, UUID categorieId, UUID uniteId, UUID taxeId, long prixVente,
            boolean prixVenteTtc, Long prixAchat, boolean suiviStock, boolean actif, List<ConditionnementVue> conditionnements,
            List<CodeBarreVue> codesBarres, Map<String, Object> champsPerso, long version) {
        static ProduitVue de(Produit p) {
            return new ProduitVue(p.getId(), p.code(), p.nom(), p.type(), p.categorieId(), p.uniteId(), p.taxeId(), p.prixVente(), p.prixVenteTtc(),
                    p.prixAchat(), p.suiviStock(), p.actif(),
                    p.conditionnements().stream().map(c -> new ConditionnementVue(c.code(), c.libelle(), c.quantite(), c.prixVente())).toList(),
                    p.codesBarres().stream().map(c -> new CodeBarreVue(c.valeur(), c.conditionnementCode())).toList(),
                    p.champsPerso(), p.version());
        }
    }

    record SaisieProduit(@NotBlank(message = "Le code est obligatoire.") String code,
            @NotBlank(message = "Le nom est obligatoire.") String nom,
            Produit.Type type, UUID categorieId, String uniteCode,
            @NotBlank(message = "La taxe est obligatoire.") String taxeCode,
            @NotNull(message = "Le prix de vente est obligatoire.") @PositiveOrZero(message = "Le prix ne peut pas être négatif.") Long prixVente,
            Boolean prixVenteTtc, @PositiveOrZero(message = "Le prix ne peut pas être négatif.") Long prixAchat, Boolean suiviStock, Boolean actif,
            List<ConditionnementVue> conditionnements, List<CodeBarreVue> codesBarres, Map<String, Object> champsPerso) {
        ServiceProduits.Commande commande() {
            return new ServiceProduits.Commande(code, nom, type, categorieId, uniteCode, taxeCode, prixVente, !Boolean.FALSE.equals(prixVenteTtc),
                    prixAchat, !Boolean.FALSE.equals(suiviStock), !Boolean.FALSE.equals(actif),
                    conditionnements == null ? List.of()
                            : conditionnements.stream()
                                    .map(c -> new Produit.DonneesConditionnement(c.code(), c.libelle(), c.quantite(), c.prixVente())).toList(),
                    codesBarres == null ? List.of() : codesBarres.stream().map(c -> new Produit.DonneesCodeBarre(c.valeur(), c.conditionnementCode())).toList(),
                    champsPerso);
        }
    }

    record NouveauProduit(@NotNull(message = "L'identifiant est obligatoire.") UUID id, @Valid @NotNull SaisieProduit produit) {
    }

    private final ServiceProduits service;

    ProduitControleur(ServiceProduits service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('socle:consulter')")
    @Operation(summary = "Rechercher des produits",
            description = "Recherche par nom, code ou code-barres, insensible aux accents ; "
                    + "filtres champ.<code>=valeur sur les champs personnalisés filtrables.")
    PageResultat<ProduitVue> rechercher(@RequestParam(required = false) String q, @RequestParam(required = false) UUID categorie,
            @RequestParam(required = false) Boolean actif, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int taille, @RequestParam Map<String, String> parametres) {
        return PageResultat.de(service.rechercher(q, categorie, actif, parametres, page, taille), ProduitVue::de);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('socle:consulter')")
    ProduitVue produit(@PathVariable UUID id) {
        return ProduitVue.de(service.produit(id));
    }

    @GetMapping("/code-barre/{valeur}")
    @PreAuthorize("hasAuthority('socle:consulter')")
    @Operation(summary = "Produit par code-barres", description = "Lecture d'un code-barres en caisse ou au magasin.")
    ProduitVue parCodeBarre(@PathVariable String valeur) {
        return service.parCodeBarre(valeur).map(ProduitVue::de)
                .orElseThrow(() -> new RessourceIntrouvableException("Aucun produit ne porte ce code-barres. Vérifiez la saisie ou créez le produit."));
    }

    @org.springframework.transaction.annotation.Transactional
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('referentiel:gerer')")
    ProduitVue creer(@Valid @RequestBody NouveauProduit n) {
        return ProduitVue.de(service.creer(n.id(), n.produit().commande()));
    }

    @org.springframework.transaction.annotation.Transactional
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('referentiel:gerer')")
    ProduitVue modifier(@PathVariable UUID id, @Valid @RequestBody SaisieProduit p) {
        return ProduitVue.de(service.modifier(id, p.commande()));
    }
}
