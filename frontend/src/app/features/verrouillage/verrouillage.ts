import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { ServiceContexte } from '../../core/service-contexte';
import { ServiceReseau } from '../../core/service-reseau';
import { VerrouPin } from '../../core/verrou-pin';
import { Icone } from '../../shared/ui/icone';
import { IndicateurSync } from '../../shared/ui/indicateur-sync';

/** A-04 Déverrouillage par code PIN : clavier 3 × 4 (touches 64 px), nom du caissier, hors-ligne signalé. */
@Component({
  selector: 'amb-verrouillage',
  imports: [TranslocoPipe, Icone, IndicateurSync],
  templateUrl: './verrouillage.html',
  host: { '(document:keydown)': 'clavier($event)' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Verrouillage {
  private readonly verrou = inject(VerrouPin);
  private readonly router = inject(Router);
  protected readonly contexte = inject(ServiceContexte);
  protected readonly reseau = inject(ServiceReseau);

  protected readonly saisie = signal('');
  protected readonly definition = signal(false);
  protected readonly confirmation = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly touches = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', 'effacer'];
  protected readonly points = computed(() => Array.from({ length: 6 }, (_, i) => i < this.saisie().length));

  constructor() {
    void this.verrou.estDefini().then((defini) => this.definition.set(!defini));
  }

  protected appuyer(touche: string): void {
    this.message.set(null);
    if (touche === 'effacer') {
      this.saisie.update((s) => s.slice(0, -1));
    } else if (touche && this.saisie().length < 6) {
      this.saisie.update((s) => s + touche);
    }
  }

  protected clavier(evenement: KeyboardEvent): void {
    if (/^\d$/.test(evenement.key)) {
      this.appuyer(evenement.key);
    } else if (evenement.key === 'Backspace') {
      this.appuyer('effacer');
    } else if (evenement.key === 'Enter') {
      void this.valider();
    }
  }

  protected async valider(): Promise<void> {
    const pin = this.saisie();
    if (pin.length < 4) {
      this.message.set('Le code PIN comporte 4 à 6 chiffres.');
      return;
    }
    this.saisie.set('');
    if (this.definition()) {
      if (this.confirmation() === null) {
        this.confirmation.set(pin);
        this.message.set('Saisissez à nouveau le code pour le confirmer.');
      } else if (this.confirmation() === pin) {
        await this.verrou.definir(pin);
        void this.router.navigateByUrl('/');
      } else {
        this.confirmation.set(null);
        this.message.set('Les deux codes sont différents. Recommencez.');
      }
      return;
    }
    try {
      if (await this.verrou.verifier(pin)) {
        void this.router.navigateByUrl('/');
      } else {
        this.message.set(`Code incorrect. ${await this.verrou.essaisRestants()} essai(s) restant(s).`);
      }
    } catch (e) {
      this.message.set((e as Error).message);
    }
  }
}
