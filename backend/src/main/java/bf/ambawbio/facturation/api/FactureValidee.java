package bf.ambawbio.facturation.api;

import java.time.LocalDate;
import java.util.UUID;

/** Facture validée (guide §6.8) : comptabilité (LOT 10), notification. */
public record FactureValidee(int version, UUID documentId, UUID societeId, String numero, LocalDate dateEmission, UUID clientId, long totalHt,
        long totalTaxes, long totalTtc, String origine, UUID origineId) {
}
