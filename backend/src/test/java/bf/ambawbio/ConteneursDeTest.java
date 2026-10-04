package bf.ambawbio;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** PostgreSQL réel pour les tests d'intégration (pas de base H2, guide §4). */
@TestConfiguration(proxyBeanMethods = false)
public class ConteneursDeTest {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18-alpine");
    }
}
