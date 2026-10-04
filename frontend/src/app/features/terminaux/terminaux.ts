import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { toDataURL } from 'qrcode';
import { firstValueFrom } from 'rxjs';

import { configuration } from '../../core/configuration';
import { uuid7 } from '../../core/identifiant';
import { ServiceContexte } from '../../core/service-contexte';
import { messageErreur } from '../../shared/raccourcis';
import { Bouton } from '../../shared/ui/bouton';
import { Champ } from '../../shared/ui/champ';
import { Confirmation } from '../../shared/ui/confirmation';
import { Notifications } from '../../shared/ui/notifications';

interface TerminalVue {
  id: string;
  etablissementId: string;
  code: string;
  nom: string;
  statut: 'EN_ATTENTE_APPAIRAGE' | 'ACTIF' | 'REVOQUE';
  derniereSynchro: string | null;
}

interface CodeAppairage {
  terminal: TerminalVue;
  code: string;
  expireLe: string;
  contenuQr: string;
}

/** W-18 Paramètres › Terminaux : enregistrement avec QR code d'appairage, révocation (UC-SOC-04). */
@Component({
  selector: 'amb-terminaux',
  imports: [FormsModule, TranslocoPipe, Bouton, Champ],
  templateUrl: './terminaux.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Terminaux {
  private readonly http = inject(HttpClient);
  private readonly confirmation = inject(Confirmation);
  private readonly notifications = inject(Notifications);
  protected readonly contexte = inject(ServiceContexte);
  private readonly base = `${configuration.api}/v1/socle/terminaux`;

  protected readonly terminaux = signal<TerminalVue[]>([]);
  protected readonly appairage = signal<(CodeAppairage & { image: string }) | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected nom = '';
  protected etablissementId = '';

  constructor() {
    void this.charger();
  }

  protected nomEtablissement(id: string): string {
    return this.contexte.contexte()?.etablissements.find((e) => e.id === id)?.nom ?? '—';
  }

  private async charger(): Promise<void> {
    this.terminaux.set(await firstValueFrom(this.http.get<TerminalVue[]>(this.base)));
  }

  protected async creer(): Promise<void> {
    this.erreur.set(null);
    const etablissement = this.etablissementId || this.contexte.etablissement()?.id;
    if (!this.nom.trim() || !etablissement) {
      this.erreur.set('Indiquez le nom du terminal et son établissement.');
      return;
    }
    try {
      await this.afficher(await firstValueFrom(this.http.post<CodeAppairage>(this.base, { id: uuid7(), etablissementId: etablissement, nom: this.nom.trim() })));
      this.nom = '';
      await this.charger();
    } catch (e) {
      this.erreur.set(messageErreur(e, "Le terminal n'a pas pu être créé."));
    }
  }

  protected async nouveauCode(t: TerminalVue): Promise<void> {
    await this.afficher(await firstValueFrom(this.http.post<CodeAppairage>(`${this.base}/${t.id}/code-appairage`, {})));
  }

  protected async revoquer(t: TerminalVue): Promise<void> {
    const accepte = await this.confirmation.demander(
      `Révoquer ${t.code} — ${t.nom} ?`,
      "Le terminal ne pourra plus synchroniser. Les ventes non envoyées resteront sur l'appareil. Cette action est définitive.",
      'Révoquer le terminal',
      true,
    );
    if (accepte) {
      await firstValueFrom(this.http.post(`${this.base}/${t.id}/revocation`, {}));
      this.notifications.succes(`Terminal ${t.code} révoqué.`);
      await this.charger();
    }
  }

  private async afficher(code: CodeAppairage): Promise<void> {
    this.appairage.set({ ...code, image: await toDataURL(code.contenuQr, { margin: 1, width: 240, color: { dark: '#1E1B18', light: '#FFFFFF' } }) });
  }
}
