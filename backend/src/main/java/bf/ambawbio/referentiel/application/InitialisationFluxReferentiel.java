package bf.ambawbio.referentiel.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.socle.api.Organisation;

/**
 * Au démarrage : publie dans le flux de changements les données du référentiel créées avant le moteur de
 * synchronisation (une seule fois par entreprise), pour que le chargement initial des terminaux soit complet.
 */
@Component
@Order(30)
class InitialisationFluxReferentiel implements ApplicationRunner {

    private static final Logger JOURNAL = LoggerFactory.getLogger(InitialisationFluxReferentiel.class);

    private final Organisation organisation;
    private final PublicationReferentiel publication;
    private final TransactionTemplate transaction;

    InitialisationFluxReferentiel(Organisation organisation, PublicationReferentiel publication, TransactionTemplate transaction) {
        this.organisation = organisation;
        this.publication = publication;
        this.transaction = transaction;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        for (var tenant : organisation.entreprises()) {
            ContexteTenant.executerPour(tenant, () -> transaction.execute(s -> {
                if (!publication.dejaPublie()) {
                    publication.toutPublier();
                    JOURNAL.info("Référentiel publié pour la synchronisation : entreprise {}", tenant);
                }
                return null;
            }));
        }
    }
}
