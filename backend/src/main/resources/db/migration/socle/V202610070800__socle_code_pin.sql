-- LOT 5 : code PIN de responsable (validation d'un écart de caisse, RG-09). Empreinte PBKDF2 salée, jamais le code.
create table socle.code_pin (
  utilisateur_id uuid primary key references socle.utilisateur(id),
  tenant_id uuid not null,
  empreinte text not null, sel text not null, iterations int not null,
  echecs int not null default 0, bloque_jusqu_a timestamptz,
  modifie_le timestamptz not null default now()
);
select socle.activer_rls('socle.code_pin');
