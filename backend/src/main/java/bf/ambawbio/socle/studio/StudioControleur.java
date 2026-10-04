package bf.ambawbio.socle.studio;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/studio/champs")
@Tag(name = "Studio — champs personnalisés", description = "UC-SOC-10 ; valeurs stockées dans champs_perso, filtrables")
class StudioControleur {

    record ChampVue(UUID id, String entite, String code, String libelle, DefinitionChamp.Type type, List<String> options,
            boolean obligatoire, boolean filtrable, int ordre) {
        static ChampVue de(DefinitionChamp d) {
            return new ChampVue(d.getId(), d.entite(), d.code(), d.libelle(), d.type(), d.options(), d.obligatoire(), d.filtrable(), d.ordre());
        }
    }

    record NouveauChamp(@NotNull(message = "L'identifiant est obligatoire.") UUID id,
            @NotBlank(message = "La fiche concernée est obligatoire.") String entite,
            @NotBlank(message = "Le code est obligatoire.") String code,
            @NotBlank(message = "Le libellé est obligatoire.") String libelle,
            @NotNull(message = "Le type est obligatoire.") DefinitionChamp.Type type,
            List<String> options, Boolean obligatoire, Boolean filtrable, Integer ordre) {
    }

    record ModificationChamp(@NotBlank(message = "Le libellé est obligatoire.") String libelle, List<String> options,
            Boolean obligatoire, Boolean filtrable, Integer ordre) {
    }

    private final ServiceChampsPersonnalises service;

    StudioControleur(ServiceChampsPersonnalises service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('socle:consulter')")
    @Operation(summary = "Champs d'une fiche", description = "Définitions utilisées pour le rendu dynamique des formulaires.")
    List<ChampVue> champs(@RequestParam String entite) {
        return service.liste(entite).stream().map(ChampVue::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('socle:parametrer')")
    ChampVue creer(@Valid @RequestBody NouveauChamp c) {
        return ChampVue.de(service.creer(c.id(), c.entite(), c.code(), c.libelle(), c.type(), c.options(),
                Boolean.TRUE.equals(c.obligatoire()), Boolean.TRUE.equals(c.filtrable()), c.ordre() == null ? 0 : c.ordre()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('socle:parametrer')")
    ChampVue modifier(@PathVariable UUID id, @Valid @RequestBody ModificationChamp c) {
        return ChampVue.de(service.modifier(id, c.libelle(), c.options(), Boolean.TRUE.equals(c.obligatoire()),
                Boolean.TRUE.equals(c.filtrable()), c.ordre() == null ? 0 : c.ordre()));
    }
}
