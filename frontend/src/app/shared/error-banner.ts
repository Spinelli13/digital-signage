import { Component, inject } from '@angular/core';

import { ErrorService } from '../core/error.service';

/** Mostra os erros no topo da tela. Cada um permanece até o usuário clicar em Fechar. */
@Component({
  selector: 'app-error-banner',
  template: `
    <div class="stack" aria-live="assertive">
      @for (error of errors.errors(); track error.id) {
        <div class="toast" role="alert">
          <span class="icon" aria-hidden="true">⚠</span>
          <span class="message">{{ error.message }}</span>
          <button type="button" class="close" (click)="errors.dismiss(error.id)" aria-label="Fechar mensagem de erro">
            Fechar ✕
          </button>
        </div>
      }
    </div>
  `,
  styles: `
    .stack { position: fixed; top: 14px; left: 50%; transform: translateX(-50%); width: min(760px, calc(100vw - 24px));
      z-index: 1000; display: flex; flex-direction: column; gap: 8px; pointer-events: none; }
    .toast { pointer-events: auto; display: flex; align-items: flex-start; gap: 10px; background: #fdf0f2; color: #8c1d2c;
      border: 1px solid #f2c8cf; border-left: 5px solid var(--red); border-radius: 12px; padding: 12px 14px;
      box-shadow: 0 8px 24px rgba(23, 32, 51, 0.18); }
    .icon { font-size: 18px; line-height: 1.4; }
    .message { flex: 1; line-height: 1.45; word-break: break-word; }
    .close { flex: none; background: #fff; color: #8c1d2c; border: 1px solid #f2c8cf; border-radius: 8px; padding: 5px 10px;
      font-weight: 600; font-size: 13px; }
    .close:hover { background: #fbe3e7; }
  `,
})
export class ErrorBanner {
  protected readonly errors = inject(ErrorService);
}
