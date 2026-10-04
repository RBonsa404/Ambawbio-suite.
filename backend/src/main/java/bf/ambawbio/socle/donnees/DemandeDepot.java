package bf.ambawbio.socle.donnees;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface DemandeDepot extends JpaRepository<DemandeDonneesPersonnelles, UUID> {
    List<DemandeDonneesPersonnelles> findAllByOrderByEcheanceAsc();
}
