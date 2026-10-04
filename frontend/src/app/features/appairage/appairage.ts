import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { AgentSynchro } from '../../core/sync/agent-synchro';
import { messageErreur } from '../../shared/raccourcis';
import { Bouton } from '../../shared/ui/bouton';
import { Champ } from '../../shared/ui/champ';

type Etape = 'saisie' | 'chargement' | 'termine';

/**
 * A-02 Appairage du terminal (SD-11) : lecture du QR code (contenu collé ou saisi : identifiant + code),
 * puis chargement initial des données avec progression. La lecture par caméra arrive avec le scanner (LOT 5).
 */
@Component({
  selector: 'amb-appairage',
  imports: [FormsModule, TranslocoPipe, Bouton, Champ],
  templateUrl: './appairage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Appairage {
  protected readonly agent = inject(AgentSynchro);
  private readonly router = inject(Router);

  protected readonly etape = signal<Etape>('saisie');
  protected readonly recus = signal(0);
  protected readonly produits = signal(0);
  protected readonly duree = signal(0);
  protected readonly erreur = signal<string | null>(null);
  protected saisie = '';
  protected code = '';

  protected async appairer(): Promise<void> {
    this.erreur.set(null);
    let terminalId = this.saisie.trim();
    let code = this.code.trim();
    try {
      const qr = JSON.parse(terminalId) as { t?: string; c?: string };
      terminalId = qr.t ?? terminalId;
      code = qr.c ?? code;
    } catch {
      // saisie manuelle : identifiant du terminal + code lisible
    }
    if (!terminalId || !code) {
      this.erreur.set('Collez le contenu du QR code, ou saisissez l\'identifiant du terminal et le code affiché.');
      return;
    }
    try {
      await this.agent.appairer(terminalId, code);
      this.etape.set('chargement');
      const debut = performance.now();
      await this.agent.synchroniser((n) => this.recus.set(n));
      this.duree.set(Math.round((performance.now() - debut) / 100) / 10);
      this.produits.set(await this.agent.store.compterEntites('produit'));
      this.etape.set('termine');
    } catch (e) {
      this.etape.set('saisie');
      this.erreur.set(messageErreur(e, "L'appairage a échoué. Vérifiez le code et la connexion, puis réessayez."));
    }
  }

  protected terminer(): void {
    void this.router.navigateByUrl('/synchronisation');
  }
}
