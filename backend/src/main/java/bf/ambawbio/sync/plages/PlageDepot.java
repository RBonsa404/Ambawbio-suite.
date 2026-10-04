package bf.ambawbio.sync.plages;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PlageDepot extends JpaRepository<PlageNumerotation, UUID> {

    List<PlageNumerotation> findByTerminalIdAndStatutOrderByTypePieceAscDebutAsc(UUID terminalId, PlageNumerotation.Statut statut);
}
