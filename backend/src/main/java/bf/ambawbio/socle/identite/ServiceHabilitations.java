package bf.ambawbio.socle.identite;

import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import bf.ambawbio.shared.domaine.RegleMetierException;
import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.socle.api.Habilitations;
import bf.ambawbio.socle.api.JournalAudit;

/** Habilitations d'un utilisateur désigné et code PIN de responsable (PBKDF2-SHA-256 salé, D-31). */
@Service
class ServiceHabilitations implements Habilitations {

    static final int ITERATIONS = 210_000;
    private static final int ECHECS_MAX = 5;
    private static final SecureRandom HASARD = new SecureRandom();

    private final ServiceIdentite identite;
    private final UtilisateurDepot utilisateurs;
    private final JdbcTemplate jdbc;
    private final JournalAudit audit;

    ServiceHabilitations(ServiceIdentite identite, UtilisateurDepot utilisateurs, JdbcTemplate jdbc, JournalAudit audit) {
        this.identite = identite;
        this.utilisateurs = utilisateurs;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean possede(UUID utilisateurId, UUID etablissementId, String permission) {
        if (utilisateurId == null) {
            return false;
        }
        return identite.profilParId(utilisateurId)
                .filter(p -> p.actif() && p.permissions().contains(permission) && p.autorise(etablissementId))
                .isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> nomComplet(UUID utilisateurId) {
        return utilisateurId == null ? Optional.empty() : utilisateurs.findById(utilisateurId).map(Utilisateur::nomComplet);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> parNomUtilisateur(String nomUtilisateur) {
        return nomUtilisateur == null ? Optional.empty() : utilisateurs.findByNomUtilisateur(nomUtilisateur.trim().toLowerCase()).map(Utilisateur::getId);
    }

    @Override
    public UUID utilisateurCourant() {
        var attributs = RequestContextHolder.getRequestAttributes();
        var profil = attributs == null ? null
                : (ProfilAcces) attributs.getAttribute(AccesEtablissementService.ATTRIBUT_PROFIL, RequestAttributes.SCOPE_REQUEST);
        if (profil == null) {
            throw new AuthorizationDeniedException("Utilisateur non rattaché à l'entreprise");
        }
        return profil.utilisateurId();
    }

    /** Définit (ou remplace) le code PIN de responsable de l'utilisateur connecté : 6 chiffres. */
    @Transactional
    public void definirCodePin(String pin) {
        if (pin == null || !pin.matches("\\d{6}")) {
            throw new RegleMetierException("PIN_INVALIDE", "Le code PIN de responsable comporte 6 chiffres.");
        }
        var utilisateur = utilisateurCourant();
        var sel = new byte[16];
        HASARD.nextBytes(sel);
        jdbc.update("""
                insert into socle.code_pin (utilisateur_id, tenant_id, empreinte, sel, iterations) values (?, ?, ?, ?, ?)
                on conflict (utilisateur_id) do update set empreinte = excluded.empreinte, sel = excluded.sel,
                  iterations = excluded.iterations, echecs = 0, bloque_jusqu_a = null, modifie_le = now()""",
                utilisateur, ContexteTenant.tenantObligatoire(), empreinte(pin, sel, ITERATIONS), Base64.getEncoder().encodeToString(sel), ITERATIONS);
        audit.enregistrer("CODE_PIN_DEFINI", "utilisateur", utilisateur, null, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean verifierCodePin(UUID utilisateurId, String pin) {
        var lignes = jdbc.query("select empreinte, sel, iterations, echecs, bloque_jusqu_a from socle.code_pin where utilisateur_id = ? for update",
                (rs, i) -> new Object[] {rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getTimestamp(5)}, utilisateurId);
        if (lignes.isEmpty()) {
            throw new RegleMetierException("PIN_ABSENT", "Ce responsable n'a pas encore défini son code PIN.");
        }
        var l = lignes.getFirst();
        if (l[4] != null && ((java.sql.Timestamp) l[4]).toInstant().isAfter(Instant.now())) {
            throw new RegleMetierException("PIN_BLOQUE", "Code PIN bloqué après 5 essais manqués : réessayez dans 15 minutes.");
        }
        boolean correct = pin != null && java.security.MessageDigest.isEqual(
                empreinte(pin, Base64.getDecoder().decode((String) l[1]), (int) l[2]).getBytes(), ((String) l[0]).getBytes());
        if (correct) {
            jdbc.update("update socle.code_pin set echecs = 0, bloque_jusqu_a = null where utilisateur_id = ?", utilisateurId);
        } else {
            int echecs = (int) l[3] + 1;
            jdbc.update("update socle.code_pin set echecs = ?, bloque_jusqu_a = ? where utilisateur_id = ?", echecs >= ECHECS_MAX ? 0 : echecs,
                    echecs >= ECHECS_MAX ? java.sql.Timestamp.from(Instant.now().plus(15, ChronoUnit.MINUTES)) : null, utilisateurId);
        }
        return correct;
    }

    static String empreinte(String pin, byte[] sel, int iterations) {
        try {
            KeySpec spec = new PBEKeySpec(pin.toCharArray(), sel, iterations, 256);
            return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
