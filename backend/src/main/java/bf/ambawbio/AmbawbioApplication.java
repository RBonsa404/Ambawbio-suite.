package bf.ambawbio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Point d'entrée du serveur Ambawbio Suite : monolithe modulaire (Spring Modulith).
 * Chaque package de premier niveau sous {@code bf.ambawbio} est un module (guide §5, R-02).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AmbawbioApplication {

    public static void main(String[] args) {
        SpringApplication.run(AmbawbioApplication.class, args);
    }
}
