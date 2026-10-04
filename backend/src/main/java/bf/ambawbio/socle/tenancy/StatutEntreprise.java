package bf.ambawbio.socle.tenancy;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Lecture rapide du statut de l'entreprise du contexte (accès refusé si l'abonnement est suspendu). */
@Component
public class StatutEntreprise {

    private final JdbcTemplate jdbc;

    StatutEntreprise(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean estSuspendue(UUID tenantId) {
        var statuts = jdbc.queryForList("select statut from socle.entreprise where id = ?", String.class, tenantId);
        return statuts.isEmpty() || "SUSPENDUE".equals(statuts.getFirst());
    }
}
