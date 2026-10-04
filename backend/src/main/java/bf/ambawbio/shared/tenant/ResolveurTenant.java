package bf.ambawbio.shared.tenant;

import java.util.Map;
import java.util.UUID;

import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Deuxième barrière (guide §6.4) : Hibernate filtre et renseigne {@code tenant_id} d'après le contexte.
 * En mode plateforme, l'identifiant « racine » désactive le filtre Hibernate ; PostgreSQL (RLS) reste la barrière.
 */
@Component
class ResolveurTenant implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

    static final UUID RACINE = new UUID(0L, 0L);
    /** Aucun contexte : identifiant qui ne correspond à aucune entreprise. */
    static final UUID AUCUN = new UUID(-1L, -1L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        if (ContexteTenant.modePlateforme()) {
            return RACINE;
        }
        return ContexteTenant.tenantCourant().orElse(AUCUN);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public boolean isRoot(UUID tenantId) {
        return RACINE.equals(tenantId);
    }

    @Override
    public void customize(Map<String, Object> proprietes) {
        proprietes.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
