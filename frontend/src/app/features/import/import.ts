import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';

import { ApiReferentiel, ResultatImport } from '../../core/api-referentiel';
import { messageErreur } from '../../shared/raccourcis';
import { Bouton } from '../../shared/ui/bouton';
import { Icone } from '../../shared/ui/icone';
import { Notifications } from '../../shared/ui/notifications';

type Etape = 1 | 2 | 3 | 4;

/** W-07 Import de données (UC-SOC-06) : 1. Modèle → 2. Fichier → 3. Vérification (rapport ligne par ligne) → 4. Import. */
@Component({
  selector: 'amb-import',
  imports: [RouterLink, TranslocoPipe, Bouton, Icone],
  templateUrl: './import.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssistantImport {
  private readonly api = inject(ApiReferentiel);
  private readonly notifications = inject(Notifications);

  protected readonly type = signal<'produits' | 'tiers'>(inject(ActivatedRoute).snapshot.queryParamMap.get('type') === 'tiers' ? 'tiers' : 'produits');
  protected readonly etape = signal<Etape>(1);
  protected readonly fichier = signal<File | null>(null);
  protected readonly verification = signal<ResultatImport | null>(null);
  protected readonly resultat = signal<ResultatImport | null>(null);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly lignesValides = computed(() => {
    const v = this.verification();
    return v ? v.lignesTotal - new Set(v.erreurs.map((e) => e.ligne)).size : 0;
  });

  protected choisirType(type: 'produits' | 'tiers'): void {
    this.type.set(type);
    this.recommencer();
  }

  protected async telechargerModele(): Promise<void> {
    await this.enregistrer(this.api.urlModele(this.type()), `modele-${this.type()}.csv`);
    this.etape.set(2);
  }

  protected async choisirFichier(evenement: Event): Promise<void> {
    const fichier = (evenement.target as HTMLInputElement).files?.[0] ?? null;
    this.fichier.set(fichier);
    if (fichier) {
      await this.verifier();
    }
  }

  protected async verifier(): Promise<void> {
    const fichier = this.fichier();
    if (!fichier) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    try {
      this.verification.set(await firstValueFrom(this.api.importer(this.type(), fichier, 'VERIFICATION', false)));
      this.etape.set(3);
    } catch (e) {
      this.erreur.set(messageErreur(e, "Le fichier n'a pas pu être lu."));
    } finally {
      this.enCours.set(false);
    }
  }

  protected async importer(): Promise<void> {
    const fichier = this.fichier();
    if (!fichier) {
      return;
    }
    this.enCours.set(true);
    try {
      const resultat = await firstValueFrom(this.api.importer(this.type(), fichier, 'IMPORT', true));
      this.resultat.set(resultat);
      this.etape.set(4);
      this.notifications.succes(`${resultat.lignesImportees} lignes importées.`);
    } catch (e) {
      this.erreur.set(messageErreur(e, "L'import n'a pas pu être réalisé."));
    } finally {
      this.enCours.set(false);
    }
  }

  protected async telechargerRapport(): Promise<void> {
    const v = this.verification();
    if (v) {
      await this.enregistrer(this.api.urlRapport(v.id), `rapport-import-${this.type()}.csv`);
    }
  }

  protected recommencer(): void {
    this.etape.set(1);
    this.fichier.set(null);
    this.verification.set(null);
    this.resultat.set(null);
    this.erreur.set(null);
  }

  private async enregistrer(url: string, nom: string): Promise<void> {
    const contenu = await firstValueFrom(this.api.telecharger(url));
    const lien = document.createElement('a');
    lien.href = URL.createObjectURL(contenu);
    lien.download = nom;
    lien.click();
    URL.revokeObjectURL(lien.href);
  }
}
