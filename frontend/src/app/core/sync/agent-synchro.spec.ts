import 'fake-indexeddb/auto';

import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Observable, from, of, switchMap, throwError } from 'rxjs';

import { uuid7 } from '../identifiant';
import { AgentSynchro, LOCAL_STORE, PlageLocale } from './agent-synchro';
import { Changement, DexieStore } from './local-store';

const terminal = { id: uuid7(), code: 'C01', nom: 'Caisse comptoir', etablissementId: uuid7() };
const plage = (prochain: number): PlageLocale => ({ id: 'p1', typePiece: 'TICKET', prefixe: 'TK-C01', annee: 2026, debut: 1, fin: 500, prochain });
const changement = (sequence: number, id: string): Changement => ({ sequence, entite: 'produit', entiteId: id, operation: 'UPSERT', donnees: { id } });

/** Le corps envoyé est compressé en gzip (ENF-02) : le serveur simulé le décompresse comme FiltreDecompression. */
async function decompresser(corps: unknown): Promise<{ operations: { idOperation: string; charge: string }[] }> {
  const flux = new Response(await (corps as Blob).arrayBuffer()).body!.pipeThrough(new DecompressionStream('gzip'));
  return JSON.parse(await new Response(flux).text());
}

/** Serveur simulé : file de réponses par route, corps envoyés conservés. */
class ServeurSimule {
  readonly envois: { operations: { idOperation: string; charge: string }[] }[] = [];
  push: (corps: { operations: { idOperation: string }[] }) => Observable<unknown> = (corps) =>
    of({ accuses: corps.operations.map((o) => ({ idOperation: o.idOperation, statut: 'APPLIQUEE', motif: null })) });
  pages: unknown[] = [];

  post(url: string, corps: unknown): Observable<unknown> {
    if (url.endsWith('/appairage')) {
      return of({ terminal, plages: [plage(1)] });
    }
    return from(decompresser(corps)).pipe(
      switchMap((json) => {
        this.envois.push(json);
        return this.push(json);
      }),
    );
  }

  get(): Observable<unknown> {
    return of(this.pages.shift() ?? { changements: [], curseur: 0, encore: false, plages: [] });
  }
}

describe('AgentSynchro (guide §8.7)', () => {
  let serveur: ServeurSimule;
  let store: DexieStore;
  let agent: AgentSynchro;

  beforeEach(async () => {
    serveur = new ServeurSimule();
    store = new DexieStore(`test-${uuid7()}`);
    TestBed.configureTestingModule({
      providers: [
        { provide: HttpClient, useValue: serveur },
        { provide: LOCAL_STORE, useValue: store },
      ],
    });
    agent = TestBed.inject(AgentSynchro);
    await agent.appairer(terminal.id, 'ABCD2345');
  });

  it('signe chaque opération et ne retire de la file que les opérations accusées', async () => {
    const a = await agent.enregistrer('FICHE_CLIENT_MODIFIEE', { tiersId: 'x', telephone: '70000000' });
    const b = await agent.enregistrer('FICHE_CLIENT_MODIFIEE', { tiersId: 'y', ville: 'Bobo' });
    expect(a.signature).toMatch(/^[A-Za-z0-9+/=]{80,}$/);
    expect(JSON.parse(a.charge)).toEqual({ tiersId: 'x', telephone: '70000000' });
    serveur.push = () =>
      of({ accuses: [{ idOperation: a.idOperation, statut: 'APPLIQUEE', motif: null }, { idOperation: b.idOperation, statut: 'A_RENVOYER', motif: null }] });

    await agent.synchroniser();

    expect(agent.envoyees()).toBe(1);
    expect(agent.enAttente()).toBe(1);
    expect((await store.toutesLesOperations()).map((o) => o.idOperation)).toEqual([b.idOperation]);
  });

  it('garde une opération en conflit avec son motif, sans la renvoyer', async () => {
    const a = await agent.enregistrer('FICHE_CLIENT_MODIFIEE', { tiersId: 'x', plafondCredit: 0 });
    serveur.push = () => of({ accuses: [{ idOperation: a.idOperation, statut: 'EN_CONFLIT', motif: 'RG-06 : champ non modifiable' }] });

    await agent.synchroniser();
    serveur.envois.length = 0;
    await agent.synchroniser();

    expect(agent.conflits()).toBe(1);
    expect(agent.enAttente()).toBe(0);
    expect((await store.toutesLesOperations())[0].motif).toContain('RG-06');
    expect(serveur.envois.every((e) => e.operations.length === 0)).toBe(true);
  });

  it('terminal révoqué : arrête la synchronisation et conserve les opérations locales', async () => {
    await agent.enregistrer('FICHE_CLIENT_MODIFIEE', { tiersId: 'x' });
    serveur.push = () =>
      throwError(() => new HttpErrorResponse({ status: 403, error: { code: 'TERMINAL_REVOQUE', detail: 'Ce terminal a été révoqué.' } }));

    await agent.synchroniser();

    expect(agent.revoque()).toBe(true);
    expect(agent.erreur()).toContain('révoqué');
    expect(agent.enAttente()).toBe(1);
  });

  it('réception paginée : applique les changements, avance le curseur et ne fait jamais reculer une plage', async () => {
    serveur.pages = [
      { changements: [changement(1, 'a'), changement(2, 'b')], curseur: 2, encore: true, plages: [plage(1)] },
      { changements: [changement(3, 'c')], curseur: 3, encore: false, plages: [plage(1), { ...plage(1), id: 'p2', debut: 501, fin: 1000, prochain: 501 }] },
    ];
    const progression: number[] = [];
    agent.plages.set([plage(420)]);
    expect(agent.plageEnAlerte()).toBe(true); // 84 % consommés, pas encore de plage suivante

    await agent.synchroniser((n) => progression.push(n));

    expect(progression).toEqual([2, 3]);
    expect(await store.compterEntites('produit')).toBe(3);
    expect(await store.lireMeta('curseur')).toBe(3);
    expect(agent.plages().find((p) => p.id === 'p1')?.prochain).toBe(420);
    expect(agent.plageEnAlerte()).toBe(false); // la plage suivante est arrivée
  });
});

describe('DexieStore', () => {
  it('upsert puis suppression des entités, comptage par type', async () => {
    const store = new DexieStore(`test-${uuid7()}`);
    await store.appliquerChangements([changement(1, 'a'), changement(2, 'b'), { ...changement(3, 'a'), donnees: { id: 'a', nom: 'Riz' } }]);
    expect(await store.compterEntites('produit')).toBe(2);
    expect((await store.entites('produit')).find((e) => e.id === 'a')?.donnees).toEqual({ id: 'a', nom: 'Riz' });

    await store.appliquerChangements([{ sequence: 4, entite: 'produit', entiteId: 'b', operation: 'SUPPRESSION', donnees: null }]);
    expect(await store.compterEntites('produit')).toBe(1);
  });

  it("rend les opérations en attente dans l'ordre chronologique, jamais celles en conflit", async () => {
    const store = new DexieStore(`test-${uuid7()}`);
    const base = { type: 'T', versionSchema: 1, utilisateurId: null, charge: '{}', signature: 's', motif: null, tentatives: 0 };
    await store.ajouterOperation({ ...base, idOperation: '2', horodatageLocal: '2026-10-04T10:00:02Z', statut: 'EN_ATTENTE' });
    await store.ajouterOperation({ ...base, idOperation: '1', horodatageLocal: '2026-10-04T10:00:01Z', statut: 'EN_ATTENTE' });
    await store.ajouterOperation({ ...base, idOperation: '3', horodatageLocal: '2026-10-04T10:00:00Z', statut: 'EN_CONFLIT' });
    expect((await store.operationsAEnvoyer(10)).map((o) => o.idOperation)).toEqual(['1', '2']);
    expect(await store.compterAEnvoyer()).toBe(2);
  });
});
