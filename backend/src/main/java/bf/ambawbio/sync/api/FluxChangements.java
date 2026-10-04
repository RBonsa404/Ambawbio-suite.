package bf.ambawbio.sync.api;

import java.util.UUID;

/**
 * Publication d'un changement à destination des terminaux (guide §8.4). À appeler dans la même transaction que
 * la modification de l'entité synchronisable.
 */
public interface FluxChangements {

    /** @param etablissementId établissement concerné, ou {@code null} pour toute l'entreprise */
    void publier(String entite, UUID entiteId, Object donnees, UUID etablissementId);

    void supprimer(String entite, UUID entiteId, UUID etablissementId);

    /** L'entreprise du contexte a-t-elle déjà publié cette entité (sert à l'initialisation des données existantes) ? */
    boolean contient(String entite);
}
