package bf.ambawbio.shared.domaine;

/** Violation d'une règle de gestion : code stable pour le client (RG-xx, INV-xx…) et message en français. */
public class RegleMetierException extends RuntimeException {

    private final String code;

    public RegleMetierException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
