package bf.ambawbio.socle.api;

import java.util.UUID;

/** RG-14 : l'utilisateur n'agit que sur les établissements qui lui sont affectés. */
public interface AccesEtablissement {

    boolean estAutorise(UUID etablissementId);

    /** Lève une erreur 403 explicite si l'établissement n'est pas autorisé. */
    void verifier(UUID etablissementId);
}
