#!/usr/bin/env node
// Télécharge les polices du système de conception (OFL 1.1) pour l'auto-hébergement,
// selon docs/design/polices/LICENCES.md : Archivo variable (wght + wdth), IBM Plex Sans, IBM Plex Mono,
// sous-ensembles latin et latin-ext uniquement, format WOFF2.
//
// Usage (depuis infra/scripts) :
//   npm run polices
//   (derrière un proxy HTTP : NODE_USE_ENV_PROXY=1 npm run polices)
//
// Résultat dans docs/design/polices/ : fichiers *.woff2 nommés famille-graisse-sous-ensemble
// et polices.css (règles @font-face avec des chemins relatifs, sans appel à un serveur externe).

import { mkdir, readdir, rm, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ICI = dirname(fileURLToPath(import.meta.url));
const SORTIE = resolve(ICI, '../../docs/design/polices');

// Axes utilisés par les maquettes : Archivo 700–800 en largeur 100–125 % (variable), IBM Plex Sans
// 400–700 (variable), IBM Plex Mono 400/500/600 (statique).
const FAMILLES = [
  { prefixe: 'archivo', requete: 'Archivo:wdth,wght@100..125,700..800' },
  { prefixe: 'ibm-plex-sans', requete: 'IBM+Plex+Sans:wght@400..700' },
  { prefixe: 'ibm-plex-mono', requete: 'IBM+Plex+Mono:wght@400;500;600' },
];
const SOUS_ENSEMBLES = ['latin', 'latin-ext'];
// Un agent utilisateur récent est nécessaire pour que Google Fonts serve du WOFF2.
const AGENT =
  'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36';

async function telecharger(url, type) {
  const reponse = await fetch(url, { headers: { 'User-Agent': AGENT } });
  if (!reponse.ok) throw new Error(`${reponse.status} pour ${url}`);
  return type === 'texte' ? reponse.text() : Buffer.from(await reponse.arrayBuffer());
}

/** Découpe la feuille Google Fonts en blocs { sousEnsemble, regle }. */
function decouper(css) {
  return [...css.matchAll(/\/\*\s*([\w-]+)\s*\*\/\s*(@font-face\s*\{[^}]*\})/g)].map((m) => ({
    sousEnsemble: m[1],
    regle: m[2],
  }));
}

await mkdir(SORTIE, { recursive: true });
for (const fichier of await readdir(SORTIE)) {
  if (fichier.endsWith('.woff2') || fichier.endsWith('.css')) await rm(join(SORTIE, fichier));
}

const regles = [];
for (const { prefixe, requete } of FAMILLES) {
  const css = await telecharger(
    `https://fonts.googleapis.com/css2?family=${requete}&display=swap`,
    'texte',
  );
  for (const { sousEnsemble, regle } of decouper(css)) {
    if (!SOUS_ENSEMBLES.includes(sousEnsemble)) continue;
    const graisse = regle.match(/font-weight:\s*([\d ]+);/)[1].trim().replace(' ', '-');
    const nom = `${prefixe}-${graisse}-${sousEnsemble}.woff2`;
    const url = regle.match(/url\(([^)]+)\)/)[1];
    await writeFile(join(SORTIE, nom), await telecharger(url, 'binaire'));
    regles.push(`/* ${prefixe} ${graisse} — ${sousEnsemble} */\n${regle.replace(url, `./${nom}`)}`);
  }
}

await writeFile(
  join(SORTIE, 'polices.css'),
  `/* Généré par infra/scripts/telecharger-polices.mjs — ne pas modifier à la main.
   Polices sous SIL Open Font License 1.1 (voir OFL.txt et LICENCES.md). */

${regles.join('\n\n')}
`,
);
console.log(`${regles.length} fichiers de police écrits dans ${SORTIE}`);
