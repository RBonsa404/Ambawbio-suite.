package bf.ambawbio.socle.parametrage;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface BaremeDepot extends JpaRepository<Bareme, UUID> {

    Optional<Bareme> findFirstByCodeAndDateEffetLessThanEqualOrderByDateEffetDesc(String code, LocalDate date);

    List<Bareme> findByCodeOrderByDateEffetDesc(String code);

    boolean existsByCodeAndDateEffet(String code, LocalDate dateEffet);

    List<Bareme> findAllByOrderByCodeAscDateEffetDesc();
}
