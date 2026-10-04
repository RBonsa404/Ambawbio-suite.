import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { ServiceContexte } from '../../core/service-contexte';
import { Icone } from '../../shared/ui/icone';

/** Accueil (W-04 en préparation) : contexte de l'utilisateur et accès rapides ; les indicateurs arrivent au LOT 12. */
@Component({
  selector: 'app-accueil',
  imports: [RouterLink, TranslocoPipe, Icone],
  templateUrl: './accueil.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Accueil {
  protected readonly contexte = inject(ServiceContexte);
}
