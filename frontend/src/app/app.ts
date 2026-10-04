import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { BoiteConfirmation } from './shared/ui/confirmation';
import { ConteneurNotifications } from './shared/ui/notifications';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ConteneurNotifications, BoiteConfirmation],
  template: '<router-outlet /><amb-notifications /><amb-confirmation />',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {}
