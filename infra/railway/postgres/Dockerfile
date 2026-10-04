# PostgreSQL 18 pour Railway : mêmes rôles qu'en local, mots de passe fournis par les variables du service.
FROM postgres:18-alpine
COPY infra/railway/postgres/initialiser.sh /docker-entrypoint-initdb.d/10-initialiser.sh
