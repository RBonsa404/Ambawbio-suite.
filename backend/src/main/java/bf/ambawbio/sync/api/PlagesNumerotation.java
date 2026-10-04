package bf.ambawbio.sync.api;

import java.util.UUID;

/** Contrôle des numéros attribués hors-ligne (guide §8.6, INV-02, INV-11). */
public interface PlagesNumerotation {

    /** Vrai si le numéro appartient à une plage attribuée à ce terminal (type de pièce : TICKET, FACTURE, AVOIR). */
    boolean couvre(UUID terminalId, String typePiece, int annee, long numero);

    /** Préfixe affiché du terminal pour ce type de pièce (ex. {@code TK-C01}). */
    String prefixe(UUID terminalId, String typePiece);
}
