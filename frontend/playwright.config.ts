import { defineConfig, devices } from '@playwright/test';

/**
 * Tests de bout en bout. Prérequis : environnement local démarré
 * (docker compose -f ../infra/docker-compose.dev.yml up -d) et serveur back-end lancé.
 * Les scénarios SD-01 à SD-13 sont ajoutés lot par lot (guide §13).
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 2 : 0,
  reporter: process.env['CI'] ? 'github' : 'list',
  use: {
    baseURL: 'http://localhost:4200',
    locale: 'fr-FR',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    // Navigateur déjà installé sur le poste (optionnel) : PLAYWRIGHT_CHROMIUM=/chemin/vers/chromium
    launchOptions: { executablePath: process.env['PLAYWRIGHT_CHROMIUM'] || undefined },
  },
  projects: [
    { name: 'telephone', use: { ...devices['Pixel 5'] } },
    { name: 'bureau', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } },
  ],
  webServer: {
    command: 'npm start',
    url: 'http://localhost:4200',
    reuseExistingServer: !process.env['CI'],
  },
});
