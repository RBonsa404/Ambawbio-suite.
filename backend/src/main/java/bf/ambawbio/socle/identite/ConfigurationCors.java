package bf.ambawbio.socle.identite;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Origines autorisées à appeler l'API depuis un navigateur ou l'application Android (https://localhost). */
@Configuration
class ConfigurationCors {

    @Bean
    // Nom imposé : Spring Security cherche le bean « corsConfigurationSource ».
    CorsConfigurationSource corsConfigurationSource(@Value("${ambawbio.cors.origines}") List<String> origines) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(origines);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Content-Encoding", "Idempotency-Key"));
        cors.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
