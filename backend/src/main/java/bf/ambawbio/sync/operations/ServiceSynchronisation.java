package bf.ambawbio.sync.operations;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import bf.ambawbio.shared.domaine.RegleMetierException;
import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.sync.api.GestionnaireOperation;
import bf.ambawbio.sync.api.OperationTerminal;
import bf.ambawbio.sync.plages.PlageNumerotation;
import bf.ambawbio.sync.plages.ServicePlages;
import bf.ambawbio.sync.terminaux.ServiceTerminaux;
import bf.ambawbio.sync.terminaux.SignatureOperation;
import bf.ambawbio.sync.terminaux.Terminal;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Envoi (push) et réception (pull) — guide §8.3 et §8.4. Chaque opération est traitée dans sa propre transaction :
 * une erreur sur l'une n'empêche pas les suivantes ; un rejeu ne crée jamais de doublon (INV-10).
 */
@Service
public class ServiceSynchronisation {

    public enum Statut { APPLIQUEE, IGNOREE_DOUBLON, EN_CONFLIT, REFUSEE, A_RENVOYER }

    /** Opération telle que transmise : la charge est le texte JSON exact qui a été signé. */
    public record OperationRecue(UUID idOperation, String type, int versionSchema, String horodatageLocal, UUID utilisateurId,
            String charge, String signature) {
    }

    public record Accuse(UUID idOperation, Statut statut, String motif) {
    }

    public record ConsommationPlage(UUID id, long prochain) {
    }

    public record Changement(long sequence, String entite, UUID entiteId, String operation, JsonNode donnees) {
    }

    public record PlageVue(UUID id, String typePiece, String prefixe, int annee, long debut, long fin, long prochain) {
        public static PlageVue de(PlageNumerotation p, String codeTerminal) {
            return new PlageVue(p.getId(), p.typePiece().name(), p.typePiece().prefixe() + "-" + codeTerminal, p.annee(), p.debut(), p.fin(), p.prochain());
        }
    }

    public record Reception(List<Changement> changements, long curseur, boolean encore, List<PlageVue> plages) {
    }

    private static final Logger JOURNAL = LoggerFactory.getLogger(ServiceSynchronisation.class);

    private final ServiceTerminaux terminaux;
    private final ServicePlages plages;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final TransactionTemplate parOperation;
    private final Map<String, GestionnaireOperation> gestionnaires;
    private final int operationsParLot;
    private final int changementsParPage;

    ServiceSynchronisation(ServiceTerminaux terminaux, ServicePlages plages, JdbcTemplate jdbc, JsonMapper json, PlatformTransactionManager transactions,
            List<GestionnaireOperation> gestionnaires, @Value("${ambawbio.sync.operations-par-lot:100}") int operationsParLot,
            @Value("${ambawbio.sync.changements-par-page:500}") int changementsParPage) {
        this.terminaux = terminaux;
        this.plages = plages;
        this.jdbc = jdbc;
        this.json = json;
        this.parOperation = new TransactionTemplate(transactions);
        this.parOperation.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.gestionnaires = gestionnaires.stream().collect(Collectors.toMap(GestionnaireOperation::type, Function.identity()));
        this.operationsParLot = operationsParLot;
        this.changementsParPage = changementsParPage;
    }

    public List<Accuse> pousser(UUID terminalId, List<OperationRecue> operations, List<ConsommationPlage> consommations) {
        if (operations.size() > operationsParLot) {
            throw new RegleMetierException("LOT_TROP_GRAND", "Un envoi contient au plus " + operationsParLot + " opérations.");
        }
        var terminal = terminaux.actif(terminalId);
        var cle = SignatureOperation.lireClePublique(terminal.clePublique());
        var accuses = new ArrayList<Accuse>();
        for (var op : operations) {
            if (!SignatureOperation.verifier(cle, SignatureOperation.message(op.idOperation().toString(), op.type(), op.horodatageLocal(), op.charge()),
                    op.signature())) {
                accuses.add(new Accuse(op.idOperation(), Statut.REFUSEE, "Signature invalide : opération refusée."));
                continue;
            }
            accuses.add(traiter(terminal, op));
        }
        if (consommations != null && !consommations.isEmpty()) {
            plages.signalerConsommation(terminalId, consommations.stream().collect(Collectors.toMap(ConsommationPlage::id, ConsommationPlage::prochain,
                    Math::max, HashMap::new)));
        }
        terminaux.marquerSynchronise(terminalId);
        return accuses;
    }

    private Accuse traiter(Terminal terminal, OperationRecue op) {
        var gestionnaire = gestionnaires.get(op.type());
        if (gestionnaire == null) {
            return enregistrerConflit(terminal, op, "Type d'opération inconnu : " + op.type() + ".");
        }
        try {
            var statut = parOperation.execute(s -> {
                if (inserer(terminal, op, "RECUE", null) == 0) {
                    return Statut.IGNOREE_DOUBLON;
                }
                gestionnaire.appliquer(new OperationTerminal(op.idOperation(), terminal.getId(), terminal.etablissementId(), op.type(), op.versionSchema(),
                        Instant.parse(op.horodatageLocal()), op.utilisateurId(), json.readTree(op.charge())));
                jdbc.update("update sync.operation_recue set statut = 'APPLIQUEE' where id_operation = ?", op.idOperation());
                return Statut.APPLIQUEE;
            });
            return new Accuse(op.idOperation(), statut, null);
        } catch (RegleMetierException e) {
            return enregistrerConflit(terminal, op, e.getMessage());
        } catch (RuntimeException e) {
            JOURNAL.warn("Opération {} non appliquée, à renvoyer : {}", op.idOperation(), e.getClass().getSimpleName());
            return new Accuse(op.idOperation(), Statut.A_RENVOYER, "Erreur temporaire : l'opération sera renvoyée.");
        }
    }

    /** Conflit (guide §8.5) : l'opération est conservée avec son motif, pour traitement par un responsable. */
    private Accuse enregistrerConflit(Terminal terminal, OperationRecue op, String motif) {
        var inseree = parOperation.execute(s -> inserer(terminal, op, "EN_CONFLIT", motif));
        return new Accuse(op.idOperation(), inseree == 0 ? Statut.IGNOREE_DOUBLON : Statut.EN_CONFLIT, inseree == 0 ? null : motif);
    }

    private int inserer(Terminal terminal, OperationRecue op, String statut, String motif) {
        return jdbc.update("""
                insert into sync.operation_recue (tenant_id, id_operation, terminal_id, type_operation, version_schema, charge, horodatage_local,
                                                  utilisateur_id, signature, statut, motif)
                values (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?) on conflict do nothing
                """, ContexteTenant.tenantObligatoire(), op.idOperation(), terminal.getId(), op.type(), op.versionSchema(), op.charge(),
                java.sql.Timestamp.from(Instant.parse(op.horodatageLocal())), op.utilisateurId(), op.signature(), statut, motif);
    }

    /** Réception : changements postérieurs au curseur, pour l'entreprise (RLS) et l'établissement du terminal ; plages garanties. */
    public Reception recevoir(UUID terminalId, long curseur, Integer limite) {
        var terminal = terminaux.actif(terminalId);
        var taille = Math.min(Math.max(limite == null ? changementsParPage : limite, 1), changementsParPage);
        var changements = parOperation.execute(s -> jdbc.query("""
                select sequence, entite, entite_id, operation, donnees::text from sync.flux_changements
                where sequence > ? and (etablissement_id is null or etablissement_id = ?) order by sequence limit ?
                """, (l, n) -> new Changement(l.getLong(1), l.getString(2), l.getObject(3, UUID.class), l.getString(4),
                l.getString(5) == null ? null : json.readTree(l.getString(5))), curseur, terminal.etablissementId(), taille + 1));
        var encore = changements.size() > taille;
        var page = encore ? changements.subList(0, taille) : changements;
        var nouveauCurseur = page.isEmpty() ? curseur : page.getLast().sequence();
        var plagesActives = parOperation.execute(s -> plages.garantir(terminalId, terminaux.societe(terminal)));
        terminaux.marquerSynchronise(terminalId);
        return new Reception(List.copyOf(page), nouveauCurseur, encore, plagesActives.stream().map(p -> PlageVue.de(p, terminal.code())).toList());
    }
}
