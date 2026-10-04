import { LocalStore } from './local-store';

const CLE = 'cles-terminal';

/**
 * Clés ECDSA P-256 du terminal (guide §8.2) : la clé privée est créée non exportable par WebCrypto et conservée
 * dans le stockage local (IndexedDB conserve l'objet CryptoKey sans jamais exposer la clé).
 */
export class ClesTerminal {
  constructor(private readonly store: LocalStore) {}

  async creer(): Promise<string> {
    const paire = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, false, ['sign', 'verify']);
    await this.store.ecrireMeta(CLE, paire);
    const spki = await crypto.subtle.exportKey('spki', paire.publicKey);
    return enBase64(new Uint8Array(spki));
  }

  /** Signature de {@code idOperation|type|horodatageLocal|sha256hex(charge)}, format r‖s (P1363) attendu par le serveur. */
  async signer(idOperation: string, type: string, horodatageLocal: string, charge: string): Promise<string> {
    const paire = await this.store.lireMeta<CryptoKeyPair>(CLE);
    if (!paire) {
      throw new Error("Ce terminal n'est pas appairé.");
    }
    const message = `${idOperation}|${type}|${horodatageLocal}|${await sha256Hex(charge)}`;
    const signature = await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, paire.privateKey, new TextEncoder().encode(message));
    return enBase64(new Uint8Array(signature));
  }
}

export async function sha256Hex(texte: string): Promise<string> {
  const empreinte = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(texte));
  return [...new Uint8Array(empreinte)].map((o) => o.toString(16).padStart(2, '0')).join('');
}

function enBase64(octets: Uint8Array): string {
  let binaire = '';
  octets.forEach((o) => (binaire += String.fromCharCode(o)));
  return btoa(binaire);
}
