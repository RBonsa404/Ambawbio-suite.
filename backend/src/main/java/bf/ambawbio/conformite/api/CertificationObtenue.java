package bf.ambawbio.conformite.api;

import java.time.Instant;
import java.util.UUID;

/** Pièce certifiée (guide §6.8) ; {@code simulee} : certification du simulateur, sans valeur fiscale. */
public record CertificationObtenue(int version, UUID documentId, String identifiant, String codeQr, Instant horodatage, boolean simulee) {
}
