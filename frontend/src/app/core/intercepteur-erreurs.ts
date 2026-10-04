import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import Keycloak from 'keycloak-js';
import { catchError, throwError } from 'rxjs';

import { effacerJetons } from './jetons';

import { Notifications } from '../shared/ui/notifications';

/**
 * Aucun blocage silencieux (brief §8.3) : abonnement suspendu → page dédiée (W-24) ; serveur injoignable ou erreur
 * inattendue → notification explicite. Les erreurs métier (422, 400) restent gérées par l'écran qui les affiche au champ.
 */
export const intercepteurErreurs: HttpInterceptorFn = (requete, suivant) => {
  const router = inject(Router);
  const notifications = inject(Notifications);
  const keycloak = inject(Keycloak);
  // La synchronisation gère elle-même l'absence de réseau (réessais) : pas de notification à chaque tentative.
  const synchro = requete.url.includes('/v1/sync/');
  return suivant(requete).pipe(
    catchError((erreur: HttpErrorResponse) => {
      const code = erreur.error?.code as string | undefined;
      if (erreur.status === 403 && code === 'ENTREPRISE_SUSPENDUE') {
        void router.navigate(['/abonnement-suspendu']);
      } else if (erreur.status === 401 && navigator.onLine) {
        // Jetons conservés devenus invalides (D-29) : nouvelle connexion, les opérations locales restent sur l'appareil.
        void effacerJetons().then(() => keycloak.login());
      } else if (erreur.status === 0 && !synchro) {
        notifications.erreur('Le serveur ne répond pas. Vérifiez la connexion, puis réessayez.');
      } else if (erreur.status === 403) {
        notifications.erreur(erreur.error?.detail ?? "Vous n'avez pas le droit d'effectuer cette action.");
      } else if (erreur.status >= 500) {
        notifications.erreur('Une erreur inattendue est survenue. Réessayez ; si elle persiste, contactez le support.');
      }
      return throwError(() => erreur);
    }),
  );
};
