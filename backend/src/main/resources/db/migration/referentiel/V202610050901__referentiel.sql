-- LOT 2 : référentiels partagés (F-SOC-06, F-SOC-07) — tiers, produits, catégories, unités, conditionnements,
-- codes-barres, taxes, régimes fiscaux, listes de prix. Recherche rapide par trigrammes, insensible aux accents.
-- Les extensions exigent un propriétaire autorisé à les créer (superutilisateur à l'installation).
create extension if not exists pg_trgm with schema public;
create extension if not exists unaccent with schema public;

grant usage on schema referentiel to ${role_application};
alter default privileges in schema referentiel grant select, insert, update, delete on tables to ${role_application};

create or replace function referentiel.sans_accent(texte text) returns text
language sql immutable parallel safe as $$ select lower(public.unaccent('public.unaccent'::regdictionary, coalesce(texte, ''))) $$;

create table referentiel.regime_fiscal (
  id uuid primary key, tenant_id uuid not null,
  code text not null, libelle text not null,
  assujetti_tva boolean not null, systeme boolean not null default false,
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(), cree_par uuid,
  version bigint not null default 0, champs_perso jsonb not null default '{}'::jsonb,
  unique (tenant_id, code)
);
select socle.activer_rls('referentiel.regime_fiscal');

create table referentiel.taxe (
  id uuid primary key, tenant_id uuid not null,
  code text not null, libelle text not null,
  taux numeric(7,4) not null check (taux >= 0 and taux <= 100),
  actif boolean not null default true, a_valider boolean not null default false,
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(), cree_par uuid,
  version bigint not null default 0, champs_perso jsonb not null default '{}'::jsonb,
  unique (tenant_id, code)
);
select socle.activer_rls('referentiel.taxe');

create table referentiel.unite_mesure (
  id uuid primary key, tenant_id uuid not null,
  code text not null, libelle text not null,
  categorie text not null check (categorie in ('UNITE','POIDS','VOLUME','LONGUEUR','SURFACE','TEMPS')),
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(), cree_par uuid,
  version bigint not null default 0, champs_perso jsonb not null default '{}'::jsonb,
  unique (tenant_id, code)
);
select socle.activer_rls('referentiel.unite_mesure');

-- Types de conditionnement usuels (sac, carton, bidon, palette…) proposés lors de la saisie d'un produit.
create table referentiel.type_conditionnement (
  id uuid primary key, tenant_id uuid not null,
  code text not null, libelle text not null,
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(), cree_par uuid,
  version bigint not null default 0, champs_perso jsonb not null default '{}'::jsonb,
  unique (tenant_id, code)
);
select socle.activer_rls('referentiel.type_conditionnement');

create table referentiel.categorie (
  id uuid primary key, tenant_id uuid not null,
  nom text not null, parent_id uuid references referentiel.categorie(id),
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(), cree_par uuid,
  version bigint not null default 0, champs_perso jsonb not null default '{}'::jsonb,
  unique (tenant_id, nom)
);
select socle.activer_rls('referentiel.categorie');

create table referentiel.liste_prix (
  id uuid primary key, tenant_id uuid not null,
  nom text not null, actif boolean not null default true,
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(), cree_par uuid,
  version bigint not null default 0, champs_perso jsonb not null default '{}'::jsonb,
  unique (tenant_id, nom)
);
select socle.activer_rls('referentiel.liste_prix');

create table referentiel.produit (
  id uuid primary key, tenant_id uuid not null,
  code text not null,
  nom text not null,
  type text not null check (type in ('BIEN','SERVICE')),
  categorie_id uuid references referentiel.categorie(id),
  unite_id uuid not null references referentiel.unite_mesure(id),
  taxe_id uuid not null references referentiel.taxe(id),
  prix_vente bigint not null check (prix_vente >= 0),
  prix_vente_ttc boolean not null default true,
  prix_achat bigint check (prix_achat >= 0),
  suivi_stock boolean not null default true,
  actif boolean not null default true,
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(), cree_par uuid,
  version bigint not null default 0, champs_perso jsonb not null default '{}'::jsonb,
  unique (tenant_id, code)
);
create index produit_recherche on referentiel.produit using gin (referentiel.sans_accent(nom || ' ' || code) gin_trgm_ops);
create index produit_champs on referentiel.produit using gin (champs_perso jsonb_path_ops);
create index on referentiel.produit (tenant_id, categorie_id);
select socle.activer_rls('referentiel.produit');

create table referentiel.conditionnement (
  id uuid primary key, tenant_id uuid not null,
  produit_id uuid not null references referentiel.produit(id) on delete cascade,
  code text not null, libelle text not null,
  quantite numeric(18,3) not null check (quantite > 0),          -- en unités de base du produit
  prix_vente bigint check (prix_vente >= 0),                      -- null : quantité × prix unitaire
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(), cree_par uuid,
  version bigint not null default 0, champs_perso jsonb not null default '{}'::jsonb,
  unique (tenant_id, produit_id, code)
);
select socle.activer_rls('referentiel.conditionnement');

create table referentiel.code_barre (
  id uuid primary key, tenant_id uuid not null,
  produit_id uuid not null references referentiel.produit(id) on delete cascade,
  conditionnement_code text,                                      -- null : unité de base
  valeur text not null,
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(), cree_par uuid,
  version bigint not null default 0, champs_perso jsonb not null default '{}'::jsonb,
  unique (tenant_id, valeur)
);
select socle.activer_rls('referentiel.code_barre');

create table referentiel.ligne_liste_prix (
  id uuid primary key, tenant_id uuid not null,
  liste_id uuid not null references referentiel.liste_prix(id) on delete cascade,
  produit_id uuid not null references referentiel.produit(id),
  conditionnement_code text,
  quantite_min numeric(18,3) not null default 1 check (quantite_min > 0),
  prix bigint not null check (prix >= 0),
  date_debut date, date_fin date,
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(), cree_par uuid,
  version bigint not null default 0, champs_perso jsonb not null default '{}'::jsonb,
  check (date_fin is null or date_debut is null or date_fin >= date_debut)
);
create index on referentiel.ligne_liste_prix (tenant_id, liste_id, produit_id);
select socle.activer_rls('referentiel.ligne_liste_prix');

create table referentiel.tiers (
  id uuid primary key, tenant_id uuid not null,
  code text not null,
  nom text not null,
  nature text not null check (nature in ('PARTICULIER','ENTREPRISE','ADMINISTRATION','ONG')),
  est_client boolean not null default true,
  est_fournisseur boolean not null default false,
  ifu text, rccm text,
  regime_fiscal_id uuid references referentiel.regime_fiscal(id),
  telephone text, courriel text, adresse text, ville text,
  liste_prix_id uuid references referentiel.liste_prix(id),
  delai_paiement_jours int not null default 0 check (delai_paiement_jours >= 0),
  plafond_credit bigint check (plafond_credit >= 0),
  actif boolean not null default true,
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(), cree_par uuid,
  version bigint not null default 0, champs_perso jsonb not null default '{}'::jsonb,
  unique (tenant_id, code),
  check (est_client or est_fournisseur)
);
create index tiers_recherche on referentiel.tiers using gin (referentiel.sans_accent(nom || ' ' || code || ' ' || coalesce(telephone, '') || ' ' || coalesce(ifu, '')) gin_trgm_ops);
create index tiers_champs on referentiel.tiers using gin (champs_perso jsonb_path_ops);
select socle.activer_rls('referentiel.tiers');

create table referentiel.contact (
  id uuid primary key, tenant_id uuid not null,
  tiers_id uuid not null references referentiel.tiers(id) on delete cascade,
  nom text not null, fonction text, telephone text, courriel text,
  consentement_prospection boolean not null default false,       -- F-SOC-18 : recueil explicite
  consentement_le timestamptz,
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(), cree_par uuid,
  version bigint not null default 0, champs_perso jsonb not null default '{}'::jsonb
);
select socle.activer_rls('referentiel.contact');

create table referentiel.compte_mobile_money (
  id uuid primary key, tenant_id uuid not null,
  tiers_id uuid not null references referentiel.tiers(id) on delete cascade,
  operateur text not null check (operateur in ('ORANGE_MONEY','MOOV_MONEY','WAVE')),
  numero text not null check (numero ~ '^\+226 [0-9]{2}( [0-9]{2}){3}$'),
  titulaire text,
  par_defaut boolean not null default false,
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(), cree_par uuid,
  version bigint not null default 0, champs_perso jsonb not null default '{}'::jsonb
);
select socle.activer_rls('referentiel.compte_mobile_money');

-- Imports de données (F-SOC-16, UC-SOC-06) : suivi et rapport d'erreurs ligne par ligne.
create table referentiel.import_donnees (
  id uuid primary key,
  tenant_id uuid not null ,
  type text not null,
  nom_fichier text not null,
  mode text not null check (mode in ('VERIFICATION','IMPORT')),
  statut text not null check (statut in ('REUSSI','REJETE','PARTIEL')),
  lignes_total int not null,
  lignes_importees int not null,
  erreurs jsonb not null default '[]'::jsonb,
  cree_le timestamptz not null default now(),
  modifie_le timestamptz not null default now(),
  cree_par uuid,
  version bigint not null default 0,
  champs_perso jsonb not null default '{}'::jsonb
);
create index on referentiel.import_donnees (tenant_id, cree_le desc);
select socle.activer_rls('referentiel.import_donnees');
