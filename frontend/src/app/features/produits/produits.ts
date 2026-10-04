import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormArray, FormControl, FormGroup, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';

import { ApiReferentiel, Categorie, DefinitionChamp, Page, Parametre, Produit, Taxe } from '../../core/api-referentiel';
import { uuid7 } from '../../core/identifiant';
import { ServiceContexte } from '../../core/service-contexte';
import { gererRaccourcis, messageErreur } from '../../shared/raccourcis';
import { Bouton } from '../../shared/ui/bouton';
import { Champ } from '../../shared/ui/champ';
import { ChampMontant } from '../../shared/ui/champ-montant';
import { ChampsDynamiques, controlesChamps, valeursChamps } from '../../shared/ui/champs-dynamiques';
import { EtatVide } from '../../shared/ui/etat-vide';
import { FcfaPipe } from '../../shared/ui/fcfa.pipe';
import { Icone } from '../../shared/ui/icone';
import { Notifications } from '../../shared/ui/notifications';
import { Pagination } from '../../shared/ui/pagination';

/** W-05 Produits : liste et fiche côte à côte (D-04), conditionnements, codes-barres, champs personnalisés (UC-SOC-09). */
@Component({
  selector: 'amb-produits',
  imports: [ReactiveFormsModule, RouterLink, TranslocoPipe, Bouton, Champ, ChampMontant, ChampsDynamiques, EtatVide, FcfaPipe, Icone, Pagination],
  templateUrl: './produits.html',
  host: { '(document:keydown)': 'raccourci($event)' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Produits {
  private readonly api = inject(ApiReferentiel);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly notifications = inject(Notifications);
  protected readonly contexte = inject(ServiceContexte);
  private readonly recherche = viewChild<ElementRef<HTMLInputElement>>('recherche');

  protected readonly q = signal('');
  protected readonly page = signal(0);
  protected readonly resultat = signal<Page<Produit> | null>(null);
  protected readonly selection = signal<Produit | null>(null);
  protected readonly creation = signal(false);
  protected readonly enregistrement = signal(false);
  protected readonly erreur = signal<string | null>(null);

  protected readonly taxes = signal<Taxe[]>([]);
  protected readonly unites = signal<Parametre[]>([]);
  protected readonly categories = signal<Categorie[]>([]);
  protected readonly typesConditionnement = signal<Parametre[]>([]);
  protected readonly definitions = signal<DefinitionChamp[]>([]);
  protected readonly peutModifier = computed(() => this.contexte.peut('referentiel:gerer'));
  protected readonly ficheOuverte = computed(() => this.creation() || this.selection() !== null);

  protected formulaire = this.nouveauFormulaire();
  protected champs: FormGroup = new FormGroup({});

  constructor() {
    void this.chargerParametres();
    this.rechercher();
  }

  private nouveauFormulaire() {
    return this.fb.group({
      code: ['', [Validators.required, Validators.maxLength(40)]],
      nom: ['', Validators.required],
      type: this.fb.control<'BIEN' | 'SERVICE'>('BIEN'),
      categorieId: this.fb.control<string | null>(null),
      uniteCode: ['U'],
      taxeCode: ['TVA18', Validators.required],
      prixVente: this.fb.control<number | null>(null, Validators.required),
      prixVenteTtc: [true],
      prixAchat: this.fb.control<number | null>(null),
      suiviStock: [true],
      actif: [true],
      conditionnements: this.fb.array<FormGroup>([]),
      codesBarres: [''],
    });
  }

  protected get conditionnements(): FormArray<FormGroup> {
    return this.formulaire.controls.conditionnements;
  }

  private async chargerParametres(): Promise<void> {
    const [taxes, unites, categories, types, definitions] = await Promise.all([
      firstValueFrom(this.api.taxes()), firstValueFrom(this.api.unites()), firstValueFrom(this.api.categories()),
      firstValueFrom(this.api.typesConditionnement()), firstValueFrom(this.api.champs('produit')),
    ]);
    this.taxes.set(taxes);
    this.unites.set(unites);
    this.categories.set(categories);
    this.typesConditionnement.set(types);
    this.definitions.set(definitions);
    this.champs = controlesChamps(definitions);
  }

  protected rechercher(page = 0): void {
    this.page.set(page);
    this.api.produits(this.q(), page).subscribe((r) => this.resultat.set(r));
  }

  protected ouvrir(produit: Produit): void {
    this.creation.set(false);
    this.selection.set(produit);
    this.erreur.set(null);
    const unite = this.unites().find((u) => u.id === produit.uniteId)?.code ?? 'U';
    const taxe = this.taxes().find((t) => t.id === produit.taxeId)?.code ?? 'TVA18';
    this.formulaire = this.nouveauFormulaire();
    this.formulaire.patchValue({ ...produit, uniteCode: unite, taxeCode: taxe, codesBarres: produit.codesBarres.map((c) => c.valeur).join(', ') });
    produit.conditionnements.forEach((c) => this.ajouterConditionnement(c.code, c.quantite, c.prixVente));
    this.champs = controlesChamps(this.definitions(), produit.champsPerso);
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

  protected ajouterConditionnement(code = 'CARTON', quantite: number | null = null, prix: number | null = null): void {
    this.conditionnements.push(
      new FormGroup({
        code: new FormControl(code, { nonNullable: true }),
        quantite: new FormControl<number | null>(quantite, [Validators.required, Validators.min(0.001)]),
        prixVente: new FormControl<number | null>(prix),
      }),
    );
  }

  protected async enregistrer(): Promise<void> {
    this.formulaire.markAllAsTouched();
    this.champs.markAllAsTouched();
    if (this.formulaire.invalid || this.champs.invalid) {
      this.erreur.set('Complétez les champs obligatoires signalés.');
      return;
    }
    const v = this.formulaire.getRawValue();
    const produit = {
      ...v,
      conditionnements: v.conditionnements.map((c) => ({ code: c['code'], libelle: null, quantite: Number(c['quantite']), prixVente: c['prixVente'] })),
      codesBarres: v.codesBarres.split(/[\s,;]+/).filter(Boolean).map((valeur) => ({ valeur, conditionnementCode: null })),
      champsPerso: valeursChamps(this.champs),
    };
    this.enregistrement.set(true);
    try {
      const enregistre = await firstValueFrom(this.api.enregistrerProduit(this.selection()?.id ?? uuid7(), produit, this.creation()));
      this.notifications.succes(`Produit « ${enregistre.nom} » enregistré.`);
      this.rechercher(this.page());
      this.ouvrir(enregistre);
    } catch (e) {
      this.erreur.set(messageErreur(e, "Le produit n'a pas pu être enregistré."));
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
        const index = elements.findIndex((p) => p.id === this.selection()?.id);
        const suivant = elements[Math.min(Math.max(index + pas, 0), elements.length - 1)];
        if (suivant) {
          this.ouvrir(suivant);
        }
      },
    });
  }
}
