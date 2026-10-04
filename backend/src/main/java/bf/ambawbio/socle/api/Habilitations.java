package bf.ambawbio.socle.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Droits d'un utilisateur désigné (pas seulement celui de la requête) : nécessaire pour les opérations des terminaux,
 * envoyées plus tard par un autre utilisateur, et pour la validation par code PIN d'un responsable (RG-09).
 */
public interface Habilitations {

    /** Vrai si l'utilisateur est actif, a la permission et est autorisé sur l'établissement (RG-14). */
    boolean possede(UUID utilisateurId, UUID etablissementId, String permission);

    Optional<String> nomComplet(UUID utilisateurId);

    Optional<UUID> parNomUtilisateur(String nomUtilisateur);

    /** Utilisateur de la requête en cours. */
    UUID utilisateurCourant();

    /**
     * Vérifie le code PIN de responsable. Un échec est compté même si la transaction appelante est annulée ;
     * 5 échecs bloquent le code 15 minutes.
     */
    boolean verifierCodePin(UUID utilisateurId, String pin);
}
