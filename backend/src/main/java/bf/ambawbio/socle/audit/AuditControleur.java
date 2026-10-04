package bf.ambawbio.socle.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bf.ambawbio.shared.web.PageResultat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasAuthority('audit:consulter')")
@Tag(name = "Audit", description = "Journal d'audit chaîné (UC-SOC-08)")
class AuditControleur {

    record EntreeJournal(long id, Instant horodatage, UUID utilisateurId, String action, String entite, UUID entiteId,
            JsonNode avant, JsonNode apres, String empreinte) {
    }

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final VerificationAudit verification;

    AuditControleur(JdbcTemplate jdbc, JsonMapper json, VerificationAudit verification) {
        this.jdbc = jdbc;
        this.json = json;
        this.verification = verification;
    }

    @GetMapping("/journal")
    @Transactional(readOnly = true)
    @Operation(summary = "Consulter le journal", description = "Du plus récent au plus ancien ; filtres facultatifs par entité et action.")
    PageResultat<EntreeJournal> journal(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int taille,
            @RequestParam(required = false) String entite, @RequestParam(required = false) String action) {
        var taillePage = Math.min(Math.max(taille, 1), 200);
        var filtres = new StringBuilder(" where true");
        var parametres = new ArrayList<Object>();
        if (entite != null) {
            filtres.append(" and entite = ?");
            parametres.add(entite);
        }
        if (action != null) {
            filtres.append(" and action = ?");
            parametres.add(action);
        }
        long total = jdbc.queryForObject("select count(*) from audit.journal" + filtres, Long.class, parametres.toArray());
        var parametresPage = new ArrayList<>(parametres);
        parametresPage.add(taillePage);
        parametresPage.add((long) Math.max(page, 0) * taillePage);
        List<EntreeJournal> entrees = jdbc.query(
                "select id, horodatage, utilisateur_id, action, entite, entite_id, avant::text, apres::text, empreinte from audit.journal"
                        + filtres + " order by id desc limit ? offset ?",
                (l, n) -> new EntreeJournal(l.getLong(1), l.getTimestamp(2).toInstant(), l.getObject(3, UUID.class), l.getString(4),
                        l.getString(5), l.getObject(6, UUID.class), lire(l.getString(7)), lire(l.getString(8)), l.getString(9)),
                parametresPage.toArray());
        return new PageResultat<>(entrees, Math.max(page, 0), taillePage, total);
    }

    @GetMapping("/journal/verification")
    @Operation(summary = "Vérifier la chaîne", description = "Recalcule les empreintes et signale la première entrée altérée.")
    VerificationAudit.Resultat verifier() {
        return verification.verifier();
    }

    private JsonNode lire(String texte) {
        return texte == null ? null : json.readTree(texte);
    }
}
