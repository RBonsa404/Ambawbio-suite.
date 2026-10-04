-- Avant chaque migration (Flyway, exécuté par le propriétaire des tables) : le rôle applicatif existe, sans
-- superutilisateur ni BYPASSRLS (guide §6.4), avec le mot de passe configuré pour le serveur. Ainsi le serveur démarre
-- aussi sur une base créée sans le script d'initialisation (PostgreSQL géré par l'hébergeur) ou dont le mot de passe
-- a changé depuis la création (D-39). Mot de passe vide (tests, initialisation externe) : le rôle n'est pas modifié.
do $amb$
declare
  role_app text := '${role_application}';
  mot_de_passe text := '${role_application_mot_de_passe}';
begin
  if mot_de_passe = '' then
    return;
  end if;
  if not exists (select 1 from pg_roles where rolname = role_app) then
    execute format('create role %I login password %L nosuperuser nobypassrls nocreatedb nocreaterole', role_app, mot_de_passe);
  else
    execute format('alter role %I login password %L nosuperuser nobypassrls', role_app, mot_de_passe);
  end if;
  execute format('grant connect on database %I to %I', current_database(), role_app);
end
$amb$;
