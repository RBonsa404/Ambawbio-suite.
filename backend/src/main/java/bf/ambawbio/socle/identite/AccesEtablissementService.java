package bf.ambawbio.socle.identite;

import java.util.UUID;

import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import bf.ambawbio.socle.api.AccesEtablissement;

/** RG-14 : vérifie l'établissement à partir du profil chargé par {@link FiltreContexte} pour la requête. */
@Component
class AccesEtablissementService implements AccesEtablissement {

    static final String ATTRIBUT_PROFIL = ProfilAcces.class.getName();

    @Override
    public boolean estAutorise(UUID etablissementId) {
        var attributs = RequestContextHolder.getRequestAttributes();
        if (attributs == null) {
            return false;
        }
        var profil = (ProfilAcces) attributs.getAttribute(ATTRIBUT_PROFIL, RequestAttributes.SCOPE_REQUEST);
        return profil != null && profil.autorise(etablissementId);
    }

    @Override
    public void verifier(UUID etablissementId) {
        if (!estAutorise(etablissementId)) {
            throw new AuthorizationDeniedException("Établissement non autorisé");
        }
    }
}
