package bf.ambawbio.socle.audit;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import bf.ambawbio.shared.tenant.ContexteTenant;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/** Vérification de la chaîne d'audit : à la demande et chaque nuit pour toutes les entreprises (guide §6.6). */
@Service
public class VerificationAudit {

    public record Resultat(UUID tenantId, long entreesVerifiees, boolean integre, Long premiereRupture) {
    }

    private static final Logger JOURNAL = LoggerFactory.getLogger(VerificationAudit.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final MeterRegistry metriques;

    VerificationAudit(JdbcTemplate jdbc, TransactionTemplate transaction, MeterRegistry metriques) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.metriques = metriques;
    }

    /** Vérifie la chaîne de l'entreprise du contexte : liens entre entrées et empreintes recalculées. */
    public Resultat verifier() {
        var tenant = ContexteTenant.tenantObligatoire();
        return transaction.execute(statut -> {
            final long[] compte = {0};
            final String[] precedente = {JournalAuditJdbc.EMPREINTE_INITIALE};
            final Long[] rupture = {null};
            jdbc.query("""
                    select id, empreinte_precedente, empreinte,
                           audit.calculer_empreinte(empreinte_precedente, horodatage, utilisateur_id, action, entite, entite_id, avant, apres) as recalculee
                    from audit.journal where tenant_id = ? order by id
                    """, ligne -> {
                compte[0]++;
                var id = ligne.getLong("id");
                var lienCorrect = precedente[0].equals(ligne.getString("empreinte_precedente"));
                var empreinteCorrecte = ligne.getString("empreinte").equals(ligne.getString("recalculee"));
                if (rupture[0] == null && (!lienCorrect || !empreinteCorrecte)) {
                    rupture[0] = id;
                }
                precedente[0] = ligne.getString("empreinte");
            }, tenant);
            return new Resultat(tenant, compte[0], rupture[0] == null, rupture[0]);
        });
    }

    /** Chaque nuit à 2 h 30 (heure de Ouagadougou = UTC) : toutes les entreprises, alerte en cas de rupture. */
    @Scheduled(cron = "${ambawbio.audit.verification-cron:0 30 2 * * *}", zone = "UTC")
    @SchedulerLock(name = "verification-audit", lockAtMostFor = "PT1H")
    public void verifierToutesLesEntreprises() {
        var entreprises = ContexteTenant.executerEnModePlateforme(() -> transaction.execute(s ->
                jdbc.queryForList("select id from socle.entreprise", UUID.class)));
        for (var tenant : entreprises) {
            var resultat = ContexteTenant.executerPour(tenant, this::verifier);
            if (!resultat.integre()) {
                metriques.counter("ambawbio.audit.ruptures").increment();
                JOURNAL.error("Rupture de la chaîne d'audit : entreprise {}, entrée {}", tenant, resultat.premiereRupture());
            }
        }
        JOURNAL.info("Chaîne d'audit vérifiée pour {} entreprise(s)", entreprises.size());
    }
}
