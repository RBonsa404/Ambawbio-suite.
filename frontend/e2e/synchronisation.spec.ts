import { writeFileSync } from 'node:fs';

import { expect, test } from '@playwright/test';

import { seConnecter } from './outils';

const PRODUITS = 2000;

/**
 * Critère d'acceptation du LOT 4 : un terminal appairé charge 2 000 produits en moins de 2 minutes sur une connexion 3G.
 * Le terminal est ici le même navigateur que le poste de la gérante ; le test sur téléphone réel est fait à la main (LOT-04.md).
 */
test.describe('Synchronisation des terminaux (W-18, A-02, A-17)', () => {
  test.skip(() => test.info().project.name !== 'bureau', 'Limitation réseau par CDP : Chromium bureau seulement');
  test.setTimeout(6 * 60 * 1000);

  test('Aminata enregistre un terminal, l’appaire et charge 2 000 produits en moins de 2 minutes en 3G', async ({ page, context }) => {
    // 1. Catalogue de 2 000 produits importés par l'assistant (W-07)
    const lot = Date.now() % 1000000;
    const lignes = Array.from({ length: PRODUITS }, (_, i) => `SYN${lot}-${i};Produit synchro ${lot} n° ${i};${500 + i};TVA18;`);
    const fichier = test.info().outputPath('produits-2000.csv');
    writeFileSync(fichier, ['code;nom;prix_vente;taxe;code_barre', ...lignes].join('\n'));
    await seConnecter(page, 'aminata', '/import?type=produits');
    await page.getByRole('button', { name: "J'ai déjà mon fichier" }).click();
    await page.locator('#fichier-import').setInputFiles(fichier);
    await page.getByRole('button', { name: `Importer les ${PRODUITS} lignes valides` }).click();
    await expect(page.getByText(`Import terminé : ${PRODUITS} lignes enregistrées.`)).toBeVisible({ timeout: 120_000 });

    // 2. Enregistrement du terminal et QR code d'appairage (W-18)
    await page.goto('/terminaux');
    await page.locator('#terminal-nom').fill(`Caisse e2e ${lot}`);
    await page.getByRole('button', { name: 'Enregistrer et afficher le QR code' }).click();
    await expect(page.getByRole('img', { name: "QR code d'appairage du terminal" })).toBeVisible();
    const terminalId = (await page.getByTestId('terminal-id').textContent())!.trim();
    const code = (await page.getByTestId('code-appairage').textContent())!.trim();

    // 3. Appairage et chargement initial en 3G (A-02) : 1,6 Mbit/s descendant, 750 kbit/s montant, 562 ms de latence
    await page.goto('/appairage');
    const cdp = await context.newCDPSession(page);
    await cdp.send('Network.enable');
    await cdp.send('Network.emulateNetworkConditions', {
      offline: false,
      latency: 562,
      downloadThroughput: (1.6 * 1024 * 1024) / 8,
      uploadThroughput: (750 * 1024) / 8,
    });
    await page.locator('#appairage-terminal').fill(JSON.stringify({ t: terminalId, c: code.replace('-', '') }));
    const debut = Date.now();
    await page.getByRole('button', { name: 'Appairer' }).click();
    const termine = page.getByTestId('chargement-termine');
    await expect(termine).toBeVisible({ timeout: 120_000 });
    const duree = (Date.now() - debut) / 1000;
    const texte = (await termine.textContent())!;
    const produits = Number(/dont (\d+) produits/.exec(texte)![1]);
    test.info().annotations.push({ type: 'chargement 3G', description: `${produits} produits en ${duree.toFixed(1)} s` });
    expect(produits).toBeGreaterThanOrEqual(PRODUITS);
    expect(duree).toBeLessThan(120);

    // 4. État de la synchronisation (A-17)
    await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1 });
    await page.getByRole('button', { name: 'Voir la synchronisation' }).click();
    await expect(page.getByRole('heading', { name: 'Synchronisation', level: 1 })).toBeVisible();
    await expect(page.getByText(/TK-C\d+-\d{4}-/)).toBeVisible();
    await page.screenshot({ path: test.info().outputPath('A-17.png'), fullPage: true });
  });
});
