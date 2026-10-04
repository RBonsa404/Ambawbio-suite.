import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnDestroy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { firstValueFrom } from 'rxjs';

import { ApiReferentiel, Produit, Tiers } from '../../core/api-referentiel';
import { configuration } from '../../core/configuration';
import { uuid7 } from '../../core/identifiant';
import { ServiceContexte } from '../../core/service-contexte';
import { messageErreur } from '../../shared/raccourcis';
import { BadgeFec, statutFec } from '../../shared/ui/badge-fec';
import { Bouton } from '../../shared/ui/bouton';
import { FcfaPipe, NombrePipe } from '../../shared/ui/fcfa.pipe';
import { Notifications } from '../../shared/ui/notifications';

export interface LigneDocument {
  id: string;
  produitId: string | null;
  designation: string;
  unite: string | null;
  quantite: number;
  prixUnitaire: number;
  prixTtc: boolean;
  remise: number;
  taxeCode: string | null;
  taux: number;
  montantHt: number;
  montantTaxe: number;
  montantTtc: number;
}

export interface DocumentFiscal {
  id: string;
  type: 'FACTURE' | 'AVOIR';
  statut: 'BROUILLON' | 'VALIDEE' | 'PARTIELLEMENT_PAYEE' | 'PAYEE' | 'ANNULEE_PAR_AVOIR';
  numero: string | null;
  dateEmission: string | null;
  dateEcheance: string | null;
  clientId: string;
  clientNom: string | null;
  clientIfu: string | null;
  factureOrigineId: string | null;
  motif: string | null;
  totalHt: number;
  totalTaxes: number;
  totalTtc: number;
  totalAvoirs: number;
  origine: string | null;
  fecStatut: 'NON_SOUMISE' | 'EN_FILE' | 'CERTIFIEE' | 'REJETEE';
  fecIdentifiant: string | null;
  fecHorodatage: string | null;
  fecSimulee: boolean;
  fecMessage: string | null;
  lignes: LigneDocument[];
}

interface LigneSaisie {
  produitId: string | null;
  designation: string;
  quantite: number;
  prixUnitaire: number;
  prixTtc: boolean;
  taxeCode: string | null;
  taux: number;
}

type Filtre = 'toutes' | 'brouillons' | 'en-file' | 'rejetees' | 'avoirs';

const STATUTS: Record<DocumentFiscal['statut'], string> = {
  BROUILLON: 'Brouillon', VALIDEE: 'Non payée', PARTIELLEMENT_PAYEE: 'Partielle', PAYEE: 'Payée', ANNULEE_PAR_AVOIR: 'Annulée par avoir',
};

/** W-10 Factures et avoirs : liste et fiche côte à côte, création, validation (UC-FAC-01), avoirs (UC-FAC-02), PDF, certification. */
@Component({
  selector: 'amb-factures',
  imports: [FormsModule, BadgeFec, Bouton, FcfaPipe, NombrePipe],
  templateUrl: './factures.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Factures implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly referentiel = inject(ApiReferentiel);
  private readonly notifications = inject(Notifications);
  private readonly assainisseur = inject(DomSanitizer);
  protected readonly contexte = inject(ServiceContexte);
  private readonly base = `${configuration.api}/v1/facturation/documents`;

  protected readonly documents = signal<DocumentFiscal[]>([]);
  protected readonly selection = signal<DocumentFiscal | null>(null);
  protected readonly filtre = signal<Filtre>('toutes');
  protected readonly apercu = signal<SafeResourceUrl | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enCours = signal(false);
  protected readonly adaptateur = signal<{ adaptateur: string; mode?: string } | null>(null);
  protected readonly statuts = STATUTS;
  protected readonly statutFec = statutFec;
  private urlApercu: string | null = null;

  // Éditeur
  protected readonly edition = signal(false);
  protected editionId: string | null = null;
  protected rechercheClient = '';
  protected readonly clients = signal<Tiers[]>([]);
  protected client: Tiers | null = null;
  protected rechercheProduit = '';
  protected readonly produits = signal<Produit[]>([]);
  protected readonly lignes = signal<LigneSaisie[]>([]);
  private taxes = new Map<string, { code: string; taux: number }>();

  // Avoir
  protected readonly avoirOuvert = signal(false);
  protected motifAvoir = '';
  protected quantitesAvoir: Record<string, number> = {};

  protected readonly compteurs = computed(() => {
    const d = this.documents();
    return {
      brouillons: d.filter((x) => x.statut === 'BROUILLON').length,
      enFile: d.filter((x) => x.statut !== 'BROUILLON' && x.fecStatut === 'EN_FILE').length,
      rejetees: d.filter((x) => x.fecStatut === 'REJETEE').length,
      avoirs: d.filter((x) => x.type === 'AVOIR').length,
    };
  });
  protected readonly visibles = computed(() => {
    const d = this.documents();
    switch (this.filtre()) {
      case 'brouillons': return d.filter((x) => x.statut === 'BROUILLON');
      case 'en-file': return d.filter((x) => x.statut !== 'BROUILLON' && x.fecStatut === 'EN_FILE');
      case 'rejetees': return d.filter((x) => x.fecStatut === 'REJETEE');
      case 'avoirs': return d.filter((x) => x.type === 'AVOIR');
      default: return d;
    }
  });
  protected readonly totalSaisie = computed(() => this.lignes().reduce((s, l) => s + Math.round(l.quantite * l.prixUnitaire), 0));

  constructor() {
    void this.charger();
    void firstValueFrom(this.http.get<{ adaptateur: string; mode?: string }>(`${configuration.api}/v1/conformite/adaptateur`))
      .then((a) => this.adaptateur.set(a)).catch(() => undefined);
    void firstValueFrom(this.referentiel.taxes()).then((t) => (this.taxes = new Map(t.map((x) => [x.id, { code: x.code, taux: Number(x.taux) }]))));
  }

  ngOnDestroy(): void {
    this.liberer();
  }

  protected async charger(): Promise<void> {
    this.documents.set(await firstValueFrom(this.http.get<DocumentFiscal[]>(this.base)));
    const s = this.selection();
    if (s) {
      const maj = this.documents().find((d) => d.id === s.id) ?? null;
      this.selection.set(maj);
    }
  }

  protected async ouvrir(d: DocumentFiscal): Promise<void> {
    this.edition.set(false);
    this.avoirOuvert.set(false);
    this.erreur.set(null);
    this.selection.set(d);
    await this.chargerApercu(d.id);
  }

  private async chargerApercu(id: string): Promise<void> {
    this.liberer();
    try {
      const pdf = await firstValueFrom(this.http.get(`${this.base}/${id}/pdf`, { responseType: 'blob' }));
      this.urlApercu = URL.createObjectURL(pdf);
      this.apercu.set(this.assainisseur.bypassSecurityTrustResourceUrl(this.urlApercu));
    } catch {
      this.apercu.set(null);
    }
  }

  private liberer(): void {
    if (this.urlApercu) {
      URL.revokeObjectURL(this.urlApercu);
      this.urlApercu = null;
    }
    this.apercu.set(null);
  }

  protected nouvelle(): void {
    this.selection.set(null);
    this.liberer();
    this.editionId = null;
    this.client = null;
    this.rechercheClient = '';
    this.lignes.set([]);
    this.erreur.set(null);
    this.edition.set(true);
  }

  protected modifier(d: DocumentFiscal): void {
    this.editionId = d.id;
    this.client = { id: d.clientId, nom: d.clientNom ?? '', ifu: d.clientIfu } as Tiers;
    this.lignes.set(d.lignes.map((l) => ({ produitId: l.produitId, designation: l.designation, quantite: Number(l.quantite),
      prixUnitaire: l.prixUnitaire, prixTtc: l.prixTtc, taxeCode: l.taxeCode, taux: Number(l.taux) })));
    this.edition.set(true);
  }

  protected async chercherClients(): Promise<void> {
    this.clients.set((await firstValueFrom(this.referentiel.tiers(this.rechercheClient, 0, { client: 'true' }))).elements.slice(0, 8));
  }

  protected async chercherProduits(): Promise<void> {
    this.produits.set((await firstValueFrom(this.referentiel.produits(this.rechercheProduit, 0))).elements.slice(0, 8));
  }

  protected choisirClient(t: Tiers): void {
    this.client = t;
    this.clients.set([]);
    this.rechercheClient = t.nom;
  }

  protected ajouterProduit(p: Produit): void {
    const taxe = this.taxes.get(p.taxeId);
    this.lignes.update((l) => [...l, { produitId: p.id, designation: p.nom, quantite: 1, prixUnitaire: p.prixVente, prixTtc: p.prixVenteTtc,
      taxeCode: taxe?.code ?? null, taux: taxe?.taux ?? 0 }]);
    this.produits.set([]);
    this.rechercheProduit = '';
  }

  protected ajouterLigneLibre(): void {
    const tva = [...this.taxes.values()].find((t) => t.code === 'TVA18');
    this.lignes.update((l) => [...l, { produitId: null, designation: '', quantite: 1, prixUnitaire: 0, prixTtc: false, taxeCode: tva?.code ?? null,
      taux: tva?.taux ?? 0 }]);
  }

  protected retirerLigne(i: number): void {
    this.lignes.update((l) => l.filter((_, j) => j !== i));
  }

  protected async enregistrer(): Promise<DocumentFiscal | null> {
    this.erreur.set(null);
    if (!this.client) {
      this.erreur.set('Choisissez le client.');
      return null;
    }
    const corps = {
      id: this.editionId ?? uuid7(), etablissementId: this.contexte.etablissement()?.id ?? null, clientId: this.client.id,
      lignes: this.lignes().map((l) => ({ ...l, quantite: String(l.quantite) })),
    };
    try {
      const doc = this.editionId
        ? await firstValueFrom(this.http.put<DocumentFiscal>(`${this.base}/${this.editionId}`, corps))
        : await firstValueFrom(this.http.post<DocumentFiscal>(this.base, corps));
      this.edition.set(false);
      await this.charger();
      await this.ouvrir(doc);
      this.notifications.succes('Brouillon enregistré.');
      return doc;
    } catch (e) {
      this.erreur.set(messageErreur(e, "La facture n'a pas pu être enregistrée."));
      return null;
    }
  }

  protected async valider(d: DocumentFiscal): Promise<void> {
    this.erreur.set(null);
    this.enCours.set(true);
    try {
      const valide = await firstValueFrom(this.http.post<DocumentFiscal>(`${this.base}/${d.id}/validation`, {}));
      this.notifications.succes(`Facture ${valide.numero} validée et transmise pour certification.`);
      await this.charger();
      await this.ouvrir(valide);
      setTimeout(() => void this.rafraichirSelection(), 1500);
    } catch (e) {
      this.erreur.set(messageErreur(e, "La facture n'a pas pu être validée."));
    } finally {
      this.enCours.set(false);
    }
  }

  protected async rafraichirSelection(): Promise<void> {
    await this.charger();
    const s = this.selection();
    if (s) {
      await this.chargerApercu(s.id);
    }
  }

  protected async supprimer(d: DocumentFiscal): Promise<void> {
    await firstValueFrom(this.http.delete(`${this.base}/${d.id}`));
    this.selection.set(null);
    this.liberer();
    await this.charger();
  }

  protected ouvrirAvoir(d: DocumentFiscal): void {
    this.motifAvoir = '';
    this.quantitesAvoir = Object.fromEntries(d.lignes.map((l) => [l.id, 0]));
    this.avoirOuvert.set(true);
  }

  protected async emettreAvoir(d: DocumentFiscal, total: boolean): Promise<void> {
    this.erreur.set(null);
    const quantites = total ? {} : Object.fromEntries(Object.entries(this.quantitesAvoir).filter(([, q]) => q > 0).map(([k, q]) => [k, String(q)]));
    try {
      const avoir = await firstValueFrom(this.http.post<DocumentFiscal>(`${this.base}/${d.id}/avoirs`, { id: uuid7(), motif: this.motifAvoir, quantites }));
      this.notifications.succes(`Avoir ${avoir.numero} émis.`);
      this.avoirOuvert.set(false);
      await this.charger();
      await this.ouvrir(avoir);
    } catch (e) {
      this.erreur.set(messageErreur(e, "L'avoir n'a pas pu être émis."));
    }
  }

  protected async relancer(d: DocumentFiscal): Promise<void> {
    await firstValueFrom(this.http.post(`${configuration.api}/v1/conformite/file/${d.id}/relance`, {}));
    await this.rafraichirSelection();
  }

  protected async changerSimulateur(mode: string): Promise<void> {
    this.adaptateur.set(await firstValueFrom(this.http.put<{ adaptateur: string; mode?: string }>(`${configuration.api}/v1/conformite/simulateur`, { mode })));
  }

  protected telecharger(d: DocumentFiscal): void {
    void firstValueFrom(this.http.get(`${this.base}/${d.id}/pdf`, { responseType: 'blob' })).then((pdf) => {
      const lien = document.createElement('a');
      lien.href = URL.createObjectURL(pdf);
      lien.download = `${d.numero ?? 'brouillon'}.pdf`;
      lien.click();
      setTimeout(() => URL.revokeObjectURL(lien.href), 1000);
    });
  }

  protected numeroOrigine(d: DocumentFiscal): string {
    return this.documents().find((x) => x.id === d.factureOrigineId)?.numero ?? '';
  }
}
