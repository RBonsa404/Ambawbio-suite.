import { ChangeDetectionStrategy, Component, Injectable, inject, signal } from '@angular/core';

import { Icone } from './icone';

export type TypeNotification = 'succes' | 'erreur' | 'info';

interface Notification {
  id: number;
  type: TypeNotification;
  message: string;
  action?: { libelle: string; executer: () => void };
}

/** Notifications (toasts) : succès, erreur, info ; action « Annuler » possible (pas de confirmation pour le réversible). */
@Injectable({ providedIn: 'root' })
export class Notifications {
  readonly liste = signal<Notification[]>([]);
  private suivant = 1;

  afficher(type: TypeNotification, message: string, action?: Notification['action'], duree = 5000): void {
    const id = this.suivant++;
    this.liste.update((l) => [...l, { id, type, message, action }]);
    setTimeout(() => this.fermer(id), duree);
  }

  succes(message: string, action?: Notification['action']): void {
    this.afficher('succes', message, action);
  }

  erreur(message: string): void {
    this.afficher('erreur', message, undefined, 8000);
  }

  fermer(id: number): void {
    this.liste.update((l) => l.filter((n) => n.id !== id));
  }
}

@Component({
  selector: 'amb-notifications',
  imports: [Icone],
  template: `
    <div class="fixed bottom-4 left-1/2 z-50 flex w-[min(28rem,calc(100%-2rem))] -translate-x-1/2 flex-col gap-2" aria-live="polite">
      @for (n of service.liste(); track n.id) {
        <div class="notification" [class]="'notification-' + n.type" role="status">
          <amb-icone [nom]="n.type === 'erreur' ? 'circle-alert' : n.type === 'succes' ? 'circle-check' : 'clock'" />
          <p class="flex-1">{{ n.message }}</p>
          @if (n.action; as a) {
            <button type="button" class="font-semibold underline" (click)="a.executer(); service.fermer(n.id)">{{ a.libelle }}</button>
          }
          <button type="button" class="bouton-icone-petit" (click)="service.fermer(n.id)" aria-label="Fermer"><amb-icone nom="x" [taille]="16" /></button>
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConteneurNotifications {
  protected readonly service = inject(Notifications);
}
