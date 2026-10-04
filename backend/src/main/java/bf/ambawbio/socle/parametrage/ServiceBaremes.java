package bf.ambawbio.socle.parametrage;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bf.ambawbio.shared.domaine.RegleMetierException;
import bf.ambawbio.shared.domaine.Uuid7;
import bf.ambawbio.socle.api.JournalAudit;

/** Paramétrage fiscal centralisé (F-SOC-09) : barèmes modifiables sans redéploiement, versionnés (RG-10, INV-13). */
@Service
public class ServiceBaremes {

    private final BaremeDepot baremes;
    private final JournalAudit audit;

    ServiceBaremes(BaremeDepot baremes, JournalAudit audit) {
        this.baremes = baremes;
        this.audit = audit;
    }

    /** Version en vigueur à une date : la plus récente dont la date d'effet est antérieure ou égale. */
    @Transactional(readOnly = true)
    public Optional<Bareme> enVigueur(String code, LocalDate date) {
        return baremes.findFirstByCodeAndDateEffetLessThanEqualOrderByDateEffetDesc(code, date);
    }

    @Transactional(readOnly = true)
    public List<Bareme> versions(String code) {
        return baremes.findByCodeOrderByDateEffetDesc(code);
    }

    @Transactional(readOnly = true)
    public List<Bareme> tous() {
        return baremes.findAllByOrderByCodeAscDateEffetDesc();
    }

    /** INV-13 : une seule version par code et par date d'effet. */
    @Transactional
    public Bareme ajouterVersion(UUID id, String code, LocalDate dateEffet, Map<String, Object> valeur, String description, boolean aValider) {
        var existant = baremes.findById(id);
        if (existant.isPresent()) {
            return existant.get();
        }
        if (baremes.existsByCodeAndDateEffet(code, dateEffet)) {
            throw new RegleMetierException("INV-13", "Une version de ce barème existe déjà à cette date d'effet. Choisissez une autre date.");
        }
        var bareme = baremes.save(new Bareme(id, code, dateEffet, valeur, description, aValider));
        audit.enregistrer("BAREME_AJOUTE", "bareme", id, null, Map.of("code", code, "dateEffet", dateEffet.toString(), "valeur", valeur));
        return bareme;
    }

    /**
     * Valeurs initiales d'une nouvelle entreprise, marquées « à valider » par l'expert-comptable (Q-03).
     * Taux normal de TVA de 18 % : valeur indiquée par le guide (§11.3), à confirmer.
     */
    @Transactional
    public void creerBaremesParDefaut(LocalDate dateEffet) {
        baremes.save(new Bareme(Uuid7.nouveau(), "TVA_TAUX_NORMAL", dateEffet, Map.of("taux", 18),
                "Taux normal de TVA (%) — à confirmer par l'expert-comptable", true));
    }
}
