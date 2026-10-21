import { HttpErrorResponse } from '@angular/common/http';
import { ErrorHandler, Injectable, inject } from '@angular/core';
import { TimeoutError } from 'rxjs';

import { ErrorService } from './error.service';
import { tr } from './i18n';

/**
 * Qualquer exceção que ninguém tratou (bug, dado inesperado) aparece como aviso em vez de sumir no console.
 * Falhas de rede já foram mostradas pelo interceptor, então são ignoradas aqui.
 */
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  private readonly errors = inject(ErrorService);

  handleError(error: unknown): void {
    console.error(error);
    if (error instanceof HttpErrorResponse || error instanceof TimeoutError) {
      return;
    }
    const detail = error instanceof Error && error.message ? ` (${error.message})` : '';
    this.errors.show(tr('Ocorreu um erro inesperado no painel{detail}. Se continuar, recarregue a página.', { detail }));
  }
}
