package bf.ambawbio.socle.api;

import java.util.UUID;

/** Journal d'audit chaîné (RG-11) : à appeler pour toute opération sensible, dans la transaction de l'opération. */
public interface JournalAudit {

    /**
     * @param action  code stable, ex. {@code FACTURE_VALIDEE}
     * @param entite  type d'entité, ex. {@code facture}
     * @param avant   état avant (sérialisé en JSON) ou {@code null} ; sans donnée personnelle superflue
     * @param apres   état après ou {@code null}
     */
    void enregistrer(String action, String entite, UUID entiteId, Object avant, Object apres);
}
