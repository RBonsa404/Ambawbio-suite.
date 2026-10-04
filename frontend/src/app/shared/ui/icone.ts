import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import {
  ArrowLeft, BadgeCheck, Check, ChevronDown, ChevronLeft, CircleAlert, CircleCheck, CircleX, Clock, CloudCheck, CloudOff, Delete, FileDown, FileText, FlaskConical,
  IconNode, LayoutDashboard, Lock, LogOut, Menu, Minus, Package, Plus, RefreshCw, ScanBarcode, Search, Settings, Smartphone, Store,
  Trash2, TriangleAlert, Upload, Users, X,
} from 'lucide';

/** Icônes Lucide (licence ISC) utilisées par l'application ; trait 2 px, grille 24 (docs/design/icones/README.md). */
const ICONES: Record<string, IconNode> = {
  'arrow-left': ArrowLeft, 'badge-check': BadgeCheck, check: Check, 'chevron-down': ChevronDown, 'chevron-left': ChevronLeft, 'circle-alert': CircleAlert,
  'circle-check': CircleCheck, 'circle-x': CircleX, clock: Clock, 'cloud-check': CloudCheck, 'cloud-off': CloudOff, delete: Delete, 'file-down': FileDown, 'file-text': FileText, 'flask-conical': FlaskConical,
  'layout-dashboard': LayoutDashboard, lock: Lock, 'log-out': LogOut, menu: Menu, minus: Minus, package: Package, plus: Plus,
  'refresh-cw': RefreshCw, 'scan-barcode': ScanBarcode, search: Search, settings: Settings, smartphone: Smartphone, store: Store, 'trash-2': Trash2,
  'triangle-alert': TriangleAlert, upload: Upload, users: Users, x: X,
};

export type NomIcone = keyof typeof ICONES;

/** Icône décorative : toujours accompagnée d'un libellé visible ou d'un aria-label sur l'élément parent. */
@Component({
  selector: 'amb-icone',
  template: `
    <svg xmlns="http://www.w3.org/2000/svg" [attr.width]="taille()" [attr.height]="taille()" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
      @for (element of elements(); track $index) {
        @switch (element[0]) {
          @case ('path') { <path [attr.d]="element[1]['d']" /> }
          @case ('circle') { <circle [attr.cx]="element[1]['cx']" [attr.cy]="element[1]['cy']" [attr.r]="element[1]['r']" /> }
          @case ('line') { <line [attr.x1]="element[1]['x1']" [attr.y1]="element[1]['y1']" [attr.x2]="element[1]['x2']" [attr.y2]="element[1]['y2']" /> }
          @case ('rect') {
            <rect [attr.x]="element[1]['x']" [attr.y]="element[1]['y']" [attr.width]="element[1]['width']" [attr.height]="element[1]['height']"
              [attr.rx]="element[1]['rx']" />
          }
          @case ('polyline') { <polyline [attr.points]="element[1]['points']" /> }
        }
      }
    </svg>
  `,
  host: { class: 'inline-flex shrink-0' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Icone {
  readonly nom = input.required<string>();
  readonly taille = input(20);
  protected readonly elements = computed(() => (ICONES[this.nom()] ?? []) as [string, Record<string, string>][]);
}
