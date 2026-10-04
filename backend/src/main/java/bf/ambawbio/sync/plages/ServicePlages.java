package bf.ambawbio.sync.plages;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bf.ambawbio.sync.api.PlagesNumerotation;
import bf.ambawbio.shared.domaine.Uuid7;
import bf.ambawbio.shared.tenant.ContexteTenant;

/**
 * Attribution des plages (guide §8.6). Le serveur garantit à chaque terminal une plage active par type ;
 * une nouvelle plage est préparée dès que l'active atteint 80 %. Les plages d'une même société, d'un même type
 * et d'une même année ne se chevauchent jamais (contrainte d'exclusion, INV-11) ; l'attribution est sérialisée
 * par un verrou consultatif.
 */
@Service
public class ServicePlages implements PlagesNumerotation {

    private final PlageDepot plages;
    private final JdbcTemplate jdbc;
    private final int taille;
    private final int tailleTicket;

    /** Les tickets ont des plages plus grandes : 7 jours de caisse hors-ligne sans épuiser la plage (D-32). */
    ServicePlages(PlageDepot plages, JdbcTemplate jdbc, @Value("${ambawbio.sync.taille-plage:500}") int taille,
            @Value("${ambawbio.sync.taille-plage-ticket:2000}") int tailleTicket) {
        this.plages = plages;
        this.jdbc = jdbc;
        this.taille = taille;
        this.tailleTicket = tailleTicket;
    }

    @Transactional(readOnly = true)
    public List<PlageNumerotation> actives(UUID terminalId) {
        return plages.findByTerminalIdAndStatutOrderByTypePieceAscDebutAsc(terminalId, PlageNumerotation.Statut.ACTIVE);
    }

    /** Met à jour la consommation signalée par le terminal ({@code idPlage → prochain numéro}). */
    @Transactional
    public void signalerConsommation(UUID terminalId, Map<UUID, Long> prochains) {
        prochains.forEach((id, prochain) -> plages.findById(id).filter(p -> p.terminalId().equals(terminalId))
                .ifPresent(p -> p.signalerProchain(prochain)));
    }

    /** Garantit une plage disponible par type (et une suivante dès 80 %) ; renvoie les plages actives. */
    @Transactional
    public List<PlageNumerotation> garantir(UUID terminalId, UUID societeId) {
        var annee = LocalDate.now(ZoneOffset.UTC).getYear();
        var actives = actives(terminalId);
        var resultat = new ArrayList<>(actives);
        for (var type : PlageNumerotation.TypePiece.values()) {
            var duType = actives.stream().filter(p -> p.typePiece() == type && p.annee() == annee).toList();
            boolean aReserve = duType.size() > 1 || (duType.size() == 1 && !duType.getFirst().seuilAlerteAtteint());
            if (!aReserve) {
                resultat.add(attribuer(terminalId, societeId, type, annee));
            }
        }
        return resultat;
    }

    private PlageNumerotation attribuer(UUID terminalId, UUID societeId, PlageNumerotation.TypePiece type, int annee) {
        var tenant = ContexteTenant.tenantObligatoire();
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtext(?))", Object.class, "plage:" + tenant + ":" + societeId + ":" + type + ":" + annee);
        Long dernier = jdbc.queryForObject("select max(fin) from sync.plage_numerotation where societe_id = ? and type_piece = ? and annee = ?",
                Long.class, societeId, type.name(), annee);
        long debut = dernier == null ? 1 : dernier + 1;
        return plages.saveAndFlush(new PlageNumerotation(Uuid7.nouveau(), societeId, terminalId, type, annee, debut,
                debut + (type == PlageNumerotation.TypePiece.TICKET ? tailleTicket : taille) - 1));
    }

    /** Révocation : les plages du terminal sont clôturées (numéros non utilisés perdus, traçables). */
    @Transactional
    public void cloturer(UUID terminalId) {
        actives(terminalId).forEach(PlageNumerotation::cloturer);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean couvre(UUID terminalId, String typePiece, int annee, long numero) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select 1 from sync.plage_numerotation
                  where terminal_id = ? and type_piece = ? and annee = ? and ? between debut and fin)""",
                Boolean.class, terminalId, typePiece, annee, numero));
    }

    @Override
    @Transactional(readOnly = true)
    public String prefixe(UUID terminalId, String typePiece) {
        var code = jdbc.queryForObject("select code from sync.terminal where id = ?", String.class, terminalId);
        return PlageNumerotation.TypePiece.valueOf(typePiece).prefixe() + "-" + code;
    }
}
