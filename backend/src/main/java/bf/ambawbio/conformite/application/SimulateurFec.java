package bf.ambawbio.conformite.application;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import bf.ambawbio.conformite.api.PieceACertifier;

/**
 * Simulateur de certification (guide §10.3) : identifiant {@code SIM-…}, contenu de QR code et horodatage ; peut
 * simuler l'indisponibilité ou le rejet. Les pièces qu'il certifie portent « CERTIFICATION SIMULÉE – SANS VALEUR FISCALE ».
 * Le contenu du QR code n'imite aucun format officiel (non publié, Q-01).
 */
@Component
@ConditionalOnProperty(name = "ambawbio.fec.adaptateur", havingValue = "simulateur", matchIfMissing = true)
public class SimulateurFec implements PortCertificationFiscale {

    public enum Mode { DISPONIBLE, INDISPONIBLE, REJET }

    private static final SecureRandom HASARD = new SecureRandom();
    private volatile Mode mode = Mode.DISPONIBLE;

    public Mode mode() {
        return mode;
    }

    public void changerMode(Mode mode) {
        this.mode = mode;
    }

    @Override
    public ResultatCertification certifier(PieceACertifier piece) throws CertificationIndisponible {
        switch (mode) {
            case INDISPONIBLE -> throw new CertificationIndisponible("Service de certification simulé indisponible.");
            case REJET -> {
                return new Rejetee("Rejet simulé : pièce refusée par l'administration fiscale (simulateur).");
            }
            default -> {
                var octets = new byte[4];
                HASARD.nextBytes(octets);
                var maintenant = Instant.now();
                var identifiant = "SIM-%d-%s".formatted(maintenant.atZone(ZoneOffset.UTC).getYear(), HexFormat.of().withUpperCase().formatHex(octets));
                var qr = "SIMULATION-SANS-VALEUR-FISCALE|%s|%s|%s|%d|%s"
                        .formatted(identifiant, piece.emetteurIfu(), piece.numero(), piece.totalTtc(), maintenant);
                return new Certifiee(identifiant, qr, maintenant, true);
            }
        }
    }

    @Override
    public ResultatVerificationIfu verifierIfu(String ifu) throws CertificationIndisponible {
        if (mode == Mode.INDISPONIBLE) {
            throw new CertificationIndisponible("Service de vérification simulé indisponible.");
        }
        return new ResultatVerificationIfu(ifu != null && ifu.matches("\\d{8}[A-Z]"), null);
    }
}
