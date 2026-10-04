package bf.ambawbio.socle.studio;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface DefinitionChampDepot extends JpaRepository<DefinitionChamp, UUID> {

    List<DefinitionChamp> findByEntiteOrderByOrdreAscLibelleAsc(String entite);

    boolean existsByEntiteAndCode(String entite, String code);
}
