package bf.ambawbio.socle.tenancy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.socle.api.Organisation;

@Component
class OrganisationService implements Organisation {

    private final EtablissementDepot etablissements;
    private final EntrepriseDepot entreprises;
    private final SocieteDepot societes;
    private final TransactionTemplate transaction;

    OrganisationService(EtablissementDepot etablissements, EntrepriseDepot entreprises, SocieteDepot societes, TransactionTemplate transaction) {
        this.etablissements = etablissements;
        this.societes = societes;
        this.entreprises = entreprises;
        this.transaction = transaction;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Emetteur> emetteur(UUID societeId) {
        return societes.findById(societeId).map(s -> {
            var e = entreprises.findById(s.tenantId()).orElseThrow();
            return new Emetteur(s.getId(), s.nom(), s.ifu() != null ? s.ifu() : e.ifu(), s.rccm() != null ? s.rccm() : e.rccm(), s.regimeFiscal(),
                    s.adresse() != null ? s.adresse() : e.adresse(), e.ville(), e.telephone(), e.courriel());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> societePrincipale() {
        return societes.findAll().stream().filter(Societe::principale).map(Societe::getId).findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> societeDe(UUID etablissementId) {
        return etablissements.findById(etablissementId).map(Etablissement::societeId);
    }

    @Override
    public List<UUID> entreprises() {
        return ContexteTenant.executerEnModePlateforme(() -> transaction.execute(s ->
                entreprises.findAll().stream().map(Entreprise::getId).toList()));
    }
}
