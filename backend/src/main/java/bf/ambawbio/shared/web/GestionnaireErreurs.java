package bf.ambawbio.shared.web;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import bf.ambawbio.shared.domaine.RegleMetierException;
import bf.ambawbio.shared.domaine.RessourceIntrouvableException;

/** Convertit les erreurs en ProblemDetail (RFC 9457), message en français et code stable (guide §6.2). */
@RestControllerAdvice
public class GestionnaireErreurs {

    private static final Logger JOURNAL = LoggerFactory.getLogger(GestionnaireErreurs.class);

    static ProblemDetail probleme(HttpStatus statut, String code, String message) {
        var probleme = ProblemDetail.forStatusAndDetail(statut, message);
        probleme.setType(URI.create("https://ambawbio.bf/erreurs/" + code.toLowerCase().replace('_', '-')));
        probleme.setProperty("code", code);
        return probleme;
    }

    @ExceptionHandler(RegleMetierException.class)
    ProblemDetail regleMetier(RegleMetierException e) {
        return probleme(HttpStatus.UNPROCESSABLE_CONTENT, e.code(), e.getMessage());
    }

    @ExceptionHandler(RessourceIntrouvableException.class)
    ProblemDetail introuvable(RessourceIntrouvableException e) {
        return probleme(HttpStatus.NOT_FOUND, "INTROUVABLE", e.getMessage());
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    ProblemDetail accesRefuse(AuthorizationDeniedException e) {
        return probleme(HttpStatus.FORBIDDEN, "ACCES_REFUSE",
                "Vous n'avez pas le droit d'effectuer cette action. Demandez-le à l'administrateur de votre entreprise.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail saisieInvalide(MethodArgumentNotValidException e) {
        var probleme = probleme(HttpStatus.BAD_REQUEST, "SAISIE_INVALIDE", "Certaines informations sont manquantes ou incorrectes.");
        Map<String, String> champs = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(f -> f.getField(), f -> String.valueOf(f.getDefaultMessage()), (a, b) -> a));
        probleme.setProperty("champs", champs);
        return probleme;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail illisible(HttpMessageNotReadableException e) {
        return probleme(HttpStatus.BAD_REQUEST, "REQUETE_ILLISIBLE", "La requête est mal formée ou incomplète.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail conflit(OptimisticLockingFailureException e) {
        return probleme(HttpStatus.CONFLICT, "MODIFICATION_CONCURRENTE", "Cette fiche a été modifiée entre-temps. Rechargez-la puis recommencez.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrite(DataIntegrityViolationException e) {
        JOURNAL.warn("Violation d'intégrité : {}", e.getMostSpecificCause().getClass().getSimpleName());
        return probleme(HttpStatus.CONFLICT, "DOUBLON", "Un enregistrement identique existe déjà.");
    }
}
