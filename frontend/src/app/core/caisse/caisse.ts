import { HttpClient } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { configuration } from '../configuration';
import { uuid7 } from '../identifiant';
import { ServiceContexte } from '../service-contexte';
import { AgentSynchro } from '../sync/agent-synchro';
import { brut, calculerLigne, enMilliemes, quantiteTexte } from './calcul-vente';
import { EncaissementPiece, LignePiece, PieceCaisse } from './ticket';

export interface ProduitCaisse {
  id: string;
  code: string;
  nom: string;
  nomRecherche: string;
  prixVente: number;
  prixTtc: boolean;
  categorieId: string | null;
  taxeCode: string | null;
  taux: string;
  conditionnements: { code: string; libelle: string; quantite: string; prixVente: number | null }[];
  codesBarres: { valeur: string; conditionnement: string | null }[];
}

export interface PointDeVenteLocal {
  id: string;
  code: string;
  nom: string;
  seuilEcart: number;
  comptageAveugle: boolean;
  remiseMaxPourcent: number;
  actif: boolean;
}

export interface SessionLocale {
  id: string;
  pointDeVenteId: string;
  fondsInitial: number;
  ouverteLe: string;
  caissierId: string;
}

export interface LignePanier {
  id: string;
  produit: ProduitCaisse;
  conditionnement: string | null;
  facteur: string;
  quantite: string;
  prixUnitaire: number;
  remise: number;
}

export interface ResultatCloture {
  theoriques: number;
  comptees: number;
  ecart: number;
  aValider: boolean;
}

export function sansAccents(texte: string): string {
  return texte.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

/**
 * Caisse du terminal (F-POS-01 à 07, SD-02) : tout fonctionne sans réseau. Chaque vente devient une opération signée
 * de l'outbox, numérotée dans la plage du terminal, et une pièce locale (ticket, retours, clôture).
 */
@Injectable({ providedIn: 'root' })
export class Caisse {
  private readonly agent = inject(AgentSynchro);
  private readonly contexte = inject(ServiceContexte);
  private readonly http = inject(HttpClient);

  readonly produits = signal<ProduitCaisse[]>([]);
  readonly pointsDeVente = signal<PointDeVenteLocal[]>([]);
  readonly sessionsDistantes = signal<{ id: string; pointDeVenteId: string; terminalId: string; statut: string }[]>([]);
  readonly session = signal<SessionLocale | null>(null);
  readonly panier = signal<LignePanier[]>([]);
  readonly pieces = signal<PieceCaisse[]>([]);
  readonly derniere = signal<PieceCaisse | null>(null);
  readonly pret = signal(false);

  readonly pointDeVente = computed(() => this.pointsDeVente().find((p) => p.id === this.session()?.pointDeVenteId) ?? null);
  readonly lignes = computed<LignePiece[]>(() => this.panier().map((l) => this.calculer(l)));
  readonly total = computed(() => this.lignes().reduce((s, l) => s + l.montantTtc, 0));
  readonly taxes = computed(() => this.lignes().reduce((s, l) => s + l.montantTaxe, 0));
  readonly articles = computed(() => this.panier().reduce((s, l) => s + enMilliemes(l.quantite), 0) / 1000);
  readonly peutRemiser = computed(() => this.contexte.peut('pos:remiser'));

  constructor() {
    effect(() => {
      this.agent.version();
      void this.charger();
    });
  }

  /** Données locales : catalogue, taxes, points de vente, session en cours, pièces de la session. */
  async charger(): Promise<void> {
    const store = this.agent.store;
    const [produits, taxes, pdv, sessions] = await Promise.all([
      store.entites('produit'), store.entites('taxe'), store.entites('point_de_vente'), store.entites('session_caisse'),
    ]);
    const parTaxe = new Map(taxes.map((t) => [t.id, t.donnees as { code: string; taux: number | string }]));
    this.produits.set(
      produits
        .map((p) => p.donnees as Record<string, unknown> & { nom: string; code: string })
        .map((d, i) => {
          const taxe = parTaxe.get(d['taxeId'] as string);
          return {
            id: produits[i].id,
            code: d.code,
            nom: d.nom,
            nomRecherche: sansAccents(`${d.code} ${d.nom}`),
            prixVente: Number(d['prixVente'] ?? 0),
            prixTtc: d['prixVenteTtc'] !== false,
            categorieId: (d['categorieId'] as string) ?? null,
            taxeCode: taxe?.code ?? null,
            taux: String(taxe?.taux ?? 0),
            conditionnements: ((d['conditionnements'] as { code: string; libelle: string; quantite: number | string; prixVente: number | string }[]) ?? []).map(
              (c) => ({ code: c.code, libelle: c.libelle, quantite: String(c.quantite), prixVente: c.prixVente === '' || c.prixVente == null ? null : Number(c.prixVente) }),
            ),
            codesBarres: ((d['codesBarres'] as { valeur: string; conditionnement: string }[]) ?? []).map((c) => ({
              valeur: c.valeur,
              conditionnement: c.conditionnement || null,
            })),
            actif: d['actif'] !== false,
          };
        })
        .filter((p) => p.actif)
        .sort((a, b) => a.nom.localeCompare(b.nom, 'fr')),
    );
    this.pointsDeVente.set(pdv.map((p) => ({ id: p.id, ...(p.donnees as Omit<PointDeVenteLocal, 'id'>) })).filter((p) => p.actif));
    this.sessionsDistantes.set(sessions.map((s) => ({ id: s.id, ...(s.donnees as { pointDeVenteId: string; terminalId: string; statut: string }) })));
    this.session.set((await store.lireMeta<SessionLocale>('session-caisse')) ?? null);
    await this.chargerPieces();
    this.pret.set(true);
  }

  private async chargerPieces(): Promise<void> {
    const id = this.session()?.id;
    const toutes = (await this.agent.store.entites('piece')).map((p) => p.donnees as unknown as PieceCaisse);
    this.pieces.set(toutes.filter((p) => p.sessionId === id).sort((a, b) => b.horodatage.localeCompare(a.horodatage)));
  }

  rechercher(texte: string, limite = 60): ProduitCaisse[] {
    const t = sansAccents(texte.trim());
    if (!t) {
      return this.produits().slice(0, limite);
    }
    const mots = t.split(/\s+/);
    return this.produits().filter((p) => mots.every((m) => p.nomRecherche.includes(m))).slice(0, limite);
  }

  /** Lecture d'un code-barres ou saisie d'un code produit : ajoute directement au panier. */
  scanner(code: string): ProduitCaisse | null {
    const valeur = code.trim();
    for (const p of this.produits()) {
      const cb = p.codesBarres.find((c) => c.valeur === valeur);
      if (cb || p.code.toUpperCase() === valeur.toUpperCase()) {
        this.ajouter(p, cb?.conditionnement ?? null);
        return p;
      }
    }
    return null;
  }

  ajouter(produit: ProduitCaisse, conditionnement: string | null = null, quantite = '1'): void {
    const existante = this.panier().find((l) => l.produit.id === produit.id && l.conditionnement === conditionnement && l.remise === 0);
    if (existante) {
      this.modifier(existante.id, { quantite: quantiteTexte(enMilliemes(existante.quantite) + enMilliemes(quantite)) });
      return;
    }
    const cond = produit.conditionnements.find((c) => c.code === conditionnement);
    const facteur = cond?.quantite ?? '1';
    const prix = cond ? (cond.prixVente ?? brut(facteur, produit.prixVente)) : produit.prixVente;
    this.panier.update((p) => [...p, { id: uuid7(), produit, conditionnement: cond?.code ?? null, facteur, quantite, prixUnitaire: prix, remise: 0 }]);
  }

  modifier(id: string, changement: Partial<Pick<LignePanier, 'quantite' | 'remise' | 'conditionnement'>>): void {
    this.panier.update((lignes) =>
      lignes.map((l) => {
        if (l.id !== id) {
          return l;
        }
        if (changement.conditionnement !== undefined && changement.conditionnement !== l.conditionnement) {
          const cond = l.produit.conditionnements.find((c) => c.code === changement.conditionnement);
          const facteur = cond?.quantite ?? '1';
          return { ...l, ...changement, conditionnement: cond?.code ?? null, facteur, remise: 0,
            prixUnitaire: cond ? (cond.prixVente ?? brut(facteur, l.produit.prixVente)) : l.produit.prixVente };
        }
        return { ...l, ...changement };
      }),
    );
  }

  retirer(id: string): void {
    this.panier.update((l) => l.filter((x) => x.id !== id));
  }

  vider(): void {
    this.panier.set([]);
  }

  /** Remise maximale de la ligne en francs (paramètre du point de vente). */
  remiseMax(ligne: LignePanier): number {
    return this.peutRemiser() ? Math.floor((brut(ligne.quantite, ligne.prixUnitaire) * (this.pointDeVente()?.remiseMaxPourcent ?? 0)) / 100) : 0;
  }

  /** Une autre caisse a déjà une session ouverte sur ce point de vente (INV-14, connu à la dernière synchronisation). */
  sessionOuverteAilleurs(pointDeVenteId: string): boolean {
    const terminal = this.agent.terminal()?.id;
    return this.sessionsDistantes().some((s) => s.pointDeVenteId === pointDeVenteId && s.statut === 'OUVERTE' && s.terminalId !== terminal);
  }

  async ouvrirSession(pointDeVenteId: string, fondsInitial: number): Promise<void> {
    const session: SessionLocale = {
      id: uuid7(), pointDeVenteId, fondsInitial, ouverteLe: new Date().toISOString(), caissierId: this.caissier(),
    };
    await this.agent.enregistrer('SESSION_OUVERTE', { sessionId: session.id, ...session }, session.caissierId);
    await this.agent.store.ecrireMeta('session-caisse', session);
    this.session.set(session);
    this.pieces.set([]);
    void this.agent.synchroniser();
  }

  /** Encaissement (UC-POS-03) : numéro de la plage, opération signée, pièce locale ; le panier est vidé. */
  async encaisser(encaissements: EncaissementPiece[], options: { clientId?: string | null; factureDemandee?: boolean } = {}): Promise<PieceCaisse> {
    const lignes = this.lignes();
    const total = this.total();
    if (!lignes.length) {
      throw new Error('Le panier est vide.');
    }
    if (encaissements.reduce((s, e) => s + e.montant, 0) !== total) {
      throw new Error('Le paiement ne correspond pas au total.');
    }
    const piece = await this.creerPiece('VENTE', lignes, encaissements, options.clientId ?? null, options.factureDemandee ?? false);
    this.vider();
    return piece;
  }

  /** Quantité encore retournable par ligne d'un ticket (retours déjà faits déduits, UC-POS-06). */
  retournable(vente: PieceCaisse): Map<string, number> {
    const restant = new Map(vente.lignes.map((l) => [l.id, enMilliemes(l.quantite)]));
    for (const p of this.pieces().filter((x) => x.type === 'RETOUR' && x.venteOrigineId === vente.venteId)) {
      p.lignes.forEach((l) => restant.set(l.ligneOrigineId!, (restant.get(l.ligneOrigineId!) ?? 0) - enMilliemes(l.quantite)));
    }
    return restant;
  }

  /** Retour remboursé en espèces au prix payé (remise au prorata) ; l'avoir certifié suit au LOT 6. */
  async retourner(vente: PieceCaisse, quantites: Map<string, string>): Promise<PieceCaisse> {
    const restant = this.retournable(vente);
    const lignes: LignePiece[] = [];
    for (const l of vente.lignes) {
      const q = quantites.get(l.id);
      if (!q || enMilliemes(q) === 0) {
        continue;
      }
      if (enMilliemes(q) > (restant.get(l.id) ?? 0)) {
        throw new Error(`Retour de « ${l.libelle} » supérieur à la quantité vendue.`);
      }
      const remise = Math.floor((l.remise * enMilliemes(q)) / enMilliemes(l.quantite));
      const m = calculerLigne(q, l.prixUnitaire, l.prixTtc, remise, l.taux);
      lignes.push({ ...l, id: uuid7(), quantite: q, remise, montantHt: m.ht, montantTaxe: m.taxe, montantTtc: m.ttc, ligneOrigineId: l.id });
    }
    if (!lignes.length) {
      throw new Error('Choisissez au moins un article à retourner.');
    }
    const total = lignes.reduce((s, l) => s + l.montantTtc, 0);
    return this.creerPiece('RETOUR', lignes, [{ moyen: 'ESPECES', montant: total }], vente.clientId, false, vente.venteId);
  }

  /** Espèces attendues : fonds + espèces encaissées − espèces remboursées (même calcul que le serveur). */
  especesTheoriques(): number {
    return this.pieces().reduce(
      (s, p) => s + (p.type === 'VENTE' ? 1 : -1) * p.encaissements.filter((e) => e.moyen === 'ESPECES').reduce((t, e) => t + e.montant, 0),
      this.session()?.fondsInitial ?? 0,
    );
  }

  async cloturer(especesComptees: number): Promise<ResultatCloture> {
    const session = this.session();
    if (!session) {
      throw new Error('Aucune session ouverte.');
    }
    const theoriques = this.especesTheoriques();
    await this.agent.enregistrer('SESSION_CLOTUREE', { sessionId: session.id, especesComptees, clotureeLe: new Date().toISOString(), caissierId: this.caissier() },
      this.caissier());
    await this.agent.store.ecrireMeta('session-caisse', null);
    await this.agent.store.ecrireMeta('derniere-cloture', { sessionId: session.id, theoriques, comptees: especesComptees });
    this.session.set(null);
    void this.agent.synchroniser();
    const ecart = especesComptees - theoriques;
    return { theoriques, comptees: especesComptees, ecart, aValider: Math.abs(ecart) > (this.pointDeVente()?.seuilEcart ?? 0) };
  }

  /** Validation d'un écart par le responsable (RG-09) : en ligne, son code PIN est vérifié par le serveur. */
  async validerEcart(sessionId: string, responsable: string, pin: string, motif: string): Promise<void> {
    await this.agent.synchroniser();
    await firstValueFrom(this.http.post(`${configuration.api}/v1/pos/sessions/${sessionId}/validation-ecart`, { responsable, pin, motif }));
  }

  private calculer(l: LignePanier): LignePiece {
    const m = calculerLigne(l.quantite, l.prixUnitaire, l.produit.prixTtc, l.remise, l.produit.taux);
    const cond = l.produit.conditionnements.find((c) => c.code === l.conditionnement);
    return {
      id: l.id, produitId: l.produit.id, libelle: l.produit.nom, conditionnement: cond?.libelle ?? null, quantite: l.quantite, facteur: l.facteur,
      prixUnitaire: l.prixUnitaire, prixTtc: l.produit.prixTtc, remise: l.remise, taxeCode: l.produit.taxeCode, taux: l.produit.taux,
      montantHt: m.ht, montantTaxe: m.taxe, montantTtc: m.ttc,
    };
  }

  private async creerPiece(type: 'VENTE' | 'RETOUR', lignes: LignePiece[], encaissements: EncaissementPiece[], clientId: string | null,
    factureDemandee: boolean, venteOrigineId?: string): Promise<PieceCaisse> {
    const session = this.session();
    if (!session) {
      throw new Error('Ouvrez une session de caisse.');
    }
    const typePiece = type === 'VENTE' ? 'TICKET' : 'AVOIR';
    const { plage, sequence } = await this.agent.consommerNumero(typePiece);
    const piece: PieceCaisse = {
      venteId: uuid7(), type, sessionId: session.id, caissierId: this.caissier(),
      numero: { typePiece, annee: plage.annee, sequence },
      numeroAffiche: `${plage.prefixe}-${plage.annee}-${String(sequence).padStart(6, '0')}`,
      horodatage: new Date().toISOString(), clientId, factureDemandee, lignes, encaissements,
      totalHt: lignes.reduce((s, l) => s + l.montantHt, 0), totalTaxes: lignes.reduce((s, l) => s + l.montantTaxe, 0),
      totalTtc: lignes.reduce((s, l) => s + l.montantTtc, 0),
      ...(venteOrigineId ? { venteOrigineId } : {}),
    };
    const charge: Record<string, unknown> = { ...piece };
    delete charge['numeroAffiche'];
    delete charge['type'];
    await this.agent.enregistrer(type === 'VENTE' ? 'VENTE_ENREGISTREE' : 'RETOUR_ENREGISTRE', charge, piece.caissierId);
    await this.agent.store.enregistrerLocale('piece', piece.venteId, piece as unknown as Record<string, unknown>);
    this.pieces.update((p) => [piece, ...p]);
    this.derniere.set(piece);
    void this.agent.synchroniser();
    return piece;
  }

  private caissier(): string {
    const id = this.contexte.contexte()?.utilisateur.id;
    if (!id) {
      throw new Error('Utilisateur inconnu : reconnectez-vous.');
    }
    return id;
  }
}
