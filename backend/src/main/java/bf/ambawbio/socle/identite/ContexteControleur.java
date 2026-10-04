package bf.ambawbio.socle.identite;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.socle.api.JournalAudit;
import bf.ambawbio.socle.tenancy.Pack;
import bf.ambawbio.socle.tenancy.ServiceEntreprise;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/socle")
@Tag(name = "Socle — contexte", description = "UC-SOC-01, SD-01")
class ContexteControleur {

    record Contexte(UtilisateurCourant utilisateur, EntrepriseCourante entreprise, List<SocieteCourante> societes,
            List<EtablissementCourant> etablissements, Set<String> roles, Set<String> permissions, Set<String> modules) {
    }

    record UtilisateurCourant(UUID id, String nomUtilisateur, String nomComplet, String courriel) {
    }

    record EntrepriseCourante(UUID id, String nom, Pack pack, String statut) {
    }

    record SocieteCourante(UUID id, String nom) {
    }

    record EtablissementCourant(UUID id, UUID societeId, String code, String nom) {
    }

    private final ServiceIdentite identite;
    private final ServiceEntreprise entreprise;
    private final JournalAudit audit;

    ContexteControleur(ServiceIdentite identite, ServiceEntreprise entreprise, JournalAudit audit) {
        this.identite = identite;
        this.entreprise = entreprise;
        this.audit = audit;
    }

    @GetMapping("/contexte")
    @PreAuthorize("isAuthenticated() and @contexteUtilisateur.estRattache()")
    @Operation(summary = "Contexte de l'utilisateur connecté",
            description = "Entreprise, sociétés, établissements autorisés (RG-14), rôles, permissions et modules actifs.")
    Contexte contexte() {
        var utilisateur = identite.utilisateur(ContexteTenant.utilisateurCourant().orElseThrow());
        var profil = identite.profilDe(utilisateur.getId());
        var e = entreprise.entrepriseCourante();
        return new Contexte(
                new UtilisateurCourant(utilisateur.getId(), utilisateur.nomUtilisateur(), utilisateur.nomComplet(), utilisateur.courriel()),
                new EntrepriseCourante(e.getId(), e.nom(), e.pack(), e.statut().name()),
                entreprise.societes().stream().map(s -> new SocieteCourante(s.getId(), s.nom())).toList(),
                entreprise.etablissements().stream().filter(et -> profil.autorise(et.getId()))
                        .map(et -> new EtablissementCourant(et.getId(), et.societeId(), et.code(), et.nom())).toList(),
                profil.roles(), profil.permissions(), entreprise.modulesActifs());
    }

    @PostMapping("/connexions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated() and @contexteUtilisateur.estRattache()")
    @Operation(summary = "Signaler une connexion", description = "Appelé par le client après l'authentification Keycloak ; journalisé (SD-01, RG-11).")
    @org.springframework.transaction.annotation.Transactional
    void connexion() {
        audit.enregistrer("CONNEXION", "utilisateur", ContexteTenant.utilisateurCourant().orElseThrow(), null, null);
    }
}
