import { expect, Page, test } from '@playwright/test';

import { seConnecter } from './outils';

/** Enregistre et appaire le navigateur comme terminal (W-18, A-02). */
async function appairer(page: Page, nom: string): Promise<void> {
  await page.goto('/terminaux');
  await page.locator('#terminal-nom').fill(nom);
  await page.getByRole('button', { name: 'Enregistrer et afficher le QR code' }).click();
  const terminalId = (await page.getByTestId('terminal-id').textContent())!.trim();
  const code = (await page.getByTestId('code-appairage').textContent())!.trim();
  await page.goto('/appairage');
  await page.locator('#appairage-terminal').fill(JSON.stringify({ t: terminalId, c: code.replace('-', '') }));
  await page.getByRole('button', { name: 'Appairer' }).click();
  await expect(page.getByTestId('chargement-termine')).toBeVisible({ timeout: 120_000 });
}

/**
 * Acceptation du LOT 5 : vente complète de 5 articles en moins de 20 secondes ; ventes hors-ligne puis synchronisées
 * sans perte ; clôture avec écart validé par le code PIN du responsable (RG-09). L'impression sur une imprimante
 * réelle se fait à la main (LOT-05.md).
 */
test.describe('Caisse hors-ligne (A-05 à A-16, W-13)', () => {
  test.skip(() => test.info().project.name !== 'bureau', 'Scénario complet sur le poste ; écrans téléphone vérifiés par les captures');
  test.setTimeout(5 * 60 * 1000);

  test('Aminata vend 5 articles en moins de 20 s, vend hors-ligne, synchronise et clôture', async ({ page, context }) => {
    const lot = Date.now() % 100000;
    await seConnecter(page, 'aminata', '/caisses');

    // W-13 : caisse et code PIN de responsable
    await page.getByRole('button', { name: 'Nouvelle caisse' }).click();
    await page.locator('#pdv-code').fill(`CE${lot}`);
    await page.locator('#pdv-nom').fill(`Caisse e2e ${lot}`);
    await page.getByRole('button', { name: 'Créer la caisse' }).click();
    await expect(page.getByRole('heading', { name: `Caisse e2e ${lot}` })).toBeVisible();
    await page.locator('#mon-pin').fill('482916');
    await page.getByRole('button', { name: 'Enregistrer' }).click();
    await expect(page.getByText('Code PIN de responsable enregistré.')).toBeVisible();

    await appairer(page, `Caisse e2e ${lot}`);

    // A-05 : ouverture
    await page.goto('/caisse');
    await page.getByRole('radio', { name: new RegExp(`Caisse e2e ${lot}`) }).check();
    await page.locator('#fonds').fill('10000');
    await page.getByRole('button', { name: 'Ouvrir la caisse' }).click();

    // A-06 → A-12 : 5 articles, espèces, chronométré
    const recherche = page.locator('#recherche-caisse');
    await expect(recherche).toBeVisible();
    const debut = Date.now();
    for (const code of ['CIM-50', 'RIZ-25', 'SAV-400', 'HUI-20', 'TOL-3']) {
      await recherche.fill(code);
      await recherche.press('Enter');
    }
    await expect(page.getByTestId('total-caisse')).not.toHaveText('0');
    await page.getByRole('button', { name: 'Encaisser' }).click();
    await page.locator('amb-pave-caisse .grid button').first().click();
    await page.getByRole('button', { name: 'Valider le paiement' }).click();
    await expect(page.getByRole('heading', { name: 'Vente enregistrée' })).toBeVisible();
    const duree = (Date.now() - debut) / 1000;
    test.info().annotations.push({ type: 'vente 5 articles', description: `${duree.toFixed(1)} s` });
    expect(duree).toBeLessThan(20);
    await expect(page.getByTestId('numero-ticket')).toContainText(/TK-C\d+-\d{4}-\d{6}/);
    await page.screenshot({ path: test.info().outputPath('A-12.png') });

    // Facture demandée en caisse (RG-03) : client choisi, numéro de la plage FACTURE du terminal
    await page.getByRole('button', { name: 'Nouvelle vente' }).click();
    await recherche.fill('CIM-50');
    await recherche.press('Enter');
    await page.getByRole('button', { name: 'Encaisser' }).click();
    await page.getByRole('button', { name: 'Carte' }).click();
    await page.getByLabel('Le client demande une facture certifiée').check();
    await page.locator('#client-facture').fill('batir');
    await page.getByRole('button', { name: /Bâtir Faso SARL/ }).click();
    await page.getByRole('button', { name: 'Valider le paiement' }).click();
    await expect(page.getByTestId('facture-caisse')).toContainText(/Facture FA-C\d+-\d{4}-\d{6}/);
    const numeroFacture = (await page.getByTestId('facture-caisse').textContent())!.match(/FA-C\d+-\d{4}-\d{6}/)![0];

    // Hors-ligne : deux ventes restent sur l'appareil puis partent au retour du réseau
    await context.setOffline(true);
    for (let i = 0; i < 2; i++) {
      await page.getByRole('button', { name: 'Nouvelle vente' }).click();
      await recherche.fill('SAV-400');
      await recherche.press('Enter');
      await page.getByRole('button', { name: 'Encaisser' }).click();
      await page.getByRole('button', { name: 'Mobile Money' }).click();
      await page.locator('#reference-mm').fill(`OM${lot}${i}`);
      await page.getByRole('button', { name: 'Valider le paiement' }).click();
      await expect(page.getByRole('heading', { name: 'Vente enregistrée' })).toBeVisible();
    }
    await page.goto('/synchronisation').catch(() => undefined);
    await context.setOffline(false);
    await page.goto('/synchronisation');
    await page.getByRole('button', { name: 'Synchroniser maintenant' }).click();
    await expect(page.locator('.carte').filter({ hasText: 'En attente' }).locator('p').nth(1)).toHaveText('0', { timeout: 30_000 });

    // A-15 / A-16 : clôture à l'aveugle, écart validé par le PIN du responsable
    await page.goto('/caisse/cloture');
    await page.locator('#especes-comptees').fill('1000');
    await page.getByRole('button', { name: 'Clôturer' }).click();
    await expect(page.getByTestId('ecart')).toContainText('-');
    await page.locator('#responsable').fill('aminata');
    await page.locator('#pin-responsable').fill('482916');
    await page.locator('#motif').fill('Test de clôture e2e');
    await page.getByRole('button', { name: "Valider l'écart" }).click();
    await expect(page.getByText('Écart validé par le responsable.')).toBeVisible({ timeout: 30_000 });

    // W-10 : la facture de caisse est établie et certifiée (simulateur)
    await page.goto('/factures');
    await expect(page.getByRole('button', { name: numeroFacture })).toBeVisible();

    // W-13 : la session apparaît avec son écart validé
    await page.goto('/caisses');
    await expect(page.getByRole('row').filter({ hasText: `Caisse e2e ${lot}` }).first()).toBeVisible();
  });
});
