import { Injectable, signal } from '@angular/core';
import { Capacitor } from '@capacitor/core';
import { Network } from '@capacitor/network';

/** État du réseau, mis à jour sur événement (plugin Capacitor sur Android, événements du navigateur sur le web). */
@Injectable({ providedIn: 'root' })
export class ServiceReseau {
  readonly enLigne = signal(typeof navigator === 'undefined' ? true : navigator.onLine);
  readonly derniereSynchro = signal<Date | null>(new Date());

  constructor() {
    if (Capacitor.isNativePlatform()) {
      void Network.getStatus().then((s) => this.enLigne.set(s.connected));
      void Network.addListener('networkStatusChange', (s) => this.enLigne.set(s.connected));
    } else if (typeof window !== 'undefined') {
      window.addEventListener('online', () => this.enLigne.set(true));
      window.addEventListener('offline', () => this.enLigne.set(false));
    }
  }
}
