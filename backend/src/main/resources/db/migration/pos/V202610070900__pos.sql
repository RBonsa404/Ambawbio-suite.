-- LOT 5 : caisse hors-ligne (F-POS-01 à 07) — points de vente, sessions (INV-14), ventes et retours, encaissements.
-- Les ventes et retours sont créés par les terminaux (opérations synchronisées) : créations uniquement, jamais modifiés.
grant usage on schema pos to ${role_application};
alter default privileges in schema pos grant select, insert, update, delete on tables to ${role_application};

create table pos.point_de_vente (
  id uuid primary key, tenant_id uuid not null,
  etablissement_id uuid not null,                     -- socle.etablissement (autre module : pas de clé étrangère)
  code text not null, nom text not null,
  seuil_ecart bigint not null default 500 check (seuil_ecart >= 0),        -- RG-09, en francs
  comptage_aveugle boolean not null default true,                          -- D-02
  remise_max_pourcent int not null default 10 check (remise_max_pourcent between 0 and 100),
  vente_sans_stock text not null default 'AUTORISER_ALERTE' check (vente_sans_stock in ('AUTORISER_ALERTE','BLOQUER')), -- RG-15
  actif boolean not null default true,
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(), cree_par uuid,
  version bigint not null default 0, champs_perso jsonb not null default '{}'::jsonb,
  unique (tenant_id, code)
);
select socle.activer_rls('pos.point_de_vente');

create table pos.session_caisse (
  id uuid primary key, tenant_id uuid not null,
  point_de_vente_id uuid not null references pos.point_de_vente(id),
  etablissement_id uuid not null, terminal_id uuid not null, caissier_id uuid,
  statut text not null check (statut in ('OUVERTE','EN_CONFLIT','CLOTUREE','ECART_A_VALIDER','ECART_VALIDE')),
  ouverte_le timestamptz not null, fonds_initial bigint not null check (fonds_initial >= 0),
  cloturee_le timestamptz, especes_comptees bigint, especes_theoriques bigint, ecart bigint,
  validee_par uuid, validee_le timestamptz, motif_validation text,
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(), cree_par uuid,
  version bigint not null default 0, champs_perso jsonb not null default '{}'::jsonb
);
select socle.activer_rls('pos.session_caisse');
-- INV-14 : une seule session ouverte par point de vente.
create unique index session_ouverte_unique on pos.session_caisse (tenant_id, point_de_vente_id) where statut = 'OUVERTE';

create table pos.vente (
  id uuid primary key, tenant_id uuid not null,
  session_id uuid not null references pos.session_caisse(id),
  societe_id uuid not null, etablissement_id uuid not null, terminal_id uuid not null, caissier_id uuid,
  type text not null check (type in ('VENTE','RETOUR')),
  type_piece text not null, annee int not null, sequence bigint not null, numero text not null,
  horodatage timestamptz not null,
  client_id uuid, vente_origine_id uuid references pos.vente(id),
  total_ht bigint not null, total_taxes bigint not null, total_ttc bigint not null check (total_ttc >= 0),
  remise bigint not null default 0,
  facture_demandee boolean not null default false,
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(), cree_par uuid,
  version bigint not null default 0, champs_perso jsonb not null default '{}'::jsonb,
  unique (tenant_id, societe_id, type_piece, annee, sequence)                 -- INV-02
);
select socle.activer_rls('pos.vente');
create index vente_session on pos.vente (session_id);

create table pos.ligne_vente (
  id uuid primary key, tenant_id uuid not null,
  vente_id uuid not null references pos.vente(id),
  rang int not null, produit_id uuid not null, libelle text not null,
  conditionnement text, quantite numeric(18,3) not null check (quantite > 0),
  facteur numeric(18,3) not null default 1, quantite_unite_stock numeric(18,3) not null,
  prix_unitaire bigint not null check (prix_unitaire >= 0), prix_ttc boolean not null,
  remise bigint not null default 0 check (remise >= 0),
  taxe_code text, taux numeric(7,4) not null default 0,
  montant_ht bigint not null, montant_taxe bigint not null, montant_ttc bigint not null,
  ligne_origine_id uuid,
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(), cree_par uuid,
  version bigint not null default 0, champs_perso jsonb not null default '{}'::jsonb
);
select socle.activer_rls('pos.ligne_vente');
create index ligne_vente_vente on pos.ligne_vente (vente_id);

create table pos.encaissement (
  id uuid primary key, tenant_id uuid not null,
  vente_id uuid not null references pos.vente(id),
  moyen text not null check (moyen in ('ESPECES','MOBILE_MONEY','CARTE')),
  montant bigint not null check (montant > 0),              -- montant affecté à la vente (rendu déduit)
  recu bigint, rendu bigint,                                 -- espèces
  operateur text, reference text,                            -- Mobile Money saisi (secours, LOT 7 pour l'agrégateur)
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(), cree_par uuid,
  version bigint not null default 0, champs_perso jsonb not null default '{}'::jsonb
);
select socle.activer_rls('pos.encaissement');
create index encaissement_vente on pos.encaissement (vente_id);

-- Ventes et retours : pièces de caisse non modifiables ni supprimables (INV-01).
revoke update, delete on pos.vente, pos.ligne_vente, pos.encaissement from ${role_application};
