package bf.ambawbio.pos.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bf.ambawbio.pos.domaine.PointDeVente;
import bf.ambawbio.pos.domaine.SessionCaisse;
import bf.ambawbio.pos.domaine.Vente;
import bf.ambawbio.shared.domaine.RegleMetierException;
import bf.ambawbio.shared.domaine.RessourceIntrouvableException;
import bf.ambawbio.socle.api.AccesEtablissement;
import bf.ambawbio.socle.api.Habilitations;
import bf.ambawbio.socle.api.JournalAudit;

/** Paramétrage des points de vente et consultation des sessions (F-POS-02, 03). */
@Service
public class ServicePointsDeVente {

    public record Parametres(String nom, long seuilEcart, boolean comptageAveugle, int remiseMaxPourcent, PointDeVente.VenteSansStock venteSansStock,
            boolean actif) {
    }

    /** Rapport Z d'une session (F-POS-02). */
    public record RapportZ(SessionCaisse session, String pointDeVente, int nombreVentes, long totalVentes, int nombreRetours, long totalRetours,
            long especes, long mobileMoney, long carte, long taxes, long remises, long especesTheoriques) {
    }

    private final PointDeVenteDepot pointsDeVente;
    private final SessionDepot sessions;
    private final VenteDepot ventes;
    private final ServiceCaisse caisse;
    private final PublicationCaisse publication;
    private final AccesEtablissement acces;
    private final JournalAudit audit;
    private final Habilitations habilitations;

    ServicePointsDeVente(PointDeVenteDepot pointsDeVente, SessionDepot sessions, VenteDepot ventes, ServiceCaisse caisse, PublicationCaisse publication,
            AccesEtablissement acces, JournalAudit audit, Habilitations habilitations) {
        this.habilitations = habilitations;
        this.pointsDeVente = pointsDeVente;
        this.sessions = sessions;
        this.ventes = ventes;
        this.caisse = caisse;
        this.publication = publication;
        this.acces = acces;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PointDeVente> liste() {
        return pointsDeVente.findAllByOrderByCodeAsc().stream().filter(p -> acces.estAutorise(p.etablissementId())).toList();
    }

    @Transactional
    public PointDeVente creer(UUID id, UUID etablissementId, String code, Parametres p) {
        var existant = pointsDeVente.findById(id);
        if (existant.isPresent()) {
            return existant.get();
        }
        acces.verifier(etablissementId);
        if (code != null && pointsDeVente.existsByCode(code.trim().toUpperCase())) {
            throw new RegleMetierException("CODE_DEJA_UTILISE", "Un point de vente porte déjà le code « " + code + " ».");
        }
        var pdv = new PointDeVente(id, etablissementId, code);
        pdv.parametrer(p.nom(), p.seuilEcart(), p.comptageAveugle(), p.remiseMaxPourcent(), p.venteSansStock(), p.actif());
        pointsDeVente.saveAndFlush(pdv);
        publication.pointDeVente(pdv);
        audit.enregistrer("POINT_DE_VENTE_CREE", "point_de_vente", id, null, p);
        return pdv;
    }

    @Transactional
    public PointDeVente modifier(UUID id, Parametres p) {
        var pdv = pointsDeVente.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Point de vente introuvable."));
        acces.verifier(pdv.etablissementId());
        pdv.parametrer(p.nom(), p.seuilEcart(), p.comptageAveugle(), p.remiseMaxPourcent(), p.venteSansStock(), p.actif());
        pointsDeVente.flush();
        publication.pointDeVente(pdv);
        audit.enregistrer("POINT_DE_VENTE_MODIFIE", "point_de_vente", id, null, p);
        return pdv;
    }

    /** Session et ce qu'il faut pour la liste W-13 : caisse, caissier, ventes nettes. */
    public record SessionResumee(SessionCaisse session, String pointDeVente, String caissier, long ventes) {
    }

    @Transactional(readOnly = true)
    public List<SessionResumee> sessions() {
        var liste = sessions.findTop200ByOrderByOuverteLeDesc().stream().filter(s -> acces.estAutorise(s.etablissementId())).toList();
        var totaux = new java.util.HashMap<UUID, Long>();
        if (!liste.isEmpty()) {
            ventes.totauxParSession(liste.stream().map(SessionCaisse::getId).toList()).forEach(l -> totaux.put((UUID) l[0], ((Number) l[1]).longValue()));
        }
        var noms = new java.util.HashMap<UUID, String>();
        var caisses = new java.util.HashMap<UUID, String>();
        pointsDeVente.findAll().forEach(p -> caisses.put(p.getId(), p.nom()));
        return liste.stream().map(s -> new SessionResumee(s, caisses.get(s.pointDeVenteId()),
                s.caissierId() == null ? null : noms.computeIfAbsent(s.caissierId(), id -> habilitations.nomComplet(id).orElse(null)),
                totaux.getOrDefault(s.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public RapportZ rapport(UUID sessionId) {
        var session = sessions.findById(sessionId).orElseThrow(() -> new RessourceIntrouvableException("Session de caisse introuvable."));
        acces.verifier(session.etablissementId());
        var pdv = pointsDeVente.findById(session.pointDeVenteId()).orElseThrow();
        int nbVentes = 0;
        int nbRetours = 0;
        long totalVentes = 0;
        long totalRetours = 0;
        long especes = 0;
        long mobile = 0;
        long carte = 0;
        long taxes = 0;
        long remises = 0;
        for (var v : ventes.findBySessionIdOrderByHorodatageAsc(sessionId)) {
            int signe = v.type() == Vente.Type.VENTE ? 1 : -1;
            if (signe > 0) {
                nbVentes++;
                totalVentes += v.totalTtc();
            } else {
                nbRetours++;
                totalRetours += v.totalTtc();
            }
            taxes += signe * v.totalTaxes();
            remises += signe * v.remise();
            for (var e : v.encaissements()) {
                switch (e.moyen()) {
                    case ESPECES -> especes += signe * e.montant();
                    case MOBILE_MONEY -> mobile += signe * e.montant();
                    case CARTE -> carte += signe * e.montant();
                    default -> throw new IllegalStateException();
                }
            }
        }
        return new RapportZ(session, pdv.nom(), nbVentes, totalVentes, nbRetours, totalRetours, especes, mobile, carte, taxes, remises,
                caisse.especesTheoriques(session));
    }
}
