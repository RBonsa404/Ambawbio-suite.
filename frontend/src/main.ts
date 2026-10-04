import { bootstrapApplication } from '@angular/platform-browser';

import { App } from './app/app';
import { creerConfiguration } from './app/app.config';
import { lireJetons } from './app/core/jetons';

lireJetons()
  .then((jetons) => bootstrapApplication(App, creerConfiguration(jetons, navigator.onLine)))
  .catch((err) => console.error(err));
