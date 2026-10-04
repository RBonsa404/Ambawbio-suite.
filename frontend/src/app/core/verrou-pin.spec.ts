import { VerrouPin } from './verrou-pin';
import { uuid7 } from './identifiant';

describe('VerrouPin (A-04, D-01)', () => {
  beforeEach(() => localStorage.clear());

  it("enregistre une empreinte, jamais le code en clair, et vérifie le code", async () => {
    const verrou = new VerrouPin();
    await verrou.definir('2580');
    expect(localStorage.getItem('ambawbio.pin')).not.toContain('2580');
    expect(await verrou.verifier('2580')).toBe(true);
    expect(await verrou.verifier('1111')).toBe(false);
    expect(await verrou.essaisRestants()).toBe(4);
  });

  it('bloque le terminal après 5 essais manqués', async () => {
    const verrou = new VerrouPin();
    await verrou.definir('2580');
    for (let i = 0; i < 5; i++) {
      await verrou.verifier('0000');
    }
    await expect(verrou.verifier('2580')).rejects.toThrow(/Reconnectez-vous/);
  });

  it('refuse un code qui ne fait pas 4 à 6 chiffres', async () => {
    await expect(new VerrouPin().definir('12a4')).rejects.toThrow();
  });
});

describe('uuid7', () => {
  it('produit des identifiants version 7, triables dans le temps', async () => {
    const premier = uuid7();
    await new Promise((r) => setTimeout(r, 2));
    const second = uuid7();
    expect(premier).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    expect(premier < second).toBe(true);
  });
});
