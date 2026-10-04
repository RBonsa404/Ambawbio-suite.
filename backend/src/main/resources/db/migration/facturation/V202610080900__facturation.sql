-- LOT 6 : facturation (F-FAC-01 à 06) — factures et avoirs, numérotation sans trou (guide §6.7), immuabilité en base
-- d'une pièce validée (RG-01, INV-01), archives PDF non modifiables (F-FAC-06).
grant usage on schema facturation to ${role_application};
alter default privileges in schema facturation grant select, insert, update, delete on tables to ${role_application};

-- Compteur par société, type de pièce et exercice : attribué sous verrou (SELECT … FOR UPDATE) dans la transaction
-- de validation ; une validation annulée ne consomme pas de numéro.
create table facturation.sequence_piece (
  tenant_id uuid not null, societe_id uuid not null,
  type_piece text not null check (type_piece in ('FACTURE','AVOIR')),
  exercice int not null, prochain bigint not null default 1,
  primary key (tenant_id, societe_id, type_piece, exercice)
);
select socle.activer_rls('facturation.sequence_piece');

create table facturation.document_fiscal (
  id uuid primary key, tenant_id uuid not null,
  societe_id uuid not null, etablissement_id uuid,
  type text not null check (type in ('FACTURE','AVOIR')),
  statut text not null check (statut in ('BROUILLON','VALIDEE','PARTIELLEMENT_PAYEE','PAYEE','ANNULEE_PAR_AVOIR')),
  numero text, exercice int, sequence bigint,                       -- null tant que brouillon
  date_emission date, date_echeance date,
  client_id uuid not null,                                          -- referentiel.tiers (autre module)
  -- Mentions figées à la validation (RG-02) : la pièce archivée ne dépend plus du référentiel.
  client_nom text, client_adresse text, client_ifu text, client_rccm text, client_regime text, client_assujetti boolean,
  emetteur jsonb,
  facture_origine_id uuid references facturation.document_fiscal(id), motif text,
  total_ht bigint not null default 0, total_taxes bigint not null default 0, total_ttc bigint not null default 0,
  total_avoirs bigint not null default 0,                           -- avoirs validés (INV-04)
  origine text, origine_id uuid,                                    -- vente de caisse, commande
  valide_le timestamptz, valide_par uuid,
  fec_statut text not null default 'NON_SOUMISE' check (fec_statut in ('NON_SOUMISE','EN_FILE','CERTIFIEE','REJETEE')),
  fec_identifiant text, fec_code_qr text, fec_horodatage timestamptz, fec_simulee boolean not null default false, fec_message text,
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(), cree_par uuid,
  version bigint not null default 0, champs_perso jsonb not null default '{}'::jsonb,
  unique (tenant_id, societe_id, type, numero),                     -- INV-02
  check (statut = 'BROUILLON' or numero is not null)
);
select socle.activer_rls('facturation.document_fiscal');
create index document_client on facturation.document_fiscal (client_id);
create index document_origine on facturation.document_fiscal (facture_origine_id);

create table facturation.ligne_document (
  id uuid primary key, tenant_id uuid not null,
  document_id uuid not null references facturation.document_fiscal(id),
  rang int not null, produit_id uuid, designation text not null, unite text,
  quantite numeric(18,3) not null check (quantite > 0),
  prix_unitaire bigint not null check (prix_unitaire >= 0), prix_ttc boolean not null,
  remise bigint not null default 0 check (remise >= 0),
  taxe_code text, taux numeric(7,4) not null default 0,
  montant_ht bigint not null, montant_taxe bigint not null, montant_ttc bigint not null,
  ligne_origine_id uuid,
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(), cree_par uuid,
  version bigint not null default 0, champs_perso jsonb not null default '{}'::jsonb
);
select socle.activer_rls('facturation.ligne_document');
create index ligne_document_document on facturation.ligne_document (document_id);

-- RG-01 / INV-01 : une pièce validée n'est ni modifiable ni supprimable, même par une requête SQL de l'application.
-- Seuls évoluent ensuite : le statut de paiement / d'annulation, le cumul des avoirs et la certification.
create or replace function facturation.proteger_document() returns trigger language plpgsql as $$
begin
  if tg_op = 'DELETE' then
    if old.statut <> 'BROUILLON' then
      raise exception 'RG-01 : la pièce % est validée, elle ne peut pas être supprimée', old.numero using errcode = 'P0001';
    end if;
    return old;
  end if;
  if old.statut <> 'BROUILLON' and (
       new.statut = 'BROUILLON' or new.numero is distinct from old.numero or new.type is distinct from old.type
       or new.societe_id is distinct from old.societe_id or new.client_id is distinct from old.client_id
       or new.date_emission is distinct from old.date_emission or new.date_echeance is distinct from old.date_echeance
       or new.total_ht is distinct from old.total_ht or new.total_taxes is distinct from old.total_taxes
       or new.total_ttc is distinct from old.total_ttc or new.client_nom is distinct from old.client_nom
       or new.client_ifu is distinct from old.client_ifu or new.client_adresse is distinct from old.client_adresse
       or new.emetteur is distinct from old.emetteur or new.facture_origine_id is distinct from old.facture_origine_id
       or new.motif is distinct from old.motif or new.valide_le is distinct from old.valide_le) then
    raise exception 'RG-01 : la pièce % est validée, elle ne peut plus être modifiée (corriger par un avoir)', old.numero using errcode = 'P0001';
  end if;
  return new;
end $$;
create trigger proteger_document before update or delete on facturation.document_fiscal
  for each row execute function facturation.proteger_document();

create or replace function facturation.proteger_lignes() returns trigger language plpgsql as $$
declare
  statut_parent text;
begin
  select statut into statut_parent from facturation.document_fiscal where id = coalesce(new.document_id, old.document_id);
  if statut_parent is not null and statut_parent <> 'BROUILLON' then
    raise exception 'RG-01 : les lignes d''une pièce validée ne peuvent pas être modifiées' using errcode = 'P0001';
  end if;
  return coalesce(new, old);
end $$;
create trigger proteger_lignes before insert or update or delete on facturation.ligne_document
  for each row execute function facturation.proteger_lignes();

-- F-FAC-06 : PDF archivés (une version par génération : à la validation, puis à la certification), empreinte SHA-256.
create table facturation.archive_pdf (
  id uuid primary key, tenant_id uuid not null,
  document_id uuid not null references facturation.document_fiscal(id),
  version int not null, motif text not null, empreinte text not null, contenu bytea not null,
  genere_le timestamptz not null default now(),
  unique (document_id, version)
);
select socle.activer_rls('facturation.archive_pdf');
revoke update, delete on facturation.archive_pdf from ${role_application};
