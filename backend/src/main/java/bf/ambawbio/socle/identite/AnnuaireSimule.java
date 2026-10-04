package bf.ambawbio.socle.identite;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Simulateur de l'annuaire (R-10) : comptes en mémoire. Actif si {@code ambawbio.annuaire.mode=simule}. */
@Component
@ConditionalOnProperty(name = "ambawbio.annuaire.mode", havingValue = "simule")
public class AnnuaireSimule implements PortAnnuaire {

    public record Compte(NouveauCompte informations, boolean actif, Set<String> roles) {
    }

    private final Map<String, Compte> comptes = new ConcurrentHashMap<>();

    @Override
    public String creerCompte(NouveauCompte compte) {
        var id = UUID.randomUUID().toString();
        comptes.put(id, new Compte(compte, true, Set.of()));
        return id;
    }

    @Override
    public void definirActif(String keycloakId, boolean actif) {
        comptes.computeIfPresent(keycloakId, (k, c) -> new Compte(c.informations(), actif, c.roles()));
    }

    @Override
    public void synchroniserRoles(String keycloakId, Set<String> codesRoles) {
        comptes.computeIfPresent(keycloakId, (k, c) -> new Compte(c.informations(), c.actif(), Set.copyOf(codesRoles)));
    }

    public Compte compte(String keycloakId) {
        return comptes.get(keycloakId);
    }
}
