import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { configuration } from './configuration';
import { Contexte } from './contexte';

const CLE_ETABLISSEMENT = 'ambawbio.etablissement';
const CLE_CONTEXTE = 'ambawbio.contexte';

/**
 * Contexte de l'utilisateur (entreprise, établissements autorisés, permissions), toujours visible dans la barre du haut
 * (brief §12). L'établissement choisi est mémorisé sur l'appareil.
 */
@Injectable({ providedIn: 'root' })
export class ServiceContexte {
  private readonly http = inject(HttpClient);

  readonly contexte = signal<Contexte | null>(null);
  readonly erreur = signal<{ code?: string; detail?: string } | null>(null);
  private readonly etablissementChoisi = signal<string | null>(lire(CLE_ETABLISSEMENT));

  readonly etablissement = computed(() => {
    const c = this.contexte();
    if (!c) {
      return null;
    }
    return c.etablissements.find((e) => e.id === this.etablissementChoisi()) ?? c.etablissements[0] ?? null;
  });

  async charger(): Promise<void> {
    try {
      const contexte = await firstValueFrom(this.http.get<Contexte>(`${configuration.api}/v1/socle/contexte`));
      this.contexte.set(contexte);
      this.erreur.set(null);
      ecrire(CLE_CONTEXTE, JSON.stringify(contexte));
      this.http.post(`${configuration.api}/v1/socle/connexions`, null).subscribe({ error: () => undefined });
    } catch (e) {
      const enCache = lire(CLE_CONTEXTE);
      if ((e as { status?: number }).status === 0 && enCache) {
        // Démarrage hors-ligne d'un terminal (D-29) : dernier contexte connu, rafraîchi au retour du réseau.
        this.contexte.set(JSON.parse(enCache) as Contexte);
        return;
      }
      const erreur = (e as { error?: { code?: string; detail?: string } }).error;
      this.erreur.set(erreur ?? { detail: undefined });
    }
  }

  choisirEtablissement(id: string): void {
    this.etablissementChoisi.set(id);
    ecrire(CLE_ETABLISSEMENT, id);
  }

  peut(permission: string): boolean {
    return this.contexte()?.permissions.includes(permission) ?? false;
  }

  moduleActif(module: string): boolean {
    return this.contexte()?.modules.includes(module) ?? false;
  }
}

function lire(cle: string): string | null {
  try {
    return localStorage.getItem(cle);
  } catch {
    return null;
  }
}

function ecrire(cle: string, valeur: string): void {
  try {
    localStorage.setItem(cle, valeur);
  } catch {
    // Stockage indisponible (navigation privée) : la valeur vaut pour la session.
  }
}
