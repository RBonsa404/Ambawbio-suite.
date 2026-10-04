-- Studio : définitions des champs personnalisés (F-STU-01, UC-SOC-10). Les valeurs vivent dans champs_perso (jsonb).
create table socle.definition_champ (
  id uuid primary key,
  tenant_id uuid not null references socle.entreprise(id),
  entite text not null check (entite in ('produit','tiers')),
  code text not null check (code ~ '^[a-z][a-z0-9_]{1,39}$'),
  libelle text not null,
  type text not null check (type in ('TEXTE','NOMBRE','DATE','BOOLEEN','LISTE')),
  options text[] not null default '{}',
  obligatoire boolean not null default false,
  filtrable boolean not null default false,
  ordre int not null default 0,
  cree_le timestamptz not null default now(),
  modifie_le timestamptz not null default now(),
  cree_par uuid,
  version bigint not null default 0,
  champs_perso jsonb not null default '{}'::jsonb,
  unique (tenant_id, entite, code)
);
select socle.activer_rls('socle.definition_champ');

