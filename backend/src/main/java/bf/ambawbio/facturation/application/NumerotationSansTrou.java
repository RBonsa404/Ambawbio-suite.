package bf.ambawbio.facturation.application;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import bf.ambawbio.facturation.domaine.DocumentFiscal;
import bf.ambawbio.shared.tenant.ContexteTenant;

/**
 * Numérotation sans trou (guide §6.7) : le compteur est verrouillé (SELECT … FOR UPDATE) puis incrémenté dans la
 * transaction de validation ; si elle est annulée, le numéro n'est pas consommé. Format {@code FA-2026-000154}.
 */
@Component
class NumerotationSansTrou {

    record Numero(String texte, int exercice, long sequence) {
    }

    private final JdbcTemplate jdbc;

    NumerotationSansTrou(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    Numero attribuer(UUID societeId, DocumentFiscal.Type type, int exercice) {
        var tenant = ContexteTenant.tenantObligatoire();
        jdbc.update("""
                insert into facturation.sequence_piece (tenant_id, societe_id, type_piece, exercice) values (?, ?, ?, ?)
                on conflict do nothing""", tenant, societeId, type.name(), exercice);
        Long prochain = jdbc.queryForObject("""
                select prochain from facturation.sequence_piece
                where tenant_id = ? and societe_id = ? and type_piece = ? and exercice = ? for update""",
                Long.class, tenant, societeId, type.name(), exercice);
        jdbc.update("update facturation.sequence_piece set prochain = prochain + 1 where tenant_id = ? and societe_id = ? and type_piece = ? and exercice = ?",
                tenant, societeId, type.name(), exercice);
        return new Numero("%s-%d-%06d".formatted(type == DocumentFiscal.Type.FACTURE ? "FA" : "AV", exercice, prochain), exercice, prochain);
    }
}
