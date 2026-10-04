import AxeBuilder from '@axe-core/playwright';
import { expect, Page, test } from '@playwright/test';

import { seConnecter } from './outils';

async function verifierAccessibilite(page: Page, nom: string) {
  const resultat = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
  const violations = resultat.violations.map((v) => `${v.id} (${v.impact}) : ${v.nodes.map((n) => n.target.join(" ")).join(" | ")}`);
  expect(violations, `Accessibilité WCAG AA — ${nom}`).toEqual([]);
}

test.describe('Référentiels (W-05, W-06, W-07)', () => {
  test('Aminata crée un produit et le retrouve sans accent', async ({ page }) => {
    await seConnecter(page, 'aminata', '/produits');
    await expect(page.getByRole('heading', { name: 'Produits', level: 1 })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'CIM-50' })).toBeVisible();
    await page.getByRole('button', { name: 'Nouveau produit' }).first().click();
    const code = `TST-${Date.now() % 100000}`;
    await page.locator('#p-code').fill(code);
    await page.locator('#p-nom').fill('Pâte d’arachide 1 kg');
    await page.locator('#p-prix').fill('1750');
    await expect(page.locator('#p-prix')).toHaveValue('1 750');
    await page.getByRole('button', { name: 'Enregistrer' }).click();
    await expect(page.getByText(/Produit « Pâte d’arachide 1 kg » enregistré/)).toBeVisible();
    // Sur téléphone, la fiche occupe tout l'écran : on la ferme pour revenir à la liste.
    await page.getByRole('region', { name: 'Fiche produit' }).getByRole('button', { name: 'Fermer' }).click();
    await page.locator('#recherche-produits').fill('pate d');
    await expect(page.getByRole('cell', { name: code })).toBeVisible();
  });

  test("l'assistant d'import vérifie, signale les lignes en erreur puis importe les lignes valides", async ({ page }) => {
    await seConnecter(page, 'aminata', '/import?type=produits');
    await page.getByRole('button', { name: "J'ai déjà mon fichier" }).click();
    await page.locator('#fichier-import').setInputFiles('e2e/fichiers/produits-e2e.csv');
    await expect(page.getByText('Rapport ligne par ligne')).toBeVisible();
    const lignes = page.locator('table.tableau tbody tr');
    await expect(lignes).toHaveCount(2);
    await expect(lignes.nth(0)).toContainText('prix_vente');
    await expect(lignes.nth(1)).toContainText('taxe');
    await page.getByRole('button', { name: 'Importer les 2 lignes valides' }).click();
    await expect(page.getByText('Import terminé : 2 lignes enregistrées.')).toBeVisible();
  });

  test('contrastes et accessibilité WCAG AA sur les écrans du lot', async ({ page }) => {
    await seConnecter(page, 'aminata', '/');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Bonjour');
    await verifierAccessibilite(page, 'accueil');
    await page.getByRole('link', { name: 'Produits' }).filter({ visible: true }).first().click();
    await page.getByRole('button', { name: 'Ciment CPJ 45 — sac 50 kg' }).click();
    await expect(page.getByRole('heading', { name: 'Ciment CPJ 45 — sac 50 kg', level: 2 })).toBeVisible();
    await verifierAccessibilite(page, 'produits (liste et fiche)');
    await page.screenshot({ path: `test-results/W-05-${test.info().project.name}.png`, fullPage: true });
    await page.getByRole('link', { name: 'Clients et fournisseurs' }).filter({ visible: true }).first().click();
    await page.getByRole('button', { name: 'Bâtir Faso SARL (démo)' }).click();
    await verifierAccessibilite(page, 'clients et fournisseurs');
    await page.getByRole('link', { name: 'Import de données' }).filter({ visible: true }).first().click();
    await verifierAccessibilite(page, 'import');
  });

  test('page de connexion aux couleurs de la marque, accessible', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/realms\/ambawbio\//);
    await expect(page.locator('#kc-header-wrapper')).toHaveCSS('background-image', /ambawbio-horizontal-blanc/);
    await verifierAccessibilite(page, 'connexion Keycloak');
    await page.screenshot({ path: `test-results/A-03-${test.info().project.name}.png` });
  });
});
