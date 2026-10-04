package bf.ambawbio.shared.domaine;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calcul d'une ligne de pièce — caisse, facture, avoir (guide §11.3) : taxe calculée par ligne, arrondie au franc (demi supérieur).
 * Prix TTC (D-17) : {@code ht = ttc × 100 / (100 + taux)} ; prix HT : {@code taxe = ht × taux / 100}.
 * Le même calcul est fait par le terminal ({@code calcul-vente.ts}) ; le serveur le refait et refuse tout écart.
 */
public final class CalculLigne {

    public record Montants(Montant ht, Montant taxe, Montant ttc) {
    }

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    private CalculLigne() {
    }

    public static Montants ligne(BigDecimal quantite, long prixUnitaire, boolean prixTtc, long remise, BigDecimal tauxPourcent) {
        if (quantite.signum() <= 0) {
            throw new RegleMetierException("QUANTITE_INVALIDE", "La quantité doit être positive.");
        }
        var brut = Montant.fcfa(prixUnitaire).multiplier(quantite);
        if (remise < 0 || remise > brut.valeur()) {
            throw new RegleMetierException("REMISE_INVALIDE", "La remise ne peut pas dépasser le montant de la ligne.");
        }
        var net = brut.soustraire(Montant.fcfa(remise));
        if (prixTtc) {
            var ht = Montant.fcfa(BigDecimal.valueOf(net.valeur()).multiply(CENT).divide(CENT.add(tauxPourcent), 0, RoundingMode.HALF_UP).longValueExact());
            return new Montants(ht, net.soustraire(ht), net);
        }
        var taxe = net.multiplier(tauxPourcent.divide(CENT));
        return new Montants(net, taxe, net.ajouter(taxe));
    }

    public static Montant brut(BigDecimal quantite, long prixUnitaire) {
        return Montant.fcfa(prixUnitaire).multiplier(quantite);
    }
}
