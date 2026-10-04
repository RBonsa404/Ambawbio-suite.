package bf.ambawbio.socle.plateforme;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.socle.api.JournalAudit;
import bf.ambawbio.socle.api.evenements.EntrepriseCreee;
import bf.ambawbio.socle.identite.Role;
import bf.ambawbio.socle.identite.ServiceIdentite;
import bf.ambawbio.socle.parametrage.ServiceBaremes;
import bf.ambawbio.socle.tenancy.Entreprise;
import bf.ambawbio.socle.tenancy.Pack;
import bf.ambawbio.socle.tenancy.ServiceEntreprise;
import bf.ambawbio.socle.tenancy.ServicePlateformeEntreprises;

/**
 * Données de démonstration (profil {@code dev} uniquement), cohérentes avec infra/keycloak/realm-ambawbio.json :
 * Quincaillerie Wend-Panga (pack Business) et Pharmacie du Progrès (démo), pour montrer l'isolation.
 * Toutes les données sont fictives.
 */
@Component
@Profile("dev")
@org.springframework.core.annotation.Order(10)
class DonneesDemonstration implements ApplicationRunner {

    static final UUID WEND_PANGA = UUID.fromString("01920000-0000-7000-8000-000000000001");
    static final UUID PHARMACIE = UUID.fromString("01920000-0000-7000-8000-000000000002");

    private record Compte(String keycloakId, String nomUtilisateur, String prenom, String nom, Set<String> roles, boolean siegeSeulement) {
    }

    private static final Logger JOURNAL = LoggerFactory.getLogger(DonneesDemonstration.class);

    private final ServicePlateformeEntreprises entreprises;
    private final ServiceEntreprise parametrage;
    private final ServiceIdentite identite;
    private final ServiceBaremes baremes;
    private final JournalAudit audit;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher evenements;

    DonneesDemonstration(ServicePlateformeEntreprises entreprises, ServiceEntreprise parametrage, ServiceIdentite identite,
            ServiceBaremes baremes, JournalAudit audit, TransactionTemplate transaction, ApplicationEventPublisher evenements) {
        this.entreprises = entreprises;
        this.parametrage = parametrage;
        this.identite = identite;
        this.baremes = baremes;
        this.audit = audit;
        this.transaction = transaction;
        this.evenements = evenements;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        creer(WEND_PANGA, "Quincaillerie Wend-Panga", Pack.BUSINESS, "00012345A", 0xA0, new Compte[] {
            new Compte("01920000-0000-7000-8000-0000000000a1", "awa", "Awa", "Kaboré", Set.of("caissier"), true),
            new Compte("01920000-0000-7000-8000-0000000000a2", "moussa", "Moussa", "Sawadogo", Set.of("gerant", "administrateur"), false),
            new Compte("01920000-0000-7000-8000-0000000000a3", "mariam", "Mariam", "Ilboudo", Set.of("comptable"), false),
            new Compte("01920000-0000-7000-8000-0000000000a4", "issouf", "Issouf", "Ouédraogo", Set.of("magasinier"), false),
            new Compte("01920000-0000-7000-8000-0000000000a5", "boukary", "Boukary", "Compaoré", Set.of("commercial"), false),
            new Compte("01920000-0000-7000-8000-0000000000a6", "aminata", "Aminata", "Zongo", Set.of("gerant"), false),
        });
        creer(PHARMACIE, "Pharmacie du Progrès (démo)", Pack.ESSENTIEL, "00067890B", 0xB0, new Compte[] {
            new Compte("01920000-0000-7000-8000-0000000000b1", "salimata", "Salimata", "Traoré", Set.of("administrateur"), false),
        });
    }

    private void creer(UUID tenant, String nom, Pack pack, String ifu, int graine, Compte[] comptes) {
        var existe = ContexteTenant.executerEnModePlateforme(() -> transaction.execute(s -> entreprises.trouver(tenant).isPresent()));
        if (Boolean.TRUE.equals(existe)) {
            return;
        }
        ContexteTenant.executerPour(tenant, () -> transaction.execute(s -> {
            var entreprise = entreprises.enregistrer(new Entreprise(tenant, nom, pack));
            entreprise.modifier(nom, ifu, null, "Avenue de la Nation", "Ouagadougou", "+226 70 00 00 00", null);
            var societe = parametrage.creerSociete(id(graine, 1), nom, ifu, null, "Réel simplifié (démo)", null);
            var siege = parametrage.creerEtablissement(id(graine, 2), societe.getId(), "SIEGE", "Ouaga — Zogona", null, "Ouagadougou");
            parametrage.creerDepot(id(graine, 3), siege.getId(), "DEP-SIEGE", "Dépôt Zogona");
            if (pack != Pack.ESSENTIEL) {
                var bobo = parametrage.creerEtablissement(id(graine, 4), societe.getId(), "BOBO", "Bobo-Dioulasso — Accart-Ville", null,
                        "Bobo-Dioulasso");
                parametrage.creerDepot(id(graine, 5), bobo.getId(), "DEP-BOBO", "Dépôt Bobo");
            }
            parametrage.definirModulesActifs(Set.copyOf(pack.modulesDisponibles()));
            Map<String, Role> roles = identite.creerRolesParDefaut();
            baremes.creerBaremesParDefaut(LocalDate.of(2026, 1, 1));
            int n = 10;
            for (var c : comptes) {
                var utilisateur = identite.rattacherCompteExistant(id(graine, n++), c.keycloakId(), c.nomUtilisateur(), c.prenom(), c.nom(),
                        c.nomUtilisateur() + "@demo.ambawbio.bf");
                for (var code : c.roles()) {
                    identite.affecterSansSynchronisation(id(graine, n++), utilisateur.getId(), roles.get(code).getId(),
                            c.siegeSeulement() ? siege.getId() : null);
                }
            }
            audit.enregistrer("ENTREPRISE_CREEE", "entreprise", tenant, null, Map.of("nom", nom, "pack", pack.name(), "demonstration", true));
            evenements.publishEvent(new EntrepriseCreee(tenant, pack.name()));
            return entreprise;
        }));
        JOURNAL.info("Données de démonstration créées : {}", nom);
    }

    /** Identifiants UUID v7 fixes et lisibles pour la démonstration. */
    private static UUID id(int graine, int numero) {
        return UUID.fromString(String.format("01920000-0000-7000-8000-%012x", graine * 0x100 + numero));
    }
}
