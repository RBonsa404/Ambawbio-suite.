import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import Keycloak from 'keycloak-js';

import { configuration } from '../../core/configuration';
import { Contexte } from '../../core/contexte';

type EtatServeur = 'attente' | 'ok' | 'erreur';

interface ErreurApi {
  error?: { detail?: string };
}

/** Page d'accueil du LOT 0 : prouve la chaîne Keycloak → application → API. */
@Component({
  selector: 'app-accueil',
  imports: [TranslocoPipe],
  templateUrl: './accueil.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Accueil {
  private readonly keycloak = inject(Keycloak);
  private readonly http = inject(HttpClient);

  protected readonly contexte = signal<Contexte | null>(null);
  protected readonly messageErreur = signal<string | null>(null);
  protected readonly etatServeur = signal<EtatServeur>('attente');
  protected readonly nom = computed(() => {
    const jeton = this.keycloak.tokenParsed as { name?: string; preferred_username?: string } | undefined;
    return this.contexte()?.utilisateur.nomComplet || jeton?.name || jeton?.preferred_username || '';
  });

  constructor() {
    this.http.get<Contexte>(`${configuration.api}/v1/socle/contexte`).subscribe({
      next: (c) => {
        this.contexte.set(c);
        this.etatServeur.set('ok');
        // SD-01 : la connexion est journalisée côté serveur (RG-11).
        this.http.post(`${configuration.api}/v1/socle/connexions`, null).subscribe({ error: () => undefined });
      },
      error: (e: ErreurApi) => {
        this.messageErreur.set(e.error?.detail ?? null);
        this.etatServeur.set('erreur');
      },
    });
  }

  protected seDeconnecter(): void {
    void this.keycloak.logout({ redirectUri: window.location.origin });
  }
}
