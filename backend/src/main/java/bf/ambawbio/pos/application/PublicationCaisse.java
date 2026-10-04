package bf.ambawbio.pos.application;

import java.util.LinkedHashMap;

import org.springframework.stereotype.Component;

import bf.ambawbio.pos.domaine.PointDeVente;
import bf.ambawbio.pos.domaine.SessionCaisse;
import bf.ambawbio.sync.api.FluxChangements;

/**
 * Publie vers les terminaux de l'établissement les points de vente et l'état des sessions (guide §8.4) : un terminal
 * voit ainsi qu'une session est déjà ouverte sur une caisse avant d'en ouvrir une autre (INV-14).
 */
@Component
class PublicationCaisse {

    private final FluxChangements flux;

    PublicationCaisse(FluxChangements flux) {
        this.flux = flux;
    }

    void pointDeVente(PointDeVente p) {
        var donnees = new LinkedHashMap<String, Object>();
        donnees.put("code", p.code());
        donnees.put("nom", p.nom());
        donnees.put("seuilEcart", p.seuilEcart());
        donnees.put("comptageAveugle", p.comptageAveugle());
        donnees.put("remiseMaxPourcent", p.remiseMaxPourcent());
        donnees.put("venteSansStock", p.venteSansStock().name());
        donnees.put("actif", p.actif());
        flux.publier("point_de_vente", p.getId(), donnees, p.etablissementId());
    }

    void session(SessionCaisse s) {
        var donnees = new LinkedHashMap<String, Object>();
        donnees.put("pointDeVenteId", s.pointDeVenteId());
        donnees.put("terminalId", s.terminalId());
        donnees.put("statut", s.statut().name());
        donnees.put("ouverteLe", s.ouverteLe().toString());
        flux.publier("session_caisse", s.getId(), donnees, s.etablissementId());
    }
}
