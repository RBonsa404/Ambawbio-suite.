package bf.ambawbio.referentiel.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bf.ambawbio.referentiel.domaine.Parametres.Categorie;
import bf.ambawbio.referentiel.domaine.Parametres.RegimeFiscal;
import bf.ambawbio.referentiel.domaine.Parametres.Taxe;
import bf.ambawbio.referentiel.domaine.Parametres.TypeConditionnement;
import bf.ambawbio.referentiel.domaine.Parametres.UniteMesure;
import bf.ambawbio.shared.domaine.RegleMetierException;
import bf.ambawbio.shared.domaine.RessourceIntrouvableException;
import bf.ambawbio.shared.domaine.Uuid7;
import bf.ambawbio.socle.api.JournalAudit;

/** Régimes fiscaux, taxes, unités, types de conditionnement et catégories ; données de démarrage (guide §7.5). */
@Service
public class ServiceParametres {

    private final RegimeDepot regimes;
    private final TaxeDepot taxes;
    private final UniteDepot unites;
    private final TypeConditionnementDepot typesConditionnement;
    private final CategorieDepot categories;
    private final JournalAudit audit;
    private final PublicationReferentiel publication;

    ServiceParametres(RegimeDepot regimes, TaxeDepot taxes, UniteDepot unites, TypeConditionnementDepot typesConditionnement,
            CategorieDepot categories, JournalAudit audit, PublicationReferentiel publication) {
        this.regimes = regimes;
        this.taxes = taxes;
        this.unites = unites;
        this.typesConditionnement = typesConditionnement;
        this.categories = categories;
        this.audit = audit;
        this.publication = publication;
    }

    /**
     * Valeurs par défaut d'une nouvelle entreprise (Q-03) : à faire valider par l'expert-comptable. Idempotent.
     */
    @Transactional
    public void initialiser() {
        if (taxes.count() > 0) {
            return;
        }
        regimes.save(new RegimeFiscal(Uuid7.nouveau(), "RNI", "Réel normal d'imposition", true, true));
        regimes.save(new RegimeFiscal(Uuid7.nouveau(), "RSI", "Réel simplifié d'imposition", true, true));
        regimes.save(new RegimeFiscal(Uuid7.nouveau(), "CME", "Contribution des micro-entreprises", false, true));
        taxes.save(new Taxe(Uuid7.nouveau(), "TVA18", "TVA taux normal 18 %", new BigDecimal("18"), true));
        taxes.save(new Taxe(Uuid7.nouveau(), "TVA10", "TVA taux réduit 10 %", new BigDecimal("10"), true));
        taxes.save(new Taxe(Uuid7.nouveau(), "EXO", "Exonéré de TVA", BigDecimal.ZERO, false));
        for (var u : List.of(
                new UniteMesure(Uuid7.nouveau(), "U", "Pièce", UniteMesure.Categorie.UNITE),
                new UniteMesure(Uuid7.nouveau(), "KG", "Kilogramme", UniteMesure.Categorie.POIDS),
                new UniteMesure(Uuid7.nouveau(), "T", "Tonne", UniteMesure.Categorie.POIDS),
                new UniteMesure(Uuid7.nouveau(), "L", "Litre", UniteMesure.Categorie.VOLUME),
                new UniteMesure(Uuid7.nouveau(), "M", "Mètre", UniteMesure.Categorie.LONGUEUR),
                new UniteMesure(Uuid7.nouveau(), "M2", "Mètre carré", UniteMesure.Categorie.SURFACE),
                new UniteMesure(Uuid7.nouveau(), "H", "Heure", UniteMesure.Categorie.TEMPS))) {
            unites.save(u);
        }
        for (var t : Map.of("PIECE", "Pièce", "CARTON", "Carton", "SAC", "Sac", "BIDON", "Bidon", "PALETTE", "Palette", "PAQUET", "Paquet")
                .entrySet()) {
            typesConditionnement.save(new TypeConditionnement(Uuid7.nouveau(), t.getKey(), t.getValue()));
        }
        taxes.flush();
        taxes.findAll().forEach(publication::taxe);
        unites.findAll().forEach(publication::unite);
    }

    @Transactional(readOnly = true)
    public List<RegimeFiscal> regimes() {
        return regimes.findAllByOrderByCodeAsc();
    }

    @Transactional
    public RegimeFiscal creerRegime(UUID id, String code, String libelle, boolean assujettiTva) {
        return regimes.findById(id).orElseGet(() -> {
            var regime = regimes.save(new RegimeFiscal(id, code, libelle, assujettiTva, false));
            audit.enregistrer("REGIME_FISCAL_CREE", "regime_fiscal", id, null, Map.of("code", code, "assujettiTva", assujettiTva));
            return regime;
        });
    }

    @Transactional(readOnly = true)
    public List<Taxe> taxes() {
        return taxes.findAllByOrderByCodeAsc();
    }

    @Transactional
    public Taxe creerTaxe(UUID id, String code, String libelle, BigDecimal taux) {
        return taxes.findById(id).orElseGet(() -> {
            var taxe = taxes.save(new Taxe(id, code, libelle, taux, false));
            publication.taxe(taxe);
            audit.enregistrer("TAXE_CREEE", "taxe", id, null, Map.of("code", code, "taux", taux));
            return taxe;
        });
    }

    /** Modification de taxe : journalisée (guide §6.6, « modification de prix ou de taxe »). */
    @Transactional
    public Taxe modifierTaxe(UUID id, String libelle, BigDecimal taux, boolean actif) {
        var taxe = taxes.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Taxe introuvable."));
        var avant = Map.of("taux", taxe.taux(), "actif", taxe.actif());
        taxe.modifier(libelle, taux, actif);
        publication.taxe(taxe);
        audit.enregistrer("TAXE_MODIFIEE", "taxe", id, avant, Map.of("taux", taux, "actif", actif));
        return taxe;
    }

    @Transactional(readOnly = true)
    public List<UniteMesure> unites() {
        return unites.findAllByOrderByCodeAsc();
    }

    @Transactional
    public UniteMesure creerUnite(UUID id, String code, String libelle, UniteMesure.Categorie categorie) {
        return unites.findById(id).orElseGet(() -> {
            var unite = unites.save(new UniteMesure(id, code, libelle, categorie));
            publication.unite(unite);
            return unite;
        });
    }

    @Transactional(readOnly = true)
    public List<TypeConditionnement> typesConditionnement() {
        return typesConditionnement.findAllByOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public List<Categorie> categories() {
        return categories.findAllByOrderByNomAsc();
    }

    @Transactional
    public Categorie creerCategorie(UUID id, String nom, UUID parentId) {
        return categories.findById(id).orElseGet(() -> {
            if (categories.findByNomIgnoreCase(nom).isPresent()) {
                throw new RegleMetierException("CATEGORIE_EXISTANTE", "La catégorie « " + nom + " » existe déjà.");
            }
            var categorie = categories.save(new Categorie(id, nom, parentId));
            publication.categorie(categorie);
            return categorie;
        });
    }

    @Transactional
    public Categorie modifierCategorie(UUID id, String nom, UUID parentId) {
        var categorie = categories.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Catégorie introuvable."));
        if (id.equals(parentId)) {
            throw new RegleMetierException("CATEGORIE_PARENT", "Une catégorie ne peut pas être sa propre parente.");
        }
        categorie.modifier(nom, parentId);
        publication.categorie(categorie);
        return categorie;
    }
}
