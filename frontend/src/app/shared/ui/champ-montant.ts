import { ChangeDetectionStrategy, Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/**
 * Champ montant FCFA : clavier numérique, entier uniquement, affichage groupé « 12 500 », suffixe FCFA,
 * chiffres tabulaires. Valeur du formulaire : nombre entier ou null.
 */
@Component({
  selector: 'amb-champ-montant',
  template: `
    <div class="champ-saisie flex items-center gap-2 focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-primaire">
      <input [id]="identifiant()" class="w-full min-w-0 bg-transparent text-right font-semibold tabular-nums outline-none" inputmode="numeric"
        autocomplete="off" [value]="affichage()" [disabled]="desactive()" [attr.aria-describedby]="decritPar()"
        (input)="saisir($any($event.target))" (blur)="toucher()" />
      <span class="text-texte-secondaire font-semibold" aria-hidden="true">FCFA</span>
    </div>
  `,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => ChampMontant), multi: true }],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChampMontant implements ControlValueAccessor {
  readonly identifiant = input.required<string>();
  readonly decritPar = input<string | null>(null);

  protected readonly affichage = signal('');
  protected readonly desactive = signal(false);
  private changer: (valeur: number | null) => void = () => undefined;
  protected toucher: () => void = () => undefined;

  static grouper(valeur: number): string {
    return new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(valeur).replace(/\u202F/g, '\u00A0');
  }

  writeValue(valeur: number | null): void {
    this.affichage.set(valeur === null || valeur === undefined ? '' : ChampMontant.grouper(valeur));
  }

  registerOnChange(fn: (valeur: number | null) => void): void {
    this.changer = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.toucher = fn;
  }

  setDisabledState(desactive: boolean): void {
    this.desactive.set(desactive);
  }

  protected saisir(element: HTMLInputElement): void {
    const chiffres = element.value.replace(/\D/g, '').slice(0, 15);
    const valeur = chiffres === '' ? null : Number(chiffres);
    const affichage = valeur === null ? '' : ChampMontant.grouper(valeur);
    // Réécrit aussitôt le champ, même si l'affichage ne change pas (caractère refusé).
    element.value = affichage;
    this.affichage.set(affichage);
    this.changer(valeur);
  }
}
