import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormGroup, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';

import { ApiReferentiel, DefinitionChamp, Page, Regime, Tiers } from '../../core/api-referentiel';
import { uuid7 } from '../../core/identifiant';
import { ServiceContexte } from '../../core/service-contexte';
import { gererRaccourcis, messageErreur } from '../../shared/raccourcis';
import { Bouton } from '../../shared/ui/bouton';
import { Champ } from '../../shared/ui/champ';
import { ChampMontant } from '../../shared/ui/champ-montant';
import { ChampsDynamiques, controlesChamps, valeursChamps } from '../../shared/ui/champs-dynamiques';
import { EtatVide } from '../../shared/ui/etat-vide';
import { Icone } from '../../shared/ui/icone';
import { Notifications } from '../../shared/ui/notifications';
import { Pagination } from '../../shared/ui/pagination';

const OPERATEURS = [
  { code: 'ORANGE_MONEY', libelle: 'Orange Money' },
  { code: 'MOOV_MONEY', libelle: 'Moov Money' },
  { code: 'WAVE', libelle: 'Wave' },
] as const;

/** W-06 Clients et fournisseurs : liste et fiche (IFU guidé, régime, Mobile Money, champs personnalisés). */
@Component({
  selector: 'amb-tiers',
  imports: [ReactiveFormsModule, TranslocoPipe, Bouton, Champ, ChampMontant, ChampsDynamiques, EtatVide, Icone, Pagination],
  templateUrl: './tiers.html',
  host: { '(document:keydown)': 'raccourci($event)' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListeTiers {
  private readonly api = inject(ApiReferentiel);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly notifications = inject(Notifications);
  protected readonly contexte = inject(ServiceContexte);
  private readonly recherche = viewChild<ElementRef<HTMLInputElement>>('recherche');
  protected readonly operateurs = OPERATEURS;

  protected readonly q = signal('');
  protected readonly filtre = signal<'tous' | 'clients' | 'fournisseurs'>('tous');
  protected readonly page = signal(0);
  protected readonly resultat = signal<Page<Tiers> | null>(null);
  protected readonly selection = signal<Tiers | null>(null);
  protected readonly creation = signal(false);
  protected readonly enregistrement = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly regimes = signal<Regime[]>([]);
  protected readonly definitions = signal<DefinitionChamp[]>([]);
  protected readonly peutModifier = computed(() => this.contexte.peut('referentiel:gerer') || this.contexte.peut('ventes:gerer'));
  protected readonly ficheOuverte = computed(() => this.creation() || this.selection() !== null);

  protected formulaire = this.nouveauFormulaire();
  protected champs: FormGroup = new FormGroup({});

  constructor() {
    void Promise.all([firstValueFrom(this.api.regimes()), firstValueFrom(this.api.champs('tiers'))]).then(([regimes, definitions]) => {
      this.regimes.set(regimes);
      this.definitions.set(definitions);
      this.champs = controlesChamps(definitions);
    });
    this.rechercher();
  }

  private nouveauFormulaire() {
    return this.fb.group({
      code: ['', Validators.required],
      nom: ['', Validators.required],
      nature: this.fb.control<string | null>(null),
      estClient: [true],
      estFournisseur: [false],
      ifu: ['', Validators.pattern(/^\s*\d{8}\s*[A-Za-z]\s*$/)],
      rccm: [''],
      regimeCode: [''],
      telephone: [''],
      courriel: ['', Validators.email],
      adresse: [''],
      ville: [''],
      delaiPaiementJours: [0, Validators.min(0)],
      plafondCredit: this.fb.control<number | null>(null),
      operateur: [''],
      numeroMobileMoney: [''],
    });
  }

  protected rechercher(page = 0): void {
    this.page.set(page);
    const filtres: Record<string, string> = this.filtre() === 'clients' ? { client: 'true' } : this.filtre() === 'fournisseurs' ? { fournisseur: 'true' } : {};
    this.api.tiers(this.q(), page, filtres).subscribe((r) => this.resultat.set(r));
  }

  protected ouvrir(t: Tiers): void {
    this.creation.set(false);
    this.selection.set(t);
    this.erreur.set(null);
    this.formulaire = this.nouveauFormulaire();
    const compte = t.comptesMobileMoney.find((c) => c.parDefaut) ?? t.comptesMobileMoney[0];
    this.formulaire.patchValue({
      ...t,
      ifu: t.ifu ?? '',
      rccm: t.rccm ?? '',
      telephone: t.telephone ?? '',
      courriel: t.courriel ?? '',
      adresse: t.adresse ?? '',
      ville: t.ville ?? '',
      regimeCode: this.regimes().find((r) => r.id === t.regimeFiscalId)?.code ?? '',
      operateur: compte?.operateur ?? '',
      numeroMobileMoney: compte?.numero ?? '',
    });
    this.champs = controlesChamps(this.definitions(), t.champsPerso);
    if (!this.peutModifier()) {
      this.formulaire.disable();
      this.champs.disable();
    }
  }

  protected nouveau(): void {
    if (!this.peutModifier()) {
      return;
    }
    this.selection.set(null);
    this.creation.set(true);
    this.erreur.set(null);
    this.formulaire = this.nouveauFormulaire();
    this.champs = controlesChamps(this.definitions());
  }

  protected fermer(): void {
    this.selection.set(null);
    this.creation.set(false);
  }

  protected async enregistrer(): Promise<void> {
    this.formulaire.markAllAsTouched();
    this.champs.markAllAsTouched();
    if (this.formulaire.invalid || this.champs.invalid) {
      this.erreur.set('Complétez ou corrigez les champs signalés.');
      return;
    }
    const v = this.formulaire.getRawValue();
    const tiers = {
      code: v.code, nom: v.nom, nature: v.nature || null, estClient: v.estClient, estFournisseur: v.estFournisseur,
      ifu: v.ifu || null, rccm: v.rccm || null, regimeCode: v.regimeCode || null, telephone: v.telephone || null,
      courriel: v.courriel || null, adresse: v.adresse || null, ville: v.ville || null, delaiPaiementJours: v.delaiPaiementJours,
      plafondCredit: v.plafondCredit,
      comptesMobileMoney: v.operateur && v.numeroMobileMoney ? [{ operateur: v.operateur, numero: v.numeroMobileMoney, titulaire: v.nom, parDefaut: true }] : [],
      champsPerso: valeursChamps(this.champs),
    };
    this.enregistrement.set(true);
    try {
      const enregistre = await firstValueFrom(this.api.enregistrerTiers(this.selection()?.id ?? uuid7(), tiers, this.creation()));
      this.notifications.succes(`Fiche « ${enregistre.nom} » enregistrée.`);
      this.rechercher(this.page());
      this.ouvrir(enregistre);
    } catch (e) {
      this.erreur.set(messageErreur(e, "La fiche n'a pas pu être enregistrée."));
    } finally {
      this.enregistrement.set(false);
    }
  }

  protected raccourci(evenement: KeyboardEvent): void {
    gererRaccourcis(evenement, {
      rechercher: () => this.recherche()?.nativeElement.focus(),
      nouveau: () => this.nouveau(),
      deplacer: (pas) => {
        const elements = this.resultat()?.elements ?? [];
        const index = elements.findIndex((t) => t.id === this.selection()?.id);
        const suivant = elements[Math.min(Math.max(index + pas, 0), elements.length - 1)];
        if (suivant) {
          this.ouvrir(suivant);
        }
      },
    });
  }
}
