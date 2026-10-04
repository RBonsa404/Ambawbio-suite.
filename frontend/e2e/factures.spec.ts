import { expect, test } from '@playwright/test';

import { seConnecter } from './outils';

/**
 * Acceptation LOT 6 côté interface (W-10) : facture créée, validée et certifiée (simulateur), certification différée
 * quand le service est indisponible puis relancée, avoir partiel, PDF.
 */
test.describe('Factures et avoirs (W-10)', () => {
  test.skip(() => test.info().project.name !== 'bureau', 'Écran de bureau (liste et fiche côte à côte)');

  test('Aminata facture, valide, suit la certification et émet un avoir', async ({ page }) => {
    await seConnecter(page, 'aminata', '/factures');
    await expect(page.getByRole('heading', { name: 'Factures et avoirs', level: 1 })).toBeVisible();
    await expect(page.getByText('Mode démonstration')).toBeVisible();

    // Brouillon : client, produit du catalogue, ligne libre
    await page.getByRole('button', { name: 'Nouvelle facture' }).click();
    await page.locator('#facture-client').fill('Bâtir');
    await page.getByRole('button', { name: /Bâtir Faso SARL/ }).click();
    await page.locator('#facture-produit').fill('ciment');
    await page.getByRole('button', { name: /Ciment CPJ 45/ }).click();
    await page.getByLabel('Quantité ligne 1').fill('10');
    await page.getByRole('button', { name: 'Enregistrer le brouillon' }).click();
    await expect(page.getByText('Brouillon enregistré.')).toBeVisible();

    // Validation : numéro sans trou, certification simulée, PDF archivé
    await page.getByRole('button', { name: 'Valider la facture' }).click();
    const message = page.getByText(/Facture FA-\d{4}-\d{6} validée/);
    await expect(message).toBeVisible();
    const numero = (await message.textContent())!.match(/FA-\d{4}-\d{6}/)![0];
    await expect(page.getByRole('region', { name: 'Fiche de la pièce' }).getByLabel('Simulée — sans valeur fiscale')).toBeVisible({ timeout: 10_000 });
    const telechargement = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Télécharger le PDF' }).click();
    await (await telechargement).saveAs(test.info().outputPath('facture.pdf'));

    // Certification différée : service indisponible, facture en file, puis relance
    await page.getByLabel('État du simulateur').selectOption('INDISPONIBLE');
    await page.getByRole('button', { name: 'Nouvelle facture' }).click();
    await page.locator('#facture-client').fill('Bâtir');
    await page.getByRole('button', { name: /Bâtir Faso SARL/ }).click();
    await page.getByRole('button', { name: 'Ligne libre (prestation)' }).click();
    await page.getByLabel('Désignation ligne 1').fill('Livraison sur chantier');
    await page.getByLabel('Prix ligne 1').fill('15000');
    await page.getByRole('button', { name: 'Enregistrer le brouillon' }).click();
    await page.getByRole('button', { name: 'Valider la facture' }).click();
    const fiche = page.getByRole('region', { name: 'Fiche de la pièce' });
    await expect(fiche.getByLabel('En file de certification')).toBeVisible();
    await expect(page.getByTestId('en-file')).toBeVisible();
    await page.getByLabel('État du simulateur').selectOption('DISPONIBLE');
    await page.getByRole('button', { name: 'Relancer la certification' }).click();
    await expect(fiche.getByLabel('Simulée — sans valeur fiscale')).toBeVisible({ timeout: 10_000 });

    // Avoir partiel sur la première facture
    await page.getByRole('button', { name: numero, exact: true }).click();
    await page.getByRole('button', { name: 'Créer un avoir' }).click();
    await page.locator('#motif-avoir').fill('Deux sacs abîmés');
    await page.getByRole('spinbutton').first().fill('2');
    await page.getByRole('button', { name: 'Avoir partiel' }).click();
    await expect(page.getByText(/Avoir AV-\d{4}-\d{6} émis/)).toBeVisible();
    await page.screenshot({ path: test.info().outputPath('W-10.png'), fullPage: true });
  });
});
