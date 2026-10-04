import { encoderTexte } from './escpos';
import { lignesTicket, PieceCaisse, ticketEscpos } from './ticket';

const piece: PieceCaisse = {
  venteId: 'v', type: 'VENTE', sessionId: 's', caissierId: 'c', numero: { typePiece: 'TICKET', annee: 2026, sequence: 142 },
  numeroAffiche: 'TK-C01-2026-000142', horodatage: '2026-10-04T10:47:00Z', clientId: null, factureDemandee: false,
  lignes: [
    { id: 'l1', produitId: 'p', libelle: 'Ciment CPJ 45 — sac 50 kg', conditionnement: 'sac', quantite: '3', facteur: '1', prixUnitaire: 5500, prixTtc: true,
      remise: 0, taxeCode: 'TVA18', taux: '18', montantHt: 13983, montantTaxe: 2517, montantTtc: 16500 },
    { id: 'l2', produitId: 'p2', libelle: 'Savon 400 g', conditionnement: null, quantite: '2', facteur: '1', prixUnitaire: 300, prixTtc: true,
      remise: 25, taxeCode: 'TVA18', taux: '18', montantHt: 487, montantTaxe: 88, montantTtc: 575 },
  ],
  encaissements: [{ moyen: 'ESPECES', montant: 17075, recu: 20000, rendu: 2925 }],
  totalHt: 14470, totalTaxes: 2605, totalTtc: 17075,
};

describe('Ticket de caisse ESC/POS (F-POS-05)', () => {
  it('encode les accents en PC858 et remplace les espaces insécables', () => {
    expect(encoderTexte('é à ç')).toEqual([0x82, 0x20, 0x85, 0x20, 0x87]);
    expect(encoderTexte('12\u202f500')).toEqual([...'12 500'].map((c) => c.charCodeAt(0)));
    expect(encoderTexte('ŋ')).toEqual([0x3f]);
  });

  it('met en page 32 colonnes en 58 mm sans dépasser la largeur', () => {
    const lignes = lignesTicket(piece, { entreprise: 'Quincaillerie Wend-Panga', etablissement: 'Ouaga — Zogona', caisse: 'Caisse 1', caissier: 'Awa', largeur: 32 });
    expect(lignes.every((l) => l.length <= 32)).toBe(true);
    expect(lignes).toContain('Ticket TK-C01-2026-000142');
    expect(lignes.find((l) => l.startsWith('TOTAL'))).toBe('TOTAL FCFA' + ' '.repeat(16) + '17 075');
    expect(lignes.find((l) => l.includes('dont TVA'))).toContain('2 605');
    expect(lignes.find((l) => l.startsWith('Rendu'))).toContain('2 925');
  });

  it('initialise, choisit la page de code, coupe le papier', () => {
    const octets = ticketEscpos(piece, { entreprise: 'Q', etablissement: 'E', caisse: 'C', caissier: 'A', largeur: 48 });
    expect([...octets.slice(0, 5)]).toEqual([0x1b, 0x40, 0x1b, 0x74, 19]);
    expect([...octets.slice(-4)]).toEqual([0x1d, 0x56, 66, 3]);
  });
});
