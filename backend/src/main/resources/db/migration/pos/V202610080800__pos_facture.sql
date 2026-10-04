-- LOT 6 : numéro de facture attribué hors-ligne par le terminal quand le client demande une facture (RG-03).
alter table pos.vente add column numero_facture text;
