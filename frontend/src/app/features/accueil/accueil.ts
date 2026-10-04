import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import Keycloak from 'keycloak-js';

import { configuration } from '../../core/configuration';

interface UtilisateurConnecte {
  identifiant: string;
  nomUtilisateur: string;
  nomComplet: string | null;
  courriel: string | null;
  roles: string[];
}

type EtatServeur = 'attente' | 'ok' | 'erreur';

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

  protected readonly utilisateur = signal<UtilisateurConnecte | null>(null);
  protected readonly etatServeur = signal<EtatServeur>('attente');
  protected readonly nom = computed(() => {
    const jeton = this.keycloak.tokenParsed as { name?: string; preferred_username?: string } | undefined;
    return this.utilisateur()?.nomComplet ?? jeton?.name ?? jeton?.preferred_username ?? '';
  });

  constructor() {
    this.http.get<UtilisateurConnecte>(`${configuration.api}/moi`).subscribe({
      next: (u) => {
        this.utilisateur.set(u);
        this.etatServeur.set('ok');
      },
      error: () => this.etatServeur.set('erreur'),
    });
  }

  protected seDeconnecter(): void {
    void this.keycloak.logout({ redirectUri: window.location.origin });
  }
}
