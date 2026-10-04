package bf.ambawbio.socle.parametrage;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import bf.ambawbio.shared.domaine.RessourceIntrouvableException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/api/v1/socle/baremes")
@Tag(name = "Socle — barèmes", description = "Paramétrage fiscal versionné (F-SOC-09, RG-10)")
class BaremeControleur {

    record BaremeVue(UUID id, String code, LocalDate dateEffet, Map<String, Object> valeur, String description, boolean aValider) {
        static BaremeVue de(Bareme b) {
            return new BaremeVue(b.getId(), b.code(), b.dateEffet(), b.valeur(), b.description(), b.aValider());
        }
    }

    record NouvelleVersion(@NotNull(message = "L'identifiant est obligatoire.") UUID id,
            @NotBlank @Pattern(regexp = "[A-Z0-9_]{2,60}", message = "Code : majuscules, chiffres et _.") String code,
            @NotNull(message = "La date d'effet est obligatoire.") LocalDate dateEffet,
            @NotNull(message = "La valeur est obligatoire.") Map<String, Object> valeur,
            String description, Boolean aValider) {
    }

    private final ServiceBaremes service;

    BaremeControleur(ServiceBaremes service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('socle:consulter')")
    List<BaremeVue> tous() {
        return service.tous().stream().map(BaremeVue::de).toList();
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasAuthority('socle:consulter')")
    @Operation(summary = "Version en vigueur", description = "À la date demandée (aujourd'hui par défaut).")
    BaremeVue enVigueur(@PathVariable String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.enVigueur(code, date == null ? LocalDate.now() : date).map(BaremeVue::de)
                .orElseThrow(() -> new RessourceIntrouvableException("Aucun barème « " + code + " » en vigueur à cette date."));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('socle:parametrer')")
    @Operation(summary = "Ajouter une version", description = "Les versions existantes ne sont jamais modifiées (RG-10).")
    BaremeVue ajouter(@Valid @RequestBody NouvelleVersion v) {
        return BaremeVue.de(service.ajouterVersion(v.id(), v.code(), v.dateEffet(), v.valeur(), v.description(), Boolean.TRUE.equals(v.aValider())));
    }
}
