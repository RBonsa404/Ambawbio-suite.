import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

import { configuration } from '../../core/configuration';
import { uuid7 } from '../../core/identifiant';
import { ServiceContexte } from '../../core/service-contexte';
import { messageErreur } from '../../shared/raccourcis';
import { Bouton } from '../../shared/ui/bouton';
import { Champ } from '../../shared/ui/champ';
import { ChampMontant } from '../../shared/ui/champ-montant';
import { FcfaPipe, NombrePipe } from '../../shared/ui/fcfa.pipe';
import { Notifications } from '../../shared/ui/notifications';

interface PointDeVenteVue {
  id: string;
  etablissementId: string;
  code: string;
  nom: string;
  seuilEcart: number;
  comptageAveugle: boolean;
  remiseMaxPourcent: number;
  actif: boolean;
}

interface SessionVue {
  id: string;
  pointDeVenteId: string;
  etablissementId: string;
  statut: 'OUVERTE' | 'EN_CONFLIT' | 'CLOTUREE' | 'ECART_A_VALIDER' | 'ECART_VALIDE';
  ouverteLe: string;
  especesTheoriques: number | null;
  ecart: number | null;
  pointDeVente: string | null;
  caissier: string | null;
  ventes: number | null;
}

interface Rapport {
  session: SessionVue;
  pointDeVente: string;
  nombreVentes: number;
  totalVentes: number;
  nombreRetours: number;
  totalRetours: number;
  especes: number;
  mobileMoney: number;
  carte: number;
  taxes: number;
  remises: number;
  especesTheoriques: number;
}

const STATUTS: Record<SessionVue['statut'], string> = {
  OUVERTE: 'en cours', EN_CONFLIT: 'en conflit', CLOTUREE: 'clôturée', ECART_A_VALIDER: 'écart à valider', ECART_VALIDE: 'écart validé',
};

/** W-13 Caisses : points de vente, sessions récentes, rapport Z, validation d'écart (RG-09), code PIN du responsable. */
@Component({
  selector: 'amb-caisses',
  imports: [FormsModule, Bouton, Champ, ChampMontant, FcfaPipe, NombrePipe],
  templateUrl: './caisses.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Caisses {
  private readonly http = inject(HttpClient);
  private readonly notifications = inject(Notifications);
  protected readonly contexte = inject(ServiceContexte);
  private readonly base = `${configuration.api}/v1/pos`;

  protected readonly pointsDeVente = signal<PointDeVenteVue[]>([]);
  protected readonly sessions = signal<SessionVue[]>([]);
  protected readonly rapport = signal<Rapport | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly formulaire = signal(false);
  protected readonly statuts = STATUTS;
  protected readonly ouvertes = computed(() => new Map(this.sessions().filter((s) => s.statut === 'OUVERTE').map((s) => [s.pointDeVenteId, s])));

  protected nouveau = { code: '', nom: '', etablissementId: '', seuilEcart: 500, comptageAveugle: true, remiseMaxPourcent: 10 };
  protected pin = '';
  protected motif = '';
  protected pinValidation = '';

  constructor() {
    void this.charger();
  }

  protected nomEtablissement(id: string): string {
    return this.contexte.contexte()?.etablissements.find((e) => e.id === id)?.nom ?? '—';
  }

  protected date(iso: string): string {
    return new Date(iso).toLocaleString('fr-FR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
  }

  private async charger(): Promise<void> {
    const [pdv, sessions] = await Promise.all([
      firstValueFrom(this.http.get<PointDeVenteVue[]>(`${this.base}/points-de-vente`)),
      firstValueFrom(this.http.get<SessionVue[]>(`${this.base}/sessions`)),
    ]);
    this.pointsDeVente.set(pdv);
    this.sessions.set(sessions);
  }

  protected async creer(): Promise<void> {
    this.erreur.set(null);
    try {
      const etablissementId = this.nouveau.etablissementId || this.contexte.etablissement()?.id;
      await firstValueFrom(this.http.post(`${this.base}/points-de-vente`, { id: uuid7(), ...this.nouveau, etablissementId }));
      this.notifications.succes(`Caisse « ${this.nouveau.nom} » créée : elle arrive sur les terminaux à la prochaine synchronisation.`);
      this.nouveau = { code: '', nom: '', etablissementId: '', seuilEcart: 500, comptageAveugle: true, remiseMaxPourcent: 10 };
      this.formulaire.set(false);
      await this.charger();
    } catch (e) {
      this.erreur.set(messageErreur(e, "La caisse n'a pas pu être créée."));
    }
  }

  protected async ouvrirRapport(s: SessionVue): Promise<void> {
    this.rapport.set(await firstValueFrom(this.http.get<Rapport>(`${this.base}/sessions/${s.id}/rapport`)));
    this.motif = '';
    this.pinValidation = '';
    this.erreur.set(null);
  }

  protected async validerEcart(): Promise<void> {
    const r = this.rapport();
    if (!r) {
      return;
    }
    this.erreur.set(null);
    try {
      await firstValueFrom(this.http.post(`${this.base}/sessions/${r.session.id}/validation-ecart`, { pin: this.pinValidation, motif: this.motif }));
      this.notifications.succes('Écart validé.');
      this.rapport.set(null);
      await this.charger();
    } catch (e) {
      this.erreur.set(messageErreur(e, "L'écart n'a pas pu être validé."));
    } finally {
      this.pinValidation = '';
    }
  }

  protected async definirPin(): Promise<void> {
    this.erreur.set(null);
    try {
      await firstValueFrom(this.http.put(`${configuration.api}/v1/socle/moi/code-pin`, { pin: this.pin }));
      this.notifications.succes('Code PIN de responsable enregistré.');
    } catch (e) {
      this.erreur.set(messageErreur(e, "Le code PIN n'a pas pu être enregistré."));
    } finally {
      this.pin = '';
    }
  }
}
