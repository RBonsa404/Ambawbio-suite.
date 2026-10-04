package bf.ambawbio.shared.tenant;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.stereotype.Component;

/**
 * Troisième barrière (guide §6.4, §7.3) : à chaque emprunt d'une connexion, positionne
 * {@code app.tenant_id} et {@code app.plateforme} lus par les politiques RLS de PostgreSQL.
 * Chaque emprunt réécrit les deux valeurs : une connexion rendue au pool ne garde pas l'entreprise précédente.
 */
@Component
class SourceDonneesTenant implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String nom) {
        if (bean instanceof DataSource source && !(bean instanceof DataSourceAvecTenant) && "dataSource".equals(nom)) {
            return new DataSourceAvecTenant(source);
        }
        return bean;
    }

    static final class DataSourceAvecTenant extends DelegatingDataSource {

        DataSourceAvecTenant(DataSource cible) {
            super(cible);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return positionner(super.getConnection());
        }

        @Override
        public Connection getConnection(String utilisateur, String motDePasse) throws SQLException {
            return positionner(super.getConnection(utilisateur, motDePasse));
        }

        private static Connection positionner(Connection connexion) throws SQLException {
            try (var requete = connexion.prepareStatement("select set_config('app.tenant_id', ?, false), set_config('app.plateforme', ?, false)")) {
                requete.setString(1, ContexteTenant.tenantCourant().map(Object::toString).orElse(""));
                requete.setString(2, ContexteTenant.modePlateforme() ? "oui" : "non");
                requete.execute();
            } catch (SQLException e) {
                connexion.close();
                throw e;
            }
            return connexion;
        }
    }
}
