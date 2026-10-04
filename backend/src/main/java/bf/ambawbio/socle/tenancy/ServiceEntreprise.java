package bf.ambawbio.socle.tenancy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bf.ambawbio.shared.domaine.RegleMetierException;
import bf.ambawbio.shared.domaine.RessourceIntrouvableException;
import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.socle.api.JournalAudit;

/** Paramétrage de l'entreprise (UC-SOC-02) : fiche, sociétés, établissements, dépôts, modules actifs. */
@Service
public class ServiceEntreprise {

    private final EntrepriseDepot entreprises;
    private final SocieteDepot societes;
    private final EtablissementDepot etablissements;
    private final DepotDepot depots;
    private final JdbcTemplate jdbc;
    private final JournalAudit audit;

    ServiceEntreprise(EntrepriseDepot entreprises, SocieteDepot societes, EtablissementDepot etablissements, DepotDepot depots,
            JdbcTemplate jdbc, JournalAudit audit) {
        this.entreprises = entreprises;
        this.societes = societes;
        this.etablissements = etablissements;
        this.depots = depots;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Entreprise entrepriseCourante() {
        return entreprises.findById(ContexteTenant.tenantObligatoire())
                .orElseThrow(() -> new RessourceIntrouvableException("Entreprise introuvable."));
    }

    @Transactional
    public Entreprise modifier(String nom, String ifu, String rccm, String adresse, String ville, String telephone, String courriel) {
        var entreprise = entrepriseCourante();
        var avant = Map.of("nom", entreprise.nom(), "ifu", String.valueOf(entreprise.ifu()));
        entreprise.modifier(nom, ifu, rccm, adresse, ville, telephone, courriel);
        audit.enregistrer("ENTREPRISE_MODIFIEE", "entreprise", entreprise.getId(), avant,
                Map.of("nom", entreprise.nom(), "ifu", String.valueOf(entreprise.ifu())));
        return entreprise;
    }

    @Transactional(readOnly = true)
    public List<Societe> societes() {
        return societes.findAllByOrderByNomAsc();
    }

    /** Idempotent : un rejeu avec le même identifiant renvoie la société existante (R-09). */
    @Transactional
    public Societe creerSociete(UUID id, String nom, String ifu, String rccm, String regimeFiscal, String adresse) {
        var existante = societes.findById(id);
        if (existante.isPresent()) {
            return existante.get();
        }
        long nombre = societes.count();
        entrepriseCourante().verifierAjoutSociete(nombre);
        var societe = societes.save(new Societe(id, nom, ifu, rccm, regimeFiscal, adresse, nombre == 0));
        audit.enregistrer("SOCIETE_CREEE", "societe", id, null, Map.of("nom", nom));
        return societe;
    }

    @Transactional(readOnly = true)
    public List<Etablissement> etablissements() {
        return etablissements.findAllByOrderByCodeAsc();
    }

    @Transactional
    public Etablissement creerEtablissement(UUID id, UUID societeId, String code, String nom, String adresse, String ville) {
        var existant = etablissements.findById(id);
        if (existant.isPresent()) {
            return existant.get();
        }
        if (!societes.existsById(societeId)) {
            throw new RessourceIntrouvableException("Société introuvable.");
        }
        var etablissement = etablissements.save(new Etablissement(id, societeId, code, nom, adresse, ville));
        audit.enregistrer("ETABLISSEMENT_CREE", "etablissement", id, null, Map.of("code", code, "nom", nom));
        return etablissement;
    }

    @Transactional(readOnly = true)
    public List<Depot> depots() {
        return depots.findAllByOrderByCodeAsc();
    }

    @Transactional
    public Depot creerDepot(UUID id, UUID etablissementId, String code, String nom) {
        var existant = depots.findById(id);
        if (existant.isPresent()) {
            return existant.get();
        }
        if (!etablissements.existsById(etablissementId)) {
            throw new RessourceIntrouvableException("Établissement introuvable.");
        }
        var depot = depots.save(new Depot(id, etablissementId, code, nom));
        audit.enregistrer("DEPOT_CREE", "depot", id, null, Map.of("code", code, "nom", nom));
        return depot;
    }

    @Transactional(readOnly = true)
    public Set<String> modulesActifs() {
        return new TreeSet<>(jdbc.queryForList("select module from socle.module_active", String.class));
    }

    /** F-SOC-05 : seuls les modules inclus dans le pack peuvent être activés. */
    @Transactional
    public Set<String> definirModulesActifs(Set<String> modules) {
        var entreprise = entrepriseCourante();
        var horsPack = new TreeSet<>(modules);
        horsPack.removeAll(entreprise.pack().modulesDisponibles());
        if (!horsPack.isEmpty()) {
            throw new RegleMetierException("MODULE_HORS_PACK",
                    "Ces modules ne sont pas inclus dans votre offre : " + String.join(", ", horsPack) + ".");
        }
        var avant = modulesActifs();
        jdbc.update("delete from socle.module_active");
        for (var module : modules) {
            jdbc.update("insert into socle.module_active (tenant_id, module) values (?, ?)", entreprise.getId(), module);
        }
        audit.enregistrer("MODULES_MODIFIES", "entreprise", entreprise.getId(), Map.of("modules", avant), Map.of("modules", new TreeSet<>(modules)));
        return modulesActifs();
    }
}
