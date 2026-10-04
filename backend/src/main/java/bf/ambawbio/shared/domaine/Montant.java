package bf.ambawbio.shared.domaine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Montant en unités entières de la devise (R-06 : jamais de double ni de float). */
public record Montant(long valeur, Devise devise) implements Comparable<Montant> {

    public static final Montant ZERO = fcfa(0);

    public Montant {
        Objects.requireNonNull(devise, "La devise est obligatoire");
    }

    public static Montant fcfa(long valeur) {
        return new Montant(valeur, Devise.XOF);
    }

    public Montant ajouter(Montant autre) {
        verifierDevise(autre);
        return new Montant(Math.addExact(valeur, autre.valeur), devise);
    }

    public Montant soustraire(Montant autre) {
        verifierDevise(autre);
        return new Montant(Math.subtractExact(valeur, autre.valeur), devise);
    }

    /** Multiplication arrondie au franc, demi supérieur (guide §6.3). */
    public Montant multiplier(BigDecimal facteur) {
        return new Montant(BigDecimal.valueOf(valeur).multiply(facteur).setScale(0, RoundingMode.HALF_UP).longValueExact(), devise);
    }

    public boolean estPositif() {
        return valeur > 0;
    }

    public boolean estNul() {
        return valeur == 0;
    }

    @Override
    public int compareTo(Montant autre) {
        verifierDevise(autre);
        return Long.compare(valeur, autre.valeur);
    }

    private void verifierDevise(Montant autre) {
        if (autre.devise != devise) {
            throw new IllegalArgumentException("Devises différentes : " + devise + " et " + autre.devise);
        }
    }
}
