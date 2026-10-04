package bf.ambawbio.sync.api;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/** Opération reçue d'un terminal, signature déjà vérifiée (guide §8.2). */
public record OperationTerminal(UUID idOperation, UUID terminalId, UUID etablissementId, String type, int versionSchema,
        Instant horodatageLocal, UUID utilisateurId, JsonNode charge) {
}
