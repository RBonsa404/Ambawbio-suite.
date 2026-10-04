package bf.ambawbio.pos.application;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import bf.ambawbio.pos.domaine.PointDeVente;

interface PointDeVenteDepot extends JpaRepository<PointDeVente, UUID> {
    List<PointDeVente> findAllByOrderByCodeAsc();

    boolean existsByCode(String code);
}
