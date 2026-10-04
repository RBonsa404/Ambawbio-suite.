-- LOT 1 : fondations du socle — multi-tenant (RLS), entreprise, sociétés, établissements, dépôts, modules actifs.
-- L'application se connecte avec le rôle ${role_application}, sans BYPASSRLS et non propriétaire des tables (guide §6.4).

grant usage on schema socle to ${role_application};
grant select, insert, update, delete on socle.event_publication to ${role_application};
alter default privileges in schema socle grant select, insert, update, delete on tables to ${role_application};
alter default privileges in schema socle grant usage, select on sequences to ${role_application};

-- Politique RLS type (guide §7.3), appelée pour chaque nouvelle table métier.
create or replace function socle.activer_rls(nom_table text) returns void
language plpgsql as $$
begin
  execute format('alter table %s enable row level security', nom_table);
  execute format('alter table %s force row level security', nom_table);
  execute format('create policy isolation_tenant on %s '
    'using (tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::uuid) '
    'with check (tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::uuid)', nom_table);
end
$$;

create table socle.entreprise (
  id uuid primary key,
  tenant_id uuid not null,
  nom text not null,
  ifu text,
  rccm text,
  adresse text,
  ville text,
  telephone text,
  courriel text,
  pack text not null check (pack in ('ESSENTIEL','BUSINESS','ENTERPRISE','INSTITUTION')),
  statut text not null check (statut in ('ACTIVE','SUSPENDUE')),
  cree_le timestamptz not null default now(),
  modifie_le timestamptz not null default now(),
  cree_par uuid,
  version bigint not null default 0,
  champs_perso jsonb not null default '{}'::jsonb,
  check (tenant_id = id)
);
select socle.activer_rls('socle.entreprise');
-- L'éditeur (rôle admin-plateforme) liste et administre les entreprises abonnées (UC-SOC-13).
create policy acces_plateforme on socle.entreprise
  using (current_setting('app.plateforme', true) = 'oui');

create table socle.societe (
  id uuid primary key,
  tenant_id uuid not null references socle.entreprise(id),
  nom text not null,
  ifu text,
  rccm text,
  regime_fiscal text,
  adresse text,
  principale boolean not null default false,
  cree_le timestamptz not null default now(),
  modifie_le timestamptz not null default now(),
  cree_par uuid,
  version bigint not null default 0,
  champs_perso jsonb not null default '{}'::jsonb
);
create index on socle.societe (tenant_id);
create unique index societe_principale_unique on socle.societe (tenant_id) where principale;
select socle.activer_rls('socle.societe');

create table socle.etablissement (
  id uuid primary key,
  tenant_id uuid not null references socle.entreprise(id),
  societe_id uuid not null references socle.societe(id),
  code text not null,
  nom text not null,
  adresse text,
  ville text,
  cree_le timestamptz not null default now(),
  modifie_le timestamptz not null default now(),
  cree_par uuid,
  version bigint not null default 0,
  champs_perso jsonb not null default '{}'::jsonb,
  unique (tenant_id, code)
);
select socle.activer_rls('socle.etablissement');

create table socle.depot (
  id uuid primary key,
  tenant_id uuid not null references socle.entreprise(id),
  etablissement_id uuid not null references socle.etablissement(id),
  code text not null,
  nom text not null,
  cree_le timestamptz not null default now(),
  modifie_le timestamptz not null default now(),
  cree_par uuid,
  version bigint not null default 0,
  champs_perso jsonb not null default '{}'::jsonb,
  unique (tenant_id, code)
);
select socle.activer_rls('socle.depot');

create table socle.module_active (
  tenant_id uuid not null references socle.entreprise(id),
  module text not null,
  active_le timestamptz not null default now(),
  primary key (tenant_id, module)
);
select socle.activer_rls('socle.module_active');

-- Identité : utilisateurs (liés à Keycloak), rôles et permissions « module:action », affectations par établissement.
create table socle.utilisateur (
  id uuid primary key,
  tenant_id uuid not null references socle.entreprise(id),
  keycloak_id text not null,
  nom_utilisateur text not null,
  prenom text,
  nom text,
  courriel text not null,
  telephone text,
  actif boolean not null default true,
  cree_le timestamptz not null default now(),
  modifie_le timestamptz not null default now(),
  cree_par uuid,
  version bigint not null default 0,
  champs_perso jsonb not null default '{}'::jsonb,
  unique (tenant_id, keycloak_id),
  unique (tenant_id, courriel)
);
select socle.activer_rls('socle.utilisateur');

create table socle.role (
  id uuid primary key,
  tenant_id uuid not null references socle.entreprise(id),
  code text not null,
  libelle text not null,
  permissions text[] not null default '{}',
  systeme boolean not null default false,
  cree_le timestamptz not null default now(),
  modifie_le timestamptz not null default now(),
  cree_par uuid,
  version bigint not null default 0,
  champs_perso jsonb not null default '{}'::jsonb,
  unique (tenant_id, code)
);
select socle.activer_rls('socle.role');

create table socle.affectation_role (
  id uuid primary key,
  tenant_id uuid not null references socle.entreprise(id),
  utilisateur_id uuid not null references socle.utilisateur(id),
  role_id uuid not null references socle.role(id),
  etablissement_id uuid references socle.etablissement(id),   -- null = tous les établissements
  cree_le timestamptz not null default now(),
  modifie_le timestamptz not null default now(),
  cree_par uuid,
  version bigint not null default 0,
  champs_perso jsonb not null default '{}'::jsonb
);
create unique index affectation_unique on socle.affectation_role
  (tenant_id, utilisateur_id, role_id, coalesce(etablissement_id, '00000000-0000-0000-0000-000000000000'::uuid));
select socle.activer_rls('socle.affectation_role');

-- Paramétrage : barèmes versionnés par date d'effet (RG-10, INV-13 : un seul barème en vigueur par code et par date).
create table socle.bareme (
  id uuid primary key,
  tenant_id uuid not null references socle.entreprise(id),
  code text not null,
  date_effet date not null,
  valeur jsonb not null,
  description text,
  a_valider boolean not null default false,
  cree_le timestamptz not null default now(),
  modifie_le timestamptz not null default now(),
  cree_par uuid,
  version bigint not null default 0,
  champs_perso jsonb not null default '{}'::jsonb,
  unique (tenant_id, code, date_effet)
);
select socle.activer_rls('socle.bareme');

-- Données personnelles (F-SOC-18, UC-SOC-11) : demandes d'accès, de rectification et d'effacement.
create table socle.demande_donnees_personnelles (
  id uuid primary key,
  tenant_id uuid not null references socle.entreprise(id),
  type text not null check (type in ('ACCES','RECTIFICATION','EFFACEMENT','OPPOSITION')),
  personne_concernee text not null,
  contact text not null,
  description text,
  statut text not null check (statut in ('RECUE','EN_COURS','TRAITEE','REFUSEE')),
  echeance date not null,
  reponse text,
  traitee_le timestamptz,
  cree_le timestamptz not null default now(),
  modifie_le timestamptz not null default now(),
  cree_par uuid,
  version bigint not null default 0,
  champs_perso jsonb not null default '{}'::jsonb
);
select socle.activer_rls('socle.demande_donnees_personnelles');

-- Verrous des traitements planifiés (ShedLock) : une seule exécution avec plusieurs instances.
create table socle.shedlock (
  name varchar(64) primary key,
  lock_until timestamp not null,
  locked_at timestamp not null,
  locked_by varchar(255) not null
);
