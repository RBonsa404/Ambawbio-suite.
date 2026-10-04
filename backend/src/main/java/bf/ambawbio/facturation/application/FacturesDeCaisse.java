package bf.ambawbio.facturation.application;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import bf.ambawbio.facturation.domaine.DocumentFiscal;
import bf.ambawbio.pos.api.RetourEnregistre;
import bf.ambawbio.pos.api.VenteEnregistree;

/**
 * Factures et avoirs issus de la caisse (F-POS-05, RG-03) : la facture demandée en caisse est établie à la réception
 * de la vente, avec le numéro pris hors-ligne par le terminal, puis mise en file de certification ; un retour sur une
 * vente facturée produit l'avoir correspondant.
 */
@Component
class FacturesDeCaisse {

    private static final Logger JOURNAL = LoggerFactory.getLogger(FacturesDeCaisse.class);

    private final ServiceFacturation service;

    FacturesDeCaisse(ServiceFacturation service) {
        this.service = service;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void vente(VenteEnregistree v) {
        if (!v.factureDemandee() || v.numeroFacture() == null || v.clientId() == null) {
            return;
        }
        var lignes = v.lignes().stream().map(l -> new DocumentFiscal.Ligne.Donnees(l.id(), l.produitId(), l.libelle(), null, l.quantite(),
                l.prixUnitaire(), l.prixTtc(), l.remise(), l.taxeCode(), l.taux(), null)).toList();
        JOURNAL.info("Facture {} établie pour la vente {}", v.numeroFacture(), v.numero());
        service.facturerVente(v.venteId(), v.etablissementId(), v.clientId(), v.numeroFacture(), v.horodatage().atZone(ServiceFacturation.FUSEAU).toLocalDate(),
                lignes);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retour(RetourEnregistre r) {
        Map<UUID, BigDecimal> quantites = new HashMap<>();
        r.lignes().forEach(l -> quantites.merge(l.ligneOrigineId(), l.quantite(), BigDecimal::add));
        service.avoirSurRetour(r.venteOrigineId(), r.retourId(), r.numero(), r.horodatage().atZone(ServiceFacturation.FUSEAU).toLocalDate(), quantites);
    }
}
