package bf.ambawbio.conformite.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import bf.ambawbio.conformite.api.PieceACertifier;

/**
 * Adaptateur de la DGI (guide §10.3) : structure seulement. Il ne sera implémenté qu'à réception du cahier des charges
 * technique de la FEC (Q-01, demande d'agrément « système de facturation commerciale certifié »). Activé par
 * {@code ambawbio.fec.adaptateur=dgi} ; en attendant, toute pièce reste en file (aucune certification inventée).
 */
@Component
@ConditionalOnProperty(name = "ambawbio.fec.adaptateur", havingValue = "dgi")
class AdaptateurDgi implements PortCertificationFiscale {

    private static final String MESSAGE = "Adaptateur DGI non disponible : spécifications officielles de la FEC non reçues (Q-01).";

    @Override
    public ResultatCertification certifier(PieceACertifier piece) throws CertificationIndisponible {
        throw new CertificationIndisponible(MESSAGE);
    }

    @Override
    public ResultatVerificationIfu verifierIfu(String ifu) throws CertificationIndisponible {
        throw new CertificationIndisponible(MESSAGE);
    }
}
