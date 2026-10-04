// Formats locaux (guide §9.2). Séparateur des milliers : espace insécable U+00A0 (décision D-05),
// car U+202F (produite par Intl en fr-FR) manque dans nos polices et sur les imprimantes thermiques.
const ESPACE_FINE = /\u202F/g;
const ESPACE_INSECABLE = '\u00A0';
const nombres = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 });

/** 12500 → « 12 500 FCFA » (montant entier en francs CFA, sans décimales). */
export function formaterFcfa(montant: number): string {
  return `${formaterNombre(montant)}${ESPACE_INSECABLE}FCFA`;
}

/** 12500 → « 12 500 » (montant entier, sans unité : affichages de caisse). */
export function formaterNombre(montant: number): string {
  if (!Number.isSafeInteger(montant)) {
    throw new RangeError(`Montant FCFA invalide : ${montant}`);
  }
  return nombres.format(montant).replace(ESPACE_FINE, ESPACE_INSECABLE);
}

/** Numéro burkinabè saisi sur 8 chiffres → « +226 70 00 00 00 ». */
export function formaterTelephone(numero: string): string {
  const chiffres = numero.replace(/\D/g, '').replace(/^226(?=\d{8}$)/, '');
  if (!/^\d{8}$/.test(chiffres)) {
    throw new RangeError(`Numéro de téléphone invalide : ${numero}`);
  }
  return `+226 ${chiffres.match(/\d{2}/g)!.join(' ')}`;
}
