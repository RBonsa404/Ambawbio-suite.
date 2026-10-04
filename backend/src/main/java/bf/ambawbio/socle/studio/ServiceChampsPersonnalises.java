package bf.ambawbio.socle.studio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bf.ambawbio.shared.domaine.RegleMetierException;
import bf.ambawbio.shared.domaine.RessourceIntrouvableException;
import bf.ambawbio.socle.api.ChampsPersonnalises;
import bf.ambawbio.socle.api.JournalAudit;

/** Studio : définitions et validation des champs personnalisés (F-STU-01, UC-SOC-10). */
@Service
/*
 * Les méthodes de validation ne sont pas transactionnelles : une erreur de saisie levée pendant un import ne doit pas
 * marquer la transaction de l'appelant comme « à annuler ».
 */
public class ServiceChampsPersonnalises implements ChampsPersonnalises {

    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DefinitionChampDepot definitions;
    private final JournalAudit audit;

    ServiceChampsPersonnalises(DefinitionChampDepot definitions, JournalAudit audit) {
        this.definitions = definitions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<DefinitionChamp> liste(String entite) {
        return definitions.findByEntiteOrderByOrdreAscLibelleAsc(entite);
    }

    @Transactional
    public DefinitionChamp creer(UUID id, String entite, String code, String libelle, DefinitionChamp.Type type, List<String> options,
            boolean obligatoire, boolean filtrable, int ordre) {
        var existante = definitions.findById(id);
        if (existante.isPresent()) {
            return existante.get();
        }
        if (definitions.existsByEntiteAndCode(entite, code)) {
            throw new RegleMetierException("CHAMP_EXISTANT", "Un champ « " + code + " » existe déjà pour cette fiche.");
        }
        var champ = definitions.save(new DefinitionChamp(id, entite, code, libelle, type, options, obligatoire, filtrable, ordre));
        audit.enregistrer("CHAMP_PERSONNALISE_CREE", "definition_champ", id, null, Map.of("entite", entite, "code", code, "type", type.name()));
        return champ;
    }

    @Transactional
    public DefinitionChamp modifier(UUID id, String libelle, List<String> options, boolean obligatoire, boolean filtrable, int ordre) {
        var champ = definitions.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Champ introuvable."));
        champ.modifier(libelle, options, obligatoire, filtrable, ordre);
        audit.enregistrer("CHAMP_PERSONNALISE_MODIFIE", "definition_champ", id, null, Map.of("code", champ.code()));
        return champ;
    }

    @Override
    public List<Definition> definitions(String entite) {
        return liste(entite).stream()
                .map(d -> new Definition(d.code(), d.libelle(), d.type().name(), d.options(), d.obligatoire(), d.filtrable(), d.ordre()))
                .toList();
    }

    @Override
    public Map<String, Object> valider(String entite, Map<String, Object> valeurs) {
        var parCode = parCode(entite);
        var resultat = new LinkedHashMap<String, Object>();
        var donnees = valeurs == null ? Map.<String, Object>of() : valeurs;
        for (var entree : donnees.entrySet()) {
            var definition = parCode.get(entree.getKey());
            if (definition == null) {
                throw invalide("Le champ « " + entree.getKey() + " » n'existe pas. Créez-le d'abord dans le Studio.");
            }
            var valeur = normaliser(definition, entree.getValue());
            if (valeur != null) {
                resultat.put(definition.code(), valeur);
            }
        }
        for (var definition : parCode.values()) {
            if (definition.obligatoire() && !resultat.containsKey(definition.code())) {
                throw invalide("Le champ « " + definition.libelle() + " » est obligatoire.");
            }
        }
        return resultat;
    }

    @Override
    public Object validerValeur(String entite, String code, Object valeur) {
        var definition = parCode(entite).get(code);
        if (definition == null) {
            throw invalide("Le champ « " + code + " » n'existe pas. Créez-le d'abord dans le Studio.");
        }
        return normaliser(definition, valeur);
    }

    @Override
    public Map<String, Object> filtre(String entite, Map<String, String> parametres) {
        var parCode = parCode(entite);
        var filtre = new LinkedHashMap<String, Object>();
        parametres.forEach((cle, valeur) -> {
            if (!cle.startsWith("champ.")) {
                return;
            }
            var code = cle.substring("champ.".length());
            var definition = parCode.get(code);
            if (definition == null || !definition.filtrable()) {
                throw invalide("Le champ « " + code + " » n'est pas filtrable.");
            }
            filtre.put(code, normaliser(definition, valeur));
        });
        return filtre;
    }

    private Map<String, DefinitionChamp> parCode(String entite) {
        return liste(entite).stream().collect(Collectors.toMap(DefinitionChamp::code, Function.identity()));
    }

    /** Valeurs typées et canoniques : le filtre d'inclusion JSON retrouve exactement les valeurs enregistrées. */
    private static Object normaliser(DefinitionChamp definition, Object brute) {
        if (brute == null || (brute instanceof String s && s.isBlank())) {
            return null;
        }
        var texte = String.valueOf(brute).trim();
        return switch (definition.type()) {
            case TEXTE -> texte;
            case NOMBRE -> {
                try {
                    yield new BigDecimal(texte.replace(" ", "").replace(" ", "").replace(',', '.')).stripTrailingZeros();
                } catch (NumberFormatException e) {
                    throw invalide("« " + definition.libelle() + " » doit être un nombre.");
                }
            }
            case DATE -> {
                try {
                    yield (texte.contains("/") ? LocalDate.parse(texte, DATE_FR) : LocalDate.parse(texte)).toString();
                } catch (DateTimeParseException e) {
                    throw invalide("« " + definition.libelle() + " » doit être une date (14/03/2027).");
                }
            }
            case BOOLEEN -> switch (texte.toLowerCase(Locale.ROOT)) {
                case "true", "oui", "1", "vrai" -> Boolean.TRUE;
                case "false", "non", "0", "faux" -> Boolean.FALSE;
                default -> throw invalide("« " + definition.libelle() + " » doit valoir oui ou non.");
            };
            case LISTE -> {
                if (!definition.options().contains(texte)) {
                    throw invalide("« " + definition.libelle() + " » doit valoir : " + String.join(", ", definition.options()) + ".");
                }
                yield texte;
            }
        };
    }

    private static RegleMetierException invalide(String message) {
        return new RegleMetierException("CHAMP_INVALIDE", message);
    }
}
