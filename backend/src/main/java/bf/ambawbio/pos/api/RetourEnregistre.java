package bf.ambawbio.pos.api;

import java.util.List;
import java.util.UUID;

/** Retour de marchandise en caisse (guide §6.8) : entrée en stock (LOT 9), avoir certifié (LOT 6). */
public record RetourEnregistre(int version, UUID retourId, UUID venteOrigineId, UUID societeId, UUID etablissementId, UUID sessionId,
        String numero, long totalTtc, List<VenteEnregistree.Ligne> lignes) {
}
