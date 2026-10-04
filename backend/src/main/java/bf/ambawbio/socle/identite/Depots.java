package bf.ambawbio.socle.identite;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface UtilisateurDepot extends JpaRepository<Utilisateur, UUID> {
    Optional<Utilisateur> findByKeycloakId(String keycloakId);

    Optional<Utilisateur> findByNomUtilisateur(String nomUtilisateur);

    List<Utilisateur> findAllByOrderByNomAscPrenomAsc();
}

interface RoleDepot extends JpaRepository<Role, UUID> {
    List<Role> findAllByOrderByLibelleAsc();

    Optional<Role> findByCode(String code);
}

interface AffectationDepot extends JpaRepository<AffectationRole, UUID> {
    List<AffectationRole> findByUtilisateurId(UUID utilisateurId);
}
