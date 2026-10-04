import { expect, test } from '@playwright/test';

// SD-01 (extrait LOT 0) : un utilisateur de démonstration se connecte par Keycloak et voit son accueil.
test('Awa se connecte et voit la page « Ambawbio Suite »', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/realms\/ambawbio\//);
  await page.getByLabel(/nom d.utilisateur|username/i).fill('awa');
  await page.getByLabel(/mot de passe|password/i).first().fill('demo-ambawbio');
  await page.getByRole('button', { name: /connexion|se connecter|sign in/i }).click();

  await expect(page.getByRole('heading', { level: 1 })).toContainText('Bonjour, Awa Kaboré');
  await expect(page.getByText('caissier')).toBeVisible();
});
