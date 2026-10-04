package bf.ambawbio.facturation.application;

import java.sql.Timestamp;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import bf.ambawbio.conformite.api.CertificationObtenue;
import bf.ambawbio.conformite.api.CertificationRejetee;

/**
 * Reporte sur la pièce le résultat de la certification et archive la version certifiée du PDF, dans la transaction de la
 * tentative : si l'écriture échoue, la tentative est annulée et reprise par la file. Seules les colonnes de
 * certification sont écrites (sans changer la version) : un avoir saisi au même moment n'est pas mis en conflit.
 */
@Component
class SuiviCertification {

    private final DocumentDepot documents;
    private final ServiceFacturation service;
    private final JdbcTemplate jdbc;

    SuiviCertification(DocumentDepot documents, ServiceFacturation service, JdbcTemplate jdbc) {
        this.documents = documents;
        this.service = service;
        this.jdbc = jdbc;
    }

    @EventListener
    public void certifiee(CertificationObtenue e) {
        int lignes = jdbc.update("""
                update facturation.document_fiscal set fec_statut = 'CERTIFIEE', fec_identifiant = ?, fec_code_qr = ?, fec_horodatage = ?,
                  fec_simulee = ?, fec_message = null where id = ?""",
                e.identifiant(), e.codeQr(), Timestamp.from(e.horodatage()), e.simulee(), e.documentId());
        if (lignes > 0) {
            documents.findById(e.documentId()).ifPresent(d -> service.archiver(d, service.numero(d.factureOrigineId()), "certification"));
        }
    }

    @EventListener
    public void rejetee(CertificationRejetee e) {
        jdbc.update("update facturation.document_fiscal set fec_statut = 'REJETEE', fec_message = ? where id = ?", e.motif(), e.documentId());
    }
}
