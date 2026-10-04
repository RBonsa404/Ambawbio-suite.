package bf.ambawbio.conformite.application;

import java.time.Instant;

import bf.ambawbio.conformite.api.PieceACertifier;

/** Port de certification fiscale (guide §10.3). Un adaptateur par fournisseur : simulateur, DGI (Q-01). */
public interface PortCertificationFiscale {

    ResultatCertification certifier(PieceACertifier piece) throws CertificationIndisponible;

    ResultatVerificationIfu verifierIfu(String ifu) throws CertificationIndisponible;

    sealed interface ResultatCertification permits Certifiee, Rejetee {
    }

    record Certifiee(String identifiant, String codeQr, Instant horodatage, boolean simulee) implements ResultatCertification {
    }

    record Rejetee(String motif) implements ResultatCertification {
    }

    record ResultatVerificationIfu(boolean valide, String raisonSociale) {
    }

    /** Service de certification injoignable : la pièce reste en file et sera soumise de nouveau. */
    class CertificationIndisponible extends Exception {
        public CertificationIndisponible(String message) {
            super(message);
        }
    }
}
