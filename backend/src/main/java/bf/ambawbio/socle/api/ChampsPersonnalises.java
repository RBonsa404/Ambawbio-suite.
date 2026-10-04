package bf.ambawbio.socle.api;

import java.util.List;
import java.util.Map;

/** Champs personnalisés du Studio (F-STU-01) : définitions, validation des valeurs, filtres. */
public interface ChampsPersonnalises {

    record Definition(String code, String libelle, String type, List<String> options, boolean obligatoire, boolean filtrable, int ordre) {
    }

    List<Definition> definitions(String entite);

    /** Valide et normalise les valeurs (types, liste fermée, obligatoires) ; erreur {@code CHAMP_INVALIDE} sinon. */
    Map<String, Object> valider(String entite, Map<String, Object> valeurs);

    /** Valide et normalise une seule valeur (import colonne par colonne) ; erreur {@code CHAMP_INVALIDE} si inconnue ou invalide. */
    Object validerValeur(String entite, String code, Object valeur);

    /** Filtre d'inclusion JSON à partir de paramètres {@code champ.<code>=valeur} ; seuls les champs filtrables sont acceptés. */
    Map<String, Object> filtre(String entite, Map<String, String> parametres);
}
