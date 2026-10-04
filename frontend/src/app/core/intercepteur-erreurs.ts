import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { Notifications } from '../shared/ui/notifications';

/**
 * Aucun blocage silencieux (brief §8.3) : abonnement suspendu → page dédiée (W-24) ; serveur injoignable ou erreur
 * inattendue → notification explicite. Les erreurs métier (422, 400) restent gérées par l'écran qui les affiche au champ.
 */
export const intercepteurErreurs: HttpInterceptorFn = (requete, suivant) => {
  const router = inject(Router);
  const notifications = inject(Notifications);
  return suivant(requete).pipe(
    catchError((erreur: HttpErrorResponse) => {
      const code = erreur.error?.code as string | undefined;
      if (erreur.status === 403 && code === 'ENTREPRISE_SUSPENDUE') {
        void router.navigate(['/abonnement-suspendu']);
      } else if (erreur.status === 0) {
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
