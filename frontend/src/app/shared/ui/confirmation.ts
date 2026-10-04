import { ChangeDetectionStrategy, Component, ElementRef, Injectable, effect, inject, signal, viewChild } from '@angular/core';

import { Bouton } from './bouton';

interface Demande {
  titre: string;
  message: string;
  confirmer: string;
  danger: boolean;
  resoudre: (accepte: boolean) => void;
}

/** Boîte de confirmation, réservée aux actions irréversibles (brief §8.3). */
@Injectable({ providedIn: 'root' })
export class Confirmation {
  readonly demande = signal<Demande | null>(null);

  demander(titre: string, message: string, confirmer: string, danger = false): Promise<boolean> {
    return new Promise((resoudre) => this.demande.set({ titre, message, confirmer, danger, resoudre }));
  }
}

@Component({
  selector: 'amb-confirmation',
  imports: [Bouton],
  template: `
    <dialog #dialogue class="modale" aria-labelledby="confirmation-titre" (cancel)="repondre(false)">
      @if (service.demande(); as d) {
        <h2 id="confirmation-titre" class="font-titre text-2xl font-extrabold">{{ d.titre }}</h2>
        <p class="mt-3 text-texte-secondaire">{{ d.message }}</p>
        <div class="mt-6 flex flex-wrap justify-end gap-3">
          <button ambBouton variante="secondaire" type="button" (click)="repondre(false)">Annuler</button>
          <button ambBouton [variante]="d.danger ? 'danger' : 'principal'" type="button" (click)="repondre(true)">{{ d.confirmer }}</button>
        </div>
      }
    </dialog>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BoiteConfirmation {
  protected readonly service = inject(Confirmation);
  private readonly dialogue = viewChild.required<ElementRef<HTMLDialogElement>>('dialogue');

  constructor() {
    effect(() => {
      const element = this.dialogue().nativeElement;
      if (this.service.demande() && !element.open) {
        element.showModal();
      } else if (!this.service.demande() && element.open) {
        element.close();
      }
    });
  }

  protected repondre(accepte: boolean): void {
    this.service.demande()?.resoudre(accepte);
    this.service.demande.set(null);
  }
}
