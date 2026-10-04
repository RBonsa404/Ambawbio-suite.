package bf.ambawbio.facturation.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import bf.ambawbio.facturation.domaine.DocumentFiscal;

interface DocumentDepot extends JpaRepository<DocumentFiscal, UUID> {
    List<DocumentFiscal> findTop300ByOrderByCreeLeDesc();

    List<DocumentFiscal> findByFactureOrigineId(UUID factureOrigineId);

    Optional<DocumentFiscal> findFirstByOrigineAndOrigineIdAndType(String origine, UUID origineId, DocumentFiscal.Type type);
}
