import { Pipe, PipeTransform } from '@angular/core';

import { formaterFcfa } from '../formats';

/** 12500 → « 12 500 FCFA » (espaces insécables, D-05). */
@Pipe({ name: 'fcfa' })
export class FcfaPipe implements PipeTransform {
  transform(valeur: number | null | undefined): string {
    return valeur === null || valeur === undefined ? '—' : formaterFcfa(valeur);
  }
}
