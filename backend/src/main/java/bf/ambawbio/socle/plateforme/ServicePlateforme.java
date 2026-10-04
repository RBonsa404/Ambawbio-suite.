package bf.ambawbio.socle.plateforme;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import bf.ambawbio.shared.domaine.RessourceIntrouvableException;
import bf.ambawbio.shared.domaine.Uuid7;
import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.socle.api.JournalAudit;
import bf.ambawbio.socle.identite.ServiceIdentite;
import bf.ambawbio.socle.parametrage.ServiceBaremes;
import bf.ambawbio.socle.tenancy.Entreprise;
import bf.ambawbio.socle.tenancy.Pack;
import bf.ambawbio.socle.tenancy.ServiceEntreprise;
import bf.ambawbio.socle.tenancy.ServicePlateformeEntreprises;

/** Administration de la plateforme par l'éditeur (UC-SOC-13, F-SOC-28). */
@Service
public class ServicePlateforme {

    public record NouvelleEntreprise(UUID id, String nom, Pack pack, String ifu, String villeSiege,
            String adminNomUtilisateur, String adminPrenom, String adminNom, String adminCourriel) {
    }

    private final ServicePlateformeEntreprises entreprises;
    private final ServiceEntreprise parametrage;
    private final ServiceIdentite identite;
    private final ServiceBaremes baremes;
    private final JournalAudit audit;
    private final TransactionTemplate transaction;

    ServicePlateforme(ServicePlateformeEntreprises entreprises, ServiceEntreprise parametrage, ServiceIdentite identite,
            ServiceBaremes baremes, JournalAudit audit, TransactionTemplate transaction) {
        this.entreprises = entreprises;
        this.parametrage = parametrage;
        this.identite = identite;
        this.baremes = baremes;
        this.audit = audit;
        this.transaction = transaction;
    }

    public List<Entreprise> entreprises() {
        return ContexteTenant.executerEnModePlateforme(() -> transaction.execute(s -> entreprises.toutes()));
    }

    /**
     * Crée une entreprise prête à l'emploi : société principale, établissement « Siège » et son dépôt,
     * rôles et barèmes par défaut, modules du pack, administrateur (compte Keycloak + courriel). Idempotent.
     */
    public Entreprise creer(NouvelleEntreprise n) {
        var existante = ContexteTenant.executerEnModePlateforme(() -> transaction.execute(s -> entreprises.trouver(n.id())));
        if (existante.isPresent()) {
            return existante.get();
        }
        return ContexteTenant.executerPour(n.id(), () -> transaction.execute(s -> {
            var entreprise = entreprises.enregistrer(new Entreprise(n.id(), n.nom(), n.pack()));
            if (n.ifu() != null && !n.ifu().isBlank()) {
                entreprise.modifier(n.nom(), n.ifu(), null, null, n.villeSiege(), null, n.adminCourriel());
            }
            var societe = parametrage.creerSociete(Uuid7.nouveau(), n.nom(), n.ifu(), null, null, null);
            var siege = parametrage.creerEtablissement(Uuid7.nouveau(), societe.getId(), "SIEGE", "Siège", null, n.villeSiege());
            parametrage.creerDepot(Uuid7.nouveau(), siege.getId(), "DEP-SIEGE", "Dépôt du siège");
            parametrage.definirModulesActifs(java.util.Set.copyOf(n.pack().modulesDisponibles()));
            var roles = identite.creerRolesParDefaut();
            baremes.creerBaremesParDefaut(LocalDate.now());
            var admin = identite.creerUtilisateur(Uuid7.nouveau(), n.id(), n.adminNomUtilisateur(), n.adminPrenom(), n.adminNom(),
                    n.adminCourriel(), null);
            identite.affecter(Uuid7.nouveau(), admin.getId(), roles.get("administrateur").getId(), null);
            audit.enregistrer("ENTREPRISE_CREEE", "entreprise", n.id(), null, Map.of("nom", n.nom(), "pack", n.pack().name()));
            return entreprise;
        }));
    }

    public Entreprise suspendre(UUID id) {
        return changerStatut(id, true);
    }

    public Entreprise reactiver(UUID id) {
        return changerStatut(id, false);
    }

    private Entreprise changerStatut(UUID id, boolean suspendre) {
        ContexteTenant.executerEnModePlateforme(() -> transaction.execute(s -> entreprises.trouver(id)))
                .orElseThrow(() -> new RessourceIntrouvableException("Entreprise introuvable."));
        return ContexteTenant.executerPour(id, () -> transaction.execute(s -> {
            var entreprise = entreprises.trouver(id).orElseThrow();
            if (suspendre) {
                entreprise.suspendre();
            } else {
                entreprise.reactiver();
            }
            audit.enregistrer(suspendre ? "ENTREPRISE_SUSPENDUE" : "ENTREPRISE_REACTIVEE", "entreprise", id, null, null);
            return entreprise;
        }));
    }
}
