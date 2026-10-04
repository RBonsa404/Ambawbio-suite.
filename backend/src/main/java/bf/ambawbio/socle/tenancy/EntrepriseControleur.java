package bf.ambawbio.socle.tenancy;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/socle")
@Tag(name = "Socle — entreprise", description = "Paramétrage de l'entreprise (UC-SOC-02)")
class EntrepriseControleur {

    record EntrepriseVue(UUID id, String nom, String ifu, String rccm, String adresse, String ville, String telephone, String courriel,
            Pack pack, Entreprise.Statut statut, long version) {
        static EntrepriseVue de(Entreprise e) {
            return new EntrepriseVue(e.getId(), e.nom(), e.ifu(), e.rccm(), e.adresse(), e.ville(), e.telephone(), e.courriel(),
                    e.pack(), e.statut(), e.version());
        }
    }

    record ModificationEntreprise(@NotBlank(message = "Le nom est obligatoire.") @Size(max = 200) String nom,
            String ifu, String rccm, String adresse, String ville, String telephone, String courriel) {
    }

    record SocieteVue(UUID id, String nom, String ifu, String rccm, String regimeFiscal, boolean principale) {
        static SocieteVue de(Societe s) {
            return new SocieteVue(s.getId(), s.nom(), s.ifu(), s.rccm(), s.regimeFiscal(), s.principale());
        }
    }

    record NouvelleSociete(@NotNull(message = "L'identifiant est obligatoire.") UUID id,
            @NotBlank(message = "Le nom est obligatoire.") String nom, String ifu, String rccm, String regimeFiscal, String adresse) {
    }

    record EtablissementVue(UUID id, UUID societeId, String code, String nom, String adresse, String ville) {
        static EtablissementVue de(Etablissement e) {
            return new EtablissementVue(e.getId(), e.societeId(), e.code(), e.nom(), e.adresse(), e.ville());
        }
    }

    record NouvelEtablissement(@NotNull(message = "L'identifiant est obligatoire.") UUID id,
            @NotNull(message = "La société est obligatoire.") UUID societeId,
            @NotBlank(message = "Le code est obligatoire.") @Size(max = 20) String code,
            @NotBlank(message = "Le nom est obligatoire.") String nom, String adresse, String ville) {
    }

    record DepotVue(UUID id, UUID etablissementId, String code, String nom) {
        static DepotVue de(Depot d) {
            return new DepotVue(d.getId(), d.etablissementId(), d.code(), d.nom());
        }
    }

    record NouveauDepot(@NotNull(message = "L'identifiant est obligatoire.") UUID id,
            @NotNull(message = "L'établissement est obligatoire.") UUID etablissementId,
            @NotBlank(message = "Le code est obligatoire.") @Size(max = 20) String code,
            @NotBlank(message = "Le nom est obligatoire.") String nom) {
    }

    private final ServiceEntreprise service;

    EntrepriseControleur(ServiceEntreprise service) {
        this.service = service;
    }

    @GetMapping("/entreprise")
    @PreAuthorize("hasAuthority('socle:consulter')")
    @Operation(summary = "Fiche de l'entreprise")
    EntrepriseVue entreprise() {
        return EntrepriseVue.de(service.entrepriseCourante());
    }

    @PutMapping("/entreprise")
    @PreAuthorize("hasAuthority('socle:parametrer')")
    @Operation(summary = "Modifier la fiche de l'entreprise")
    EntrepriseVue modifier(@Valid @RequestBody ModificationEntreprise m) {
        return EntrepriseVue.de(service.modifier(m.nom(), m.ifu(), m.rccm(), m.adresse(), m.ville(), m.telephone(), m.courriel()));
    }

    @GetMapping("/societes")
    @PreAuthorize("hasAuthority('socle:consulter')")
    List<SocieteVue> societes() {
        return service.societes().stream().map(SocieteVue::de).toList();
    }

    @PostMapping("/societes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('socle:parametrer')")
    @Operation(summary = "Créer une société", description = "Plusieurs sociétés : pack Enterprise uniquement (RG-16, INV-15).")
    SocieteVue creerSociete(@Valid @RequestBody NouvelleSociete s) {
        return SocieteVue.de(service.creerSociete(s.id(), s.nom(), s.ifu(), s.rccm(), s.regimeFiscal(), s.adresse()));
    }

    @GetMapping("/etablissements")
    @PreAuthorize("hasAuthority('socle:consulter')")
    List<EtablissementVue> etablissements() {
        return service.etablissements().stream().map(EtablissementVue::de).toList();
    }

    @PostMapping("/etablissements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('socle:parametrer')")
    EtablissementVue creerEtablissement(@Valid @RequestBody NouvelEtablissement e) {
        return EtablissementVue.de(service.creerEtablissement(e.id(), e.societeId(), e.code(), e.nom(), e.adresse(), e.ville()));
    }

    @GetMapping("/depots")
    @PreAuthorize("hasAuthority('socle:consulter')")
    List<DepotVue> depots() {
        return service.depots().stream().map(DepotVue::de).toList();
    }

    @PostMapping("/depots")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('socle:parametrer')")
    DepotVue creerDepot(@Valid @RequestBody NouveauDepot d) {
        return DepotVue.de(service.creerDepot(d.id(), d.etablissementId(), d.code(), d.nom()));
    }

    @GetMapping("/modules")
    @PreAuthorize("hasAuthority('socle:consulter')")
    Set<String> modules() {
        return service.modulesActifs();
    }

    @PutMapping("/modules")
    @PreAuthorize("hasAuthority('socle:parametrer')")
    @Operation(summary = "Activer les modules", description = "Seuls les modules inclus dans le pack sont acceptés (F-SOC-05).")
    Set<String> definirModules(@RequestBody Set<String> modules) {
        return service.definirModulesActifs(modules);
    }
}
