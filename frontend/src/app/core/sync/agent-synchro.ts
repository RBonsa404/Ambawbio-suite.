import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Injectable, InjectionToken, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { configuration } from '../configuration';
import { uuid7 } from '../identifiant';
import { ServiceReseau } from '../service-reseau';
import { ClesTerminal } from './cles-terminal';
import { Changement, DexieStore, LocalStore, OperationSortante } from './local-store';

export const LOCAL_STORE = new InjectionToken<LocalStore>('LocalStore', { providedIn: 'root', factory: () => new DexieStore() });

export interface PlageLocale {
  id: string;
  typePiece: 'TICKET' | 'FACTURE' | 'AVOIR';
  prefixe: string;
  annee: number;
  debut: number;
  fin: number;
  prochain: number;
}

export interface TerminalLocal {
  id: string;
  code: string;
  nom: string;
  etablissementId: string;
}

interface Accuse {
  idOperation: string;
  statut: 'APPLIQUEE' | 'IGNOREE_DOUBLON' | 'EN_CONFLIT' | 'REFUSEE' | 'A_RENVOYER';
  motif: string | null;
}

interface Reception {
  changements: Changement[];
  curseur: number;
  encore: boolean;
  plages: PlageLocale[];
}

const TAILLE_LOT = 100;
const INTERVALLE = 2 * 60 * 1000;
const DELAI_MAX = 5 * 60 * 1000;
/** Guide §8.7 : alerte si plus de 24 h sans synchronisation. */
const ALERTE_SANS_SYNCHRO = 24 * 60 * 60 * 1000;

/**
 * Agent de synchronisation (guide §8.7) : envoi par lots de l'outbox, puis réception paginée des changements.
 * Déclenché au retour du réseau, toutes les 2 minutes en ligne et à la demande ; verrou anti-doublon ; réessais
 * avec délai exponentiel plafonné. Seules les opérations accusées APPLIQUEE ou IGNOREE_DOUBLON quittent la file.
 */
@Injectable({ providedIn: 'root' })
export class AgentSynchro {
  private readonly http = inject(HttpClient);
  private readonly reseau = inject(ServiceReseau);
  readonly store = inject(LOCAL_STORE);
  private readonly cles = new ClesTerminal(this.store);

  readonly terminal = signal<TerminalLocal | null>(null);
  readonly enAttente = signal(0);
  readonly conflits = signal(0);
  readonly envoyees = signal(0);
  readonly enCours = signal(false);
  readonly derniereSynchro = signal<Date | null>(null);
  readonly revoque = signal(false);
  readonly plages = signal<PlageLocale[]>([]);
  readonly erreur = signal<string | null>(null);

  readonly plageEnAlerte = computed(() =>
    this.plages().some((p) => p.prochain - p.debut >= (p.fin - p.debut + 1) * 0.8 && !this.plages().some((q) => q !== p && q.typePiece === p.typePiece && q.debut > p.debut)),
  );
  readonly etat = computed<'en-ligne' | 'hors-ligne' | 'envoi' | 'en-attente' | 'alerte'>(() => {
    const derniere = this.derniereSynchro();
    if (this.revoque() || this.plageEnAlerte() || (derniere && Date.now() - derniere.getTime() > ALERTE_SANS_SYNCHRO)) {
      return 'alerte';
    }
    if (!this.reseau.enLigne()) {
      return 'hors-ligne';
    }
    if (this.enCours()) {
      return 'envoi';
    }
    return this.enAttente() > 0 ? 'en-attente' : 'en-ligne';
  });

  private verrou: Promise<void> | null = null;
  private echecs = 0;
  private minuterie: ReturnType<typeof setTimeout> | null = null;

  async initialiser(): Promise<void> {
    this.terminal.set((await this.store.lireMeta<TerminalLocal>('terminal')) ?? null);
    this.plages.set((await this.store.lireMeta<PlageLocale[]>('plages')) ?? []);
    const derniere = await this.store.lireMeta<string>('derniereSynchro');
    this.derniereSynchro.set(derniere ? new Date(derniere) : null);
    await this.compter();
    if (typeof navigator !== 'undefined' && navigator.storage?.persist) {
      void navigator.storage.persist();
    }
    if (typeof window !== 'undefined') {
      window.addEventListener('online', () => void this.synchroniser());
    }
    this.planifier(INTERVALLE);
  }

  /** Enregistre une opération signée dans la file locale (fonctionne hors-ligne). */
  async enregistrer(type: string, charge: unknown, utilisateurId: string | null = null): Promise<OperationSortante> {
    const operation: OperationSortante = {
      idOperation: uuid7(),
      type,
      versionSchema: 1,
      horodatageLocal: new Date().toISOString(),
      utilisateurId,
      charge: JSON.stringify(charge),
      signature: '',
      statut: 'EN_ATTENTE',
      motif: null,
      tentatives: 0,
    };
    operation.signature = await this.cles.signer(operation.idOperation, type, operation.horodatageLocal, operation.charge);
    await this.store.ajouterOperation(operation);
    await this.compter();
    return operation;
  }

  /** Appairage (SD-11) : clés du terminal, présentation du code du QR code, plages reçues. */
  async appairer(terminalId: string, code: string): Promise<TerminalLocal> {
    const clePublique = await this.cles.creer();
    const reponse = await firstValueFrom(
      this.http.post<{ terminal: TerminalLocal; plages: PlageLocale[] }>(`${configuration.api}/v1/socle/terminaux/appairage`, { terminalId, code, clePublique }),
    );
    await this.store.ecrireMeta('terminal', reponse.terminal);
    await this.store.ecrireMeta('curseur', 0);
    await this.enregistrerPlages(reponse.plages);
    this.terminal.set(reponse.terminal);
    this.revoque.set(false);
    return reponse.terminal;
  }

  /** Synchronisation complète ; un seul passage à la fois. */
  synchroniser(surProgression?: (recus: number) => void): Promise<void> {
    this.verrou ??= this.executer(surProgression).finally(() => (this.verrou = null));
    return this.verrou;
  }

  private async executer(surProgression?: (recus: number) => void): Promise<void> {
    const terminal = this.terminal();
    if (!terminal || this.revoque()) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    try {
      await this.envoyer(terminal);
      await this.recevoir(terminal, surProgression);
      const maintenant = new Date();
      await this.store.ecrireMeta('derniereSynchro', maintenant.toISOString());
      this.derniereSynchro.set(maintenant);
      this.echecs = 0;
      this.planifier(INTERVALLE);
    } catch (e) {
      const erreur = e as HttpErrorResponse;
      if (erreur.status === 403 && erreur.error?.code === 'TERMINAL_REVOQUE') {
        this.revoque.set(true);
        this.erreur.set(erreur.error.detail);
      } else {
        this.echecs++;
        this.erreur.set(erreur.status === 0 ? 'Réseau indisponible : nouvel essai automatique.' : (erreur.error?.detail ?? 'Synchronisation interrompue.'));
        this.planifier(Math.min(DELAI_MAX, 5000 * 2 ** this.echecs));
      }
    } finally {
      this.enCours.set(false);
      await this.compter();
    }
  }

  private async envoyer(terminal: TerminalLocal): Promise<void> {
    this.envoyees.set(0);
    for (;;) {
      const lot = await this.store.operationsAEnvoyer(TAILLE_LOT);
      const consommation = this.plages().map((p) => ({ id: p.id, prochain: p.prochain }));
      if (lot.length === 0 && consommation.length === 0) {
        return;
      }
      const corps = {
        terminalId: terminal.id,
        operations: lot.map(({ idOperation, type, versionSchema, horodatageLocal, utilisateurId, charge, signature }) => ({
          idOperation, type, versionSchema, horodatageLocal, utilisateurId, charge, signature,
        })),
        plages: consommation,
      };
      const reponse = await firstValueFrom(this.http.post<{ accuses: Accuse[] }>(`${configuration.api}/v1/sync/push`, await compresser(corps), {
        headers: enTetesCompression(),
      }));
      const retirees: string[] = [];
      for (const a of reponse.accuses) {
        if (a.statut === 'APPLIQUEE' || a.statut === 'IGNOREE_DOUBLON') {
          retirees.push(a.idOperation);
        } else if (a.statut === 'EN_CONFLIT' || a.statut === 'REFUSEE') {
          await this.store.marquerOperation(a.idOperation, a.statut, a.motif);
        }
      }
      await this.store.retirerOperations(retirees);
      this.envoyees.update((n) => n + retirees.length);
      if (lot.length < TAILLE_LOT || retirees.length === 0) {
        return;
      }
    }
  }

  private async recevoir(terminal: TerminalLocal, surProgression?: (recus: number) => void): Promise<void> {
    let curseur = (await this.store.lireMeta<number>('curseur')) ?? 0;
    let recus = 0;
    for (;;) {
      const page = await firstValueFrom(
        this.http.get<Reception>(`${configuration.api}/v1/sync/pull`, { params: { terminalId: terminal.id, curseur, limite: 500 } }),
      );
      await this.store.appliquerChangements(page.changements);
      curseur = page.curseur;
      await this.store.ecrireMeta('curseur', curseur);
      await this.enregistrerPlages(page.plages);
      recus += page.changements.length;
      surProgression?.(recus);
      if (!page.encore) {
        return;
      }
    }
  }

  /** Les plages reçues remplacent les locales ; le prochain numéro local ne recule jamais. */
  private async enregistrerPlages(recues: PlageLocale[]): Promise<void> {
    const locales = new Map(this.plages().map((p) => [p.id, p]));
    const fusion = recues.map((p) => ({ ...p, prochain: Math.max(p.prochain, locales.get(p.id)?.prochain ?? 0) }));
    this.plages.set(fusion);
    await this.store.ecrireMeta('plages', fusion);
  }

  private async compter(): Promise<void> {
    this.enAttente.set(await this.store.compterAEnvoyer());
    this.conflits.set((await this.store.toutesLesOperations()).filter((o) => o.statut !== 'EN_ATTENTE').length);
  }

  private planifier(delai: number): void {
    if (typeof window === 'undefined') {
      return;
    }
    if (this.minuterie) {
      clearTimeout(this.minuterie);
    }
    this.minuterie = setTimeout(() => {
      if (this.reseau.enLigne()) {
        void this.synchroniser();
      } else {
        this.planifier(INTERVALLE);
      }
    }, delai);
  }
}

/** Corps compressé en gzip quand le navigateur le permet (ENF-02) ; sinon JSON simple. */
async function compresser(corps: unknown): Promise<unknown> {
  if (typeof CompressionStream === 'undefined') {
    return corps;
  }
  const flux = new Response(JSON.stringify(corps)).body!.pipeThrough(new CompressionStream('gzip'));
  return new Response(flux).blob();
}

function enTetesCompression(): HttpHeaders {
  return typeof CompressionStream === 'undefined'
    ? new HttpHeaders({ 'Content-Type': 'application/json' })
    : new HttpHeaders({ 'Content-Type': 'application/json', 'Content-Encoding': 'gzip' });
}
