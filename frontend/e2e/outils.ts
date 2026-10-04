import { expect, Page } from '@playwright/test';

export async function seConnecter(page: Page, utilisateur: string, chemin = '/') {
  await page.goto(chemin);
  await expect(page).toHaveURL(/\/realms\/ambawbio\//);
  await page.getByLabel(/nom d.utilisateur|username/i).fill(utilisateur);
  await page.getByLabel(/mot de passe|password/i).first().fill('demo-ambawbio');
  await page.getByRole('button', { name: /connexion|se connecter|sign in/i }).click();
}
