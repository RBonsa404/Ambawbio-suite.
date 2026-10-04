import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { Bouton } from '../../shared/ui/bouton';

/** W-24 Pages système : page introuvable, accès refusé, abonnement suspendu — chacune dit quoi faire. */
@Component({
  selector: 'amb-page-systeme',
  imports: [RouterLink, TranslocoPipe, Bouton],
  template: `
    <div class="mx-auto flex max-w-xl flex-col items-start gap-4 py-12">
      <div class="frise h-3 w-32" aria-hidden="true"></div>
      <p class="font-code text-texte-secondaire">{{ 'systeme.' + page + '.code' | transloco }}</p>
      <h1 class="titre-page">{{ 'systeme.' + page + '.titre' | transloco }}</h1>
      <p class="text-lg text-texte-secondaire">{{ 'systeme.' + page + '.message' | transloco }}</p>
      <a ambBouton variante="principal" routerLink="/">{{ 'systeme.retourAccueil' | transloco }}</a>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PageSysteme {
  protected readonly page = (inject(ActivatedRoute).snapshot.data['page'] as string) ?? 'introuvable';
}
