import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** État vide : ce qui manque et l'action pour commencer (projetée). Frise de marque, pas d'illustration générique. */
@Component({
  selector: 'amb-etat-vide',
  template: `
    <div class="flex flex-col items-center gap-3 border border-dashed border-bordure bg-surface px-6 py-10 text-center">
      <div class="frise h-2 w-24" aria-hidden="true"></div>
      <h2 class="font-titre text-xl font-extrabold">{{ titre() }}</h2>
      <p class="max-w-md text-texte-secondaire">{{ message() }}</p>
      <ng-content />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EtatVide {
  readonly titre = input.required<string>();
  readonly message = input.required<string>();
}
