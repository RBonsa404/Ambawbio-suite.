package bf.ambawbio.facturation.api;

import java.time.LocalDate;
import java.util.UUID;

/** Avoir validé (guide §6.8) : extourne comptable (LOT 10), reste à payer (LOT 7). */
public record AvoirValide(int version, UUID documentId, UUID factureOrigineId, UUID societeId, String numero, LocalDate dateEmission, UUID clientId,
        long totalHt, long totalTaxes, long totalTtc) {
}
