package bf.ambawbio.conformite.api;

import java.util.Optional;

/** Contrôle de l'IFU d'un client auprès de l'administration (F-FAC-03) ; vide si le service est injoignable. */
public interface ControleIfu {

    Optional<Boolean> valide(String ifu);
}
