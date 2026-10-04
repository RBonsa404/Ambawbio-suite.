package bf.ambawbio.shared.tenant;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Contexte de la requête ou du traitement en cours : entreprise (tenant), utilisateur, mode plateforme.
 * Rempli après validation du jeton (jamais depuis un paramètre de requête, guide §6.4) ou explicitement
 * par un traitement système qui itère sur les entreprises.
 */
public final class ContexteTenant {

    private record Etat(UUID tenantId, UUID utilisateurId, boolean plateforme) {
    }

    private static final ThreadLocal<Etat> COURANT = new ThreadLocal<>();

    private ContexteTenant() {
    }

    public static void definir(UUID tenantId, UUID utilisateurId) {
        COURANT.set(new Etat(tenantId, utilisateurId, false));
    }

    public static void definirPlateforme(UUID utilisateurId) {
        COURANT.set(new Etat(null, utilisateurId, true));
    }

    public static void effacer() {
        COURANT.remove();
    }

    public static Optional<UUID> tenantCourant() {
        return Optional.ofNullable(COURANT.get()).map(Etat::tenantId);
    }

    public static UUID tenantObligatoire() {
        return tenantCourant().orElseThrow(() -> new IllegalStateException("Aucune entreprise dans le contexte"));
    }

    public static Optional<UUID> utilisateurCourant() {
        return Optional.ofNullable(COURANT.get()).map(Etat::utilisateurId);
    }

    public static boolean modePlateforme() {
        var etat = COURANT.get();
        return etat != null && etat.plateforme();
    }

    /** Exécute un traitement pour une entreprise donnée, puis restaure le contexte précédent. */
    public static <T> T executerPour(UUID tenantId, Supplier<T> traitement) {
        var precedent = COURANT.get();
        COURANT.set(new Etat(tenantId, precedent == null ? null : precedent.utilisateurId(), false));
        try {
            return traitement.get();
        } finally {
            if (precedent == null) {
                COURANT.remove();
            } else {
                COURANT.set(precedent);
            }
        }
    }

    /** Exécute un traitement en mode plateforme (lecture de toutes les entreprises), puis restaure le contexte. */
    public static <T> T executerEnModePlateforme(Supplier<T> traitement) {
        var precedent = COURANT.get();
        COURANT.set(new Etat(null, precedent == null ? null : precedent.utilisateurId(), true));
        try {
            return traitement.get();
        } finally {
            if (precedent == null) {
                COURANT.remove();
            } else {
                COURANT.set(precedent);
            }
        }
    }
}
