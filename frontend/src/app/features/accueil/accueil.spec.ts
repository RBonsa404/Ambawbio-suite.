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
    http.expectOne('/api/v1/socle/contexte').flush({
      utilisateur: { id: 'u1', nomUtilisateur: 'awa', nomComplet: 'Awa Kaboré', courriel: 'awa@demo.bf' },
      entreprise: { id: 't1', nom: 'Quincaillerie Wend-Panga', pack: 'BUSINESS', statut: 'ACTIVE' },
      societes: [],
      etablissements: [{ id: 'e1', societeId: 's1', code: 'SIEGE', nom: 'Ouaga — Zogona' }],
      roles: ['caissier'],
      permissions: ['pos:vendre'],
      modules: ['pos'],
    });
    http.expectOne('/api/v1/socle/connexions').flush(null);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('caissier');
    expect(page.textContent).toContain('Quincaillerie Wend-Panga');
    expect(page.textContent).toContain('Ouaga — Zogona');
  });

  it('affiche le message du serveur quand l\'accès est refusé', async () => {
    const fixture = TestBed.createComponent(Accueil);
    http.expectOne('/api/v1/socle/contexte').flush(
      { code: 'ENTREPRISE_SUSPENDUE', detail: "L'abonnement de votre entreprise est suspendu." },
      { status: 403, statusText: 'Forbidden' },
    );
    await fixture.whenStable();
    expect(fixture.componentInstance['etatServeur']()).toBe('erreur');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('abonnement de votre entreprise est suspendu');
  });
});
