package bf.ambawbio.socle.audit;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

/** Traitements planifiés avec ShedLock : une seule exécution même avec plusieurs instances (guide §4). */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
@ConditionalOnProperty(name = "ambawbio.planification.active", havingValue = "true", matchIfMissing = true)
class ConfigurationPlanification {

    @Bean
    LockProvider fournisseurVerrous(DataSource source) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(source))
                .withTableName("socle.shedlock")
                .usingDbTime()
                .build());
    }
}
