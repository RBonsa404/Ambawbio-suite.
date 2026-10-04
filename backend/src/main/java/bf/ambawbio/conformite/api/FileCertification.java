package bf.ambawbio.conformite.api;

/** Entrée de la file de certification (F-FAC-04). */
public interface FileCertification {

    /**
     * Met la pièce en file dans la transaction de validation (atomique avec elle) ; une première tentative est faite
     * dès la validation enregistrée, puis la file est reprise avec un délai croissant.
     */
    void soumettre(PieceACertifier piece);
}
