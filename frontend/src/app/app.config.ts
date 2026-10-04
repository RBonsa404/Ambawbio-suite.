import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, isDevMode, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import {
  createInterceptorCondition,
  INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
  IncludeBearerTokenCondition,
  includeBearerTokenInterceptor,
  provideKeycloak,
  withAutoRefreshToken,
  AutoRefreshTokenService,
  UserActivityService,
} from 'keycloak-angular';

import { routes } from './app.routes';
import { configuration } from './core/configuration';
import { intercepteurErreurs } from './core/intercepteur-erreurs';
import { TranslocoChargeur } from './core/transloco-chargeur';

const appelsApi = createInterceptorCondition<IncludeBearerTokenCondition>({
  urlPattern: /^(https?:\/\/[^/]+)?\/api(\/.*)?$/i,
});

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideKeycloak({
      config: configuration.keycloak,
      initOptions: {
        onLoad: 'login-required',
        pkceMethod: 'S256',
        checkLoginIframe: false,
      },
      features: [withAutoRefreshToken({ onInactivityTimeout: 'logout', sessionTimeout: 8 * 60 * 60 * 1000 })],
      providers: [AutoRefreshTokenService, UserActivityService],
    }),
    { provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG, useValue: [appelsApi] },
    provideHttpClient(withInterceptors([includeBearerTokenInterceptor, intercepteurErreurs])),
    provideRouter(routes, withComponentInputBinding()),
    provideTransloco({
      config: {
        availableLangs: ['fr'],
        defaultLang: 'fr',
        reRenderOnLangChange: false,
        prodMode: !isDevMode(),
      },
      loader: TranslocoChargeur,
    }),
  ],
};
