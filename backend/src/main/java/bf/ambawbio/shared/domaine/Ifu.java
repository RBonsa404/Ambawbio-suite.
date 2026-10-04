package bf.ambawbio.shared.domaine;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Identifiant fiscal unique (IFU). Format guidé retenu : 8 chiffres suivis d'une lettre (ex. 00012345A).
 * Aucune clé de contrôle n'est vérifiée tant que la règle officielle n'est pas fournie (docs/QUESTIONS.md, Q-15).
 */
public record Ifu(String valeur) {

    private static final Pattern FORMAT = Pattern.compile("\\d{8}[A-Z]");

    public Ifu {
        if (valeur == null || !FORMAT.matcher(valeur).matches()) {
            throw new RegleMetierException("IFU_INVALIDE", "L'IFU doit comporter 8 chiffres suivis d'une lettre (ex. 00012345A).");
        }
    }

    /** Accepte les saisies avec espaces ou minuscules (« 00012345 a »). */
    public static Ifu depuisSaisie(String saisie) {
        return new Ifu(saisie == null ? null : saisie.replaceAll("\\s", "").toUpperCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return valeur;
    }
}
