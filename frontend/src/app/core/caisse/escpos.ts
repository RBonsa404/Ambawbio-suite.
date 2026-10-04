/**
 * Encodeur ESC/POS minimal pour imprimantes thermiques 58 et 80 mm (F-POS-05). Page de code PC858 (Europe de l'Ouest
 * avec €), reconnue par la plupart des imprimantes compatibles Epson ; les caractères absents sont remplacés par leur
 * lettre sans accent.
 */
const ESC = 0x1b;
const GS = 0x1d;

const PC858: Record<string, number> = {
  Ç: 0x80, ü: 0x81, é: 0x82, â: 0x83, ä: 0x84, à: 0x85, ç: 0x87, ê: 0x88, ë: 0x89, è: 0x8a, ï: 0x8b, î: 0x8c, É: 0x90,
  ô: 0x93, ö: 0x94, û: 0x96, ù: 0x97, À: 0xb7, Â: 0xb6, Ê: 0xd2, Ë: 0xd3, È: 0xd4, '€': 0xd5, Î: 0xd7, Ï: 0xd8, Ô: 0xe2,
  Ù: 0xeb, Û: 0xea, '«': 0xae, '»': 0xaf, '°': 0xf8,
};

export function encoderTexte(texte: string): number[] {
  const octets: number[] = [];
  for (const c of texte.replace(/[\u202f\u00a0]/g, ' ').replace(/[’‘]/g, "'").replace(/[—–]/g, '-')) {
    const code = c.charCodeAt(0);
    if (code >= 0x20 && code < 0x7f) {
      octets.push(code);
    } else if (PC858[c] !== undefined) {
      octets.push(PC858[c]);
    } else {
      const simple = c.normalize('NFD').replace(/[̀-ͯ]/g, '');
      octets.push(simple.length === 1 && simple.charCodeAt(0) < 0x7f ? simple.charCodeAt(0) : 0x3f);
    }
  }
  return octets;
}

export class Escpos {
  private readonly octets: number[] = [ESC, 0x40, ESC, 0x74, 19];

  texte(texte: string): this {
    this.octets.push(...encoderTexte(texte));
    return this;
  }

  ligne(texte = ''): this {
    return this.texte(texte).saut();
  }

  saut(n = 1): this {
    for (let i = 0; i < n; i++) {
      this.octets.push(0x0a);
    }
    return this;
  }

  aligner(alignement: 'gauche' | 'centre' | 'droite'): this {
    this.octets.push(ESC, 0x61, { gauche: 0, centre: 1, droite: 2 }[alignement]);
    return this;
  }

  gras(actif: boolean): this {
    this.octets.push(ESC, 0x45, actif ? 1 : 0);
    return this;
  }

  /** Double hauteur et double largeur (total, titre). */
  grand(actif: boolean): this {
    this.octets.push(GS, 0x21, actif ? 0x11 : 0x00);
    return this;
  }

  couper(): this {
    this.octets.push(GS, 0x56, 66, 3);
    return this;
  }

  octetsBruts(): Uint8Array {
    return Uint8Array.from(this.octets);
  }
}
