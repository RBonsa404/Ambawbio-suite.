package bf.ambawbio.pos.application;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import bf.ambawbio.pos.domaine.Vente;

interface VenteDepot extends JpaRepository<Vente, UUID> {
    boolean existsBySocieteIdAndTypePieceAndAnneeAndSequence(UUID societeId, String typePiece, int annee, long sequence);

    List<Vente> findBySessionIdOrderByHorodatageAsc(UUID sessionId);

    List<Vente> findByVenteOrigineId(UUID venteOrigineId);

    /** Ventes nettes (ventes − retours) par session : [sessionId, total]. */
    @Query("select v.sessionId, sum(case when v.type = bf.ambawbio.pos.domaine.Vente.Type.VENTE then v.totalTtc else -v.totalTtc end) "
            + "from Vente v where v.sessionId in :sessions group by v.sessionId")
    List<Object[]> totauxParSession(java.util.Collection<UUID> sessions);
}
