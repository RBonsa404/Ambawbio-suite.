import { ChangeDetectionStrategy, Component, computed, input, model, output } from '@angular/core';

import { raccourcisBillets } from '../../core/caisse/calcul-vente';
import { NombrePipe } from './fcfa.pipe';

/** Pavé de caisse et rendu de monnaie (docs/design/composants/pave-caisse.md, A-09). */
@Component({
  selector: 'amb-pave-caisse',
  imports: [NombrePipe],
  template: `
    <div class="flex flex-col gap-3" (keydown)="clavier($event)" role="group" aria-label="Pavé de caisse" tabindex="-1">
      <label class="flex items-center justify-between border-2 border-texte bg-surface px-4 py-3">
        <span class="text-sm font-semibold">Reçu</span>
        <input class="montant-geant w-2/3 bg-transparent text-right text-3xl outline-none" inputmode="numeric" [value]="valeur() ? (valeur() | nombre) : ''"
          (input)="saisir($any($event.target))" aria-label="Montant reçu en espèces" data-testid="montant-recu" />
      </label>
      <div class="grid grid-cols-3 gap-2">
        @for (r of raccourcis(); track r) {
          <button type="button" class="min-h-11 border-2 border-texte bg-surface font-bold tabular-nums" (click)="valeur.set(r)">{{ r | nombre }}</button>
        }
      </div>
      <div class="grid grid-cols-3 gap-1.5">
        @for (t of touches; track t) {
          <button type="button" class="touche-caisse" (click)="taper(t)" [attr.aria-label]="t === '⌫' ? 'Effacer' : t">{{ t }}</button>
        }
      </div>
      <div class="bloc-rendu" [class.bloc-rendu-manque]="manque() > 0" role="status" aria-live="polite">
        <span class="font-semibold">{{ manque() > 0 ? 'Il manque' : 'Rendu' }}</span>
        <span class="montant-geant text-4xl" data-testid="rendu">{{ (manque() > 0 ? manque() : rendu()) | nombre }} F</span>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaveCaisse {
  readonly totalAPayer = input.required<number>();
  readonly valeur = model(0);
  readonly max = input(9_999_999);
  readonly valider = output<void>();

  protected readonly touches = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '00', '0', '⌫'];
  protected readonly raccourcis = computed(() => raccourcisBillets(this.totalAPayer()));
  protected readonly rendu = computed(() => Math.max(0, this.valeur() - this.totalAPayer()));
  protected readonly manque = computed(() => Math.max(0, this.totalAPayer() - this.valeur()));

  protected taper(t: string): void {
    if (t === '⌫') {
      this.valeur.set(Math.floor(this.valeur() / 10));
      return;
    }
    const suivant = Number(String(this.valeur()) + t);
    if (suivant <= this.max()) {
      this.valeur.set(suivant);
    }
  }

  protected saisir(champ: HTMLInputElement): void {
    const n = Number(champ.value.replace(/\D/g, '') || '0');
    this.valeur.set(Math.min(n, this.max()));
  }

  protected clavier(e: KeyboardEvent): void {
    if (e.key === 'Enter' && this.valeur() >= this.totalAPayer()) {
      e.preventDefault();
      this.valider.emit();
    }
  }
}
