package bf.ambawbio.socle.identite;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import bf.ambawbio.shared.tenant.ContexteTenant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/api/v1/socle")
@PreAuthorize("hasAuthority('socle:utilisateurs')")
@Tag(name = "Socle — utilisateurs et rôles", description = "UC-SOC-03 ; comptes synchronisés avec Keycloak")
class IdentiteControleur {

    record UtilisateurVue(UUID id, String nomUtilisateur, String prenom, String nom, String courriel, String telephone, boolean actif,
            List<AffectationVue> affectations) {
    }

    record AffectationVue(UUID id, UUID roleId, UUID etablissementId) {
        static AffectationVue de(AffectationRole a) {
            return new AffectationVue(a.getId(), a.roleId(), a.etablissementId());
        }
    }

    record NouvelUtilisateur(@NotNull(message = "L'identifiant est obligatoire.") UUID id,
            @NotBlank(message = "Le nom d'utilisateur est obligatoire.")
            @Pattern(regexp = "[a-z0-9._-]{3,40}", message = "3 à 40 caractères : lettres minuscules, chiffres, point, tiret.")
            String nomUtilisateur,
            @NotBlank(message = "Le prénom est obligatoire.") String prenom,
            @NotBlank(message = "Le nom est obligatoire.") String nom,
            @NotBlank(message = "Le courriel est obligatoire.") @Email(message = "Courriel invalide.") String courriel,
            String telephone) {
    }

    record ModificationUtilisateur(@NotBlank(message = "Le prénom est obligatoire.") String prenom,
            @NotBlank(message = "Le nom est obligatoire.") String nom, String telephone, boolean actif) {
    }

    record NouvelleAffectation(@NotNull(message = "L'identifiant est obligatoire.") UUID id,
            @NotNull(message = "Le rôle est obligatoire.") UUID roleId, UUID etablissementId) {
    }

    record RoleVue(UUID id, String code, String libelle, List<String> permissions, boolean systeme) {
        static RoleVue de(Role r) {
            return new RoleVue(r.getId(), r.code(), r.libelle(), r.permissions(), r.systeme());
        }
    }

    record NouveauRole(@NotNull(message = "L'identifiant est obligatoire.") UUID id,
            @NotBlank(message = "Le code est obligatoire.") @Pattern(regexp = "[a-z0-9-]{2,40}", message = "Code : minuscules, chiffres, tiret.")
            String code,
            @NotBlank(message = "Le libellé est obligatoire.") String libelle,
            @NotNull List<String> permissions) {
    }

    record ModificationRole(@NotBlank(message = "Le libellé est obligatoire.") String libelle, @NotNull List<String> permissions) {
    }

    private final ServiceIdentite service;

    IdentiteControleur(ServiceIdentite service) {
        this.service = service;
    }

    private UtilisateurVue vue(Utilisateur u) {
        return new UtilisateurVue(u.getId(), u.nomUtilisateur(), u.prenom(), u.nom(), u.courriel(), u.telephone(), u.actif(),
                service.affectationsDe(u.getId()).stream().map(AffectationVue::de).toList());
    }

    @GetMapping("/utilisateurs")
    List<UtilisateurVue> utilisateurs() {
        return service.utilisateurs().stream().map(this::vue).toList();
    }

    @PostMapping("/utilisateurs")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un utilisateur", description = "Crée le compte Keycloak et envoie le courriel de définition du mot de passe.")
    UtilisateurVue creer(@Valid @RequestBody NouvelUtilisateur n) {
        return vue(service.creerUtilisateur(n.id(), ContexteTenant.tenantObligatoire(), n.nomUtilisateur(), n.prenom(), n.nom(),
                n.courriel(), n.telephone()));
    }

    @PutMapping("/utilisateurs/{id}")
    UtilisateurVue modifier(@PathVariable UUID id, @Valid @RequestBody ModificationUtilisateur m) {
        return vue(service.modifierUtilisateur(id, m.prenom(), m.nom(), m.telephone(), m.actif()));
    }

    @PostMapping("/utilisateurs/{id}/affectations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Affecter un rôle", description = "Établissement vide : tous les établissements (RG-14).")
    AffectationVue affecter(@PathVariable UUID id, @Valid @RequestBody NouvelleAffectation a) {
        return AffectationVue.de(service.affecter(a.id(), id, a.roleId(), a.etablissementId()));
    }

    @DeleteMapping("/affectations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void retirer(@PathVariable UUID id) {
        service.retirerAffectation(id);
    }

    @GetMapping("/roles")
    List<RoleVue> roles() {
        return service.roles().stream().map(RoleVue::de).toList();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    RoleVue creerRole(@Valid @RequestBody NouveauRole r) {
        return RoleVue.de(service.creerRole(r.id(), r.code(), r.libelle(), r.permissions()));
    }

    @PutMapping("/roles/{id}")
    RoleVue modifierRole(@PathVariable UUID id, @Valid @RequestBody ModificationRole r) {
        return RoleVue.de(service.modifierRole(id, r.libelle(), r.permissions()));
    }
}
