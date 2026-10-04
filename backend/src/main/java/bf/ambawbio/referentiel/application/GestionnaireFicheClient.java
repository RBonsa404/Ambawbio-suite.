package bf.ambawbio.referentiel.application;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import bf.ambawbio.referentiel.domaine.Tiers;
import bf.ambawbio.shared.domaine.RegleMetierException;
import bf.ambawbio.sync.api.GestionnaireOperation;
import bf.ambawbio.sync.api.OperationTerminal;

/**
 * Opération {@code FICHE_CLIENT_MODIFIEE} (guide §8.2, §8.5) : règle « dernière modification » (l'ordre de réception
 * au serveur fait foi) ; le terminal ne peut modifier que les coordonnées. Charge :
 * {@code {"tiersId", "telephone", "courriel", "adresse", "ville"}}.
 */
@Component
class GestionnaireFicheClient implements GestionnaireOperation {

    private static final Set<String> CHAMPS_AUTORISES = Set.of("tiersId", "telephone", "courriel", "adresse", "ville");

    private final TiersDepot tiers;
    private final PublicationReferentiel publication;

    GestionnaireFicheClient(TiersDepot tiers, PublicationReferentiel publication) {
        this.tiers = tiers;
        this.publication = publication;
    }

    @Override
    public String type() {
        return "FICHE_CLIENT_MODIFIEE";
    }

    @Override
    public void appliquer(OperationTerminal operation) {
        var charge = operation.charge();
        var interdits = charge.propertyNames().stream().filter(c -> !CHAMPS_AUTORISES.contains(c)).toList();
        if (!interdits.isEmpty()) {
            throw new RegleMetierException("RG-06", "Un terminal ne peut modifier que les coordonnées du client (champs refusés : "
                    + String.join(", ", interdits) + ").");
        }
        var id = charge.path("tiersId").asString(null);
        var client = tiers.findById(id == null ? null : UUID.fromString(id))
                .orElseThrow(() -> new RegleMetierException("CLIENT_INCONNU", "Client introuvable : la fiche a peut-être été supprimée."));
        client.modifierCoordonnees(new Tiers.Coordonnees(texte(charge, "telephone", client.telephone()), texte(charge, "courriel", client.courriel()),
                texte(charge, "adresse", client.adresse()), texte(charge, "ville", client.ville())));
        tiers.flush();
        publication.tiers(client);
    }

    private static String texte(tools.jackson.databind.JsonNode charge, String champ, String actuel) {
        return charge.has(champ) ? (charge.get(champ).isNull() ? null : charge.get(champ).asString()) : actuel;
    }
}
