-- Journal d'audit chaîné par entreprise (guide §6.6, RG-11, F-SOC-10). Insertion seule.
grant usage on schema audit to ${role_application};

create table audit.journal (
  id bigserial primary key,
  tenant_id uuid not null,
  horodatage timestamptz not null,
  utilisateur_id uuid,
  action text not null,
  entite text not null,
  entite_id uuid,
  avant jsonb,
  apres jsonb,
  empreinte_precedente text not null,
  empreinte text not null
);
create index on audit.journal (tenant_id, id);
create index on audit.journal (tenant_id, entite, entite_id);
select socle.activer_rls('audit.journal');

grant select, insert on audit.journal to ${role_application};
grant usage on sequence audit.journal_id_seq to ${role_application};

-- Empreinte calculée par PostgreSQL : la forme textuelle canonique de jsonb rend le calcul reproductible.
create or replace function audit.calculer_empreinte(
  precedente text, horodatage timestamptz, utilisateur_id uuid, action text,
  entite text, entite_id uuid, avant jsonb, apres jsonb) returns text
language sql immutable as $$
  select encode(sha256(convert_to(concat_ws('|',
    precedente,
    (extract(epoch from horodatage) * 1000000)::bigint::text,
    coalesce(utilisateur_id::text, ''),
    action, entite,
    coalesce(entite_id::text, ''),
    coalesce(avant::text, ''),
    coalesce(apres::text, '')), 'UTF8')), 'hex')
$$;

create or replace function audit.refuser_modification() returns trigger
language plpgsql as $$
begin
  raise exception 'Le journal d''audit est en insertion seule (RG-11)';
end
$$;

create trigger journal_insertion_seule before update or delete on audit.journal
  for each row execute function audit.refuser_modification();
