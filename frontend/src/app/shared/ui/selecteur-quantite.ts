import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';

import { Icone } from './icone';

export interface OptionConditionnement {
  code: string;
  libelle: string;
  quantite: number;
}

/** Sélecteur de quantité (+/−, cibles 48 px) avec choix du conditionnement : pièce, carton, sac… (F-STK-03). */
@Component({
  selector: 'amb-selecteur-quantite',
  imports: [Icone],
  template: `
    <div class="flex flex-wrap items-center gap-2">
      <div class="flex items-stretch border border-bordure bg-surface" role="group" [attr.aria-label]="libelle()">
        <button type="button" class="bouton-icone" [disabled]="quantite() <= minimum()" (click)="ajuster(-1)" aria-label="Diminuer">
          <amb-icone nom="minus" />
        </button>
        <output class="min-w-16 self-center px-2 text-center text-xl font-bold tabular-nums" aria-live="polite">{{ quantite() }}</output>
        <button type="button" class="bouton-icone" (click)="ajuster(1)" aria-label="Augmenter"><amb-icone nom="plus" /></button>
      </div>
      @if (conditionnements().length) {
        <select class="champ-saisie w-auto" [value]="conditionnement() ?? ''" (change)="conditionnement.set($any($event.target).value || null)"
          aria-label="Conditionnement">
          <option value="">{{ uniteDeBase() }}</option>
          @for (c of conditionnements(); track c.code) {
            <option [value]="c.code">{{ c.libelle }} ({{ c.quantite }})</option>
          }
        </select>
      }
      @if (equivalence(); as e) { <span class="text-sm text-texte-secondaire">= {{ e }} {{ uniteDeBase() }}</span> }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SelecteurQuantite {
  readonly quantite = model(1);
  readonly conditionnement = model<string | null>(null);
  readonly conditionnements = input<OptionConditionnement[]>([]);
  readonly uniteDeBase = input('pièce');
  readonly libelle = input('Quantité');
  readonly minimum = input(1);

  protected readonly equivalence = computed(() => {
    const c = this.conditionnements().find((x) => x.code === this.conditionnement());
    return c ? this.quantite() * c.quantite : null;
  });

  protected ajuster(pas: number): void {
    this.quantite.set(Math.max(this.minimum(), this.quantite() + pas));
  }
}
