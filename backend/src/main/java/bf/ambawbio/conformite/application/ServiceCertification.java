package bf.ambawbio.conformite.application;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import bf.ambawbio.conformite.api.CertificationObtenue;
import bf.ambawbio.conformite.api.CertificationRejetee;
import bf.ambawbio.conformite.api.ControleIfu;
import bf.ambawbio.conformite.api.FileCertification;
import bf.ambawbio.conformite.api.PieceACertifier;
import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.socle.api.Organisation;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import tools.jackson.databind.json.JsonMapper;

/**
 * File de certification (guide §10.3) : statut EN_FILE, prochain essai après 1 min, 5 min, 15 min, 1 h puis toutes les
 * heures ; alerte au-delà de 24 h en file. Chaque tentative est faite dans sa propre transaction.
 */
@Service
public class ServiceCertification implements FileCertification, ControleIfu {

    private static final Logger JOURNAL = LoggerFactory.getLogger(ServiceCertification.class);
    private static final List<Duration> DELAIS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1));
    private static final Duration ALERTE = Duration.ofHours(24);

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final PortCertificationFiscale port;
    private final ApplicationEventPublisher evenements;
    private final Organisation organisation;
    private final ServiceCertification moi;
    /** Première tentative hors du fil de la validation (qui garde sa connexion jusqu'à la fin) ; au plus 4 à la fois. */
    private final java.util.concurrent.ExecutorService tentatives = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    private final java.util.concurrent.Semaphore limite = new java.util.concurrent.Semaphore(4);

    ServiceCertification(JdbcTemplate jdbc, JsonMapper json, PortCertificationFiscale port, ApplicationEventPublisher evenements,
            Organisation organisation, @org.springframework.context.annotation.Lazy ServiceCertification moi) {
        this.jdbc = jdbc;
        this.json = json;
        this.port = port;
        this.evenements = evenements;
        this.organisation = organisation;
        this.moi = moi;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void soumettre(PieceACertifier piece) {
        jdbc.update("""
                insert into conformite.certification (document_id, tenant_id, type_document, numero, statut, charge)
                values (?, ?, ?, ?, 'EN_FILE', ?::jsonb) on conflict (document_id) do nothing""",
                piece.documentId(), ContexteTenant.tenantObligatoire(), piece.type(), piece.numero(), json.writeValueAsString(piece));
        var tenant = ContexteTenant.tenantObligatoire();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                tentatives.execute(() -> {
                    limite.acquireUninterruptibly();
                    try {
                        ContexteTenant.executerPour(tenant, () -> {
                            moi.tenter(piece.documentId());
                            return null;
                        });
                    } catch (RuntimeException e) {
                        JOURNAL.warn("Première tentative de certification de {} : {}", piece.numero(), e.getMessage());
                    } finally {
                        limite.release();
                    }
                });
            }
        });
    }

    /** Une tentative pour une pièce en file (entreprise du contexte). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tenter(UUID documentId) {
        var lignes = jdbc.query(
                "select charge::text, tentatives, soumis_le from conformite.certification where document_id = ? and statut = 'EN_FILE' for update",
                (rs, i) -> new Object[] {rs.getString(1), rs.getInt(2), rs.getTimestamp(3).toInstant()}, documentId);
        if (lignes.isEmpty()) {
            return;
        }
        var piece = json.readValue((String) lignes.getFirst()[0], PieceACertifier.class);
        int tentatives = (int) lignes.getFirst()[1] + 1;
        var soumisLe = (Instant) lignes.getFirst()[2];
        try {
            switch (port.certifier(piece)) {
                case PortCertificationFiscale.Certifiee c -> {
                    jdbc.update("""
                            update conformite.certification set statut = 'CERTIFIEE', tentatives = ?, identifiant = ?, code_qr = ?, horodatage = ?,
                              simulee = ?, dernier_message = null, alerte = false, modifie_le = now() where document_id = ?""",
                            tentatives, c.identifiant(), c.codeQr(), Timestamp.from(c.horodatage()), c.simulee(), documentId);
                    evenements.publishEvent(new CertificationObtenue(1, documentId, c.identifiant(), c.codeQr(), c.horodatage(), c.simulee()));
                }
                case PortCertificationFiscale.Rejetee r -> {
                    jdbc.update("update conformite.certification set statut = 'REJETEE', tentatives = ?, dernier_message = ?, alerte = true, "
                            + "modifie_le = now() where document_id = ?", tentatives, r.motif(), documentId);
                    evenements.publishEvent(new CertificationRejetee(1, documentId, r.motif()));
                }
            }
        } catch (PortCertificationFiscale.CertificationIndisponible e) {
            var delai = DELAIS.get(Math.min(tentatives - 1, DELAIS.size() - 1));
            boolean alerte = Duration.between(soumisLe, Instant.now()).compareTo(ALERTE) > 0;
            jdbc.update("update conformite.certification set tentatives = ?, prochain_essai = ?, dernier_message = ?, alerte = ?, modifie_le = now() "
                    + "where document_id = ?", tentatives, Timestamp.from(Instant.now().plus(delai)), e.getMessage(), alerte, documentId);
            if (alerte) {
                JOURNAL.warn("Pièce {} en attente de certification depuis plus de 24 h", piece.numero());
            }
        }
    }

    /** Reprise de la file pour l'entreprise du contexte : pièces dont l'essai est échu. */
    public int reprendre() {
        var echues = jdbc.queryForList("select document_id from conformite.certification where statut = 'EN_FILE' and prochain_essai <= now() "
                + "order by prochain_essai limit 200", UUID.class);
        echues.forEach(moi::tenter);
        return echues.size();
    }

    /** Relance manuelle : nouvel essai immédiat. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void relancer(UUID documentId) {
        jdbc.update("update conformite.certification set prochain_essai = now() where document_id = ? and statut = 'EN_FILE'", documentId);
    }

    /** Toutes les 30 secondes, pour toutes les entreprises (une seule instance à la fois). */
    @Scheduled(fixedDelayString = "${ambawbio.fec.reprise-ms:30000}", initialDelayString = "${ambawbio.fec.reprise-ms:30000}")
    @SchedulerLock(name = "file-certification", lockAtMostFor = "PT10M")
    public void reprendreToutesLesEntreprises() {
        var entreprises = ContexteTenant.executerEnModePlateforme(organisation::entreprises);
        for (var tenant : entreprises) {
            try {
                ContexteTenant.executerPour(tenant, this::reprendre);
            } catch (RuntimeException e) {
                JOURNAL.error("Reprise de la file de certification impossible pour l'entreprise {}", tenant, e);
            }
        }
    }

    public record Element(UUID documentId, String typeDocument, String numero, String statut, int tentatives, Instant soumisLe, Instant prochainEssai,
            String dernierMessage, boolean alerte) {
    }

    @Transactional(readOnly = true)
    public List<Element> file() {
        return jdbc.query("""
                select document_id, type_document, numero, statut, tentatives, soumis_le, prochain_essai, dernier_message, alerte
                from conformite.certification where statut <> 'CERTIFIEE' order by soumis_le""",
                (rs, i) -> new Element(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5),
                        rs.getTimestamp(6).toInstant(), rs.getTimestamp(7).toInstant(), rs.getString(8), rs.getBoolean(9)));
    }

    @Override
    public java.util.Optional<Boolean> valide(String ifu) {
        try {
            return java.util.Optional.of(port.verifierIfu(ifu).valide());
        } catch (PortCertificationFiscale.CertificationIndisponible e) {
            return java.util.Optional.empty();
        }
    }
}
