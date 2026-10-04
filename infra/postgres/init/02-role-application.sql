-- Rôle de l'application : pas de superutilisateur, pas de BYPASSRLS, non propriétaire des tables (guide §6.4).
-- Les droits sur les tables sont accordés par les migrations Flyway (placeholder role_application).
CREATE ROLE ambawbio_app LOGIN PASSWORD 'ambawbio-app-dev' NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
GRANT CONNECT ON DATABASE ambawbio TO ambawbio_app;
