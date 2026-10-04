import { Routes } from '@angular/router';

import { Coquille } from './shell/coquille';

export const routes: Routes = [
  { path: 'verrouillage', loadComponent: () => import('./features/verrouillage/verrouillage').then((m) => m.Verrouillage) },
  {
    path: '',
    component: Coquille,
    children: [
      { path: '', loadComponent: () => import('./features/accueil/accueil').then((m) => m.Accueil), title: 'Ambawbio Suite' },
      { path: 'produits', loadComponent: () => import('./features/produits/produits').then((m) => m.Produits), title: 'Produits — Ambawbio Suite' },
      { path: 'tiers', loadComponent: () => import('./features/tiers/tiers').then((m) => m.ListeTiers), title: 'Clients et fournisseurs — Ambawbio Suite' },
      { path: 'import', loadComponent: () => import('./features/import/import').then((m) => m.AssistantImport), title: 'Import — Ambawbio Suite' },
      { path: 'caisse', loadComponent: () => import('./features/caisse/caisse').then((m) => m.EcranCaisse), title: 'Caisse — Ambawbio Suite' },
      { path: 'caisse/retour', loadComponent: () => import('./features/caisse/retour').then((m) => m.EcranRetour), title: 'Retour — Ambawbio Suite' },
      { path: 'caisse/cloture', loadComponent: () => import('./features/caisse/cloture').then((m) => m.EcranCloture), title: 'Clôture — Ambawbio Suite' },
      { path: 'caisses', loadComponent: () => import('./features/caisses/caisses').then((m) => m.Caisses), title: 'Caisses — Ambawbio Suite' },
      { path: 'terminaux', loadComponent: () => import('./features/terminaux/terminaux').then((m) => m.Terminaux), title: 'Terminaux — Ambawbio Suite' },
      { path: 'appairage', loadComponent: () => import('./features/appairage/appairage').then((m) => m.Appairage), title: 'Appairage — Ambawbio Suite' },
      {
        path: 'synchronisation',
        loadComponent: () => import('./features/synchronisation/synchronisation').then((m) => m.Synchronisation),
        title: 'Synchronisation — Ambawbio Suite',
      },
      {
        path: 'abonnement-suspendu',
        loadComponent: () => import('./features/systeme/page-systeme').then((m) => m.PageSysteme),
        data: { page: 'suspendu' },
        title: 'Abonnement suspendu — Ambawbio Suite',
      },
      {
        path: 'acces-refuse',
        loadComponent: () => import('./features/systeme/page-systeme').then((m) => m.PageSysteme),
        data: { page: 'refuse' },
      },
      {
        path: '**',
        loadComponent: () => import('./features/systeme/page-systeme').then((m) => m.PageSysteme),
        data: { page: 'introuvable' },
        title: 'Page introuvable — Ambawbio Suite',
      },
    ],
  },
];
