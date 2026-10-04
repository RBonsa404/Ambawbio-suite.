package bf.ambawbio.sync.terminaux;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TerminalDepot extends JpaRepository<Terminal, UUID> {

    List<Terminal> findAllByOrderByCodeAsc();

    @Query("select count(t) from Terminal t")
    long nombre();
}
