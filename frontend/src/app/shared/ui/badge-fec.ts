import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { Icone } from './icone';

export type StatutFec = 'certifiee' | 'en-file' | 'rejetee' | 'simulee' | 'brouillon';

const VARIANTES: Record<StatutFec, { icone: string; libelle: string; court: string; classes: string }> = {
  certifiee: { icone: 'badge-check', libelle: 'Certifiée DGI', court: 'Certifiée', classes: 'bg-vert-100 text-vert-800 border-vert-300' },
  'en-file': { icone: 'clock', libelle: 'En file de certification', court: 'En file', classes: 'bg-or-100 text-or-800 border-or-300' },
  rejetee: { icone: 'circle-x', libelle: 'Rejetée — à corriger', court: 'Rejetée', classes: 'bg-rouge-100 text-rouge-800 border-rouge-300' },
  simulee: { icone: 'flask-conical', libelle: 'Simulée — sans valeur fiscale', court: 'Simulée', classes: 'badge-fec-simulee text-banco-900 border-dashed border-banco-600' },
  brouillon: { icone: 'file-text', libelle: 'Brouillon · non certifiée', court: 'Brouillon', classes: 'bg-surface text-texte border-dashed border-texte' },
};

/** Badge de certification FEC (docs/design/composants/badge-fec.md) : icône et libellé toujours présents. */
@Component({
  selector: 'amb-badge-fec',
  imports: [Icone],
  template: `
    <span class="inline-flex items-center gap-1.5 border-[1.5px] font-semibold" [class]="variante().classes"
      [class.px-2]="taille() === 'sm'" [class.py-0.5]="taille() === 'sm'" [class.text-sm]="taille() === 'sm'"
      [class.px-3]="taille() === 'md'" [class.py-1.5]="taille() === 'md'" [attr.title]="variante().libelle" [attr.aria-label]="variante().libelle">
      <amb-icone [nom]="variante().icone" [taille]="taille() === 'sm' ? 14 : 16" />
      <span aria-hidden="true">{{ taille() === 'sm' ? variante().court : variante().libelle }}</span>
    </span>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BadgeFec {
  readonly statut = input.required<StatutFec>();
  readonly taille = input<'sm' | 'md'>('sm');
  protected readonly variante = computed(() => VARIANTES[this.statut()]);
}

/** Statut du badge à partir d'une pièce (API facturation). */
export function statutFec(d: { statut: string; fecStatut: string; fecSimulee: boolean }): StatutFec {
  if (d.statut === 'BROUILLON') return 'brouillon';
  if (d.fecStatut === 'CERTIFIEE') return d.fecSimulee ? 'simulee' : 'certifiee';
  if (d.fecStatut === 'REJETEE') return 'rejetee';
  return 'en-file';
}
