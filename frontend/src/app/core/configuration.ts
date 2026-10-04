import { Capacitor } from '@capacitor/core';

/**
 * Configuration du client (LOT 0 : environnement local, infra/docker-compose.dev.yml).
 * Pour l'APK de démonstration sur un téléphone, remplacer HOTE_DEV par l'adresse IP du poste
 * de développement sur le réseau local (voir docs/lots/LOT-00.md). L'externalisation arrive au LOT 3.
 */
const HOTE_DEV = 'localhost';
const natif = Capacitor.isNativePlatform();

export const configuration = {
  keycloak: {
    url: `http://${HOTE_DEV}:8180`,
    realm: 'ambawbio',
    clientId: 'ambawbio-web',
  },
  /** Navigateur : relais du serveur Angular vers :8080 (proxy.conf.json). Android : appel direct au serveur. */
  api: natif ? `http://${HOTE_DEV}:8080/api` : '/api',
} as const;
