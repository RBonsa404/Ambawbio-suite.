// Copie les ressources du paquet de design (source unique : docs/design) dans l'application.
// Lancé automatiquement avant start, build et test (Angular refuse les ressources hors de frontend/).
// Les dossiers produits sont ignorés par git.
import { cp, mkdir, rm } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const FRONT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const DESIGN = resolve(FRONT, '../docs/design');
const PUBLIC = join(FRONT, 'public');

const copies = [
  [join(DESIGN, 'polices'), join(PUBLIC, 'polices')],
  [join(DESIGN, 'logo'), join(PUBLIC, 'logo')],
  [join(DESIGN, 'icones-app/png/web'), join(PUBLIC, 'icones')],
  [join(DESIGN, 'icones-app/favicon.svg'), join(PUBLIC, 'icones/favicon.svg')],
];

for (const dossier of ['polices', 'logo', 'icones']) {
  await rm(join(PUBLIC, dossier), { recursive: true, force: true });
}
for (const [source, cible] of copies) {
  await mkdir(dirname(cible), { recursive: true });
  await cp(source, cible, { recursive: true, filter: (f) => !f.includes(join('logo', 'source')) && !f.endsWith('.md') });
}

// Icônes Android (si le projet Capacitor existe) : remplacent celles générées par défaut.
const resAndroid = join(FRONT, 'android/app/src/main/res');
if (existsSync(resAndroid)) {
  await cp(join(DESIGN, 'icones-app/png/android/res'), resAndroid, { recursive: true });
}
console.log('Ressources de design synchronisées depuis docs/design.');
