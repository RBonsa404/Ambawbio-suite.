import Dexie, { Table } from 'dexie';

import { Chiffre, Chiffreur, cleStockage } from './chiffrement';

/** Opération en attente d'envoi (outbox, guide §8.7). */
export interface OperationSortante {
  idOperation: string;
  type: string;
  versionSchema: number;
  horodatageLocal: string;
  utilisateurId: string | null;
  /** Texte JSON exact qui a été signé. */
  charge: string;
  signature: string;
  statut: 'EN_ATTENTE' | 'EN_CONFLIT' | 'REFUSEE';
  motif: string | null;
  tentatives: number;
}

export interface Changement {
  sequence: number;
  entite: string;
  entiteId: string;
  operation: 'UPSERT' | 'SUPPRESSION';
  donnees: Record<string, unknown> | null;
}

export interface EntiteLocale {
  entite: string;
  id: string;
  donnees: Record<string, unknown>;
}

/**
 * Stockage local du terminal : interface unique (guide §8.7). Implémentation IndexedDB (Dexie) pour le navigateur
 * et, au LOT 4, pour Android (WebView) ; la base SQLite chiffrée la remplace sur Android au LOT 5 (D-26).
 */
export interface LocalStore {
  lireMeta<T>(cle: string): Promise<T | undefined>;
  ecrireMeta<T>(cle: string, valeur: T): Promise<void>;
  ajouterOperation(operation: OperationSortante): Promise<void>;
  operationsAEnvoyer(limite: number): Promise<OperationSortante[]>;
  toutesLesOperations(): Promise<OperationSortante[]>;
  retirerOperations(ids: string[]): Promise<void>;
  marquerOperation(id: string, statut: OperationSortante['statut'], motif: string | null): Promise<void>;
  compterAEnvoyer(): Promise<number>;
  appliquerChangements(changements: Changement[]): Promise<void>;
  entites(entite: string): Promise<EntiteLocale[]>;
  /** Entité propre au terminal (pièces de caisse, session) : jamais écrasée par la réception. */
  enregistrerLocale(entite: string, id: string, donnees: Record<string, unknown>): Promise<void>;
  compterEntites(entite: string): Promise<number>;
  vider(): Promise<void>;
}

class BaseTerminal extends Dexie {
  operations!: Table<Omit<OperationSortante, 'charge'> & { charge: string | Chiffre }, string>;
  entites!: Table<{ entite: string; id: string; donnees: Record<string, unknown> | Chiffre }, [string, string]>;
  meta!: Table<{ cle: string; valeur: unknown }, string>;

  constructor(nom: string) {
    super(nom);
    this.version(1).stores({
      operations: 'idOperation, statut, horodatageLocal',
      entites: '[entite+id], entite',
      meta: 'cle',
    });
  }
}

/** Stockage IndexedDB (Dexie) chiffré : charges des opérations et données des entités en AES-GCM (D-33). */
export class DexieStore implements LocalStore {
  private readonly base: BaseTerminal;
  private readonly chiffreur: Chiffreur;

  constructor(nom = 'ambawbio-terminal', chiffreur?: Chiffreur) {
    this.base = new BaseTerminal(nom);
    this.chiffreur =
      chiffreur ??
      new Chiffreur(() =>
        cleStockage(
          () => this.lireMeta<CryptoKey>('cle-stockage'),
          (cle) => this.ecrireMeta('cle-stockage', cle),
        ),
      );
  }

  private async ouvrirOperation(o: Omit<OperationSortante, 'charge'> & { charge: string | Chiffre }): Promise<OperationSortante> {
    return { ...o, charge: await this.chiffreur.dechiffrer<string>(o.charge) };
  }

  async lireMeta<T>(cle: string): Promise<T | undefined> {
    return (await this.base.meta.get(cle))?.valeur as T | undefined;
  }

  async ecrireMeta<T>(cle: string, valeur: T): Promise<void> {
    await this.base.meta.put({ cle, valeur });
  }

  async ajouterOperation(operation: OperationSortante): Promise<void> {
    await this.base.operations.add({ ...operation, charge: await this.chiffreur.chiffrer(operation.charge) });
  }

  async operationsAEnvoyer(limite: number): Promise<OperationSortante[]> {
    const lot = await this.base.operations.where('statut').equals('EN_ATTENTE').sortBy('horodatageLocal').then((l) => l.slice(0, limite));
    return Promise.all(lot.map((o) => this.ouvrirOperation(o)));
  }

  async toutesLesOperations(): Promise<OperationSortante[]> {
    return Promise.all((await this.base.operations.orderBy('horodatageLocal').toArray()).map((o) => this.ouvrirOperation(o)));
  }

  async retirerOperations(ids: string[]): Promise<void> {
    await this.base.operations.bulkDelete(ids);
  }

  async marquerOperation(id: string, statut: OperationSortante['statut'], motif: string | null): Promise<void> {
    await this.base.operations.update(id, { statut, motif });
  }

  async compterAEnvoyer(): Promise<number> {
    return this.base.operations.where('statut').equals('EN_ATTENTE').count();
  }

  /** Application des changements reçus : la dernière version reçue l'emporte (le serveur est la source de vérité). */
  async appliquerChangements(changements: Changement[]): Promise<void> {
    // Chiffrement avant la transaction : une transaction IndexedDB se termine dès qu'on attend autre chose qu'elle.
    const derniers = new Map(changements.map((c) => [`${c.entite}|${c.entiteId}`, c]));
    const aEcrire = await Promise.all(
      [...derniers.values()]
        .filter((c) => c.operation !== 'SUPPRESSION')
        .map(async (c) => ({ entite: c.entite, id: c.entiteId, donnees: await this.chiffreur.chiffrer(c.donnees ?? {}) })),
    );
    const aSupprimer = [...derniers.values()].filter((c) => c.operation === 'SUPPRESSION').map((c) => [c.entite, c.entiteId] as [string, string]);
    await this.base.transaction('rw', this.base.entites, async () => {
      await this.base.entites.bulkDelete(aSupprimer);
      await this.base.entites.bulkPut(aEcrire);
    });
  }

  async entites(entite: string): Promise<EntiteLocale[]> {
    const lignes = await this.base.entites.where('entite').equals(entite).toArray();
    return Promise.all(lignes.map(async (l) => ({ ...l, donnees: await this.chiffreur.dechiffrer<Record<string, unknown>>(l.donnees) })));
  }

  async enregistrerLocale(entite: string, id: string, donnees: Record<string, unknown>): Promise<void> {
    await this.base.entites.put({ entite, id, donnees: await this.chiffreur.chiffrer(donnees) });
  }

  async compterEntites(entite: string): Promise<number> {
    return this.base.entites.where('entite').equals(entite).count();
  }

  async vider(): Promise<void> {
    await Promise.all([this.base.operations.clear(), this.base.entites.clear(), this.base.meta.clear()]);
  }
}
