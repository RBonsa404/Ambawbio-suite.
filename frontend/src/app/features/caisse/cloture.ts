import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Caisse, ResultatCloture } from '../../core/caisse/caisse';
import { ServiceReseau } from '../../core/service-reseau';
import { messageErreur } from '../../shared/raccourcis';
import { Bouton } from '../../shared/ui/bouton';
import { ChampMontant } from '../../shared/ui/champ-montant';
import { FcfaPipe } from '../../shared/ui/fcfa.pipe';

/**
 * A-15 Clôture de session (UC-POS-07, SD-06) : comptage à l'aveugle par défaut (D-02), puis écart ; au-delà du seuil,
 * A-16 validation par un responsable avec son code PIN (RG-09), vérifiée par le serveur dès que le réseau est là.
 */
@Component({
  selector: 'amb-cloture',
  imports: [FormsModule, RouterLink, Bouton, ChampMontant, FcfaPipe],
  template: `
    <div class="mx-auto flex max-w-xl flex-col gap-4">
      <a routerLink="/caisse" class="lien-retour">← Caisse</a>
      <h1 class="titre-page">Clôturer la caisse</h1>
      @if (!resultat()) {
        @if (!caisse.session()) {
          <p>Aucune session ouverte.</p>
        } @else {
          <div class="carte grid grid-cols-2 gap-2 text-sm" aria-label="Rapport de la session">
            <span>Ventes</span><strong class="text-right">{{ resume().ventes }} · {{ resume().totalVentes | fcfa }}</strong>
            <span>Retours</span><strong class="text-right">{{ resume().retours }} · {{ resume().totalRetours | fcfa }}</strong>
            <span>Mobile Money</span><strong class="text-right">{{ resume().mobile | fcfa }}</strong>
            <span>Carte</span><strong class="text-right">{{ resume().carte | fcfa }}</strong>
            @if (!aveugle()) { <span>Espèces attendues</span><strong class="text-right">{{ caisse.especesTheoriques() | fcfa }}</strong> }
          </div>
          <form class="carte flex flex-col gap-3" (ngSubmit)="cloturer()">
            <label class="champ-libelle" for="especes-comptees">Espèces comptées dans le tiroir</label>
            @if (aveugle()) { <p class="champ-aide">Comptez sans regarder le montant attendu : il s'affichera après la saisie.</p> }
            <amb-champ-montant identifiant="especes-comptees" name="comptees" [(ngModel)]="comptees" />
            @if (erreur()) { <p class="alerte-erreur" role="alert">{{ erreur() }}</p> }
            <button ambBouton variante="caisse" type="submit">Clôturer</button>
          </form>
        }
      } @else {
        @let r = resultat()!;
        <div class="carte grid grid-cols-2 gap-2" role="status">
          <span>Espèces attendues</span><strong class="text-right">{{ r.theoriques | fcfa }}</strong>
          <span>Espèces comptées</span><strong class="text-right">{{ r.comptees | fcfa }}</strong>
          <span>Écart</span><strong class="text-right" [class.text-danger]="r.ecart !== 0" data-testid="ecart">{{ r.ecart | fcfa }}</strong>
        </div>
        @if (!r.aValider) {
          <p class="alerte-succes">Caisse clôturée. Le rapport part au bureau à la prochaine synchronisation.</p>
          <a ambBouton variante="principal" routerLink="/caisse">Terminer</a>
        } @else if (valide()) {
          <p class="alerte-succes">Écart validé par le responsable.</p>
          <a ambBouton variante="principal" routerLink="/caisse">Terminer</a>
        } @else {
          <form class="carte flex flex-col gap-3" (ngSubmit)="valider()">
            <h2 class="sous-titre">Validation de l'écart par un responsable</h2>
            <p class="text-sm">L'écart dépasse le seuil de la caisse. Un responsable saisit son identifiant et son code PIN.
              @if (!reseau.enLigne()) { <strong>Connexion nécessaire :</strong> la validation peut aussi se faire depuis le bureau (Caisses › Sessions). }</p>
            <label class="champ-libelle" for="responsable">Identifiant du responsable</label>
            <input id="responsable" name="responsable" class="champ-saisie" [(ngModel)]="responsable" autocomplete="username" />
            <label class="champ-libelle" for="pin-responsable">Code PIN</label>
            <input id="pin-responsable" name="pin" class="champ-saisie font-code tracking-widest" type="password" inputmode="numeric" maxlength="6"
              [(ngModel)]="pin" autocomplete="off" />
            <label class="champ-libelle" for="motif">Raison de l'écart</label>
            <input id="motif" name="motif" class="champ-saisie" [(ngModel)]="motif" />
            @if (erreur()) { <p class="alerte-erreur" role="alert">{{ erreur() }}</p> }
            <button ambBouton variante="principal" type="submit" [disabled]="!reseau.enLigne()">Valider l'écart</button>
            <a ambBouton variante="tertiaire" routerLink="/caisse">Plus tard</a>
          </form>
        }
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EcranCloture {
  protected readonly caisse = inject(Caisse);
  protected readonly reseau = inject(ServiceReseau);
  protected comptees = 0;
  protected responsable = '';
  protected pin = '';
  protected motif = '';
  protected readonly resultat = signal<ResultatCloture | null>(null);
  protected readonly valide = signal(false);
  protected readonly erreur = signal<string | null>(null);
  private sessionId: string | null = null;

  protected readonly aveugle = computed(() => this.caisse.pointDeVente()?.comptageAveugle ?? true);
  protected readonly resume = computed(() => {
    const r = { ventes: 0, totalVentes: 0, retours: 0, totalRetours: 0, mobile: 0, carte: 0 };
    for (const p of this.caisse.pieces()) {
      const signe = p.type === 'VENTE' ? 1 : -1;
      if (signe > 0) {
        r.ventes++;
        r.totalVentes += p.totalTtc;
      } else {
        r.retours++;
        r.totalRetours += p.totalTtc;
      }
      p.encaissements.forEach((e) => {
        if (e.moyen === 'MOBILE_MONEY') r.mobile += signe * e.montant;
        if (e.moyen === 'CARTE') r.carte += signe * e.montant;
      });
    }
    return r;
  });

  protected async cloturer(): Promise<void> {
    this.erreur.set(null);
    try {
      this.sessionId = this.caisse.session()!.id;
      this.resultat.set(await this.caisse.cloturer(this.comptees ?? 0));
    } catch (e) {
      this.erreur.set(messageErreur(e, 'La clôture a échoué.'));
    }
  }

  protected async valider(): Promise<void> {
    this.erreur.set(null);
    try {
      await this.caisse.validerEcart(this.sessionId!, this.responsable, this.pin, this.motif);
      this.valide.set(true);
    } catch (e) {
      this.erreur.set(messageErreur(e, "L'écart n'a pas pu être validé."));
    } finally {
      this.pin = '';
    }
  }
}
