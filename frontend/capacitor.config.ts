import type { CapacitorConfig } from '@capacitor/cli';

// Démonstration LOT 0 sur réseau local : Keycloak et l'API sont en HTTP sur le poste de développement.
// Ces autorisations seront retirées avant toute diffusion (LOT 13, HTTPS partout).
const hoteDev = process.env['AMBAWBIO_HOTE_DEV'] ?? 'localhost';

const config: CapacitorConfig = {
  appId: 'bf.ambawbio.suite',
  appName: 'Ambawbio Suite',
  webDir: 'dist/frontend/browser',
  server: {
    androidScheme: 'https',
    cleartext: true,
    allowNavigation: [hoteDev],
  },
  android: {
    allowMixedContent: true,
  },
};

export default config;
