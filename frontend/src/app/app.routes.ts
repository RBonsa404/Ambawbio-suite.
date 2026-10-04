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
