import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

/** Pagination : « 1–50 sur 584 », précédent / suivant. */
@Component({
  selector: 'amb-pagination',
  template: `
    <nav class="flex items-center justify-between gap-3 text-sm" aria-label="Pagination">
      <span class="tabular-nums text-texte-secondaire">{{ resume() }}</span>
      <div class="flex gap-2">
        <button type="button" class="bouton bouton-tertiaire" [disabled]="page() === 0" (click)="changer.emit(page() - 1)">‹ Précédent</button>
        <button type="button" class="bouton bouton-tertiaire" [disabled]="(page() + 1) * taille() >= total()" (click)="changer.emit(page() + 1)">
          Suivant ›
        </button>
      </div>
    </nav>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Pagination {
  readonly page = input(0);
  readonly taille = input(50);
  readonly total = input(0);
  readonly changer = output<number>();

  protected readonly resume = computed(() => {
    if (this.total() === 0) {
      return '0 résultat';
    }
    const debut = this.page() * this.taille() + 1;
    const fin = Math.min(this.total(), debut + this.taille() - 1);
    return `${debut}–${fin} sur ${new Intl.NumberFormat('fr-FR').format(this.total()).replace(/\u202F/g, '\u00A0')}`;
  });
}
