import { Component, computed, inject } from '@angular/core';

import { ServerStatusService } from '../core/server-status.service';
import { TPipe } from '../core/i18n';
import { IconComponent } from './icon';
import { IconName } from './icons';

/**
 * Alerta de que o servidor está acordando. O servidor gratuito dorme quando fica sem uso e leva até 1 minuto para
 * responder; sem este aviso a pessoa acha que o painel travou. Some sozinho assim que o servidor responde.
 */
@Component({
  selector: 'app-server-status',
  imports: [IconComponent, TPipe],
  template: `
    @if (visible()) {
      <div class="srv" [class]="status.state()" role="status" aria-live="polite" data-testid="server-status">
        <span class="ic"><app-icon [name]="icon()" [size]="22" /></span>
        <div class="txt">
          @switch (status.state()) {
            @case ('ready') {
              <b>{{ 'Servidor pronto' | t }}</b>
              <small>{{ 'Já pode continuar.' | t }}</small>
            }
            @case ('down') {
              <b>{{ 'O servidor não respondeu' | t }}</b>
              <small>{{ 'Confira a sua internet. Vamos continuar tentando.' | t }}</small>
            }
            @default {
              <b>{{ 'O servidor está acordando' | t }}</b>
              <small>{{ 'Na primeira vez do dia isso pode levar até 1 minuto. As suas TVs não são afetadas: elas continuam exibindo o que já baixaram.' | t }}</small>
            }
          }
        </div>
        @if (status.state() === 'down') {
          <button type="button" class="secondary small" (click)="status.probe()"><app-icon name="refresh-cw" [size]="15" />{{ 'Tentar de novo' | t }}</button>
        }
        @if (status.state() === 'waking') {
          <span class="bar" aria-hidden="true"></span>
        }
      </div>
    }
  `,
  styles: `
    .srv {
      position: fixed; z-index: 900; left: 50%; bottom: 18px; transform: translateX(-50%); width: min(560px, calc(100vw - 24px));
      display: flex; align-items: center; gap: 12px; padding: 12px 14px 14px; overflow: hidden; border-radius: var(--r-lg);
      background: var(--warn-bg); color: var(--warn-fg); border: 1px solid var(--warn-line); box-shadow: var(--sh-3); animation: rise 0.25s var(--ease);
    }
    .srv.ready { background: var(--ok-bg); color: var(--ok-fg); border-color: var(--ok-line); }
    .srv.down { background: var(--err-bg); color: var(--err-fg); border-color: var(--err-line); }
    .ic { flex: none; display: grid; place-items: center; width: 40px; height: 40px; border-radius: 12px; background: rgba(255, 255, 255, 0.55); }
    :host-context(:root[data-theme='dark']) .ic { background: rgba(255, 255, 255, 0.08); }
    .txt { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
    .txt small { color: inherit; opacity: 0.88; line-height: 1.35; }
    .bar { position: absolute; left: 0; right: 0; bottom: 0; height: 3px; background: linear-gradient(90deg, transparent, currentColor, transparent); background-size: 40% 100%; background-repeat: no-repeat; animation: slide 1.4s linear infinite; opacity: 0.7; }
    @keyframes slide { from { background-position: -40% 0; } to { background-position: 140% 0; } }
    @media (prefers-reduced-motion: reduce) { .bar { animation: none; background-size: 100% 100%; opacity: 0.35; } }
    @media (max-width: 820px) { .srv { bottom: calc(var(--bottombar-h) + 14px + env(safe-area-inset-bottom)); } }
  `,
})
export class ServerStatusBanner {
  protected readonly status = inject(ServerStatusService);

  protected readonly visible = computed(() => ['waking', 'ready', 'down'].includes(this.status.state()));
  protected readonly icon = computed<IconName>(() => {
    switch (this.status.state()) {
      case 'ready':
        return 'circle-check';
      case 'down':
        return 'wifi-off';
      default:
        return 'server';
    }
  });
}
