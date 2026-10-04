import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import Keycloak from 'keycloak-js';

import { Accueil } from './accueil';

const textes = {
  accueil: {
    bonjour: 'Bonjour, {{ nom }}',
    serveurOk: 'Connecté',
    serveurErreur: 'Serveur injoignable',
    roles: 'Vos rôles',
    serveur: 'Serveur',
    deconnexion: 'Se déconnecter',
  },
};

describe('Accueil', () => {
  let http: HttpTestingController;
  const keycloak = { tokenParsed: { name: 'Awa Kaboré' }, logout: vi.fn() };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [
        Accueil,
        TranslocoTestingModule.forRoot({
          langs: { fr: textes },
          translocoConfig: { availableLangs: ['fr'], defaultLang: 'fr' },
          preloadLangs: true,
        }),
      ],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Keycloak, useValue: keycloak },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it("affiche l'utilisateur et ses rôles renvoyés par le serveur", async () => {
    const fixture = TestBed.createComponent(Accueil);
    http.expectOne('/api/moi').flush({
      identifiant: 'u1',
      nomUtilisateur: 'awa',
      nomComplet: 'Awa Kaboré',
      courriel: null,
      roles: ['caissier'],
    });
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('caissier');
  });

  it('signale clairement un serveur injoignable', async () => {
    const fixture = TestBed.createComponent(Accueil);
    http.expectOne('/api/moi').flush(null, { status: 0, statusText: 'Erreur réseau' });
    await fixture.whenStable();
    expect(fixture.componentInstance['etatServeur']()).toBe('erreur');
  });
});
