package bf.ambawbio.pos.application;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import bf.ambawbio.pos.domaine.SessionCaisse;

interface SessionDepot extends JpaRepository<SessionCaisse, UUID> {
    boolean existsByPointDeVenteIdAndStatut(UUID pointDeVenteId, SessionCaisse.Statut statut);

    List<SessionCaisse> findTop200ByOrderByOuverteLeDesc();
}
