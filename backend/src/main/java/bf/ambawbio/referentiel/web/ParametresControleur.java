package bf.ambawbio.referentiel.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import bf.ambawbio.referentiel.application.ServiceParametres;
import bf.ambawbio.referentiel.domaine.Parametres.UniteMesure;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/referentiel")
@Tag(name = "Référentiel — paramètres", description = "Régimes fiscaux, taxes, unités, types de conditionnement, catégories")
class ParametresControleur {

    record RegimeVue(UUID id, String code, String libelle, boolean assujettiTva, boolean systeme) {
    }

    record NouveauRegime(@NotNull UUID id, @NotBlank String code, @NotBlank String libelle, boolean assujettiTva) {
    }

    record TaxeVue(UUID id, String code, String libelle, BigDecimal taux, boolean actif, boolean aValider) {
    }

    record NouvelleTaxe(@NotNull UUID id, @NotBlank String code, @NotBlank String libelle, @NotNull BigDecimal taux) {
    }

    record ModificationTaxe(@NotBlank String libelle, @NotNull BigDecimal taux, Boolean actif) {
    }

    record UniteVue(UUID id, String code, String libelle, UniteMesure.Categorie categorie) {
    }

    record NouvelleUnite(@NotNull UUID id, @NotBlank String code, @NotBlank String libelle, @NotNull UniteMesure.Categorie categorie) {
    }

    record TypeConditionnementVue(UUID id, String code, String libelle) {
    }

    record CategorieVue(UUID id, String nom, UUID parentId) {
    }

    record SaisieCategorie(@NotNull UUID id, @NotBlank(message = "Le nom est obligatoire.") String nom, UUID parentId) {
    }

    private final ServiceParametres service;

    ParametresControleur(ServiceParametres service) {
        this.service = service;
    }

    @GetMapping("/regimes-fiscaux")
    @PreAuthorize("hasAuthority('socle:consulter')")
    List<RegimeVue> regimes() {
        return service.regimes().stream().map(r -> new RegimeVue(r.getId(), r.code(), r.libelle(), r.assujettiTva(), r.systeme())).toList();
    }

    @PostMapping("/regimes-fiscaux")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('socle:parametrer')")
    RegimeVue creerRegime(@Valid @RequestBody NouveauRegime r) {
        var regime = service.creerRegime(r.id(), r.code(), r.libelle(), r.assujettiTva());
        return new RegimeVue(regime.getId(), regime.code(), regime.libelle(), regime.assujettiTva(), regime.systeme());
    }

    @GetMapping("/taxes")
    @PreAuthorize("hasAuthority('socle:consulter')")
    List<TaxeVue> taxes() {
        return service.taxes().stream().map(t -> new TaxeVue(t.getId(), t.code(), t.libelle(), t.taux(), t.actif(), t.aValider())).toList();
    }

    @PostMapping("/taxes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('socle:parametrer')")
    TaxeVue creerTaxe(@Valid @RequestBody NouvelleTaxe n) {
        var t = service.creerTaxe(n.id(), n.code(), n.libelle(), n.taux());
        return new TaxeVue(t.getId(), t.code(), t.libelle(), t.taux(), t.actif(), t.aValider());
    }

    @PutMapping("/taxes/{id}")
    @PreAuthorize("hasAuthority('socle:parametrer')")
    TaxeVue modifierTaxe(@PathVariable UUID id, @Valid @RequestBody ModificationTaxe m) {
        var t = service.modifierTaxe(id, m.libelle(), m.taux(), !Boolean.FALSE.equals(m.actif()));
        return new TaxeVue(t.getId(), t.code(), t.libelle(), t.taux(), t.actif(), t.aValider());
    }

    @GetMapping("/unites")
    @PreAuthorize("hasAuthority('socle:consulter')")
    List<UniteVue> unites() {
        return service.unites().stream().map(u -> new UniteVue(u.getId(), u.code(), u.libelle(), u.categorie())).toList();
    }

    @PostMapping("/unites")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('referentiel:gerer')")
    UniteVue creerUnite(@Valid @RequestBody NouvelleUnite n) {
        var u = service.creerUnite(n.id(), n.code(), n.libelle(), n.categorie());
        return new UniteVue(u.getId(), u.code(), u.libelle(), u.categorie());
    }

    @GetMapping("/types-conditionnement")
    @PreAuthorize("hasAuthority('socle:consulter')")
    List<TypeConditionnementVue> typesConditionnement() {
        return service.typesConditionnement().stream().map(t -> new TypeConditionnementVue(t.getId(), t.code(), t.libelle())).toList();
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('socle:consulter')")
    List<CategorieVue> categories() {
        return service.categories().stream().map(c -> new CategorieVue(c.getId(), c.nom(), c.parentId())).toList();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('referentiel:gerer')")
    CategorieVue creerCategorie(@Valid @RequestBody SaisieCategorie s) {
        var c = service.creerCategorie(s.id(), s.nom(), s.parentId());
        return new CategorieVue(c.getId(), c.nom(), c.parentId());
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('referentiel:gerer')")
    CategorieVue modifierCategorie(@PathVariable UUID id, @Valid @RequestBody SaisieCategorie s) {
        var c = service.modifierCategorie(id, s.nom(), s.parentId());
        return new CategorieVue(c.getId(), c.nom(), c.parentId());
    }
}
