package bf.ambawbio.referentiel.api;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Données du référentiel recopiées sur les pièces fiscales au moment de leur création (elles ne changent plus ensuite). */
public interface CatalogueFiscal {

    Optional<ClientFiscal> client(UUID tiersId);

    Optional<Article> article(UUID produitId);

    /** Client à facturer ; {@code assujetti} : son régime impose un IFU sur la facture (RG-02, INV-03). */
    record ClientFiscal(UUID id, String code, String nom, String adresse, String ville, String ifu, String rccm, String regime, boolean assujetti,
            int delaiPaiementJours) {
    }

    /** Article vendable : prix de vente (TTC ou HT, D-17), taxe applicable. */
    record Article(UUID id, String code, String nom, String unite, long prixVente, boolean prixTtc, String taxeCode, BigDecimal taux) {
    }
}
