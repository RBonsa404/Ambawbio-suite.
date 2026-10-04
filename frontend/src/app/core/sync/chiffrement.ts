import { SecureStorage } from '@aparajita/capacitor-secure-storage';
import { Capacitor } from '@capacitor/core';

/** Donnée chiffrée stockée : vecteur d'initialisation (12 octets) suivi du texte chiffré AES-GCM. */
export interface Chiffre {
  c: Uint8Array;
}

/**
 * Chiffrement du stockage local du terminal (guide §9.3, D-33) : AES-GCM 256 bits par enregistrement.
 * Sur Android, la clé est conservée dans le stockage sécurisé adossé au Keystore ; dans le navigateur, c'est une
 * clé WebCrypto non exportable conservée dans IndexedDB (elle ne peut pas être lue, seulement utilisée).
 */
export class Chiffreur {
  private cle: Promise<CryptoKey> | null = null;

  constructor(private readonly fournirCle: () => Promise<CryptoKey>) {}

  async chiffrer(valeur: unknown): Promise<Chiffre> {
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const clair = new TextEncoder().encode(JSON.stringify(valeur));
    const chiffre = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, await this.obtenir(), clair));
    const c = new Uint8Array(12 + chiffre.length);
    c.set(iv);
    c.set(chiffre, 12);
    return { c };
  }

  async dechiffrer<T>(valeur: Chiffre | T): Promise<T> {
    if (!estChiffre(valeur)) {
      return valeur as T; // donnée enregistrée avant le chiffrement (LOT 4)
    }
    const octets = new Uint8Array(valeur.c);
    const clair = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: octets.slice(0, 12) }, await this.obtenir(), octets.slice(12));
    return JSON.parse(new TextDecoder().decode(clair)) as T;
  }

  private obtenir(): Promise<CryptoKey> {
    this.cle ??= this.fournirCle();
    return this.cle;
  }
}

export function estChiffre(valeur: unknown): valeur is Chiffre {
  return typeof valeur === 'object' && valeur !== null && 'c' in valeur && ArrayBuffer.isView((valeur as Chiffre).c);
}

const CLE_ANDROID = 'ambawbio.cle-stockage';

/** Clé du stockage : Keystore (Android) ou clé non exportable conservée par l'appelant (navigateur). */
export async function cleStockage(lire: () => Promise<CryptoKey | undefined>, ecrire: (cle: CryptoKey) => Promise<void>): Promise<CryptoKey> {
  if (Capacitor.isNativePlatform()) {
    let brute = (await SecureStorage.get(CLE_ANDROID)) as string | null;
    if (!brute) {
      brute = btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(32))));
      await SecureStorage.set(CLE_ANDROID, brute);
    }
    const octets = Uint8Array.from(atob(brute), (c) => c.charCodeAt(0));
    return crypto.subtle.importKey('raw', octets, 'AES-GCM', false, ['encrypt', 'decrypt']);
  }
  const existante = await lire();
  if (existante) {
    return existante;
  }
  const cle = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
  await ecrire(cle);
  return cle;
}
