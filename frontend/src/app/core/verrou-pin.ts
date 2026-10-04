import { Injectable } from '@angular/core';
import { Capacitor } from '@capacitor/core';
import { SecureStorage } from '@aparajita/capacitor-secure-storage';

const CLE = 'ambawbio.pin';
const ITERATIONS = 150_000;
const ESSAIS_MAX = 5;

interface PinEnregistre {
  sel: string;
  empreinte: string;
  essais: number;
}

/**
 * Code PIN du terminal (A-04, décision D-01) : empreinte PBKDF2-SHA-256 salée, jamais le PIN en clair.
 * Android : stockage sécurisé (Keystore) ; navigateur : stockage local (démonstration uniquement).
 * Blocage après 5 essais : il faut alors se reconnecter en ligne.
 */
@Injectable({ providedIn: 'root' })
export class VerrouPin {
  async estDefini(): Promise<boolean> {
    return (await this.lire()) !== null;
  }

  async definir(pin: string): Promise<void> {
    if (!/^\d{4,6}$/.test(pin)) {
      throw new Error('Le code PIN comporte 4 à 6 chiffres.');
    }
    const sel = crypto.getRandomValues(new Uint8Array(16));
    await this.ecrire({ sel: enBase64(sel), empreinte: await empreinte(pin, sel), essais: 0 });
  }

  /** Renvoie vrai si le PIN est correct ; lève une erreur quand le terminal est bloqué. */
  async verifier(pin: string): Promise<boolean> {
    const enregistre = await this.lire();
    if (!enregistre) {
      return false;
    }
    if (enregistre.essais >= ESSAIS_MAX) {
      throw new Error('Trop d\'essais. Reconnectez-vous avec votre identifiant et votre mot de passe.');
    }
    const correct = (await empreinte(pin, depuisBase64(enregistre.sel))) === enregistre.empreinte;
    await this.ecrire({ ...enregistre, essais: correct ? 0 : enregistre.essais + 1 });
    return correct;
  }

  async essaisRestants(): Promise<number> {
    const enregistre = await this.lire();
    return enregistre ? Math.max(0, ESSAIS_MAX - enregistre.essais) : ESSAIS_MAX;
  }

  async effacer(): Promise<void> {
    if (Capacitor.isNativePlatform()) {
      await SecureStorage.remove(CLE);
    } else {
      localStorage.removeItem(CLE);
    }
  }

  private async lire(): Promise<PinEnregistre | null> {
    const texte = Capacitor.isNativePlatform() ? ((await SecureStorage.get(CLE)) as string | null) : localStorage.getItem(CLE);
    return texte ? (JSON.parse(texte) as PinEnregistre) : null;
  }

  private async ecrire(valeur: PinEnregistre): Promise<void> {
    const texte = JSON.stringify(valeur);
    if (Capacitor.isNativePlatform()) {
      await SecureStorage.set(CLE, texte);
    } else {
      localStorage.setItem(CLE, texte);
    }
  }
}

async function empreinte(pin: string, sel: Uint8Array): Promise<string> {
  const cle = await crypto.subtle.importKey('raw', new TextEncoder().encode(pin), 'PBKDF2', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt: sel as BufferSource, iterations: ITERATIONS }, cle, 256);
  return enBase64(new Uint8Array(bits));
}

function enBase64(octets: Uint8Array): string {
  return btoa(String.fromCharCode(...octets));
}

function depuisBase64(texte: string): Uint8Array {
  return Uint8Array.from(atob(texte), (c) => c.charCodeAt(0));
}
