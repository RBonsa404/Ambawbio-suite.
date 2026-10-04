package bf.ambawbio.conformite.api;

import java.util.UUID;

/** Pièce rejetée par l'administration fiscale : à corriger par un avoir puis une nouvelle facture. */
public record CertificationRejetee(int version, UUID documentId, String motif) {
}
