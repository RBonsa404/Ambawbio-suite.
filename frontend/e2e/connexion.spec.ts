import { expect, Page, test } from '@playwright/test';

async function seConnecter(page: Page, utilisateur: string) {
  await page.goto('/');
  await expect(page).toHaveURL(/\/realms\/ambawbio\//);
  await page.getByLabel(/nom d.utilisateur|username/i).fill(utilisateur);
  await page.getByLabel(/mot de passe|password/i).first().fill('demo-ambawbio');
  await page.getByRole('button', { name: /connexion|se connecter|sign in/i }).click();
}

// SD-01 : authentification, contexte de l'entreprise et établissements autorisés (RG-14).
test('Awa se connecte et ne voit que son établissement', async ({ page }) => {
  await seConnecter(page, 'awa');
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Bonjour, Awa Kaboré');
  await expect(page.getByText('Quincaillerie Wend-Panga')).toBeVisible();
  await expect(page.getByText('Ouaga — Zogona')).toBeVisible();
  await expect(page.getByText('Bobo-Dioulasso — Accart-Ville')).toHaveCount(0);
  await expect(page.getByText('caissier', { exact: true })).toBeVisible();
});

// Acceptation LOT 1 : la double authentification est exigée pour un comptable (guide §6.5).
test('Mariam, comptable, doit configurer la double authentification', async ({ page }) => {
  await seConnecter(page, 'mariam');
  await expect(page).toHaveURL(/\/realms\/ambawbio\//);
  await expect(page.getByText(/authentificat(eur|ion) mobile|application d.authentification|FreeOTP|Google Authenticator/i).first()).toBeVisible();
  await expect(page.getByText('Bonjour')).toHaveCount(0);
});
