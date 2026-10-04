import { ChangeDetectionStrategy, Component, computed, inject, OnDestroy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Caisse, LignePanier, ProduitCaisse } from '../../core/caisse/caisse';
import { brut, enMilliemes, quantiteTexte } from '../../core/caisse/calcul-vente';
import { ServiceImpression } from '../../core/caisse/impression';
import { DetecteurDouchette, ServiceScanner } from '../../core/caisse/scanner';
import { EncaissementPiece, PieceCaisse } from '../../core/caisse/ticket';
import { ServiceContexte } from '../../core/service-contexte';
import { ServiceReseau } from '../../core/service-reseau';
import { AgentSynchro } from '../../core/sync/agent-synchro';
import { messageErreur } from '../../shared/raccourcis';
import { Bouton } from '../../shared/ui/bouton';
import { ChampMontant } from '../../shared/ui/champ-montant';
import { FcfaPipe, NombrePipe } from '../../shared/ui/fcfa.pipe';
import { Icone } from '../../shared/ui/icone';
import { PaveCaisse } from '../../shared/ui/pave-caisse';

type Vue = 'vente' | 'encaissement' | 'confirmation';
type Mode = 'ESPECES' | 'MOBILE_MONEY' | 'MIXTE' | 'CARTE';

/**
 * Caisse tactile (A-05 ouverture, A-06 vente, A-07 ligne, A-09 encaissement, A-11 Mobile Money de secours, A-12 vente
 * enregistrée). Fonctionne entièrement hors-ligne ; chaque vente part à la synchronisation dès que le réseau revient.
 */
@Component({
  selector: 'amb-caisse',
  imports: [FormsModule, RouterLink, Bouton, ChampMontant, FcfaPipe, NombrePipe, Icone, PaveCaisse],
  templateUrl: './caisse.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown)': 'clavier($event)' },
})
export class EcranCaisse implements OnDestroy {
  protected readonly caisse = inject(Caisse);
  protected readonly agent = inject(AgentSynchro);
  protected readonly reseau = inject(ServiceReseau);
  protected readonly contexte = inject(ServiceContexte);
  protected readonly impression = inject(ServiceImpression);
  protected readonly scanner = inject(ServiceScanner);

  protected readonly vue = signal<Vue>('vente');
  protected readonly recherche = signal('');
  protected readonly resultats = computed(() => this.caisse.rechercher(this.recherche()));
  protected readonly quantites = computed(() => {
    const m = new Map<string, number>();
    this.caisse.panier().forEach((l) => m.set(l.produit.id, (m.get(l.produit.id) ?? 0) + enMilliemes(l.quantite) / 1000));
    return m;
  });
  protected readonly ligneEditee = signal<LignePanier | null>(null);
  protected readonly panierOuvert = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly erreur = signal<string | null>(null);

  // Ouverture de session
  protected pointDeVenteId = '';
  protected fonds = 0;

  // Encaissement
  protected readonly mode = signal<Mode>('ESPECES');
  protected readonly recu = signal(0);
  protected readonly partMobile = signal(0);
  protected operateur = 'Orange Money';
  protected reference = '';
  protected factureDemandee = false;
  protected rechercheClient = '';
  protected readonly clientFacture = signal<{ id: string; nom: string; ifu: string | null } | null>(null);
  protected readonly enCours = signal(false);
  protected readonly ticketImprime = signal<boolean | null>(null);
  protected readonly retourAuto = signal(0);
  private minuterie: ReturnType<typeof setInterval> | null = null;

  protected readonly resteEspeces = computed(() => this.caisse.total() - (this.mode() === 'MIXTE' ? this.partMobile() : 0));
  private readonly douchette = new DetecteurDouchette((code) => this.lireCode(code));

  protected readonly operateurs = ['Orange Money', 'Moov Money', 'Wave'];

  ngOnDestroy(): void {
    this.arreterMinuterie();
  }

  protected titrePointDeVente(): string {
    return `${this.caisse.pointDeVente()?.nom ?? 'Caisse'} · ${this.contexte.contexte()?.utilisateur.nomComplet.split(' ')[0] ?? ''}`;
  }

  protected clavier(e: KeyboardEvent): void {
    if (this.vue() === 'vente' && this.caisse.session() && this.douchette.touche(e)) {
      e.preventDefault();
    }
  }

  protected lireCode(code: string): void {
    const p = this.caisse.scanner(code);
    this.recherche.set('');
    this.message.set(p ? `${p.nom} ajouté` : `Code « ${code} » inconnu`);
  }

  protected async scannerCamera(): Promise<void> {
    try {
      const code = await this.scanner.lire();
      if (code) {
        this.lireCode(code);
      }
    } catch (e) {
      this.erreur.set(messageErreur(e, 'Lecture impossible.'));
    }
  }

  protected rechercheEntree(): void {
    const exact = this.caisse.scanner(this.recherche());
    if (exact) {
      this.recherche.set('');
    } else if (this.resultats().length === 1) {
      this.caisse.ajouter(this.resultats()[0]);
      this.recherche.set('');
    }
  }

  protected toucherProduit(p: ProduitCaisse): void {
    this.caisse.ajouter(p);
  }

  protected editer(l: LignePanier): void {
    this.ligneEditee.set({ ...l });
  }

  protected changerQuantite(delta: number): void {
    const l = this.ligneEditee();
    if (l) {
      const q = Math.max(1000, enMilliemes(l.quantite) + delta * 1000);
      this.ligneEditee.set({ ...l, quantite: quantiteTexte(q), remise: Math.min(l.remise, this.remiseMaxPour({ ...l, quantite: quantiteTexte(q) })) });
    }
  }

  protected choisirConditionnement(code: string | null): void {
    const l = this.ligneEditee();
    if (l) {
      const cond = l.produit.conditionnements.find((c) => c.code === code);
      const facteur = cond?.quantite ?? '1';
      this.ligneEditee.set({ ...l, conditionnement: cond?.code ?? null, facteur, remise: 0,
        prixUnitaire: cond ? (cond.prixVente ?? brut(facteur, l.produit.prixVente)) : l.produit.prixVente });
    }
  }

  protected remiseMaxPour(l: LignePanier): number {
    return this.caisse.remiseMax(l);
  }

  protected remisePourcent(pourcent: number): void {
    const l = this.ligneEditee();
    if (l) {
      this.ligneEditee.set({ ...l, remise: Math.min(this.remiseMaxPour(l), Math.round((brut(l.quantite, l.prixUnitaire) * pourcent) / 100)) });
    }
  }

  protected montantLigne(l: LignePanier): number {
    return brut(l.quantite, l.prixUnitaire) - l.remise;
  }

  protected validerLigne(): void {
    const l = this.ligneEditee();
    if (l) {
      this.caisse.modifier(l.id, { conditionnement: l.conditionnement });
      this.caisse.modifier(l.id, { quantite: l.quantite, remise: l.remise });
      this.ligneEditee.set(null);
    }
  }

  protected retirerLigne(): void {
    const l = this.ligneEditee();
    if (l) {
      this.caisse.retirer(l.id);
      this.ligneEditee.set(null);
    }
  }

  protected async ouvrir(): Promise<void> {
    this.erreur.set(null);
    if (!this.pointDeVenteId) {
      this.erreur.set('Choisissez la caisse.');
      return;
    }
    try {
      await this.caisse.ouvrirSession(this.pointDeVenteId, this.fonds ?? 0);
    } catch (e) {
      this.erreur.set(messageErreur(e, "La caisse n'a pas pu être ouverte."));
    }
  }

  protected allerEncaisser(): void {
    this.mode.set('ESPECES');
    this.recu.set(0);
    this.partMobile.set(0);
    this.reference = '';
    this.factureDemandee = false;
    this.clientFacture.set(null);
    this.rechercheClient = '';
    this.erreur.set(null);
    this.vue.set('encaissement');
  }

  protected peutValider(): boolean {
    const total = this.caisse.total();
    if (this.factureDemandee && !this.clientFacture()) {
      return false;
    }
    switch (this.mode()) {
      case 'ESPECES':
        return this.recu() >= total;
      case 'MOBILE_MONEY':
        return this.reference.trim().length >= 4;
      case 'CARTE':
        return true;
      case 'MIXTE':
        return this.reference.trim().length >= 4 && this.partMobile() > 0 && this.partMobile() < total && this.recu() >= this.resteEspeces();
    }
  }

  protected async validerPaiement(): Promise<void> {
    if (!this.peutValider() || this.enCours()) {
      return;
    }
    const total = this.caisse.total();
    const especes = (montant: number): EncaissementPiece => ({ moyen: 'ESPECES', montant, recu: this.recu(), rendu: this.recu() - montant });
    const mobile = (montant: number): EncaissementPiece => ({ moyen: 'MOBILE_MONEY', montant, operateur: this.operateur, reference: this.reference.trim() });
    const encaissements: EncaissementPiece[] = {
      ESPECES: () => [especes(total)],
      MOBILE_MONEY: () => [mobile(total)],
      CARTE: () => [{ moyen: 'CARTE', montant: total } as EncaissementPiece],
      MIXTE: () => [mobile(this.partMobile()), especes(total - this.partMobile())],
    }[this.mode()]();
    this.enCours.set(true);
    try {
      const piece = await this.caisse.encaisser(encaissements, { factureDemandee: this.factureDemandee, clientId: this.clientFacture()?.id ?? null });
      this.vue.set('confirmation');
      void this.imprimer(piece);
      this.demarrerRetourAuto();
    } catch (e) {
      this.erreur.set(messageErreur(e, "La vente n'a pas pu être enregistrée."));
    } finally {
      this.enCours.set(false);
    }
  }

  protected async imprimer(piece: PieceCaisse | null): Promise<void> {
    if (!piece) {
      return;
    }
    const c = this.contexte.contexte();
    try {
      await this.impression.imprimer(piece, {
        entreprise: c?.entreprise.nom ?? '', etablissement: this.contexte.etablissement()?.nom ?? '', caisse: this.caisse.pointDeVente()?.nom ?? '',
        caissier: c?.utilisateur.nomComplet ?? '',
      });
      this.ticketImprime.set(true);
    } catch {
      this.ticketImprime.set(false);
    }
  }

  protected rendu(): number {
    return this.caisse.derniere()?.encaissements.find((e) => e.moyen === 'ESPECES')?.rendu ?? 0;
  }

  protected recuDerniere(): number {
    return this.caisse.derniere()?.encaissements.find((e) => e.moyen === 'ESPECES')?.recu ?? this.caisse.derniere()?.totalTtc ?? 0;
  }

  protected nouvelleVente(): void {
    this.arreterMinuterie();
    this.ticketImprime.set(null);
    this.vue.set('vente');
  }

  private demarrerRetourAuto(): void {
    this.retourAuto.set(8);
    this.minuterie = setInterval(() => {
      this.retourAuto.update((n) => n - 1);
      if (this.retourAuto() <= 0) {
        this.nouvelleVente();
      }
    }, 1000);
  }

  protected suspendreRetourAuto(): void {
    this.arreterMinuterie();
    this.retourAuto.set(0);
  }

  private arreterMinuterie(): void {
    if (this.minuterie) {
      clearInterval(this.minuterie);
      this.minuterie = null;
    }
  }
}
