import { Directive, computed, input } from '@angular/core';

export type VarianteBouton = 'principal' | 'secondaire' | 'tertiaire' | 'danger' | 'caisse';

/**
 * Bouton du système de conception (Phase 3, famille « Actions »). Cible tactile 48 px minimum, 64 px pour « caisse ».
 * Usage : <button ambBouton variante="principal">Encaisser</button>
 */
@Directive({
  selector: 'button[ambBouton], a[ambBouton]',
  host: { '[class]': 'classes()', '[attr.aria-busy]': 'chargement() || null' },
})
export class Bouton {
  readonly variante = input<VarianteBouton>('secondaire');
  readonly chargement = input(false);

  protected readonly classes = computed(() => `bouton bouton-${this.variante()}`);
}
