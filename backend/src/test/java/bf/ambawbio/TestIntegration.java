package bf.ambawbio;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;

import bf.ambawbio.shared.domaine.Uuid7;
import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.socle.identite.ServiceIdentite;
import bf.ambawbio.socle.plateforme.ServicePlateforme;
import bf.ambawbio.socle.tenancy.Pack;

/**
 * Base des tests d'intégration : PostgreSQL 18 réel (pas de H2), migrations exécutées par le propriétaire,
 * application connectée avec le rôle {@code ambawbio_app} sans BYPASSRLS — comme en production (guide §6.4).
 */
@SpringBootTest(properties = {"ambawbio.annuaire.mode=simule", "ambawbio.planification.active=false"})
@AutoConfigureMockMvc
public abstract class TestIntegration {

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withInitScript("init-role-application.sql");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registre) {
        registre.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registre.add("spring.datasource.username", () -> "ambawbio_app");
        registre.add("spring.datasource.password", () -> "ambawbio-app-test");
        registre.add("spring.flyway.user", POSTGRES::getUsername);
        registre.add("spring.flyway.password", POSTGRES::getPassword);
    }

    public record EntrepriseTest(UUID id, String adminKeycloakId) {
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ServicePlateforme plateforme;
    @Autowired
    protected ServiceIdentite identite;

    protected EntrepriseTest creerEntreprise(String nom, Pack pack) {
        return creerEntreprise(nom, pack, null);
    }

    /** {@code ifu} : IFU de la société principale, obligatoire pour valider une facture (RG-02). */
    protected EntrepriseTest creerEntreprise(String nom, Pack pack, String ifu) {
        var id = Uuid7.nouveau();
        var suffixe = id.toString().substring(30);
        plateforme.creer(new ServicePlateforme.NouvelleEntreprise(id, nom, pack, ifu, "Ouagadougou",
                "admin-" + suffixe, "Admin", nom, "admin-" + suffixe + "@test.bf"));
        var admin = ContexteTenant.executerPour(id, () -> identite.utilisateurs().getFirst());
        return new EntrepriseTest(id, admin.keycloakId());
    }

    /** Jeton d'un utilisateur d'entreprise : sujet Keycloak + revendication tenant_id. */
    protected static RequestPostProcessor comme(UUID tenant, String keycloakId) {
        return jwt().jwt(j -> j.subject(keycloakId).claim("tenant_id", tenant.toString()));
    }

    protected static RequestPostProcessor commeEditeur() {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_admin-plateforme"));
    }

    /** Connexion du propriétaire (superutilisateur du conteneur) pour simuler une altération directe en base. */
    protected static Connection connexionProprietaire() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
