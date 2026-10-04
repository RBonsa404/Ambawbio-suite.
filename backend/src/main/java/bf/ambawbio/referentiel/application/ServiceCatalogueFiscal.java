package bf.ambawbio.referentiel.application;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bf.ambawbio.referentiel.api.CatalogueFiscal;

@Service
class ServiceCatalogueFiscal implements CatalogueFiscal {

    private final TiersDepot tiers;
    private final ProduitDepot produits;
    private final RegimeDepot regimes;
    private final TaxeDepot taxes;
    private final UniteDepot unites;

    ServiceCatalogueFiscal(TiersDepot tiers, ProduitDepot produits, RegimeDepot regimes, TaxeDepot taxes, UniteDepot unites) {
        this.tiers = tiers;
        this.produits = produits;
        this.regimes = regimes;
        this.taxes = taxes;
        this.unites = unites;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClientFiscal> client(UUID tiersId) {
        return tiers.findById(tiersId).filter(t -> t.estClient()).map(t -> {
            var regime = t.regimeFiscalId() == null ? null : regimes.findById(t.regimeFiscalId()).orElse(null);
            return new ClientFiscal(t.getId(), t.code(), t.nom(), t.adresse(), t.ville(), t.ifu(), t.rccm(), regime == null ? null : regime.libelle(),
                    regime != null && regime.assujettiTva(), t.delaiPaiementJours());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Article> article(UUID produitId) {
        return produits.findById(produitId).map(p -> {
            var taxe = p.taxeId() == null ? null : taxes.findById(p.taxeId()).orElse(null);
            var unite = p.uniteId() == null ? null : unites.findById(p.uniteId()).orElse(null);
            return new Article(p.getId(), p.code(), p.nom(), unite == null ? null : unite.code(), p.prixVente(), p.prixVenteTtc(),
                    taxe == null ? null : taxe.code(), taxe == null ? BigDecimal.ZERO : taxe.taux());
        });
    }
}
