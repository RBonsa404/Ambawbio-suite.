import { Injectable } from '@angular/core';
import { BarcodeFormat, BarcodeScanner } from '@capacitor-mlkit/barcode-scanning';
import { Capacitor } from '@capacitor/core';

/**
 * Lecture des codes-barres (F-POS-01) : caméra du téléphone (ML Kit, hors-ligne) sur Android ; dans le navigateur,
 * douchette USB/Bluetooth (saisie clavier rapide terminée par Entrée), gérée par l'écran de caisse.
 */
@Injectable({ providedIn: 'root' })
export class ServiceScanner {
  readonly camera = Capacitor.isNativePlatform();

  /** Lit un code avec la caméra ; null si annulé. */
  async lire(): Promise<string | null> {
    const { camera } = await BarcodeScanner.requestPermissions();
    if (camera !== 'granted' && camera !== 'limited') {
      throw new Error("L'accès à la caméra est refusé : autorisez-le dans les réglages du téléphone.");
    }
    const { barcodes } = await BarcodeScanner.scan({
      formats: [BarcodeFormat.Ean13, BarcodeFormat.Ean8, BarcodeFormat.UpcA, BarcodeFormat.UpcE, BarcodeFormat.Code128, BarcodeFormat.Code39, BarcodeFormat.QrCode],
    });
    return barcodes[0]?.rawValue ?? null;
  }
}

/** Détecte une douchette : caractères tapés à moins de 40 ms d'intervalle, terminés par Entrée. */
export class DetecteurDouchette {
  private tampon = '';
  private dernier = 0;

  constructor(private readonly surCode: (code: string) => void) {}

  touche(evenement: KeyboardEvent): boolean {
    const maintenant = evenement.timeStamp || performance.now();
    if (maintenant - this.dernier > 40) {
      this.tampon = '';
    }
    this.dernier = maintenant;
    if (evenement.key === 'Enter') {
      const code = this.tampon;
      this.tampon = '';
      if (code.length >= 4) {
        this.surCode(code);
        return true;
      }
      return false;
    }
    if (evenement.key.length === 1) {
      this.tampon += evenement.key;
    }
    return false;
  }
}
