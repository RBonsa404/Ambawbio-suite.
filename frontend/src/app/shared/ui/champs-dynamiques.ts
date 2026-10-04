import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { DefinitionChamp } from '../../core/api-referentiel';
import { Champ } from './champ';

/** Crée les contrôles d'un formulaire à partir des définitions du Studio (F-STU-01). */
export function controlesChamps(definitions: DefinitionChamp[], valeurs: Record<string, unknown> = {}): FormGroup {
  const groupe = new FormGroup<Record<string, FormControl<unknown>>>({});
  for (const d of definitions) {
    const valeur = valeurs[d.code] ?? (d.type === 'BOOLEEN' ? false : '');
    groupe.addControl(d.code, new FormControl<unknown>(valeur, d.obligatoire && d.type !== 'BOOLEEN' ? [Validators.required] : []));
  }
  return groupe;
}

/** Valeurs à envoyer au serveur : les champs vides sont omis (le serveur normalise et valide). */
export function valeursChamps(groupe: FormGroup): Record<string, unknown> {
  return Object.fromEntries(Object.entries(groupe.getRawValue()).filter(([, v]) => v !== '' && v !== null && v !== undefined));
}

/** Rendu dynamique des champs personnalisés dans les formulaires (texte, nombre, date, oui/non, liste). */
@Component({
  selector: 'amb-champs-dynamiques',
  imports: [ReactiveFormsModule, Champ],
  template: `
    <div class="grid gap-4 petite-tablette:grid-cols-2" [formGroup]="groupe()">
      @for (d of definitions(); track d.code) {
        @switch (d.type) {
          @case ('BOOLEEN') {
            <label class="flex min-h-12 items-center gap-3">
              <input type="checkbox" class="case" [formControlName]="d.code" [id]="'champ-' + d.code" /> {{ d.libelle }}
            </label>
          }
          @case ('LISTE') {
            <amb-champ [libelle]="d.libelle" [pour]="'champ-' + d.code" [obligatoire]="d.obligatoire">
              <select class="champ-saisie" [id]="'champ-' + d.code" [formControlName]="d.code">
                <option value="">—</option>
                @for (o of d.options; track o) { <option [value]="o">{{ o }}</option> }
              </select>
            </amb-champ>
          }
          @default {
            <amb-champ [libelle]="d.libelle" [pour]="'champ-' + d.code" [obligatoire]="d.obligatoire">
              <input class="champ-saisie" [id]="'champ-' + d.code" [formControlName]="d.code"
                [type]="d.type === 'DATE' ? 'date' : 'text'" [attr.inputmode]="d.type === 'NOMBRE' ? 'decimal' : null" />
            </amb-champ>
          }
        }
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChampsDynamiques {
  readonly definitions = input.required<DefinitionChamp[]>();
  readonly groupe = input.required<FormGroup>();
}
