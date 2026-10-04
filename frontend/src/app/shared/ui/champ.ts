import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Champ de formulaire : libellé visible au-dessus, aide, message d'erreur explicite (« ce qui ne va pas et quoi faire »).
 * Le contrôle (input, select, textarea, amb-champ-montant…) est projeté ; son id doit valoir `pour`.
 */
@Component({
  selector: 'amb-champ',
  template: `
    <label class="champ-libelle" [attr.for]="pour()">
      {{ libelle() }} @if (obligatoire()) { <span aria-hidden="true" class="text-danger">*</span> }
    </label>
    <ng-content />
    @if (erreur()) {
      <p class="champ-erreur" [id]="pour() + '-erreur'" role="alert">{{ erreur() }}</p>
    } @else if (aide()) {
      <p class="champ-aide" [id]="pour() + '-aide'">{{ aide() }}</p>
    }
  `,
  host: { class: 'flex flex-col gap-1' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Champ {
  readonly libelle = input.required<string>();
  readonly pour = input.required<string>();
  readonly aide = input<string | null>(null);
  readonly erreur = input<string | null>(null);
  readonly obligatoire = input(false);
}
