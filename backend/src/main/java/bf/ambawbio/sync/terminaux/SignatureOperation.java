package bf.ambawbio.sync.terminaux;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

import bf.ambawbio.shared.domaine.RegleMetierException;

/**
 * Signature des opérations (guide §8.2) : ECDSA P-256 / SHA-256, format IEEE P1363 (r‖s), produite par WebCrypto.
 * Message signé : {@code idOperation|type|horodatageLocal|sha256hex(charge)}, la charge étant le texte JSON exact transmis.
 */
public final class SignatureOperation {

    private SignatureOperation() {
    }

    public static PublicKey lireClePublique(String spkiBase64) {
        try {
            var cle = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(spkiBase64)));
            if (!(cle instanceof java.security.interfaces.ECPublicKey ec) || ec.getParams().getCurve().getField().getFieldSize() != 256) {
                throw new RegleMetierException("CLE_INVALIDE", "La clé du terminal doit être une clé ECDSA P-256.");
            }
            return cle;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new RegleMetierException("CLE_INVALIDE", "Clé publique du terminal illisible.");
        }
    }

    public static String message(String idOperation, String type, String horodatageLocal, String charge) {
        return idOperation + "|" + type + "|" + horodatageLocal + "|" + sha256(charge);
    }

    public static boolean verifier(PublicKey cle, String message, String signatureBase64) {
        try {
            var verificateur = Signature.getInstance("SHA256withECDSAinP1363Format");
            verificateur.initVerify(cle);
            verificateur.update(message.getBytes(StandardCharsets.UTF_8));
            return verificateur.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    public static String sha256(String texte) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texte.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
