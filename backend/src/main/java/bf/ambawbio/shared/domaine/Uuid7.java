package bf.ambawbio.shared.domaine;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Générateur d'UUID version 7 (RFC 9562) : horodatage en millisecondes + aléa, triables dans le temps (R-05).
 * Le client (terminal hors-ligne) utilise l'équivalent TypeScript.
 */
public final class Uuid7 {

    private static final SecureRandom ALEA = new SecureRandom();

    private Uuid7() {
    }

    public static UUID nouveau() {
        long millis = System.currentTimeMillis();
        long hauts = (millis << 16) | 0x7000L | (ALEA.nextInt() & 0x0FFFL);
        long bas = (ALEA.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(hauts, bas);
    }

    public static boolean estUuid7(UUID uuid) {
        return uuid != null && uuid.version() == 7 && uuid.variant() == 2;
    }
}
