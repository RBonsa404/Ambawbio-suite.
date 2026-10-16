// Rendu de la vidéo de lancement : image par image (30 i/s) dans Chromium, assemblage MP4 par ffmpeg,
// plus le texte de la voix off et les sous-titres SRT, tous calés sur le même minutage (scenes.js).
// Usage : node outils/video-lancement/rendre.mjs <dossier de sortie> [--apercu]
import { spawn } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { minuter } from './scenes.js';

const ici = dirname(fileURLToPath(import.meta.url));
const require = createRequire(join(ici, '../../frontend/package.json'));
const { chromium } = require('playwright');

const sortie = resolve(process.argv[2] ?? 'video-lancement');
const apercu = process.argv.includes('--apercu');
mkdirSync(sortie, { recursive: true });
const IPS = 30;
const scenes = minuter();
const duree = process.env.DUREE ? +process.env.DUREE : scenes.at(-1).debut + scenes.at(-1).duree;

// --- Sous-titres et script ---------------------------------------------------------------------
const hms = (s) => {
  const ms = Math.round(s * 1000);
  const p = (n, l = 2) => String(n).padStart(l, '0');
  return `${p(Math.floor(ms / 3600000))}:${p(Math.floor(ms / 60000) % 60)}:${p(Math.floor(ms / 1000) % 60)},${p(ms % 1000, 3)}`;
};
function decouper(texte) {
  // Phrases, puis morceaux de 84 caractères au plus (deux lignes de 42).
  const morceaux = [];
  for (const phrase of texte.match(/[^.!?…]+[.!?…]*/g).map((x) => x.trim()).filter(Boolean)) {
    let courant = '';
    for (const mot of phrase.split(' ')) {
      if ((courant + ' ' + mot).trim().length > 84) { morceaux.push(courant.trim()); courant = ''; }
      courant += ' ' + mot;
    }
    if (courant.trim()) morceaux.push(courant.trim());
  }
  return morceaux;
}
function enLignes(m) {
  if (m.length <= 42) return m;
  const mots = m.split(' ');
  let meilleur = m, ecart = Infinity;
  for (let i = 1; i < mots.length; i++) {
    const a = mots.slice(0, i).join(' '), b = mots.slice(i).join(' ');
    if (Math.abs(a.length - b.length) < ecart) { ecart = Math.abs(a.length - b.length); meilleur = a + '\n' + b; }
  }
  return meilleur;
}
let srt = '', n = 1;
for (const s of scenes) {
  const morceaux = decouper(s.voix);
  const total = morceaux.reduce((a, m) => a + m.length, 0);
  let t = s.paroleDebut;
  for (const m of morceaux) {
    const d = (s.paroleFin - s.paroleDebut) * (m.length / total);
    srt += `${n++}\n${hms(t)} --> ${hms(t + d - 0.05)}\n${enLignes(m)}\n\n`;
    t += d;
  }
}
writeFileSync(join(sortie, 'sous-titres.srt'), srt);

const mm = (s) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`;
let script = `# Ambawbio Suite — vidéo de lancement : texte de la voix off\n\nDurée totale : ${mm(duree)}. Une voix posée et chaleureuse, débit d'environ 2,5 mots par seconde (150 mots par minute).\n` +
  `Chaque passage commence environ 0,6 seconde après le début de sa scène. Les minutages sont ceux de la vidéo et de \`sous-titres.srt\`.\n\n`;
scenes.forEach((s, i) => {
  script += `## ${i + 1}. ${s.titre} — ${mm(s.debut)} à ${mm(s.debut + s.duree)}\n\n${s.voix}\n\n`;
});
script += `---\n\n## Texte intégral (à coller dans un générateur de voix)\n\n${scenes.map((s) => s.voix).join('\n\n')}\n`;
writeFileSync(join(sortie, 'voix-off.md'), script);
writeFileSync(join(sortie, 'voix-off.txt'), scenes.map((s) => s.voix).join('\n\n') + '\n');

// --- Images -------------------------------------------------------------------------------------
const navigateur = await chromium.launch(process.env.CHROMIUM ? { executablePath: process.env.CHROMIUM } : {});
const page = await navigateur.newPage({ viewport: { width: 1920, height: 1080 } });
await page.goto(pathToFileURL(join(ici, 'video.html')).href);
await page.evaluate((tl) => { window.TIMELINE = tl; }, scenes);
await page.evaluate(() => document.fonts.ready);

if (apercu) {
  for (const s of scenes) {
    await page.evaluate((t) => window.rendre(t), s.debut + Math.min(s.duree - 0.6, 7.5));
    await page.screenshot({ path: join(sortie, `apercu-${s.id}.png`) });
  }
  await navigateur.close();
  console.log('Aperçus écrits dans', sortie);
  process.exit(0);
}

const fichier = join(sortie, 'ambawbio-suite-lancement.mp4');
const ffmpeg = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(IPS), '-c:v', 'mjpeg', '-i', '-',
  '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', fichier], { stdio: ['pipe', 'inherit', 'inherit'] });
const cdp = await page.context().newCDPSession(page);
const images = Math.round(duree * IPS);
for (let f = 0; f < images; f++) {
  await page.evaluate((t) => window.rendre(t), f / IPS);
  const { data } = await cdp.send('Page.captureScreenshot', { format: 'jpeg', quality: 92 });
  const img = Buffer.from(data, 'base64');
  if (!ffmpeg.stdin.write(img)) await new Promise((r) => ffmpeg.stdin.once('drain', r));
  if (f % 300 === 0) console.log(`${mm(f / IPS)} / ${mm(duree)}`);
}
ffmpeg.stdin.end();
await new Promise((r) => ffmpeg.on('close', r));
await navigateur.close();
console.log('Vidéo :', fichier);
