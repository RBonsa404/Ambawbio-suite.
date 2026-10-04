package bf.ambawbio.socle.audit;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.socle.api.JournalAudit;
import tools.jackson.databind.json.JsonMapper;

/**
 * Journal chaîné par entreprise (guide §6.6). L'insertion est sérialisée par un verrou consultatif
 * propre à l'entreprise ; l'empreinte est calculée par PostgreSQL ({@code audit.calculer_empreinte}).
 */
@Component
class JournalAuditJdbc implements JournalAudit {

    static final String EMPREINTE_INITIALE = "0".repeat(64);

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    JournalAuditJdbc(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enregistrer(String action, String entite, UUID entiteId, Object avant, Object apres) {
        var tenant = ContexteTenant.tenantObligatoire();
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtext(?))", Object.class, "audit:" + tenant);
        var precedentes = jdbc.queryForList(
                "select empreinte from audit.journal where tenant_id = ? order by id desc limit 1", String.class, tenant);
        var precedente = precedentes.isEmpty() ? EMPREINTE_INITIALE : precedentes.getFirst();
        // Microsecondes : précision de timestamptz, pour un recalcul identique à la vérification.
        var horodatage = Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS));
        jdbc.update("""
                insert into audit.journal (tenant_id, horodatage, utilisateur_id, action, entite, entite_id, avant, apres,
                                           empreinte_precedente, empreinte)
                values (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?,
                        audit.calculer_empreinte(?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb))
                """,
                tenant, horodatage, ContexteTenant.utilisateurCourant().orElse(null), action, entite, entiteId, enJson(avant), enJson(apres),
                precedente,
                precedente, horodatage, ContexteTenant.utilisateurCourant().orElse(null), action, entite, entiteId, enJson(avant), enJson(apres));
    }

    private String enJson(Object valeur) {
        return valeur == null ? null : json.writeValueAsString(valeur);
    }
}
