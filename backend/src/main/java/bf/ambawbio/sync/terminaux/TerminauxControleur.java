package bf.ambawbio.sync.terminaux;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import bf.ambawbio.sync.operations.ServiceSynchronisation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/socle/terminaux")
@Tag(name = "Terminaux", description = "Enregistrement, appairage par QR code et révocation (UC-SOC-04, SD-11)")
class TerminauxControleur {

    record TerminalVue(UUID id, UUID etablissementId, String code, String nom, Terminal.Statut statut, Instant appaireLe, Instant revoqueLe,
            Instant derniereSynchro) {
        static TerminalVue de(Terminal t) {
            return new TerminalVue(t.getId(), t.etablissementId(), t.code(), t.nom(), t.statut(), t.appaireLe(), t.revoqueLe(), t.derniereSynchro());
        }
    }

    /** Contenu du QR code : terminal et code à usage unique ; le code lisible est affiché à côté pour une saisie manuelle. */
    record CodeAppairage(TerminalVue terminal, String code, Instant expireLe, String contenuQr) {
        static CodeAppairage de(ServiceTerminaux.Appairage a) {
            var code = a.codeAppairage().substring(0, 4) + "-" + a.codeAppairage().substring(4);
            return new CodeAppairage(TerminalVue.de(a.terminal()), code, a.terminal().appairageExpireLe(),
                    "{\"t\":\"" + a.terminal().getId() + "\",\"c\":\"" + code + "\"}");
        }
    }

    record NouveauTerminal(@NotNull UUID id, @NotNull(message = "L'établissement est obligatoire.") UUID etablissementId,
            @NotBlank(message = "Le nom est obligatoire.") String nom) {
    }

    record DemandeAppairage(@NotNull UUID terminalId, @NotBlank(message = "Le code d'appairage est obligatoire.") String code,
            @NotBlank String clePublique) {
    }

    record ReponseAppairage(TerminalVue terminal, List<ServiceSynchronisation.PlageVue> plages) {
    }

    private final ServiceTerminaux service;

    TerminauxControleur(ServiceTerminaux service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('terminaux:gerer')")
    List<TerminalVue> liste() {
        return service.liste().stream().map(TerminalVue::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('terminaux:gerer')")
    @Operation(summary = "Enregistrer un terminal", description = "Renvoie le QR code d'appairage, valable 15 minutes.")
    CodeAppairage creer(@Valid @RequestBody NouveauTerminal n) {
        return CodeAppairage.de(service.creer(n.id(), n.etablissementId(), n.nom()));
    }

    @PostMapping("/{id}/code-appairage")
    @PreAuthorize("hasAuthority('terminaux:gerer')")
    CodeAppairage nouveauCode(@PathVariable UUID id) {
        return CodeAppairage.de(service.nouveauCode(id));
    }

    @PostMapping("/appairage")
    @PreAuthorize("isAuthenticated() and @contexteUtilisateur.estRattache()")
    @Operation(summary = "Appairer ce terminal", description = "Le terminal présente le code du QR code et sa clé publique ECDSA P-256 ; il reçoit ses plages.")
    ReponseAppairage appairer(@Valid @RequestBody DemandeAppairage d) {
        var resultat = service.appairer(d.terminalId(), d.code(), d.clePublique());
        return new ReponseAppairage(TerminalVue.de(resultat.terminal()),
                resultat.plages().stream().map(p -> ServiceSynchronisation.PlageVue.de(p, resultat.terminal().code())).toList());
    }

    @PostMapping("/{id}/revocation")
    @PreAuthorize("hasAuthority('terminaux:gerer')")
    @Operation(summary = "Révoquer un terminal", description = "Irréversible : le terminal ne peut plus synchroniser.")
    TerminalVue revoquer(@PathVariable UUID id) {
        return TerminalVue.de(service.revoquer(id));
    }

    @ExceptionHandler(ServiceTerminaux.TerminalRevoqueException.class)
    ProblemDetail revoque(ServiceTerminaux.TerminalRevoqueException e) {
        var probleme = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        probleme.setProperty("code", e.code());
        return probleme;
    }
}
