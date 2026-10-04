package bf.ambawbio.pos.api;

import java.util.UUID;

/** Session de caisse clôturée (guide §6.8) : la comptabilité passe l'écart de caisse (LOT 10). */
public record SessionCloturee(int version, UUID sessionId, UUID etablissementId, long especesTheoriques, long especesComptees, long ecart,
        boolean aValider) {
}
