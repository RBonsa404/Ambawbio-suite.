package bf.ambawbio.sync.terminaux;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bf.ambawbio.shared.domaine.RegleMetierException;
import bf.ambawbio.shared.domaine.RessourceIntrouvableException;
import bf.ambawbio.socle.api.AccesEtablissement;
import bf.ambawbio.socle.api.JournalAudit;
import bf.ambawbio.socle.api.Organisation;
import bf.ambawbio.sync.plages.PlageNumerotation;
import bf.ambawbio.sync.plages.ServicePlages;

/** Enregistrement, appairage et révocation des terminaux (UC-SOC-04, SD-11). */
@Service
public class ServiceTerminaux {

    public record Appairage(Terminal terminal, String codeAppairage) {
    }

    public record TerminalAppaire(Terminal terminal, List<PlageNumerotation> plages) {
    }

    static final Duration VALIDITE_CODE = Duration.ofMinutes(15);
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom ALEA = new SecureRandom();

    private final TerminalDepot terminaux;
    private final ServicePlages plages;
    private final Organisation organisation;
    private final AccesEtablissement acces;
    private final JournalAudit audit;

    ServiceTerminaux(TerminalDepot terminaux, ServicePlages plages, Organisation organisation, AccesEtablissement acces, JournalAudit audit) {
        this.terminaux = terminaux;
        this.plages = plages;
        this.organisation = organisation;
        this.acces = acces;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Terminal> liste() {
        return terminaux.findAllByOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public Terminal terminal(UUID id) {
        return terminaux.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Terminal introuvable."));
    }

    /** Crée le terminal et son premier code d'appairage (QR code affiché sur l'écran W-18). Idempotent par identifiant. */
    @Transactional
    public Appairage creer(UUID id, UUID etablissementId, String nom) {
        if (terminaux.existsById(id)) {
            return nouveauCode(id);
        }
        acces.verifier(etablissementId);
        if (organisation.societeDe(etablissementId).isEmpty()) {
            throw new RessourceIntrouvableException("Établissement introuvable.");
        }
        var code = "C%02d".formatted(terminaux.nombre() + 1);
        var terminal = terminaux.save(new Terminal(id, etablissementId, code, nom));
        audit.enregistrer("TERMINAL_CREE", "terminal", id, null, Map.of("code", code, "nom", nom));
        return preparer(terminal);
    }

    @Transactional
    public Appairage nouveauCode(UUID id) {
        return preparer(terminal(id));
    }

    private Appairage preparer(Terminal terminal) {
        var code = codeAleatoire();
        terminal.preparerAppairage(SignatureOperation.sha256(code), Instant.now().plus(VALIDITE_CODE));
        return new Appairage(terminal, code);
    }

    /** Le terminal présente le code lu dans le QR code et sa clé publique ; il reçoit ses plages de numérotation. */
    @Transactional
    public TerminalAppaire appairer(UUID id, String codeAppairage, String clePublique) {
        var terminal = terminal(id);
        SignatureOperation.lireClePublique(clePublique);
        terminal.appairer(SignatureOperation.sha256(codeAppairage.trim().toUpperCase().replace("-", "")), clePublique, Instant.now());
        audit.enregistrer("TERMINAL_APPAIRE", "terminal", id, null, Map.of("code", terminal.code()));
        return new TerminalAppaire(terminal, plages.garantir(id, societe(terminal)));
    }

    /** Révocation : le terminal ne peut plus synchroniser ; ses plages sont clôturées. Irréversible. */
    @Transactional
    public Terminal revoquer(UUID id) {
        var terminal = terminal(id);
        if (terminal.statut() == Terminal.Statut.REVOQUE) {
            return terminal;
        }
        terminal.revoquer(Instant.now());
        plages.cloturer(id);
        audit.enregistrer("TERMINAL_REVOQUE", "terminal", id, null, Map.of("code", terminal.code()));
        return terminal;
    }

    /** Terminal actif de l'entreprise du jeton, sinon refus explicite (guide §8.3, étape 1). */
    @Transactional(readOnly = true)
    public Terminal actif(UUID id) {
        var terminal = terminaux.findById(id)
                .orElseThrow(() -> new RegleMetierException("TERMINAL_INCONNU", "Terminal inconnu pour votre entreprise. Appairez-le à nouveau."));
        if (terminal.statut() == Terminal.Statut.REVOQUE) {
            throw new TerminalRevoqueException();
        }
        if (terminal.statut() != Terminal.Statut.ACTIF) {
            throw new RegleMetierException("TERMINAL_NON_APPAIRE", "Ce terminal n'est pas encore appairé.");
        }
        return terminal;
    }

    @Transactional
    public void marquerSynchronise(UUID id) {
        terminaux.findById(id).ifPresent(t -> t.synchronise(Instant.now()));
    }

    public UUID societe(Terminal terminal) {
        return organisation.societeDe(terminal.etablissementId())
                .orElseThrow(() -> new RessourceIntrouvableException("Établissement du terminal introuvable."));
    }

    static String codeAleatoire() {
        var code = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            code.append(ALPHABET.charAt(ALEA.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** Terminal révoqué : refus (403) ; le terminal conserve ses opérations localement. */
    public static class TerminalRevoqueException extends RegleMetierException {
        public TerminalRevoqueException() {
            super("TERMINAL_REVOQUE", "Ce terminal a été révoqué. Vos opérations restent sur l'appareil ; contactez l'administrateur.");
        }
    }
}
