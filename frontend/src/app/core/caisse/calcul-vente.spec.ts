import { brut, calculerLigne, enMilliemes, quantiteTexte, raccourcisBillets } from './calcul-vente';

describe('Calcul de ligne (guide §11.3, mêmes valeurs que CalculVente.java)', () => {
  it('prix TTC : HT et TVA déduits, arrondi au franc', () => {
    expect(calculerLigne('3', 5500, true, 0, '18')).toEqual({ ht: 13983, taxe: 2517, ttc: 16500 });
    expect(calculerLigne('1', 17500, true, 0, '18.0000')).toEqual({ ht: 14831, taxe: 2669, ttc: 17500 });
  });

  it('prix HT avec quantité décimale et remise', () => {
    expect(calculerLigne('0.5', 17500, false, 250, '18')).toEqual({ ht: 8500, taxe: 1530, ttc: 10030 });
  });

  it('exonéré, conditionnement de 40 et demi-franc arrondi au supérieur', () => {
    expect(calculerLigne('40', 300, true, 0, '0')).toEqual({ ht: 12000, taxe: 0, ttc: 12000 });
    expect(brut('0.333', 1500)).toBe(500); // 499,5 → 500
  });

  it('refuse une remise supérieure à la ligne et une quantité invalide', () => {
    expect(() => calculerLigne('1', 1000, true, 1001, '18')).toThrow();
    expect(() => enMilliemes('1,2345')).toThrow();
    expect(quantiteTexte(enMilliemes('2,50'))).toBe('2.5');
  });

  it('raccourcis de billets au-dessus du total', () => {
    expect(raccourcisBillets(34600)).toEqual([35000, 40000, 50000]);
    expect(raccourcisBillets(50000)).toEqual([50000]);
    expect(raccourcisBillets(4000)).toEqual([5000, 10000, 50000]);
  });
});
