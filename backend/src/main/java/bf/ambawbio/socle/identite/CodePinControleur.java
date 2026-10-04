package bf.ambawbio.socle.identite;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Code PIN de responsable de l'utilisateur connecté (validation d'écart de caisse, RG-09). */
@RestController
@RequestMapping("/api/v1/socle/moi/code-pin")
class CodePinControleur {

    record Demande(String pin) {
    }

    private final ServiceHabilitations service;

    CodePinControleur(ServiceHabilitations service) {
        this.service = service;
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void definir(@RequestBody Demande demande) {
        service.definirCodePin(demande.pin());
    }
}
