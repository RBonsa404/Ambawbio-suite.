package bf.ambawbio.socle.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lecture de l'organisation de l'entreprise courante pour les autres modules. */
public interface Organisation {

    /** Société d'un établissement de l'entreprise du contexte. */
    Optional<UUID> societeDe(UUID etablissementId);

    /** Société émettrice des pièces fiscales (mentions de la facture, RG-02). */
    Optional<Emetteur> emetteur(UUID societeId);

    /** Société principale de l'entreprise du contexte. */
    Optional<UUID> societePrincipale();

    record Emetteur(UUID societeId, String raisonSociale, String ifu, String rccm, String regimeFiscal, String adresse, String ville,
            String telephone, String courriel) {
    }

    /** Identifiants de toutes les entreprises (traitements système, mode plateforme). */
    List<UUID> entreprises();
}
