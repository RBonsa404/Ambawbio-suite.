package bf.ambawbio.sync.api;

/**
 * Applique un type d'opération venant des terminaux (un bean par type). Appelé dans la transaction de l'opération,
 * avec le contexte de l'entreprise positionné. Une {@link bf.ambawbio.shared.domaine.RegleMetierException} met
 * l'opération « en conflit » avec son message comme motif (guide §8.5) ; toute autre erreur annule l'opération,
 * que le terminal renverra plus tard.
 */
public interface GestionnaireOperation {

    String type();

    void appliquer(OperationTerminal operation);
}
