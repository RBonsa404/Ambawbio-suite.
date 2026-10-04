-- LOT 6 : conformité fiscale (guide §10.3) — file de certification des factures et avoirs (F-FAC-02, F-FAC-04).
grant usage on schema conformite to ${role_application};
alter default privileges in schema conformite grant select, insert, update, delete on tables to ${role_application};

create table conformite.certification (
  document_id uuid primary key, tenant_id uuid not null,
  type_document text not null, numero text not null,
  statut text not null check (statut in ('EN_FILE','CERTIFIEE','REJETEE')),
  charge jsonb not null,                                   -- document à certifier, figé à la validation
  tentatives int not null default 0,
  soumis_le timestamptz not null default now(), prochain_essai timestamptz not null default now(),
  identifiant text, code_qr text, horodatage timestamptz, simulee boolean not null default false,
  dernier_message text, alerte boolean not null default false,   -- en file depuis plus de 24 h
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now()
);
select socle.activer_rls('conformite.certification');
create index certification_a_traiter on conformite.certification (prochain_essai) where statut = 'EN_FILE';
