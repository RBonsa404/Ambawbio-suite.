package bf.ambawbio.pos.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Vente de caisse enregistrée (guide §6.8). Consommée par le stock (sortie, LOT 9), la comptabilité (LOT 10) et la
 * facturation quand le client a demandé une facture certifiée (LOT 6).
 */
public record VenteEnregistree(int version, UUID venteId, UUID societeId, UUID etablissementId, UUID sessionId, String numero,
        java.time.Instant horodatage, long totalTtc, long totalTaxes, UUID clientId, boolean factureDemandee, String numeroFacture, List<Ligne> lignes) {

    /** {@code numeroFacture} : numéro pris hors-ligne dans la plage FACTURE du terminal quand le client demande une facture (RG-03). */
    public record Ligne(UUID id, UUID produitId, String libelle, BigDecimal quantite, BigDecimal quantiteUniteStock, long prixUnitaire, boolean prixTtc,
            long remise, String taxeCode, BigDecimal taux, long montantHt, long montantTaxe, long montantTtc, UUID ligneOrigineId) {
    }
}
