package bf.ambawbio.pos.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import bf.ambawbio.pos.application.ServiceCaisse;
import bf.ambawbio.pos.application.ServicePointsDeVente;
import bf.ambawbio.pos.domaine.PointDeVente;
import bf.ambawbio.pos.domaine.SessionCaisse;

/** Caisse en ligne (guide §12) : points de vente, sessions, rapport Z, validation d'écart. Les ventes passent par la synchronisation. */
@RestController
@RequestMapping("/api/v1/pos")
class CaisseControleur {

    record PointDeVenteVue(UUID id, UUID etablissementId, String code, String nom, long seuilEcart, boolean comptageAveugle, int remiseMaxPourcent,
            PointDeVente.VenteSansStock venteSansStock, boolean actif) {
        static PointDeVenteVue de(PointDeVente p) {
            return new PointDeVenteVue(p.getId(), p.etablissementId(), p.code(), p.nom(), p.seuilEcart(), p.comptageAveugle(), p.remiseMaxPourcent(),
                    p.venteSansStock(), p.actif());
        }
    }

    record DemandePointDeVente(UUID id, UUID etablissementId, String code, String nom, Long seuilEcart, Boolean comptageAveugle,
            Integer remiseMaxPourcent, PointDeVente.VenteSansStock venteSansStock, Boolean actif) {
        ServicePointsDeVente.Parametres parametres() {
            return new ServicePointsDeVente.Parametres(nom, seuilEcart == null ? 500 : seuilEcart, comptageAveugle == null || comptageAveugle,
                    remiseMaxPourcent == null ? 10 : remiseMaxPourcent, venteSansStock, actif == null || actif);
        }
    }

    record SessionVue(UUID id, UUID pointDeVenteId, UUID etablissementId, UUID terminalId, UUID caissierId, SessionCaisse.Statut statut, Instant ouverteLe,
            long fondsInitial, Instant clotureeLe, Long especesComptees, Long especesTheoriques, Long ecart, String motifValidation, String pointDeVente,
            String caissier, Long ventes) {
        static SessionVue de(SessionCaisse s) {
            return de(s, null, null, null);
        }

        static SessionVue de(SessionCaisse s, String pointDeVente, String caissier, Long ventes) {
            return new SessionVue(s.getId(), s.pointDeVenteId(), s.etablissementId(), s.terminalId(), s.caissierId(), s.statut(), s.ouverteLe(),
                    s.fondsInitial(), s.clotureeLe(), s.especesComptees(), s.especesTheoriques(), s.ecart(), s.motifValidation(), pointDeVente, caissier,
                    ventes);
        }
    }

    record RapportVue(SessionVue session, String pointDeVente, int nombreVentes, long totalVentes, int nombreRetours, long totalRetours,
            long especes, long mobileMoney, long carte, long taxes, long remises, long especesTheoriques) {
        static RapportVue de(ServicePointsDeVente.RapportZ r) {
            return new RapportVue(SessionVue.de(r.session()), r.pointDeVente(), r.nombreVentes(), r.totalVentes(), r.nombreRetours(), r.totalRetours(),
                    r.especes(), r.mobileMoney(), r.carte(), r.taxes(), r.remises(), r.especesTheoriques());
        }
    }

    record DemandeValidation(String responsable, String pin, String motif) {
    }

    private final ServicePointsDeVente service;
    private final ServiceCaisse caisse;

    CaisseControleur(ServicePointsDeVente service, ServiceCaisse caisse) {
        this.service = service;
        this.caisse = caisse;
    }

    @GetMapping("/points-de-vente")
    @PreAuthorize("hasAnyAuthority('pos:vendre', 'pos:parametrer')")
    List<PointDeVenteVue> pointsDeVente() {
        return service.liste().stream().map(PointDeVenteVue::de).toList();
    }

    @PostMapping("/points-de-vente")
    @PreAuthorize("hasAuthority('pos:parametrer')")
    PointDeVenteVue creer(@RequestBody DemandePointDeVente d) {
        return PointDeVenteVue.de(service.creer(d.id(), d.etablissementId(), d.code(), d.parametres()));
    }

    @PutMapping("/points-de-vente/{id}")
    @PreAuthorize("hasAuthority('pos:parametrer')")
    PointDeVenteVue modifier(@PathVariable UUID id, @RequestBody DemandePointDeVente d) {
        return PointDeVenteVue.de(service.modifier(id, d.parametres()));
    }

    @GetMapping("/sessions")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('pos:valider-ecart', 'pos:parametrer')")
    List<SessionVue> sessions() {
        return service.sessions().stream().map(r -> SessionVue.de(r.session(), r.pointDeVente(), r.caissier(), r.ventes())).toList();
    }

    @GetMapping("/sessions/{id}/rapport")
    @PreAuthorize("hasAnyAuthority('pos:cloturer', 'pos:valider-ecart')")
    RapportVue rapport(@PathVariable UUID id) {
        return RapportVue.de(service.rapport(id));
    }

    /** Le responsable s'identifie (ou est l'utilisateur connecté) et saisit son code PIN (RG-09). */
    @PostMapping("/sessions/{id}/validation-ecart")
    SessionVue validerEcart(@PathVariable UUID id, @RequestBody DemandeValidation d) {
        return SessionVue.de(caisse.validerEcart(id, d.responsable(), d.pin(), d.motif()));
    }
}
