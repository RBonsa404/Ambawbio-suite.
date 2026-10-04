import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { configuration } from './configuration';

export interface Page<T> {
  elements: T[];
  page: number;
  taille: number;
  total: number;
}

export interface Conditionnement {
  code: string;
  libelle: string | null;
  quantite: number;
  prixVente: number | null;
}

export interface Produit {
  id: string;
  code: string;
  nom: string;
  type: 'BIEN' | 'SERVICE';
  categorieId: string | null;
  uniteId: string;
  taxeId: string;
  prixVente: number;
  prixVenteTtc: boolean;
  prixAchat: number | null;
  suiviStock: boolean;
  actif: boolean;
  conditionnements: Conditionnement[];
  codesBarres: { valeur: string; conditionnementCode: string | null }[];
  champsPerso: Record<string, unknown>;
  version: number;
}

export interface CompteMobileMoney {
  operateur: 'ORANGE_MONEY' | 'MOOV_MONEY' | 'WAVE';
  numero: string;
  titulaire: string | null;
  parDefaut: boolean;
}

export interface Tiers {
  id: string;
  code: string;
  nom: string;
  nature: 'PARTICULIER' | 'ENTREPRISE' | 'ADMINISTRATION' | 'ONG';
  estClient: boolean;
  estFournisseur: boolean;
  ifu: string | null;
  rccm: string | null;
  regimeFiscalId: string | null;
  telephone: string | null;
  courriel: string | null;
  adresse: string | null;
  ville: string | null;
  delaiPaiementJours: number;
  plafondCredit: number | null;
  actif: boolean;
  comptesMobileMoney: CompteMobileMoney[];
  champsPerso: Record<string, unknown>;
  version: number;
}

export interface Parametre {
  id: string;
  code: string;
  libelle: string;
}

export interface Taxe extends Parametre {
  taux: number;
}

export interface Regime extends Parametre {
  assujettiTva: boolean;
}

export interface Categorie {
  id: string;
  nom: string;
}

export interface DefinitionChamp {
  id: string;
  entite: 'produit' | 'tiers';
  code: string;
  libelle: string;
  type: 'TEXTE' | 'NOMBRE' | 'DATE' | 'BOOLEEN' | 'LISTE';
  options: string[];
  obligatoire: boolean;
  filtrable: boolean;
  ordre: number;
}

export interface ErreurImport {
  ligne: number;
  colonne: string;
  valeur: string | null;
  message: string;
}

export interface ResultatImport {
  id: string;
  type: string;
  nomFichier: string;
  mode: 'VERIFICATION' | 'IMPORT';
  statut: 'REUSSI' | 'REJETE' | 'PARTIEL';
  lignesTotal: number;
  lignesImportees: number;
  erreurs: ErreurImport[];
}

/** Client de l'API du référentiel et du Studio (/api/v1/referentiel, /api/v1/studio). */
@Injectable({ providedIn: 'root' })
export class ApiReferentiel {
  private readonly http = inject(HttpClient);
  private readonly base = `${configuration.api}/v1/referentiel`;

  produits(q: string, page: number, filtres: Record<string, string> = {}) {
    return this.http.get<Page<Produit>>(`${this.base}/produits`, { params: new HttpParams({ fromObject: { q, page, taille: 50, ...filtres } }) });
  }

  enregistrerProduit(id: string, produit: unknown, creation: boolean) {
    return creation
      ? this.http.post<Produit>(`${this.base}/produits`, { id, produit })
      : this.http.put<Produit>(`${this.base}/produits/${id}`, produit);
  }

  tiers(q: string, page: number, filtres: Record<string, string> = {}) {
    return this.http.get<Page<Tiers>>(`${this.base}/tiers`, { params: new HttpParams({ fromObject: { q, page, taille: 50, ...filtres } }) });
  }

  enregistrerTiers(id: string, tiers: unknown, creation: boolean) {
    return creation ? this.http.post<Tiers>(`${this.base}/tiers`, { id, tiers }) : this.http.put<Tiers>(`${this.base}/tiers/${id}`, tiers);
  }

  taxes() {
    return this.http.get<Taxe[]>(`${this.base}/taxes`);
  }

  unites() {
    return this.http.get<Parametre[]>(`${this.base}/unites`);
  }

  regimes() {
    return this.http.get<Regime[]>(`${this.base}/regimes-fiscaux`);
  }

  categories() {
    return this.http.get<Categorie[]>(`${this.base}/categories`);
  }

  typesConditionnement() {
    return this.http.get<Parametre[]>(`${this.base}/types-conditionnement`);
  }

  champs(entite: 'produit' | 'tiers') {
    return this.http.get<DefinitionChamp[]>(`${configuration.api}/v1/studio/champs`, { params: { entite } });
  }

  importer(type: 'produits' | 'tiers', fichier: File, mode: 'VERIFICATION' | 'IMPORT', lignesValidesSeulement: boolean) {
    const donnees = new FormData();
    donnees.append('fichier', fichier);
    return this.http.post<ResultatImport>(`${this.base}/imports/${type}`, donnees, {
      params: { mode, lignesValidesSeulement: String(lignesValidesSeulement) },
    });
  }

  urlRapport(id: string): string {
    return `${this.base}/imports/${id}/rapport.csv`;
  }

  urlModele(type: 'produits' | 'tiers'): string {
    return `${this.base}/imports/modeles/${type}.csv`;
  }

  telecharger(url: string) {
    return this.http.get(url, { responseType: 'blob' });
  }
}
