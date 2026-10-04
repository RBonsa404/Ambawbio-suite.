#!/usr/bin/env node
// Génère les icônes PNG (favicons, PWA, Apple, Android adaptative) à partir des SVG
// de docs/design/icones-app/, selon docs/design/icones-app/README.md.
//
// Usage (depuis infra/scripts) :
//   npm ci && npm run icones
//   node generer-icones.mjs --sortie <dossier>     (défaut : docs/design/icones-app/png)
//
// Résultat :
//   <sortie>/web/      favicon-16/32/48.png, favicon.ico, icon-192.png, icon-512.png,
//                      apple-touch-icon-180.png
//   <sortie>/android/res/
//                      mipmap-{mdpi…xxxhdpi}/ic_launcher{,_round,_foreground,_background,_monochrome}.png
//                      mipmap-anydpi-v26/ic_launcher{,_round}.xml, values/ic_launcher_background.xml
//                      (à copier dans frontend/android/app/src/main/res une fois le projet Capacitor créé)

import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const ICI = dirname(fileURLToPath(import.meta.url));
const RACINE = resolve(ICI, '../..');
const SOURCE = join(RACINE, 'docs/design/icones-app');

const argSortie = process.argv.indexOf('--sortie');
const SORTIE = argSortie > -1 ? resolve(process.argv[argSortie + 1]) : join(SOURCE, 'png');

const DENSITES_ANDROID = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 };
const DP_ICONE_LEGACY = 48;
const DP_CALQUE_ADAPTATIF = 108;

const lireSvg = (chemin) => readFile(join(SOURCE, chemin), 'utf8');

/** Largeur du viewBox, pour rendre le SVG directement à la taille voulue (net à 16 px). */
function largeurViewBox(svg) {
  const m = svg.match(/viewBox="\s*[-\d.]+\s+[-\d.]+\s+([\d.]+)\s+[\d.]+\s*"/);
  if (!m) throw new Error('viewBox introuvable');
  return Number(m[1]);
}

/** Contenu d'un SVG sans la balise racine ni les métadonnées (pour composer plusieurs calques). */
function contenuSvg(svg) {
  return svg
    .replace(/<metadata>[\s\S]*?<\/metadata>/g, '')
    .replace(/^[\s\S]*?<svg[^>]*>/, '')
    .replace(/<\/svg>\s*$/, '');
}

async function rendrePng(svg, taille, fichier) {
  const densite = (72 * taille) / largeurViewBox(svg);
  const png = await sharp(Buffer.from(svg), { density: densite })
    .resize(taille, taille)
    .png({ compressionLevel: 9 })
    .toBuffer();
  await mkdir(dirname(fichier), { recursive: true });
  await writeFile(fichier, png);
  return png;
}

/** Fichier .ico contenant des images PNG (format accepté par tous les navigateurs actuels). */
function construireIco(images) {
  const entete = Buffer.alloc(6);
  entete.writeUInt16LE(0, 0);
  entete.writeUInt16LE(1, 2);
  entete.writeUInt16LE(images.length, 4);
  const repertoire = Buffer.alloc(16 * images.length);
  let decalage = 6 + repertoire.length;
  images.forEach(({ taille, png }, i) => {
    const o = i * 16;
    repertoire.writeUInt8(taille >= 256 ? 0 : taille, o);
    repertoire.writeUInt8(taille >= 256 ? 0 : taille, o + 1);
    repertoire.writeUInt8(0, o + 2);
    repertoire.writeUInt8(0, o + 3);
    repertoire.writeUInt16LE(1, o + 4);
    repertoire.writeUInt16LE(32, o + 6);
    repertoire.writeUInt32LE(png.length, o + 8);
    repertoire.writeUInt32LE(decalage, o + 12);
    decalage += png.length;
  });
  return Buffer.concat([entete, repertoire, ...images.map((i) => i.png)]);
}

async function genererWeb() {
  const dossier = join(SORTIE, 'web');
  const favicon = await lireSvg('favicon.svg');
  const pwa = await lireSvg('pwa-maskable.svg');
  const apple = await lireSvg('apple-touch-icon.svg');

  const icoImages = [];
  for (const taille of [16, 32, 48]) {
    const png = await rendrePng(favicon, taille, join(dossier, `favicon-${taille}.png`));
    icoImages.push({ taille, png });
  }
  await writeFile(join(dossier, 'favicon.ico'), construireIco(icoImages));

  for (const taille of [192, 512]) {
    await rendrePng(pwa, taille, join(dossier, `icon-${taille}.png`));
  }
  await rendrePng(apple, 180, join(dossier, 'apple-touch-icon-180.png'));
}

async function genererAndroid() {
  const res = join(SORTIE, 'android/res');
  const arrierePlan = await lireSvg('android/ic_launcher_background.svg');
  const premierPlan = await lireSvg('android/ic_launcher_foreground.svg');
  const monochrome = await lireSvg('android/ic_launcher_monochrome.svg');

  const couleurFond = arrierePlan.match(/<rect[^>]*fill="(#[0-9A-Fa-f]{3,8})"/)?.[1];
  if (!couleurFond) throw new Error("Couleur de fond introuvable dans ic_launcher_background.svg");

  // Icône « legacy » (Android < 8) : les deux calques composés, recadrés sur la zone visible de 72 dp.
  const calques = contenuSvg(arrierePlan) + contenuSvg(premierPlan);
  const legacy = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72">
<defs><clipPath id="m"><rect x="18" y="18" width="72" height="72" rx="10"/></clipPath></defs>
<g clip-path="url(#m)">${calques}</g></svg>`;
  const legacyRond = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72">
<defs><clipPath id="m"><circle cx="54" cy="54" r="36"/></clipPath></defs>
<g clip-path="url(#m)">${calques}</g></svg>`;

  for (const [nom, facteur] of Object.entries(DENSITES_ANDROID)) {
    const dossier = join(res, `mipmap-${nom}`);
    const tailleLegacy = Math.round(DP_ICONE_LEGACY * facteur);
    const tailleCalque = Math.round(DP_CALQUE_ADAPTATIF * facteur);
    await rendrePng(legacy, tailleLegacy, join(dossier, 'ic_launcher.png'));
    await rendrePng(legacyRond, tailleLegacy, join(dossier, 'ic_launcher_round.png'));
    await rendrePng(premierPlan, tailleCalque, join(dossier, 'ic_launcher_foreground.png'));
    await rendrePng(arrierePlan, tailleCalque, join(dossier, 'ic_launcher_background.png'));
    await rendrePng(monochrome, tailleCalque, join(dossier, 'ic_launcher_monochrome.png'));
  }

  const adaptatif = `<?xml version="1.0" encoding="utf-8"?>
<!-- Généré par infra/scripts/generer-icones.mjs — ne pas modifier à la main. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>
</adaptive-icon>
`;
  await mkdir(join(res, 'mipmap-anydpi-v26'), { recursive: true });
  await writeFile(join(res, 'mipmap-anydpi-v26/ic_launcher.xml'), adaptatif);
  await writeFile(join(res, 'mipmap-anydpi-v26/ic_launcher_round.xml'), adaptatif);

  await mkdir(join(res, 'values'), { recursive: true });
  await writeFile(
    join(res, 'values/ic_launcher_background.xml'),
    `<?xml version="1.0" encoding="utf-8"?>
<!-- Généré par infra/scripts/generer-icones.mjs — ne pas modifier à la main. -->
<resources>
    <color name="ic_launcher_background">${couleurFond.toUpperCase()}</color>
</resources>
`,
  );
}

await genererWeb();
await genererAndroid();
console.log(`Icônes générées dans ${SORTIE}`);
