package bf.ambawbio.conformite.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
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

import bf.ambawbio.conformite.application.ServiceCertification;
import bf.ambawbio.conformite.application.SimulateurFec;
import bf.ambawbio.shared.domaine.RegleMetierException;

/** File de certification (W-10) et pilotage du simulateur en démonstration. */
@RestController
@RequestMapping("/api/v1/conformite")
@PreAuthorize("hasAnyAuthority('facturation:creer', 'facturation:valider')")
class ConformiteControleur {

    record ModeSimulateur(SimulateurFec.Mode mode) {
    }

    private final ServiceCertification service;
    private final ObjectProvider<SimulateurFec> simulateur;

    ConformiteControleur(ServiceCertification service, ObjectProvider<SimulateurFec> simulateur) {
        this.service = service;
        this.simulateur = simulateur;
    }

    @GetMapping("/file")
    List<ServiceCertification.Element> file() {
        return service.file();
    }

    @PostMapping("/file/{documentId}/relance")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void relancer(@PathVariable UUID documentId) {
        service.relancer(documentId);
        service.tenter(documentId);
    }

    @GetMapping("/adaptateur")
    Map<String, Object> adaptateur() {
        var s = simulateur.getIfAvailable();
        return s == null ? Map.of("adaptateur", "dgi") : Map.of("adaptateur", "simulateur", "mode", s.mode());
    }

    /** Démonstration : rendre le simulateur indisponible (certification différée) ou le faire rejeter. */
    @PutMapping("/simulateur")
    @PreAuthorize("hasAuthority('facturation:valider')")
    Map<String, Object> changerMode(@RequestBody ModeSimulateur demande) {
        var s = simulateur.getIfAvailable();
        if (s == null) {
            throw new RegleMetierException("SIMULATEUR_ABSENT", "Le simulateur de certification n'est pas actif sur cette installation.");
        }
        s.changerMode(demande.mode());
        return adaptateur();
    }
}
