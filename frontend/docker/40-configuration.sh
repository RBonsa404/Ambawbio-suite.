#!/bin/sh
# Génère config.js à partir des variables d'environnement du conteneur (D-34).
set -eu
: "${AMBAWBIO_API_URL:?Variable AMBAWBIO_API_URL manquante (ex. https://serveur.up.railway.app/api)}"
: "${AMBAWBIO_KEYCLOAK_URL:?Variable AMBAWBIO_KEYCLOAK_URL manquante (ex. https://connexion.up.railway.app)}"
cat > /usr/share/nginx/html/config.js <<FIN
window.AMBAWBIO_CONFIG = { api: '${AMBAWBIO_API_URL}', keycloak: '${AMBAWBIO_KEYCLOAK_URL}' };
FIN
echo "config.js : api=${AMBAWBIO_API_URL} keycloak=${AMBAWBIO_KEYCLOAK_URL}"
