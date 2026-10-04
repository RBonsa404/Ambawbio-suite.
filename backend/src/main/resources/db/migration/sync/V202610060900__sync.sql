-- LOT 4 : moteur hors-ligne et synchronisation (guide §8) — terminaux, opérations reçues, flux de changements,
-- plages de numérotation sans chevauchement (INV-11).
create extension if not exists btree_gist with schema public;

grant usage on schema sync to ${role_application};
alter default privileges in schema sync grant select, insert, update, delete on tables to ${role_application};
alter default privileges in schema sync grant usage, select on sequences to ${role_application};

create table sync.terminal (
  id uuid primary key, tenant_id uuid not null,
  etablissement_id uuid not null,                     -- socle.etablissement (autre module : pas de clé étrangère)
  code text not null,                                 -- C01, C02… : utilisé dans les numéros hors-ligne
  nom text not null,
  statut text not null check (statut in ('EN_ATTENTE_APPAIRAGE','ACTIF','REVOQUE')),
  empreinte_code_appairage text,                      -- SHA-256 du code à usage unique
  appairage_expire_le timestamptz,
  cle_publique text,                                  -- ECDSA P-256, SubjectPublicKeyInfo en base64
  appaire_le timestamptz, revoque_le timestamptz, derniere_synchro timestamptz,
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(), cree_par uuid,
  version bigint not null default 0, champs_perso jsonb not null default '{}'::jsonb,
  unique (tenant_id, code)
);
select socle.activer_rls('sync.terminal');

create table sync.operation_recue (
  tenant_id uuid not null,
  id_operation uuid not null,
  terminal_id uuid not null references sync.terminal(id),
  type_operation text not null,
  version_schema int not null,
  charge jsonb not null,
  horodatage_local timestamptz not null,
  utilisateur_id uuid,
  signature text not null,
  statut text not null check (statut in ('RECUE','APPLIQUEE','IGNOREE_DOUBLON','EN_CONFLIT')),
  motif text,
  recue_le timestamptz not null default now(),
  primary key (tenant_id, id_operation)                -- INV-10 : idOperation unique par entreprise
);
create index on sync.operation_recue (tenant_id, statut) where statut = 'EN_CONFLIT';
select socle.activer_rls('sync.operation_recue');

create table sync.flux_changements (
  sequence bigserial primary key,
  tenant_id uuid not null,
  etablissement_id uuid,                              -- null = toute l'entreprise
  entite text not null, entite_id uuid not null,
  operation text not null check (operation in ('UPSERT','SUPPRESSION')),
  donnees jsonb,
  cree_le timestamptz not null default now()
);
create index on sync.flux_changements (tenant_id, sequence);
create index on sync.flux_changements (tenant_id, entite);
select socle.activer_rls('sync.flux_changements');

create table sync.plage_numerotation (
  id uuid primary key, tenant_id uuid not null,
  societe_id uuid not null,
  terminal_id uuid not null references sync.terminal(id),
  type_piece text not null check (type_piece in ('TICKET','FACTURE','AVOIR')),
  annee int not null,
  debut bigint not null, fin bigint not null, prochain bigint not null,
  statut text not null check (statut in ('ACTIVE','EPUISEE','CLOTUREE')),
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(), cree_par uuid,
  version bigint not null default 0, champs_perso jsonb not null default '{}'::jsonb,
  check (debut >= 1 and fin >= debut and prochain between debut and fin + 1),
  -- INV-11 : deux plages d'une même société, d'un même type et d'une même année ne se chevauchent jamais.
  exclude using gist (tenant_id with =, societe_id with =, type_piece with =, annee with =, int8range(debut, fin, '[]') with &&)
);
select socle.activer_rls('sync.plage_numerotation');

-- Opérations reçues : insertion et changement de statut uniquement, jamais de suppression par l'application.
revoke delete on sync.operation_recue from ${role_application};
