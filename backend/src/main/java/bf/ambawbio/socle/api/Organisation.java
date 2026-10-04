package bf.ambawbio.socle.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lecture de l'organisation de l'entreprise courante pour les autres modules. */
public interface Organisation {

    /** Société d'un établissement de l'entreprise du contexte. */
    Optional<UUID> societeDe(UUID etablissementId);

    /** Identifiants de toutes les entreprises (traitements système, mode plateforme). */
    List<UUID> entreprises();
}
