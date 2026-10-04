import { formaterFcfa, formaterTelephone } from './formats';

describe('formaterFcfa', () => {
  it('sépare les milliers par une espace insécable U+00A0 (D-05)', () => {
    expect(formaterFcfa(12500)).toBe('12\u00A0500\u00A0FCFA');
    expect(formaterFcfa(1250000)).toBe('1\u00A0250\u00A0000\u00A0FCFA');
  });

  it("n'utilise jamais l'espace fine U+202F", () => {
    expect(formaterFcfa(34600)).not.toContain('\u202F');
  });

  it('refuse un montant non entier', () => {
    expect(() => formaterFcfa(12.5)).toThrow(RangeError);
  });
});

describe('formaterTelephone', () => {
  it("ajoute l'indicatif +226", () => {
    expect(formaterTelephone('70000000')).toBe('+226 70 00 00 00');
    expect(formaterTelephone('+226 70 12 34 56')).toBe('+226 70 12 34 56');
  });

  it('refuse un numéro qui ne fait pas 8 chiffres', () => {
    expect(() => formaterTelephone('7000')).toThrow(RangeError);
  });
});
