import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./features/accueil/accueil').then((m) => m.Accueil) },
  { path: '**', redirectTo: '' },
];
