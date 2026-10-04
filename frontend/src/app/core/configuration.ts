import { Capacitor } from '@capacitor/core';

/**
 * Configuration du client, lue à l'exécution dans public/config.js (D-34) : la même image sert en local, sur Railway
 * et dans l'APK ; seules les adresses changent.
 */
interface ConfigurationExecution {
  api: string | null;
  keycloak: string;
}

const execution: ConfigurationExecution =
  (globalThis as { AMBAWBIO_CONFIG?: ConfigurationExecution }).AMBAWBIO_CONFIG ?? { api: null, keycloak: 'http://localhost:8180' };
const natif = Capacitor.isNativePlatform();

export const configuration = {
  keycloak: {
    url: execution.keycloak,
    realm: 'ambawbio',
    clientId: 'ambawbio-web',
  },
  /** Navigateur en local : relais du serveur Angular vers :8080 (proxy.conf.json). */
  api: execution.api ?? (natif ? 'http://localhost:8080/api' : '/api'),
} as const;
