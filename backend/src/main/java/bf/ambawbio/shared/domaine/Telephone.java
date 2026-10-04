package bf.ambawbio.shared.domaine;

/** Numéro burkinabè : 8 chiffres, indicatif +226 ajouté automatiquement ; forme affichée « +226 70 00 00 00 » (guide §9.2). */
public final class Telephone {

    private Telephone() {
    }

    public static String normaliser(String saisie) {
        if (saisie == null || saisie.isBlank()) {
            return null;
        }
        var chiffres = saisie.replaceAll("\\D", "");
        if (chiffres.length() == 11 && chiffres.startsWith("226")) {
            chiffres = chiffres.substring(3);
        } else if (chiffres.length() == 13 && chiffres.startsWith("00226")) {
            chiffres = chiffres.substring(5);
        }
        if (chiffres.length() != 8) {
            throw new RegleMetierException("TELEPHONE_INVALIDE", "Le numéro doit comporter 8 chiffres (ex. 70 00 00 00).");
        }
        return "+226 " + chiffres.substring(0, 2) + " " + chiffres.substring(2, 4) + " " + chiffres.substring(4, 6) + " " + chiffres.substring(6);
    }
}
