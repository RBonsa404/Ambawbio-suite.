package bf.ambawbio.socle.identite;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import bf.ambawbio.socle.tenancy.StatutEntreprise;

/**
 * Serveur de ressources OAuth2 : jetons JWT émis par Keycloak (royaume {@code ambawbio}).
 * Les rôles du royaume deviennent {@code ROLE_<role>} ; les permissions métier sont ajoutées par {@link FiltreContexte}.
 */
@Configuration
@EnableMethodSecurity
class ConfigurationSecurite {

    @Bean
    SecurityFilterChain chaineSecurite(HttpSecurity http, ServiceIdentite identite, StatutEntreprise statut) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> { })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/plateforme/**").hasRole("admin-plateforme")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(convertisseurJeton())))
                .addFilterBefore(new FiltreContexte(identite, statut), AuthorizationFilter.class)
                .build();
    }

    static Converter<Jwt, AbstractAuthenticationToken> convertisseurJeton() {
        return jwt -> new JwtAuthenticationToken(jwt, rolesDuRoyaume(jwt), jwt.getClaimAsString("preferred_username"));
    }

    static Collection<GrantedAuthority> rolesDuRoyaume(Jwt jwt) {
        Map<String, Object> acces = jwt.getClaimAsMap("realm_access");
        if (acces == null || !(acces.get("roles") instanceof List<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
