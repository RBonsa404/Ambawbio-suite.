package bf.ambawbio.socle.api.evenements;

import java.util.UUID;

/** Publié à la création d'une entreprise : chaque module y charge ses données de démarrage (guide §7.5). */
public record EntrepriseCreee(int version, UUID tenantId, String pack) {

    public EntrepriseCreee(UUID tenantId, String pack) {
        this(1, tenantId, pack);
    }
}
