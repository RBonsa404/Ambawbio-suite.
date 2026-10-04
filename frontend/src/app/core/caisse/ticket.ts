import { Escpos } from './escpos';

/** Pièce de caisse telle qu'enregistrée sur le terminal (charge de VENTE_ENREGISTREE / RETOUR_ENREGISTRE). */
export interface LignePiece {
  id: string;
  produitId: string;
  libelle: string;
  conditionnement: string | null;
  quantite: string;
  facteur: string;
  prixUnitaire: number;
  prixTtc: boolean;
  remise: number;
  taxeCode: string | null;
  taux: string;
  montantHt: number;
  montantTaxe: number;
  montantTtc: number;
  ligneOrigineId?: string;
}

export interface EncaissementPiece {
  moyen: 'ESPECES' | 'MOBILE_MONEY' | 'CARTE';
  montant: number;
  recu?: number | null;
  rendu?: number | null;
  operateur?: string | null;
  reference?: string | null;
}

export interface PieceCaisse {
  venteId: string;
  type: 'VENTE' | 'RETOUR';
  sessionId: string;
  caissierId: string;
  numero: { typePiece: 'TICKET' | 'AVOIR'; annee: number; sequence: number };
  numeroAffiche: string;
  horodatage: string;
  clientId: string | null;
  factureDemandee: boolean;
  /** Numéro de facture pris hors-ligne dans la plage FACTURE du terminal (RG-03). */
  facture?: { typePiece: 'FACTURE'; annee: number; sequence: number };
  numeroFacture?: string;
  venteOrigineId?: string;
  lignes: LignePiece[];
  encaissements: EncaissementPiece[];
  totalHt: number;
  totalTaxes: number;
  totalTtc: number;
}

export interface EnteteTicket {
  entreprise: string;
  etablissement: string;
  caisse: string;
  caissier: string;
  largeur: 32 | 48;
}

const MOYENS = { ESPECES: 'Espèces', MOBILE_MONEY: 'Mobile Money', CARTE: 'Carte' } as const;

function montant(valeur: number): string {
  return new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(valeur).replace(/[\u202f\u00a0]/g, ' ');
}

function colonnes(gauche: string, droite: string, largeur: number): string {
  const espace = Math.max(1, largeur - gauche.length - droite.length);
  return gauche.length + droite.length + 1 > largeur ? `${gauche.slice(0, largeur - droite.length - 1)} ${droite}` : gauche + ' '.repeat(espace) + droite;
}

/** Lignes de texte du ticket (aperçu à l'écran et impression ; 32 colonnes en 58 mm, 48 en 80 mm). */
export function lignesTicket(piece: PieceCaisse, entete: EnteteTicket): string[] {
  const l = entete.largeur;
  const trait = '-'.repeat(l);
  const date = new Date(piece.horodatage).toLocaleString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  const sortie = [entete.entreprise, entete.etablissement, trait];
  sortie.push(piece.type === 'VENTE' ? `Ticket ${piece.numeroAffiche}` : `Retour ${piece.numeroAffiche}`);
  sortie.push(colonnes(date, entete.caisse, l), `Caissier : ${entete.caissier}`, trait);
  for (const ligne of piece.lignes) {
    sortie.push(ligne.libelle.slice(0, l));
    const detail = `  ${ligne.quantite.replace('.', ',')}${ligne.conditionnement ? ' ' + ligne.conditionnement : ''} x ${montant(ligne.prixUnitaire)}`;
    sortie.push(colonnes(detail, montant(ligne.montantTtc + ligne.remise), l));
    if (ligne.remise > 0) {
      sortie.push(colonnes('  Remise', `-${montant(ligne.remise)}`, l));
    }
  }
  sortie.push(trait, colonnes(piece.type === 'VENTE' ? 'TOTAL FCFA' : 'REMBOURSÉ FCFA', montant(piece.totalTtc), l));
  const parTaux = new Map<string, number>();
  piece.lignes.forEach((x) => parTaux.set(x.taux, (parTaux.get(x.taux) ?? 0) + x.montantTaxe));
  parTaux.forEach((taxe, taux) => sortie.push(colonnes(`  dont TVA ${Number(taux)} %`, montant(taxe), l)));
  sortie.push(trait);
  for (const e of piece.encaissements) {
    sortie.push(colonnes(MOYENS[e.moyen] + (e.operateur ? ` ${e.operateur}` : ''), montant(e.recu ?? e.montant), l));
    if (e.rendu) {
      sortie.push(colonnes('Rendu', montant(e.rendu), l));
    }
    if (e.reference) {
      sortie.push(`  Réf. ${e.reference}`);
    }
  }
  if (piece.numeroFacture) {
    sortie.push(trait, `Facture ${piece.numeroFacture}`, 'EN ATTENTE DE CERTIFICATION');
  }
  sortie.push(trait, 'Merci de votre visite', 'Édité avec Ambawbio Suite');
  return sortie;
}

/** Commandes ESC/POS : en-tête centré, total en grand, coupe du papier. */
export function ticketEscpos(piece: PieceCaisse, entete: EnteteTicket): Uint8Array {
  const lignes = lignesTicket(piece, entete);
  const e = new Escpos().aligner('centre').gras(true).ligne(lignes[0]).gras(false).ligne(lignes[1]).aligner('gauche');
  for (const ligne of lignes.slice(2)) {
    if (ligne.startsWith('TOTAL') || ligne.startsWith('REMBOURSÉ')) {
      e.gras(true).ligne(ligne).gras(false);
    } else {
      e.ligne(ligne);
    }
  }
  return e.saut(3).couper().octetsBruts();
}
