import { Component, inject } from '@angular/core';

import { ErrorService } from '../core/error.service';
import { TPipe } from '../core/i18n';

/** Mostra os erros no topo da tela. Cada um permanece até o usuário clicar em Fechar. */
@Component({
  selector: 'app-error-banner',
  imports: [TPipe],
  template: `
    <div class="stack" aria-live="assertive">
      @for (error of errors.errors(); track error.id) {
        <div class="toast" role="alert">
          <span class="icon" aria-hidden="true">⚠</span>
          <span class="message">{{ error.message }}</span>
          <button type="button" class="close" (click)="errors.dismiss(error.id)" [attr.aria-label]="'Fechar mensagem de erro' | t">
            {{ 'Fechar' | t }} ✕
          </button>
        </div>
      }
    </div>
  `,
  styles: `
    .stack { position: fixed; top: 14px; left: 50%; transform: translateX(-50%); width: min(760px, calc(100vw - 24px));
      z-index: 1000; display: flex; flex-direction: column; gap: 8px; pointer-events: none; }
    .toast { pointer-events: auto; display: flex; align-items: flex-start; gap: 10px; background: var(--err-bg); color: var(--err-fg);
      border: 1px solid var(--err-line); border-left: 5px solid var(--red); border-radius: 12px; padding: 12px 14px;
      box-shadow: var(--shadow); }
    .icon { font-size: 18px; line-height: 1.4; }
    .message { flex: 1; line-height: 1.45; word-break: break-word; }
    .close { flex: none; background: var(--panel); color: var(--err-fg); border: 1px solid var(--err-line); border-radius: 8px; padding: 5px 10px;
      font-weight: 600; font-size: 13px; }
    .close:hover { background: var(--err-bg); }
    @media (max-width: 720px) {
      .stack { top: calc(8px + env(safe-area-inset-top)); width: calc(100vw - 16px); }
    }
  `,
})
export class ErrorBanner {
  protected readonly errors = inject(ErrorService);
}
