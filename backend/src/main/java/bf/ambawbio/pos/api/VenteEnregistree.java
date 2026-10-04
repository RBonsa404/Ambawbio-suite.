package bf.ambawbio.pos.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Vente de caisse enregistrée (guide §6.8). Consommée par le stock (sortie, LOT 9), la comptabilité (LOT 10) et la
 * facturation quand le client a demandé une facture certifiée (LOT 6).
 */
public record VenteEnregistree(int version, UUID venteId, UUID societeId, UUID etablissementId, UUID sessionId, String numero,
        long totalTtc, long totalTaxes, UUID clientId, boolean factureDemandee, List<Ligne> lignes) {

    public record Ligne(UUID produitId, BigDecimal quantiteUniteStock, long montantHt, long montantTaxe, String taxeCode) {
    }
}
