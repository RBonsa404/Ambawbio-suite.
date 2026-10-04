package bf.ambawbio.socle.donnees;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import bf.ambawbio.shared.domaine.RessourceIntrouvableException;
import bf.ambawbio.socle.api.JournalAudit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/socle/donnees-personnelles")
@PreAuthorize("hasAuthority('donnees-personnelles:traiter')")
@Tag(name = "Socle — données personnelles", description = "Registre des traitements et demandes des personnes (F-SOC-18, UC-SOC-11)")
class DonneesPersonnellesControleur {

    /** Délai de réponse retenu en attendant la confirmation du délai légal (docs/QUESTIONS.md, Q-17). */
    static final int DELAI_REPONSE_JOURS = 30;

    record Traitement(String finalite, String donnees, String baseLegale, String conservation) {
    }

    record DemandeVue(UUID id, DemandeDonneesPersonnelles.Type type, String personneConcernee, String contact, String description,
            DemandeDonneesPersonnelles.Statut statut, LocalDate echeance, String reponse, Instant traiteeLe) {
        static DemandeVue de(DemandeDonneesPersonnelles d) {
            return new DemandeVue(d.getId(), d.type(), d.personneConcernee(), d.contact(), d.description(), d.statut(), d.echeance(),
                    d.reponse(), d.traiteeLe());
        }
    }

    record NouvelleDemande(@NotNull(message = "L'identifiant est obligatoire.") UUID id,
            @NotNull(message = "Le type de demande est obligatoire.") DemandeDonneesPersonnelles.Type type,
            @NotBlank(message = "La personne concernée est obligatoire.") String personneConcernee,
            @NotBlank(message = "Un moyen de contact est obligatoire.") String contact, String description) {
    }

    record Cloture(boolean acceptee, @NotBlank(message = "La réponse est obligatoire.") String reponse) {
    }

    /** Registre des traitements tenu par l'éditeur pour le compte de l'entreprise (à compléter par chaque module). */
    static final List<Traitement> REGISTRE = List.of(
            new Traitement("Gestion des comptes utilisateurs et des droits", "Nom, prénom, courriel, téléphone, rôles",
                    "Exécution du contrat d'abonnement", "Durée du contrat, puis suppression"),
            new Traitement("Journal d'audit des opérations sensibles", "Identifiant de l'utilisateur, action, date",
                    "Obligation légale (traçabilité comptable et fiscale)", "Durée légale de conservation (Q-06)"),
            new Traitement("Gestion des clients et fournisseurs (à partir du LOT 2)", "Nom, téléphone, IFU, adresse",
                    "Exécution des contrats commerciaux", "Durée légale de conservation des pièces (Q-06)"));

    private final DemandeDepot demandes;
    private final JournalAudit audit;

    DonneesPersonnellesControleur(DemandeDepot demandes, JournalAudit audit) {
        this.demandes = demandes;
        this.audit = audit;
    }

    @GetMapping("/registre")
    List<Traitement> registre() {
        return REGISTRE;
    }

    @GetMapping("/demandes")
    @Transactional(readOnly = true)
    List<DemandeVue> demandes() {
        return demandes.findAllByOrderByEcheanceAsc().stream().map(DemandeVue::de).toList();
    }

    @PostMapping("/demandes")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @Operation(summary = "Enregistrer une demande", description = "Échéance de réponse calculée automatiquement.")
    DemandeVue enregistrer(@Valid @RequestBody NouvelleDemande n) {
        var existante = demandes.findById(n.id());
        if (existante.isPresent()) {
            return DemandeVue.de(existante.get());
        }
        var demande = demandes.save(new DemandeDonneesPersonnelles(n.id(), n.type(), n.personneConcernee(), n.contact(), n.description(),
                LocalDate.now().plusDays(DELAI_REPONSE_JOURS)));
        audit.enregistrer("DEMANDE_DONNEES_RECUE", "demande_donnees_personnelles", n.id(), null, Map.of("type", n.type().name()));
        return DemandeVue.de(demande);
    }

    @PostMapping("/demandes/{id}/prise-en-charge")
    @Transactional
    DemandeVue prendreEnCharge(@PathVariable UUID id) {
        var demande = trouver(id);
        demande.prendreEnCharge();
        return DemandeVue.de(demande);
    }

    @PostMapping("/demandes/{id}/cloture")
    @Transactional
    DemandeVue cloturer(@PathVariable UUID id, @Valid @RequestBody Cloture c) {
        var demande = trouver(id);
        demande.cloturer(c.acceptee(), c.reponse());
        audit.enregistrer(demande.type() == DemandeDonneesPersonnelles.Type.EFFACEMENT ? "DEMANDE_EFFACEMENT_TRAITEE" : "DEMANDE_DONNEES_TRAITEE",
                "demande_donnees_personnelles", id, null, Map.of("statut", demande.statut().name()));
        return DemandeVue.de(demande);
    }

    private DemandeDonneesPersonnelles trouver(UUID id) {
        return demandes.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Demande introuvable."));
    }
}
