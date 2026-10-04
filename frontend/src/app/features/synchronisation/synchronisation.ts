import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { ServiceImpression } from '../../core/caisse/impression';
import { ServiceReseau } from '../../core/service-reseau';
import { AgentSynchro } from '../../core/sync/agent-synchro';
import { OperationSortante } from '../../core/sync/local-store';
import { Bouton } from '../../shared/ui/bouton';
import { IndicateurSync } from '../../shared/ui/indicateur-sync';

/** A-17 État de la synchronisation : opérations en attente, conflits, dernière synchronisation, plages (RG-04), synchroniser. */
@Component({
  selector: 'amb-synchronisation',
  imports: [RouterLink, TranslocoPipe, Bouton, IndicateurSync],
  templateUrl: './synchronisation.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Synchronisation {
  protected readonly agent = inject(AgentSynchro);
  protected readonly reseau = inject(ServiceReseau);
  protected readonly impression = inject(ServiceImpression);
  protected readonly erreurImprimante = signal<string | null>(null);

  protected async choisirImprimante(largeur: 32 | 48): Promise<void> {
    this.erreurImprimante.set(null);
    try {
      await this.impression.choisir(largeur);
    } catch {
      this.erreurImprimante.set("Aucune imprimante choisie : allumez-la, activez le Bluetooth et réessayez.");
    }
  }
  protected readonly operations = signal<OperationSortante[]>([]);
  protected readonly enConflit = computed(() => this.operations().filter((o) => o.statut !== 'EN_ATTENTE'));

  constructor() {
    void this.charger();
  }

  protected pourcentage(debut: number, fin: number, prochain: number): number {
    return Math.min(100, Math.round(((prochain - debut) * 100) / (fin - debut + 1)));
  }

  protected heure(iso: string): string {
    return new Date(iso).toLocaleString('fr-FR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
  }

  protected async synchroniser(): Promise<void> {
    await this.agent.synchroniser();
    await this.charger();
  }

  private async charger(): Promise<void> {
    this.operations.set(await this.agent.store.toutesLesOperations());
  }
}
