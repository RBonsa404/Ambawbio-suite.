package bf.ambawbio.referentiel.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.socle.api.evenements.EntrepriseCreee;

/**
 * Données de démarrage du référentiel à la création d'une entreprise (guide §7.5). Écouteur enregistré dans le registre
 * d'événements Spring Modulith (R-03) : rejoué en cas d'échec. Le contexte de l'entreprise est positionné avant la
 * transaction, pour que la connexion porte {@code app.tenant_id} (RLS).
 */
@Component
class InitialisationReferentiel {

    private final ServiceParametres parametres;
    private final TransactionTemplate nouvelleTransaction;

    InitialisationReferentiel(ServiceParametres parametres, PlatformTransactionManager transactions) {
        this.parametres = parametres;
        this.nouvelleTransaction = new TransactionTemplate(transactions);
        this.nouvelleTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener
    void entrepriseCreee(EntrepriseCreee evenement) {
        ContexteTenant.executerPour(evenement.tenantId(), () -> nouvelleTransaction.execute(statut -> {
            parametres.initialiser();
            return null;
        }));
    }
}
