import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { configuration } from './configuration';
import { Contexte } from './contexte';

const CLE_ETABLISSEMENT = 'ambawbio.etablissement';

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
      this.contexte.set(await firstValueFrom(this.http.get<Contexte>(`${configuration.api}/v1/socle/contexte`)));
      this.erreur.set(null);
      this.http.post(`${configuration.api}/v1/socle/connexions`, null).subscribe({ error: () => undefined });
    } catch (e) {
      const erreur = (e as { error?: { code?: string; detail?: string } }).error;
      this.erreur.set(erreur ?? { detail: undefined });
    }
  }

  choisirEtablissement(id: string): void {
    this.etablissementChoisi.set(id);
    try {
      localStorage.setItem(CLE_ETABLISSEMENT, id);
    } catch {
      // Stockage indisponible (navigation privée) : le choix vaut pour la session.
    }
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
