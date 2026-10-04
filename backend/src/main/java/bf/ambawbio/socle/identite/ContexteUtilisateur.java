package bf.ambawbio.socle.identite;

import org.springframework.stereotype.Component;

import bf.ambawbio.shared.tenant.ContexteTenant;

/** Expressions de sécurité : l'utilisateur appartient-il à une entreprise ? */
@Component("contexteUtilisateur")
class ContexteUtilisateur {

    public boolean estRattache() {
        return ContexteTenant.tenantCourant().isPresent() && ContexteTenant.utilisateurCourant().isPresent();
    }
}
