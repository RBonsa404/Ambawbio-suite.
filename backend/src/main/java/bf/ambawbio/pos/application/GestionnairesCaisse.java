package bf.ambawbio.pos.application;

import org.springframework.stereotype.Component;

import bf.ambawbio.pos.domaine.Vente;
import bf.ambawbio.sync.api.GestionnaireOperation;
import bf.ambawbio.sync.api.OperationTerminal;

/** Opérations de caisse synchronisées (guide §8.2) ; règles dans {@link ServiceCaisse}. */
final class GestionnairesCaisse {

    private GestionnairesCaisse() {
    }

    @Component
    static class OuvertureSession implements GestionnaireOperation {
        private final ServiceCaisse caisse;

        OuvertureSession(ServiceCaisse caisse) {
            this.caisse = caisse;
        }

        @Override
        public String type() {
            return "SESSION_OUVERTE";
        }

        @Override
        public void appliquer(OperationTerminal operation) {
            caisse.ouvrir(operation);
        }
    }

    @Component
    static class EnregistrementVente implements GestionnaireOperation {
        private final ServiceCaisse caisse;

        EnregistrementVente(ServiceCaisse caisse) {
            this.caisse = caisse;
        }

        @Override
        public String type() {
            return "VENTE_ENREGISTREE";
        }

        @Override
        public void appliquer(OperationTerminal operation) {
            caisse.enregistrer(operation, Vente.Type.VENTE);
        }
    }

    @Component
    static class EnregistrementRetour implements GestionnaireOperation {
        private final ServiceCaisse caisse;

        EnregistrementRetour(ServiceCaisse caisse) {
            this.caisse = caisse;
        }

        @Override
        public String type() {
            return "RETOUR_ENREGISTRE";
        }

        @Override
        public void appliquer(OperationTerminal operation) {
            caisse.enregistrer(operation, Vente.Type.RETOUR);
        }
    }

    @Component
    static class ClotureSession implements GestionnaireOperation {
        private final ServiceCaisse caisse;

        ClotureSession(ServiceCaisse caisse) {
            this.caisse = caisse;
        }

        @Override
        public String type() {
            return "SESSION_CLOTUREE";
        }

        @Override
        public void appliquer(OperationTerminal operation) {
            caisse.cloturer(operation);
        }
    }
}
