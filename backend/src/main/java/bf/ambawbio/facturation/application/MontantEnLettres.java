package bf.ambawbio.facturation.application;

/**
 * Montant en toutes lettres (gabarit D-01) : « un million deux cent quarante-deux mille huit cents francs CFA ».
 * Règles classiques (traits d'union entre dizaines et unités seulement, pas les rectifications de 1990).
 */
final class MontantEnLettres {

    private static final String[] UNITES = {"zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf", "dix", "onze", "douze",
        "treize", "quatorze", "quinze", "seize", "dix-sept", "dix-huit", "dix-neuf"};
    private static final String[] DIZAINES = {"", "", "vingt", "trente", "quarante", "cinquante", "soixante", "soixante", "quatre-vingt", "quatre-vingt"};

    private MontantEnLettres() {
    }

    static String francsCfa(long montant) {
        if (montant < 0) {
            return "moins " + francsCfa(-montant);
        }
        var texte = enLettres(montant);
        return Character.toUpperCase(texte.charAt(0)) + texte.substring(1) + " francs CFA";
    }

    static String enLettres(long n) {
        if (n == 0) {
            return "zéro";
        }
        var morceaux = new StringBuilder();
        long milliards = n / 1_000_000_000L;
        long millions = (n / 1_000_000L) % 1000;
        long milliers = (n / 1000) % 1000;
        long reste = n % 1000;
        if (milliards > 0) {
            ajouter(morceaux, centaines((int) milliards, false) + (milliards > 1 ? " milliards" : " milliard"));
        }
        if (millions > 0) {
            ajouter(morceaux, centaines((int) millions, false) + (millions > 1 ? " millions" : " million"));
        }
        if (milliers > 0) {
            ajouter(morceaux, milliers == 1 ? "mille" : centaines((int) milliers, true) + " mille");
        }
        if (reste > 0) {
            ajouter(morceaux, centaines((int) reste, false));
        }
        return morceaux.toString();
    }

    private static void ajouter(StringBuilder s, String morceau) {
        if (!s.isEmpty()) {
            s.append(' ');
        }
        s.append(morceau);
    }

    /** 0 < n < 1000 ; {@code devantMille} : « deux cent mille » (pas de « s » à cent ni à quatre-vingt devant mille). */
    private static String centaines(int n, boolean devantMille) {
        int c = n / 100;
        int r = n % 100;
        var s = new StringBuilder();
        if (c > 0) {
            s.append(c == 1 ? "cent" : UNITES[c] + " cent");
            if (c > 1 && r == 0 && !devantMille) {
                s.append('s');
            }
        }
        if (r > 0) {
            if (!s.isEmpty()) {
                s.append(' ');
            }
            s.append(dizaines(r, devantMille));
        }
        return s.toString();
    }

    private static String dizaines(int n, boolean devantMille) {
        if (n < 20) {
            return UNITES[n];
        }
        int d = n / 10;
        int u = n % 10;
        if (d == 7 || d == 9) {
            int sous = 10 + u;
            return DIZAINES[d] + (d == 7 && u == 1 ? " et " : "-") + UNITES[sous];
        }
        if (u == 0) {
            return DIZAINES[d] + (d == 8 && !devantMille ? "s" : "");
        }
        if (u == 1 && d != 8) {
            return DIZAINES[d] + " et un";
        }
        return DIZAINES[d] + "-" + UNITES[u];
    }
}
