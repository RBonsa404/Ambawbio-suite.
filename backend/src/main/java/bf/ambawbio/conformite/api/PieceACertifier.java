package bf.ambawbio.conformite.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Facture ou avoir validé, tel qu'il est transmis pour certification (figé à la validation). */
public record PieceACertifier(UUID documentId, String type, String numero, LocalDate dateEmission, Instant valideLe, String emetteurIfu,
        String emetteurRaisonSociale, String clientIfu, String clientNom, String factureOrigineNumero, long totalHt, long totalTaxes, long totalTtc,
        List<Taxe> taxes) {

    public record Taxe(String code, BigDecimal taux, long baseHt, long montant) {
    }
}
