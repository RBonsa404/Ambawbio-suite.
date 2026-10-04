package bf.ambawbio.referentiel.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bf.ambawbio.referentiel.domaine.Tiers;
import bf.ambawbio.shared.domaine.RegleMetierException;
import bf.ambawbio.shared.domaine.RessourceIntrouvableException;
import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.socle.api.ChampsPersonnalises;
import bf.ambawbio.socle.api.JournalAudit;
import tools.jackson.databind.json.JsonMapper;

/** Clients et fournisseurs (F-SOC-06, UC-SOC-09). */
@Service
public class ServiceTiers {

    public record Commande(String code, String nom, Tiers.Nature nature, boolean estClient, boolean estFournisseur, String ifu, String rccm,
            String regimeCode, Tiers.Coordonnees coordonnees, Tiers.ConditionsCommerciales conditions, boolean actif,
            List<Tiers.DonneesContact> contacts, List<Tiers.DonneesCompte> comptesMobileMoney, Map<String, Object> champsPerso) {
    }

    private final TiersDepot tiers;
    private final RegimeDepot regimes;
    private final ListePrixDepot listes;
    private final ChampsPersonnalises champs;
    private final JournalAudit audit;
    private final JsonMapper json;
    private final PublicationReferentiel publication;

    ServiceTiers(TiersDepot tiers, RegimeDepot regimes, ListePrixDepot listes, ChampsPersonnalises champs, JournalAudit audit, JsonMapper json,
            PublicationReferentiel publication) {
        this.tiers = tiers;
        this.regimes = regimes;
        this.listes = listes;
        this.champs = champs;
        this.audit = audit;
        this.json = json;
        this.publication = publication;
    }

    @Transactional(readOnly = true)
    public Page<Tiers> rechercher(String q, Boolean client, Boolean fournisseur, Boolean actif, Map<String, String> filtresChamps,
            int page, int taille) {
        var filtre = champs.filtre("tiers", filtresChamps);
        return tiers.rechercher(ContexteTenant.tenantObligatoire(), q == null || q.isBlank() ? null : q.trim(), client, fournisseur, actif,
                json.writeValueAsString(filtre), PageRequest.of(Math.max(page, 0), Math.min(Math.max(taille, 1), 200)));
    }

    @Transactional(readOnly = true)
    public Tiers tiers(UUID id) {
        return tiers.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Tiers introuvable."));
    }

    @Transactional
    public Tiers creer(UUID id, Commande c) {
        var existant = tiers.findById(id);
        if (existant.isPresent()) {
            return existant.get();
        }
        if (tiers.findByCode(c.code()).isPresent()) {
            throw new RegleMetierException("CODE_EXISTANT", "Un tiers porte déjà le code « " + c.code() + " ».");
        }
        var nouveau = new Tiers(id, c.code());
        appliquer(nouveau, c);
        tiers.save(nouveau);
        publication.tiers(nouveau);
        audit.enregistrer("TIERS_CREE", "tiers", id, null, Map.of("code", c.code()));
        return nouveau;
    }

    @Transactional
    public Tiers modifier(UUID id, Commande c) {
        var existant = tiers(id);
        appliquer(existant, c);
        tiers.flush();
        publication.tiers(existant);
        return existant;
    }

    void appliquer(Tiers t, Commande c) {
        var regime = c.regimeCode() == null || c.regimeCode().isBlank() ? null : regimes.findByCode(c.regimeCode())
                .orElseThrow(() -> new RegleMetierException("REGIME_INCONNU", "Régime fiscal inconnu : « " + c.regimeCode() + " »."));
        var conditions = c.conditions() == null ? new Tiers.ConditionsCommerciales(null, 0, null) : c.conditions();
        if (conditions.listePrixId() != null && !listes.existsById(conditions.listePrixId())) {
            throw new RessourceIntrouvableException("Liste de prix introuvable.");
        }
        t.modifier(new Tiers.Identification(c.nom(), c.nature(), c.estClient(), c.estFournisseur(), c.ifu(), c.rccm(),
                        regime == null ? null : regime.getId(), regime != null && regime.assujettiTva()),
                c.coordonnees() == null ? new Tiers.Coordonnees(null, null, null, null) : c.coordonnees(),
                conditions, c.actif(), champs.valider("tiers", c.champsPerso()));
        t.definirContacts(c.contacts() == null ? List.of() : c.contacts());
        t.definirComptesMobileMoney(c.comptesMobileMoney() == null ? List.of() : c.comptesMobileMoney());
    }
}
