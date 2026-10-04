import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, isDevMode, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideServiceWorker } from '@angular/service-worker';
import { Capacitor } from '@capacitor/core';
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
import { estTerminal, Jetons } from './core/jetons';
import { TranslocoChargeur } from './core/transloco-chargeur';

const appelsApi = createInterceptorCondition<IncludeBearerTokenCondition>({
  urlPattern: /^(https?:\/\/[^/]+)?\/api(\/.*)?$/i,
});

/**
 * Configuration de l'application. Un terminal de caisse (D-29) demande un jeton hors-ligne (offline_access, 30 jours
 * d'inactivité tolérés) et redémarre sans réseau avec ses jetons conservés : la connexion n'est exigée qu'en ligne,
 * et le verrouillage se fait par code PIN plutôt que par déconnexion.
 */
export function creerConfiguration(jetons: Jetons | null, enLigne: boolean): ApplicationConfig {
  const terminal = estTerminal();
  const base = { pkceMethod: 'S256' as const, checkLoginIframe: false, ...(terminal ? { scope: 'openid offline_access' } : {}) };
  const initOptions = jetons
    ? { ...base, token: jetons.token, refreshToken: jetons.refreshToken, idToken: jetons.idToken, ...(enLigne ? { onLoad: 'login-required' as const } : {}) }
    : { ...base, onLoad: 'login-required' as const };
  return {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideServiceWorker('ngsw-worker.js', { enabled: !isDevMode() && !Capacitor.isNativePlatform(), registrationStrategy: 'registerWhenStable:30000' }),
    provideKeycloak({
      config: configuration.keycloak,
      initOptions,
      features: [withAutoRefreshToken({ onInactivityTimeout: terminal ? 'none' : 'logout', sessionTimeout: 8 * 60 * 60 * 1000 })],
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
}
