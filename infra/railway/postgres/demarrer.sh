#!/bin/sh
# Démarre PostgreSQL puis réapplique, à chaque démarrage, les comptes et mots de passe des variables du service.
# Changer un mot de passe dans Railway suffit donc : plus besoin de vider le volume (le script d'initialisation
# officiel ne s'exécute qu'une fois, sur un volume vide).
set -eu
: "${AMBAWBIO_BD_MOT_DE_PASSE:?Variable AMBAWBIO_BD_MOT_DE_PASSE manquante}"
: "${KEYCLOAK_BD_MOT_DE_PASSE:?Variable KEYCLOAK_BD_MOT_DE_PASSE manquante}"
: "${POSTGRES_PASSWORD:?Variable POSTGRES_PASSWORD manquante}"

docker-entrypoint.sh postgres "$@" &
pid=$!
trap 'kill -TERM "$pid" 2>/dev/null' TERM INT

# Le serveur définitif écoute en TCP ; le serveur temporaire de la première initialisation, non.
until pg_isready -q -h 127.0.0.1 -p 5432 -U "${POSTGRES_USER:-postgres}"; do
  kill -0 "$pid" 2>/dev/null || { wait "$pid"; exit $?; }
  sleep 1
done

# Connexion locale par socket (authentification « trust » posée par initdb) : fonctionne même si un mot de passe a changé.
psql -v ON_ERROR_STOP=1 -q -h /var/run/postgresql -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-postgres}" \
  -v mdp_proprietaire="$POSTGRES_PASSWORD" -v mdp_app="$AMBAWBIO_BD_MOT_DE_PASSE" -v mdp_keycloak="$KEYCLOAK_BD_MOT_DE_PASSE" <<'FIN'
select format('alter role %I password %L', current_user, :'mdp_proprietaire') \gexec
select 'create role ambawbio_app login' where not exists (select 1 from pg_roles where rolname = 'ambawbio_app') \gexec
select format('alter role ambawbio_app login password %L nosuperuser nobypassrls nocreatedb nocreaterole', :'mdp_app') \gexec
select format('grant connect on database %I to ambawbio_app', current_database()) \gexec
select 'create role keycloak login' where not exists (select 1 from pg_roles where rolname = 'keycloak') \gexec
select format('alter role keycloak login password %L', :'mdp_keycloak') \gexec
select 'create database keycloak owner keycloak' where not exists (select 1 from pg_database where datname = 'keycloak') \gexec
FIN
echo "Comptes ambawbio, ambawbio_app et keycloak synchronisés avec les variables du service."

wait "$pid"
