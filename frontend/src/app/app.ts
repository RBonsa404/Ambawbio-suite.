import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import Keycloak from 'keycloak-js';
import { KEYCLOAK_EVENT_SIGNAL, KeycloakEventType } from 'keycloak-angular';

import { ecrireJetons, effacerJetons } from './core/jetons';
import { BoiteConfirmation } from './shared/ui/confirmation';
import { ConteneurNotifications } from './shared/ui/notifications';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ConteneurNotifications, BoiteConfirmation],
  template: '<router-outlet /><amb-notifications /><amb-confirmation />',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  private readonly keycloak = inject(Keycloak);

  constructor() {
    // Jetons conservés à chaque connexion ou rafraîchissement, pour redémarrer un terminal sans réseau (D-29).
    const evenements = inject(KEYCLOAK_EVENT_SIGNAL);
    effect(() => {
      const type = evenements().type;
      if ((type === KeycloakEventType.AuthSuccess || type === KeycloakEventType.AuthRefreshSuccess || type === KeycloakEventType.Ready) &&
        this.keycloak.token && this.keycloak.refreshToken) {
        void ecrireJetons({ token: this.keycloak.token, refreshToken: this.keycloak.refreshToken, idToken: this.keycloak.idToken });
      } else if (type === KeycloakEventType.AuthLogout) {
        void effacerJetons();
      } else if (type === KeycloakEventType.Ready && !this.keycloak.authenticated && navigator.onLine) {
        // Jetons conservés devenus invalides (session révoquée, plus de 30 jours) : nouvelle connexion.
        void effacerJetons().then(() => this.keycloak.login());
      }
    });
  }
}
