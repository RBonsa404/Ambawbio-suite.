import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Translation, TranslocoLoader } from '@jsverse/transloco';

/** Charge les textes depuis public/i18n/<langue>.json (R-14 : textes externalisés). */
@Injectable({ providedIn: 'root' })
export class TranslocoChargeur implements TranslocoLoader {
  private readonly http = inject(HttpClient);

  getTranslation(langue: string) {
    return this.http.get<Translation>(`i18n/${langue}.json`);
  }
}
