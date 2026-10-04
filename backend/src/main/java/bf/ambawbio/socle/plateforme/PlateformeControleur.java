package bf.ambawbio.socle.plateforme;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import bf.ambawbio.socle.tenancy.Entreprise;
import bf.ambawbio.socle.tenancy.Pack;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Réservé au rôle Keycloak {@code admin-plateforme} (voir ConfigurationSecurite). */
@RestController
@RequestMapping("/api/v1/plateforme/entreprises")
@Tag(name = "Plateforme", description = "Administration des entreprises abonnées par l'éditeur (UC-SOC-13)")
class PlateformeControleur {

    record EntrepriseVue(UUID id, String nom, Pack pack, Entreprise.Statut statut, String ifu, String ville) {
        static EntrepriseVue de(Entreprise e) {
            return new EntrepriseVue(e.getId(), e.nom(), e.pack(), e.statut(), e.ifu(), e.ville());
        }
    }

    record Creation(@NotNull(message = "L'identifiant est obligatoire.") UUID id,
            @NotBlank(message = "Le nom est obligatoire.") String nom,
            @NotNull(message = "Le pack est obligatoire.") Pack pack,
            String ifu, String villeSiege,
            @NotBlank @Pattern(regexp = "[a-z0-9._-]{3,40}", message = "3 à 40 caractères : minuscules, chiffres, point, tiret.")
            String adminNomUtilisateur,
            @NotBlank(message = "Le prénom de l'administrateur est obligatoire.") String adminPrenom,
            @NotBlank(message = "Le nom de l'administrateur est obligatoire.") String adminNom,
            @NotBlank @Email(message = "Courriel invalide.") String adminCourriel) {
    }

    private final ServicePlateforme service;

    PlateformeControleur(ServicePlateforme service) {
        this.service = service;
    }

    @GetMapping
    List<EntrepriseVue> entreprises() {
        return service.entreprises().stream().map(EntrepriseVue::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une entreprise abonnée",
            description = "Société principale, siège, dépôt, rôles et barèmes par défaut, modules du pack, compte administrateur.")
    EntrepriseVue creer(@Valid @RequestBody Creation c) {
        return EntrepriseVue.de(service.creer(new ServicePlateforme.NouvelleEntreprise(c.id(), c.nom(), c.pack(), c.ifu(), c.villeSiege(),
                c.adminNomUtilisateur(), c.adminPrenom(), c.adminNom(), c.adminCourriel())));
    }

    @PostMapping("/{id}/suspension")
    EntrepriseVue suspendre(@PathVariable UUID id) {
        return EntrepriseVue.de(service.suspendre(id));
    }

    @PostMapping("/{id}/reactivation")
    EntrepriseVue reactiver(@PathVariable UUID id) {
        return EntrepriseVue.de(service.reactiver(id));
    }
}
