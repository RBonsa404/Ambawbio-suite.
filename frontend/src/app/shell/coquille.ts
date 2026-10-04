import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { App } from '@capacitor/app';
import { Capacitor } from '@capacitor/core';
import { TranslocoPipe } from '@jsverse/transloco';
import Keycloak from 'keycloak-js';

import { ServiceContexte } from '../core/service-contexte';
import { ServiceReseau } from '../core/service-reseau';
import { VerrouPin } from '../core/verrou-pin';
import { Icone } from '../shared/ui/icone';
import { AgentSynchro } from '../core/sync/agent-synchro';
import { IndicateurSync } from '../shared/ui/indicateur-sync';

interface EntreeMenu {
  lien: string;
  cle: string;
  icone: string;
  permission?: string;
}

const CLE_REPLI = 'ambawbio.menu-replie';
/** Terminal verrouillé après 5 minutes en arrière-plan (A-04). */
const DELAI_VERROUILLAGE = 5 * 60 * 1000;

/**
 * Coquille de l'application (Phase 3 §04) : barre latérale repliable à 64 px (repliée par défaut sous 1280 px),
 * barre du haut avec entreprise et établissement toujours visibles, navigation basse sur téléphone (4 entrées).
 */
@Component({
  selector: 'amb-coquille',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslocoPipe, Icone, IndicateurSync],
  templateUrl: './coquille.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Coquille {
  protected readonly contexte = inject(ServiceContexte);
  protected readonly reseau = inject(ServiceReseau);
  protected readonly agent = inject(AgentSynchro);
  private readonly keycloak = inject(Keycloak);

  protected readonly replie = signal(lireRepli());
  protected readonly menuProfil = signal(false);

  private readonly menu: EntreeMenu[] = [
    { lien: '/', cle: 'menu.tableauDeBord', icone: 'layout-dashboard' },
    { lien: '/produits', cle: 'menu.produits', icone: 'package', permission: 'socle:consulter' },
    { lien: '/tiers', cle: 'menu.tiers', icone: 'users', permission: 'socle:consulter' },
    { lien: '/import', cle: 'menu.import', icone: 'upload', permission: 'referentiel:gerer' },
    { lien: '/synchronisation', cle: 'menu.synchronisation', icone: 'refresh-cw' },
    { lien: '/terminaux', cle: 'menu.terminaux', icone: 'smartphone', permission: 'terminaux:gerer' },
  ];

  protected readonly entrees = computed(() => this.menu.filter((e) => !e.permission || this.contexte.peut(e.permission)));
  protected readonly entreesBasses = computed(() => this.entrees().slice(0, 4));
  protected readonly initiales = computed(() =>
    (this.contexte.contexte()?.utilisateur.nomComplet ?? '')
      .split(' ')
      .filter(Boolean)
      .map((m) => m[0])
      .slice(0, 2)
      .join('')
      .toUpperCase(),
  );

  private readonly router = inject(Router);
  private readonly verrou = inject(VerrouPin);
  private misEnPause = 0;

  constructor() {
    void this.contexte.charger();
    void this.agent.initialiser();
    if (Capacitor.isNativePlatform()) {
      void this.verrouillerAuDemarrage();
      void App.addListener('appStateChange', ({ isActive }) => {
        if (!isActive) {
          this.misEnPause = Date.now();
        } else if (this.misEnPause && Date.now() - this.misEnPause > DELAI_VERROUILLAGE) {
          void this.router.navigate(['/verrouillage']);
        }
      });
    }
  }

  /** Android : premier lancement → choix du code PIN ; ensuite, déverrouillage par PIN (D-01). */
  private async verrouillerAuDemarrage(): Promise<void> {
    if (!sessionStorage.getItem('ambawbio.deverrouille')) {
      sessionStorage.setItem('ambawbio.deverrouille', 'oui');
      await this.router.navigate(['/verrouillage']);
    }
  }

  protected basculerMenu(): void {
    this.replie.update((r) => !r);
    try {
      localStorage.setItem(CLE_REPLI, String(this.replie()));
    } catch {
      // préférence non mémorisée
    }
  }

  protected ouvrirSynchro(): void {
    void this.router.navigateByUrl('/synchronisation');
  }

  protected seDeconnecter(): void {
    void this.keycloak.logout({ redirectUri: window.location.origin });
  }
}

function lireRepli(): boolean {
  try {
    const valeur = localStorage.getItem(CLE_REPLI);
    return valeur === null ? window.innerWidth < 1280 : valeur === 'true';
  } catch {
    return false;
  }
}
