package bf.ambawbio.socle.identite;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Connexion à l'API d'administration de Keycloak ({@code ambawbio.annuaire.*}). */
@ConfigurationProperties("ambawbio.annuaire")
record ProprietesAnnuaire(String mode, String url, String royaume, String clientId, String secret) {
}
