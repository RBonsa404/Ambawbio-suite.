// Configuration lue au démarrage (D-34). En local : valeurs ci-dessous. Sur Railway, ce fichier est régénéré au
// démarrage du conteneur à partir des variables AMBAWBIO_API_URL et AMBAWBIO_KEYCLOAK_URL (frontend/docker/).
// Pour une APK de démonstration, remplacer « localhost » par l'adresse du serveur avant « npx cap sync android ».
window.AMBAWBIO_CONFIG = {
  api: null, // null : relais du serveur Angular (/api) dans le navigateur, http://localhost:8080/api sur Android
  keycloak: 'http://localhost:8180',
};
