import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Caisse } from '../../core/caisse/caisse';
import { quantiteTexte } from '../../core/caisse/calcul-vente';
import { ServiceImpression } from '../../core/caisse/impression';
import { PieceCaisse } from '../../core/caisse/ticket';
import { ServiceContexte } from '../../core/service-contexte';
import { messageErreur } from '../../shared/raccourcis';
import { Bouton } from '../../shared/ui/bouton';
import { FcfaPipe } from '../../shared/ui/fcfa.pipe';

/** A-13 Retour de marchandise (UC-POS-06, SD-08) : ticket de la session, quantités retournées, remboursement en espèces. */
@Component({
  selector: 'amb-retour',
  imports: [FormsModule, RouterLink, Bouton, FcfaPipe],
  template: `
    <div class="mx-auto flex max-w-2xl flex-col gap-4">
      <a routerLink="/caisse" class="lien-retour">← Caisse</a>
      <h1 class="titre-page">Retour de marchandise</h1>
      @if (!vente()) {
        <label class="champ-libelle" for="recherche-ticket">Numéro du ticket</label>
        <input id="recherche-ticket" class="champ-saisie font-code" [ngModel]="filtre()" (ngModelChange)="filtre.set($event)" placeholder="ex. 000142" />
        <ul class="flex flex-col gap-2">
          @for (p of ventes(); track p.venteId) {
            <li><button type="button" class="carte-lien w-full justify-between" (click)="choisir(p)">
              <span class="font-code">{{ p.numeroAffiche }}</span><span>{{ heure(p.horodatage) }}</span><strong>{{ p.totalTtc | fcfa }}</strong></button></li>
          } @empty { <li class="text-texte-secondaire">Aucun ticket dans cette session.</li> }
        </ul>
      } @else {
        <p class="font-code">Ticket {{ vente()!.numeroAffiche }}</p>
        <table class="tableau">
          <thead><tr><th scope="col">Article</th><th scope="col" class="nombre">Vendu</th><th scope="col" class="nombre">Retourné</th></tr></thead>
          <tbody>
            @for (l of vente()!.lignes; track l.id) {
              <tr>
                <td>{{ l.libelle }}</td>
                <td class="nombre">{{ l.quantite.replace('.', ',') }}</td>
                <td class="nombre"><input type="number" min="0" [max]="max(l.id)" step="1" class="champ-saisie w-24 text-right" [attr.aria-label]="'Quantité retournée : ' + l.libelle"
                  [ngModel]="quantites().get(l.id) ?? 0" (ngModelChange)="saisir(l.id, $event)" /></td>
              </tr>
            }
          </tbody>
        </table>
        @if (erreur()) { <p class="alerte-erreur" role="alert">{{ erreur() }}</p> }
        @if (fait(); as f) {
          <p class="alerte-succes" role="status">Retour {{ f.numeroAffiche }} enregistré : rendez {{ f.totalTtc | fcfa }} au client.</p>
          <a ambBouton variante="caisse" routerLink="/caisse">Retour à la caisse</a>
        } @else {
          <button ambBouton variante="principal" type="button" (click)="valider()">Rembourser en espèces</button>
          <button ambBouton variante="tertiaire" type="button" (click)="vente.set(null)">Choisir un autre ticket</button>
        }
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EcranRetour {
  private readonly caisse = inject(Caisse);
  private readonly impression = inject(ServiceImpression);
  private readonly contexte = inject(ServiceContexte);
  protected readonly filtre = signal('');
  protected readonly vente = signal<PieceCaisse | null>(null);
  protected readonly quantites = signal(new Map<string, number>());
  protected readonly erreur = signal<string | null>(null);
  protected readonly fait = signal<PieceCaisse | null>(null);
  protected readonly ventes = computed(() =>
    this.caisse.pieces().filter((p) => p.type === 'VENTE' && p.numeroAffiche.includes(this.filtre().trim())),
  );

  protected heure(iso: string): string {
    return new Date(iso).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  }

  protected choisir(p: PieceCaisse): void {
    this.vente.set(p);
    this.quantites.set(new Map());
    this.fait.set(null);
  }

  protected max(ligneId: string): number {
    return Math.floor((this.caisse.retournable(this.vente()!).get(ligneId) ?? 0) / 1000);
  }

  protected saisir(ligneId: string, valeur: number): void {
    this.quantites.update((m) => new Map(m).set(ligneId, Math.max(0, Math.min(Number(valeur) || 0, this.max(ligneId)))));
  }

  protected async valider(): Promise<void> {
    this.erreur.set(null);
    try {
      const q = new Map([...this.quantites()].filter(([, v]) => v > 0).map(([k, v]) => [k, quantiteTexte(v * 1000)]));
      const retour = await this.caisse.retourner(this.vente()!, q);
      this.fait.set(retour);
      const c = this.contexte.contexte();
      void this.impression
        .imprimer(retour, { entreprise: c?.entreprise.nom ?? '', etablissement: this.contexte.etablissement()?.nom ?? '',
          caisse: this.caisse.pointDeVente()?.nom ?? '', caissier: c?.utilisateur.nomComplet ?? '' })
        .catch(() => undefined);
    } catch (e) {
      this.erreur.set(messageErreur(e, "Le retour n'a pas pu être enregistré."));
    }
  }
}
