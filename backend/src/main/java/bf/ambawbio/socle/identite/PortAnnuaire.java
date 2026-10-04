package bf.ambawbio.socle.identite;

import java.util.Set;
import java.util.UUID;

/**
 * Annuaire des comptes (Keycloak) : création, activation, rôles du royaume (utilisés pour la MFA conditionnelle).
 * Adaptateurs : {@link AnnuaireKeycloak} (réel) et {@link AnnuaireSimule} (tests, démonstration sans Keycloak).
 */
public interface PortAnnuaire {

    record NouveauCompte(String nomUtilisateur, String prenom, String nom, String courriel, UUID tenantId) {
    }

    /** Crée le compte et envoie le courriel de définition du mot de passe ; renvoie l'identifiant Keycloak. */
    String creerCompte(NouveauCompte compte);

    void definirActif(String keycloakId, boolean actif);

    /** Aligne les rôles du royaume du compte sur les codes de rôle de l'utilisateur. */
    void synchroniserRoles(String keycloakId, Set<String> codesRoles);
}
