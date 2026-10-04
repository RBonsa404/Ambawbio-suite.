package bf.ambawbio.socle.identite;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class ConfigurationSecuriteTest {

    private static Jwt jeton(Map<String, Object> revendications) {
        var b = Jwt.withTokenValue("jeton").header("alg", "RS256").subject("u1");
        revendications.forEach(b::claim);
        return b.build();
    }

    @Test
    void lesRolesDuRoyaumeDeviennentDesAutorites() {
        var autorites = ConfigurationSecurite.rolesDuRoyaume(
                jeton(Map.of("realm_access", Map.of("roles", List.of("caissier", "gerant")))));
        assertThat(autorites).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_caissier", "ROLE_gerant");
    }

    @Test
    void unJetonSansRoleNeDonneAucuneAutorite() {
        assertThat(ConfigurationSecurite.rolesDuRoyaume(jeton(Map.of("scope", "openid")))).isEmpty();
    }
}
