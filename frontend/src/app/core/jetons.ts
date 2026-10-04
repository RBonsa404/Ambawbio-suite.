import { SecureStorage } from '@aparajita/capacitor-secure-storage';
import { Capacitor } from '@capacitor/core';

/** Jetons Keycloak conservés pour redémarrer sans réseau (D-29). */
export interface Jetons {
  token: string;
  refreshToken: string;
  idToken?: string;
}

const CLE = 'ambawbio.jetons';
const CLE_TERMINAL = 'ambawbio.terminal';

/** Un appareil de caisse (Android, ou navigateur appairé comme terminal) garde sa session pour travailler hors-ligne. */
export function estTerminal(): boolean {
  if (Capacitor.isNativePlatform()) {
    return true;
  }
  try {
    return localStorage.getItem(CLE_TERMINAL) === '1';
  } catch {
    return false;
  }
}

export function marquerTerminal(): void {
  try {
    localStorage.setItem(CLE_TERMINAL, '1');
  } catch {
    // stockage indisponible : pas de redémarrage hors-ligne
  }
}

export async function lireJetons(): Promise<Jetons | null> {
  if (!estTerminal()) {
    return null;
  }
  try {
    const texte = Capacitor.isNativePlatform() ? ((await SecureStorage.get(CLE)) as string | null) : localStorage.getItem(CLE);
    return texte ? (JSON.parse(texte) as Jetons) : null;
  } catch {
    return null;
  }
}

export async function ecrireJetons(jetons: Jetons): Promise<void> {
  if (!estTerminal()) {
    return;
  }
  const texte = JSON.stringify(jetons);
  if (Capacitor.isNativePlatform()) {
    await SecureStorage.set(CLE, texte);
  } else {
    localStorage.setItem(CLE, texte);
  }
}

export async function effacerJetons(): Promise<void> {
  if (Capacitor.isNativePlatform()) {
    await SecureStorage.remove(CLE);
  } else {
    localStorage.removeItem(CLE);
  }
}
