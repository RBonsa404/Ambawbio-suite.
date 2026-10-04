-- Base dédiée à Keycloak dans le même serveur PostgreSQL (développement uniquement).
CREATE USER keycloak WITH PASSWORD 'keycloak-dev';
CREATE DATABASE keycloak OWNER keycloak;
