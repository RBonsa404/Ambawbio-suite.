import Dexie, { Table } from 'dexie';

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
  compterEntites(entite: string): Promise<number>;
  vider(): Promise<void>;
}

class BaseTerminal extends Dexie {
  operations!: Table<OperationSortante, string>;
  entites!: Table<EntiteLocale, [string, string]>;
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

export class DexieStore implements LocalStore {
  private readonly base: BaseTerminal;

  constructor(nom = 'ambawbio-terminal') {
    this.base = new BaseTerminal(nom);
  }

  async lireMeta<T>(cle: string): Promise<T | undefined> {
    return (await this.base.meta.get(cle))?.valeur as T | undefined;
  }

  async ecrireMeta<T>(cle: string, valeur: T): Promise<void> {
    await this.base.meta.put({ cle, valeur });
  }

  async ajouterOperation(operation: OperationSortante): Promise<void> {
    await this.base.operations.add(operation);
  }

  async operationsAEnvoyer(limite: number): Promise<OperationSortante[]> {
    return this.base.operations.where('statut').equals('EN_ATTENTE').sortBy('horodatageLocal').then((l) => l.slice(0, limite));
  }

  async toutesLesOperations(): Promise<OperationSortante[]> {
    return this.base.operations.orderBy('horodatageLocal').toArray();
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
    await this.base.transaction('rw', this.base.entites, async () => {
      for (const c of changements) {
        if (c.operation === 'SUPPRESSION') {
          await this.base.entites.delete([c.entite, c.entiteId]);
        } else {
          await this.base.entites.put({ entite: c.entite, id: c.entiteId, donnees: c.donnees ?? {} });
        }
      }
    });
  }

  async entites(entite: string): Promise<EntiteLocale[]> {
    return this.base.entites.where('entite').equals(entite).toArray();
  }

  async compterEntites(entite: string): Promise<number> {
    return this.base.entites.where('entite').equals(entite).count();
  }

  async vider(): Promise<void> {
    await Promise.all([this.base.operations.clear(), this.base.entites.clear(), this.base.meta.clear()]);
  }
}
