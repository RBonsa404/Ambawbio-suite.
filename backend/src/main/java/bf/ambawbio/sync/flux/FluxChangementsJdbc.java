package bf.ambawbio.sync.flux;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.sync.api.FluxChangements;
import tools.jackson.databind.json.JsonMapper;

@Component
class FluxChangementsJdbc implements FluxChangements {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    FluxChangementsJdbc(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publier(String entite, UUID entiteId, Object donnees, UUID etablissementId) {
        jdbc.update("insert into sync.flux_changements (tenant_id, etablissement_id, entite, entite_id, operation, donnees) "
                + "values (?, ?, ?, ?, 'UPSERT', ?::jsonb)", ContexteTenant.tenantObligatoire(), etablissementId, entite, entiteId,
                json.writeValueAsString(donnees));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void supprimer(String entite, UUID entiteId, UUID etablissementId) {
        jdbc.update("insert into sync.flux_changements (tenant_id, etablissement_id, entite, entite_id, operation) values (?, ?, ?, ?, 'SUPPRESSION')",
                ContexteTenant.tenantObligatoire(), etablissementId, entite, entiteId);
    }

    @Override
    public boolean contient(String entite) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from sync.flux_changements where entite = ?)", Boolean.class, entite));
    }
}
