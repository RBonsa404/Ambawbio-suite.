import { Injectable, signal } from '@angular/core';
import { BleClient } from '@capacitor-community/bluetooth-le';
import { Capacitor } from '@capacitor/core';

import { EnteteTicket, lignesTicket, PieceCaisse, ticketEscpos } from './ticket';

const CLE = 'ambawbio.imprimante';
/** Services Bluetooth LE des imprimantes thermiques courantes (constructeurs chinois, ISSC/Microchip). */
const SERVICES_IMPRIMANTES = [
  '000018f0-0000-1000-8000-00805f9b34fb',
  'e7810a71-73ae-499d-8c15-faa9aef0c3f2',
  '49535343-fe7d-4ae5-8fa9-9fafd205e455',
  '0000ff00-0000-1000-8000-00805f9b34fb',
];

interface Imprimante {
  id: string;
  nom: string;
  largeur: 32 | 48;
}

/**
 * Impression des tickets (F-POS-05) : imprimante thermique Bluetooth (ESC/POS, 58 ou 80 mm) sur Android ; dans le
 * navigateur, impression du ticket mis en page par le système (imprimante USB ou réseau du poste).
 */
@Injectable({ providedIn: 'root' })
export class ServiceImpression {
  readonly imprimante = signal<Imprimante | null>(lire());
  readonly bluetooth = Capacitor.isNativePlatform();

  largeur(): 32 | 48 {
    return this.imprimante()?.largeur ?? 32;
  }

  async choisir(largeur: 32 | 48): Promise<void> {
    await BleClient.initialize({ androidNeverForLocation: true });
    const appareil = await BleClient.requestDevice({ optionalServices: SERVICES_IMPRIMANTES });
    const imprimante = { id: appareil.deviceId, nom: appareil.name ?? 'Imprimante', largeur };
    localStorage.setItem(CLE, JSON.stringify(imprimante));
    this.imprimante.set(imprimante);
  }

  oublier(): void {
    localStorage.removeItem(CLE);
    this.imprimante.set(null);
  }

  async imprimer(piece: PieceCaisse, entete: Omit<EnteteTicket, 'largeur'>): Promise<void> {
    const imprimante = this.imprimante();
    if (this.bluetooth && imprimante) {
      await this.envoyerBluetooth(imprimante.id, ticketEscpos(piece, { ...entete, largeur: imprimante.largeur }));
      return;
    }
    imprimerDansLeNavigateur(lignesTicket(piece, { ...entete, largeur: 32 }));
  }

  private async envoyerBluetooth(id: string, octets: Uint8Array): Promise<void> {
    await BleClient.initialize({ androidNeverForLocation: true });
    await BleClient.connect(id);
    try {
      const services = await BleClient.getServices(id);
      let cible: { service: string; caracteristique: string; sansReponse: boolean } | null = null;
      for (const s of services) {
        const c = s.characteristics.find((x) => x.properties.writeWithoutResponse || x.properties.write);
        if (c) {
          cible = { service: s.uuid, caracteristique: c.uuid, sansReponse: c.properties.writeWithoutResponse };
          break;
        }
      }
      if (!cible) {
        throw new Error("Cette imprimante n'accepte pas l'impression Bluetooth LE.");
      }
      for (let i = 0; i < octets.length; i += 100) {
        const morceau = new DataView(octets.slice(i, i + 100).buffer);
        if (cible.sansReponse) {
          await BleClient.writeWithoutResponse(id, cible.service, cible.caracteristique, morceau);
        } else {
          await BleClient.write(id, cible.service, cible.caracteristique, morceau);
        }
      }
    } finally {
      await BleClient.disconnect(id);
    }
  }
}

function imprimerDansLeNavigateur(lignes: string[]): void {
  const cadre = document.createElement('iframe');
  cadre.setAttribute('aria-hidden', 'true');
  cadre.style.cssText = 'position:fixed;width:0;height:0;border:0;';
  document.body.appendChild(cadre);
  const doc = cadre.contentDocument!;
  const pre = doc.createElement('pre');
  pre.style.cssText = 'font:12px/1.3 "IBM Plex Mono",monospace;margin:0;';
  pre.textContent = lignes.join('\n');
  doc.body.appendChild(pre);
  cadre.contentWindow!.print();
  setTimeout(() => cadre.remove(), 1000);
}

function lire(): Imprimante | null {
  try {
    return JSON.parse(localStorage.getItem(CLE) ?? 'null') as Imprimante | null;
  } catch {
    return null;
  }
}
