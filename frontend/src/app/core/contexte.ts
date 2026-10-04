/** Contexte de l'utilisateur connecté (GET /api/v1/socle/contexte, UC-SOC-01). */
export interface Contexte {
  utilisateur: { id: string; nomUtilisateur: string; nomComplet: string; courriel: string };
  entreprise: { id: string; nom: string; pack: string; statut: string };
  societes: { id: string; nom: string }[];
  etablissements: { id: string; societeId: string; code: string; nom: string }[];
  roles: string[];
  permissions: string[];
  modules: string[];
}
