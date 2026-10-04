/** Raccourcis clavier des listes web (Phase 3) : « / » recherche, « N » nouveau, ↑ ↓ élément précédent / suivant. */
export function gererRaccourcis(
  evenement: KeyboardEvent,
  actions: { rechercher: () => void; nouveau: () => void; deplacer: (pas: number) => void },
): void {
  const cible = evenement.target as HTMLElement | null;
  const saisie = cible && ['INPUT', 'TEXTAREA', 'SELECT'].includes(cible.tagName);
  if (evenement.key === '/' && !saisie) {
    evenement.preventDefault();
    actions.rechercher();
  } else if ((evenement.key === 'n' || evenement.key === 'N') && !saisie && !evenement.ctrlKey && !evenement.metaKey) {
    evenement.preventDefault();
    actions.nouveau();
  } else if ((evenement.key === 'ArrowDown' || evenement.key === 'ArrowUp') && !saisie) {
    evenement.preventDefault();
    actions.deplacer(evenement.key === 'ArrowDown' ? 1 : -1);
  }
}

/** Message lisible d'une erreur de l'API (ProblemDetail). */
export function messageErreur(erreur: unknown, defaut: string): string {
  const e = erreur as { error?: { detail?: string; champs?: Record<string, string> } };
  const champs = e.error?.champs ? Object.values(e.error.champs).join(' ') : '';
  return [e.error?.detail, champs].filter(Boolean).join(' ') || defaut;
}
