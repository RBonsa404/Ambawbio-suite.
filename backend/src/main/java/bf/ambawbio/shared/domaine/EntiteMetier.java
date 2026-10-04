package bf.ambawbio.shared.domaine;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import bf.ambawbio.shared.tenant.ContexteTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

/**
 * Base des entités métier (guide §6.2, §7.2) : identifiant fourni à la création (UUID v7),
 * entreprise ({@code tenant_id}, INV-12), horodatages, auteur, verrouillage optimiste, champs personnalisés.
 */
@MappedSuperclass
public abstract class EntiteMetier implements Persistable<UUID> {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe;

    @Column(name = "modifie_le", nullable = false)
    private Instant modifieLe;

    @Column(name = "cree_par", updatable = false)
    private UUID creePar;

    @Version
    private long version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "champs_perso", nullable = false)
    private Map<String, Object> champsPerso = new HashMap<>();

    @Transient
    private boolean nouveau = true;

    protected EntiteMetier() {
    }

    protected EntiteMetier(UUID id) {
        if (!Uuid7.estUuid7(id)) {
            throw new RegleMetierException("IDENTIFIANT_INVALIDE", "L'identifiant doit être un UUID version 7.");
        }
        this.id = id;
    }

    @PrePersist
    void avantCreation() {
        creeLe = Instant.now();
        modifieLe = creeLe;
        creePar = ContexteTenant.utilisateurCourant().orElse(null);
    }

    @PreUpdate
    void avantModification() {
        modifieLe = Instant.now();
    }

    @PostLoad
    @PostPersist
    void marquerExistante() {
        nouveau = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return nouveau;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public Instant creeLe() {
        return creeLe;
    }

    public Instant modifieLe() {
        return modifieLe;
    }

    public long version() {
        return version;
    }

    public Map<String, Object> champsPerso() {
        return champsPerso;
    }

    @Override
    public boolean equals(Object autre) {
        return this == autre || (autre instanceof EntiteMetier e && getClass() == e.getClass() && id != null && id.equals(e.id));
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
