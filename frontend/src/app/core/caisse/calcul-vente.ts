/**
 * Calcul d'une ligne de caisse (guide §11.3), identique à CalculVente.java : taxe par ligne, arrondi au franc
 * demi supérieur, en arithmétique entière (jamais de nombres à virgule pour les montants).
 * Quantités : chaînes décimales à 3 chiffres au plus ; taux : pourcentage (« 18 », « 18.0000 »).
 */
export interface MontantsLigne {
  ht: number;
  taxe: number;
  ttc: number;
}

/** Quantité en millièmes (« 0,5 » → 500). */
export function enMilliemes(quantite: string): number {
  const [entier, decimales = ''] = quantite.trim().replace(',', '.').split('.');
  if (!/^\d+$/.test(entier) || !/^\d{0,3}$/.test(decimales)) {
    throw new Error(`Quantité invalide : ${quantite}`);
  }
  return Number(entier) * 1000 + Number(decimales.padEnd(3, '0'));
}

export function quantiteTexte(milliemes: number): string {
  const entier = Math.floor(milliemes / 1000);
  const reste = milliemes % 1000;
  return reste === 0 ? String(entier) : `${entier}.${String(reste).padStart(3, '0').replace(/0+$/, '')}`;
}

/** Taux en dix-millièmes de point (« 18 » → 180 000). */
function tauxDixMilliemes(taux: string): number {
  const [entier, decimales = ''] = taux.trim().split('.');
  return Number(entier) * 10000 + Number(decimales.slice(0, 4).padEnd(4, '0'));
}

/** n / d arrondi au plus proche, demi supérieur (n ≥ 0, d > 0). */
function diviserArrondi(n: number, d: number): number {
  return Math.floor((2 * n + d) / (2 * d));
}

export function brut(quantite: string, prixUnitaire: number): number {
  return diviserArrondi(enMilliemes(quantite) * prixUnitaire, 1000);
}

export function calculerLigne(quantite: string, prixUnitaire: number, prixTtc: boolean, remise: number, taux: string): MontantsLigne {
  const montantBrut = brut(quantite, prixUnitaire);
  if (enMilliemes(quantite) <= 0) {
    throw new Error('La quantité doit être positive.');
  }
  if (remise < 0 || remise > montantBrut) {
    throw new Error('La remise ne peut pas dépasser le montant de la ligne.');
  }
  const net = montantBrut - remise;
  const t = tauxDixMilliemes(taux);
  if (prixTtc) {
    const ht = diviserArrondi(net * 1_000_000, 1_000_000 + t);
    return { ht, taxe: net - ht, ttc: net };
  }
  const taxe = diviserArrondi(net * t, 1_000_000);
  return { ht: net, taxe, ttc: net + taxe };
}

/** Raccourcis de billets du pavé de caisse (composant pave-caisse, A-09). */
export function raccourcisBillets(total: number): number[] {
  const arrondi = (pas: number) => Math.ceil(total / pas) * pas;
  const valeurs = [...new Set([arrondi(5000), arrondi(10000), arrondi(50000)])].filter((v) => v !== total);
  while (valeurs.length < 3 && !valeurs.includes(total)) {
    valeurs.unshift(total);
  }
  return valeurs.slice(0, 3);
}
