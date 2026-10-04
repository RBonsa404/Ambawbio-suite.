import { expect, test } from '@playwright/test';

import { seConnecter } from './outils';

// SD-01 : authentification, contexte de l'entreprise et établissements autorisés (RG-14).
test('Awa se connecte et ne voit que son établissement', async ({ page }) => {
  await seConnecter(page, 'awa');
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Bonjour, Awa Kaboré');
  await expect(page.getByText('Quincaillerie Wend-Panga').first()).toBeVisible();
  const etablissements = page.locator('#choix-etablissement option');
  await expect(etablissements).toHaveText(['Ouaga — Zogona']);
  // Une caissière n'a pas l'import dans son menu.
  await expect(page.getByRole('link', { name: 'Import de données' })).toHaveCount(0);
});

// Acceptation LOT 1 : la double authentification est exigée pour un comptable (guide §6.5).
test('Mariam, comptable, doit configurer la double authentification', async ({ page }) => {
  await seConnecter(page, 'mariam');
  await expect(page).toHaveURL(/\/realms\/ambawbio\//);
  await expect(page.getByText(/authentificat(eur|ion) mobile|application d.authentification|FreeOTP|Google Authenticator/i).first()).toBeVisible();
});
