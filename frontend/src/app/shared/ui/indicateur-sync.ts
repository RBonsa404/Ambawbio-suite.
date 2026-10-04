import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { Icone } from './icone';

export type EtatSynchronisation = 'en-ligne' | 'hors-ligne' | 'envoi' | 'en-attente' | 'alerte';

/**
 * Indicateur réseau et synchronisation (docs/design/composants/indicateur-synchronisation.md).
 * Jamais la couleur seule : icône + libellé ; role="status" ; hors-ligne n'est pas une erreur (couleur neutre).
 */
@Component({
  selector: 'amb-indicateur-sync',
  imports: [Icone],
  template: `
    <div role="status" aria-live="polite">
      <button type="button" class="indicateur-sync" [class]="'indicateur-sync indicateur-' + variante() + ' etat-' + etat()" (click)="ouvrir.emit()">
        <amb-icone [nom]="icone()" [taille]="16" [class.animate-spin]="etat() === 'envoi'" class="motion-reduce:animate-none" />
        <span>{{ libelle() }}</span>
      </button>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IndicateurSync {
  readonly etat = input<EtatSynchronisation>('en-ligne');
  readonly enAttente = input(0);
  readonly envoyees = input(0);
  readonly derniereSynchro = input<Date | null>(null);
  readonly variante = input<'bandeau' | 'pastille'>('pastille');
  readonly ouvrir = output<void>();

  protected readonly icone = computed(() =>
    ({ 'en-ligne': 'cloud-check', 'hors-ligne': 'cloud-off', envoi: 'refresh-cw', 'en-attente': 'clock', alerte: 'triangle-alert' })[this.etat()],
  );

  protected readonly libelle = computed(() => {
    const heure = this.derniereSynchro()?.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' }).replace(':', ' h ');
    switch (this.etat()) {
      case 'hors-ligne':
        return this.enAttente() > 0 ? `Hors-ligne — ${this.enAttente()} opérations sur l'appareil` : 'Hors-ligne — vos saisies restent sur l\'appareil';
      case 'envoi':
        return `Envoi en cours — ${this.envoyees()} sur ${this.enAttente()}`;
      case 'en-attente':
        return `${this.enAttente()} opérations en attente d'envoi`;
      case 'alerte':
        return 'Pas de synchronisation depuis plus de 48 h — Synchroniser';
      default:
        return heure ? `En ligne — tout est synchronisé (${heure})` : 'En ligne';
    }
  });
}
