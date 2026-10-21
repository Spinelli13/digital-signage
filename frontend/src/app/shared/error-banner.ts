import { Component, inject } from '@angular/core';

import { ErrorService } from '../core/error.service';
import { TPipe } from '../core/i18n';
import { IconComponent } from './icon';

/** Mostra os erros no topo da tela. Cada um permanece até o usuário clicar em Fechar. */
@Component({
  selector: 'app-error-banner',
  imports: [TPipe, IconComponent],
  template: `
    <div class="stack" aria-live="assertive">
      @for (error of errors.errors(); track error.id) {
        <div class="toast" role="alert">
          <span class="icon" aria-hidden="true"><app-icon name="triangle-alert" [size]="20" /></span>
          <span class="message">{{ error.message }}</span>
          <button type="button" class="close" (click)="errors.dismiss(error.id)" [attr.aria-label]="'Fechar mensagem de erro' | t">
            {{ 'Fechar' | t }}<app-icon name="x" [size]="15" />
          </button>
        </div>
      }
    </div>
  `,
  styles: `
    .stack { position: fixed; top: 14px; left: 50%; transform: translateX(-50%); width: min(760px, calc(100vw - 24px));
      z-index: 1000; display: flex; flex-direction: column; gap: 8px; pointer-events: none; }
    .toast { pointer-events: auto; display: flex; align-items: flex-start; gap: 10px; background: var(--err-bg); color: var(--err-fg);
      border: 1px solid var(--err-line); border-left: 5px solid var(--red); border-radius: var(--r-lg); padding: 12px 14px;
      box-shadow: var(--sh-3); animation: rise 0.2s var(--ease); }
    .icon { flex: none; display: grid; place-items: center; width: 24px; height: 24px; }
    .message { flex: 1; line-height: 1.45; word-break: break-word; }
    .close { flex: none; display: inline-flex; align-items: center; gap: 6px; background: var(--panel); color: var(--err-fg); border: 1px solid var(--err-line);
      border-radius: var(--r-sm); padding: 5px 10px; font-weight: 600; font-size: 13px; }
    .close:hover { background: var(--err-bg); }
    @media (max-width: 720px) {
      .stack { top: calc(8px + env(safe-area-inset-top)); width: calc(100vw - 16px); }
    }
  `,
})
export class ErrorBanner {
  protected readonly errors = inject(ErrorService);
}
