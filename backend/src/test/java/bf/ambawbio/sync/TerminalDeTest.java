package bf.ambawbio.sync;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import bf.ambawbio.shared.domaine.Uuid7;
import bf.ambawbio.sync.terminaux.SignatureOperation;

/** Terminal simulé : paire de clés ECDSA P-256 et signature des opérations comme le fait WebCrypto (format P1363). */
public final class TerminalDeTest {

    public final UUID id;
    public final KeyPair cles;

    public TerminalDeTest(UUID id) {
        this.id = id;
        try {
            var generateur = KeyPairGenerator.getInstance("EC");
            generateur.initialize(new ECGenParameterSpec("secp256r1"));
            this.cles = generateur.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String clePublique() {
        return Base64.getEncoder().encodeToString(cles.getPublic().getEncoded());
    }

    /** Opération JSON prête à envoyer ; la charge est transmise comme texte et c'est ce texte qui est signé. */
    public String operation(UUID idOperation, String type, String charge) {
        var horodatage = Instant.now().toString();
        try {
            var signature = Signature.getInstance("SHA256withECDSAinP1363Format");
            signature.initSign(cles.getPrivate());
            signature.update(SignatureOperation.message(idOperation.toString(), type, horodatage, charge).getBytes(StandardCharsets.UTF_8));
            var signee = Base64.getEncoder().encodeToString(signature.sign());
            return """
                    {"idOperation":"%s","type":"%s","versionSchema":1,"horodatageLocal":"%s","charge":%s,"signature":"%s"}"""
                    .formatted(idOperation, type, horodatage, enChaineJson(charge), signee);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String operation(String type, String charge) {
        return operation(Uuid7.nouveau(), type, charge);
    }

    public String envoi(java.util.List<String> operations) {
        return "{\"terminalId\":\"" + id + "\",\"operations\":[" + String.join(",", operations) + "]}";
    }

    static String enChaineJson(String texte) {
        return "\"" + texte.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
