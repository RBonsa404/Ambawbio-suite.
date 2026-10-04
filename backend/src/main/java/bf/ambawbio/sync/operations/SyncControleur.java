package bf.ambawbio.sync.operations;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bf.ambawbio.sync.terminaux.ServiceTerminaux;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/sync")
@PreAuthorize("isAuthenticated() and @contexteUtilisateur.estRattache()")
@Tag(name = "Synchronisation", description = "Envoi des opérations et réception des changements (UC-SOC-05, SD-03)")
class SyncControleur {

    record OperationEnvoyee(@NotNull UUID idOperation, @NotBlank String type, int versionSchema, @NotBlank String horodatageLocal, UUID utilisateurId,
            @NotBlank String charge, @NotBlank String signature) {
    }

    record Envoi(@NotNull UUID terminalId, @NotNull List<@Valid OperationEnvoyee> operations, List<ServiceSynchronisation.ConsommationPlage> plages) {
    }

    record ReponseEnvoi(List<ServiceSynchronisation.Accuse> accuses) {
    }

    private final ServiceSynchronisation service;

    SyncControleur(ServiceSynchronisation service) {
        this.service = service;
    }

    @PostMapping("/push")
    @Operation(summary = "Envoyer des opérations",
            description = "100 opérations au plus, corps éventuellement compressé (Content-Encoding: gzip). Le terminal ne retire de sa file "
                    + "que les opérations accusées APPLIQUEE ou IGNOREE_DOUBLON.")
    ReponseEnvoi pousser(@Valid @RequestBody Envoi envoi) {
        return new ReponseEnvoi(service.pousser(envoi.terminalId(), envoi.operations().stream()
                .map(o -> new ServiceSynchronisation.OperationRecue(o.idOperation(), o.type(), o.versionSchema(), o.horodatageLocal(), o.utilisateurId(),
                        o.charge(), o.signature())).toList(), envoi.plages()));
    }

    @GetMapping("/pull")
    @Operation(summary = "Recevoir les changements", description = "Changements postérieurs au curseur (0 pour le chargement initial), par pages.")
    ServiceSynchronisation.Reception recevoir(@RequestParam UUID terminalId, @RequestParam(defaultValue = "0") long curseur,
            @RequestParam(required = false) Integer limite) {
        return service.recevoir(terminalId, curseur, limite);
    }

    @ExceptionHandler(ServiceTerminaux.TerminalRevoqueException.class)
    ProblemDetail revoque(ServiceTerminaux.TerminalRevoqueException e) {
        var probleme = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        probleme.setProperty("code", e.code());
        return probleme;
    }
}
