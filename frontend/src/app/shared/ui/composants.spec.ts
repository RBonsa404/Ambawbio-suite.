import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { ChampMontant } from './champ-montant';
import { IndicateurSync, EtatSynchronisation } from './indicateur-sync';

@Component({
  imports: [ReactiveFormsModule, ChampMontant, IndicateurSync],
  template: `
    <amb-champ-montant identifiant="m" [formControl]="montant" />
    <amb-indicateur-sync [etat]="etat()" [enAttente]="3" />
  `,
})
class Hote {
  readonly montant = new FormControl<number | null>(12500);
  readonly etat = signal<EtatSynchronisation>('hors-ligne');
}

describe('Composants du système de conception', () => {
  it('le champ montant affiche « 12 500 » et renvoie un entier', async () => {
    const fixture = TestBed.createComponent(Hote);
    await fixture.whenStable();
    const champ = (fixture.nativeElement as HTMLElement).querySelector('input') as HTMLInputElement;
    expect(champ.value).toBe('12 500');
    champ.value = '34 600 F';
    champ.dispatchEvent(new Event('input'));
    expect(fixture.componentInstance.montant.value).toBe(34600);
    expect(champ.value).toBe('34 600');
  });

  it("l'indicateur dit l'état en toutes lettres (jamais la couleur seule)", async () => {
    const fixture = TestBed.createComponent(Hote);
    await fixture.whenStable();
    const indicateur = (fixture.nativeElement as HTMLElement).querySelector('[role="status"]') as HTMLElement;
    expect(indicateur.textContent).toContain("Hors-ligne — 3 opérations sur l'appareil");
    fixture.componentInstance.etat.set('en-attente');
    await fixture.whenStable();
    expect(indicateur.textContent).toContain("3 opérations en attente d'envoi");
  });
});
