#!/bin/sh
# Premier démarrage uniquement (base vide) : rôle applicatif sans BYPASSRLS (guide §6.4) et base dédiée à Keycloak.
set -eu
: "${AMBAWBIO_BD_MOT_DE_PASSE:?Variable AMBAWBIO_BD_MOT_DE_PASSE manquante}"
: "${KEYCLOAK_BD_MOT_DE_PASSE:?Variable KEYCLOAK_BD_MOT_DE_PASSE manquante}"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<FIN
CREATE ROLE ambawbio_app LOGIN PASSWORD '${AMBAWBIO_BD_MOT_DE_PASSE}' NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
GRANT CONNECT ON DATABASE ${POSTGRES_DB} TO ambawbio_app;
CREATE USER keycloak WITH PASSWORD '${KEYCLOAK_BD_MOT_DE_PASSE}';
CREATE DATABASE keycloak OWNER keycloak;
FIN
