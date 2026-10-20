import { HttpErrorResponse, HttpEventType, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, filter, throwError, timeout, TimeoutError } from 'rxjs';

import { ErrorService } from './error.service';
import { REQUEST_TIMEOUT_MS, SILENT_ERRORS, describeHttpError, describeTimeout } from './http-errors';

/**
 * Toda falha de comunicação com o servidor vira uma mensagem na tela (até o usuário fechar), com a ação que
 * falhou e uma explicação em português. Também impede uma requisição de ficar pendurada para sempre quando o
 * servidor gratuito está acordando.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const errors = inject(ErrorService);
  const silent = req.context.get(SILENT_ERRORS);

  // Envios de arquivo podem levar muito tempo legitimamente: só as demais requisições têm limite.
  // O Angular emite um evento "requisição enviada" logo de cara; sem filtrar, o timeout contaria só até ele
  // (e nunca dispararia). Só a resposta final interessa nas requisições sem progresso.
  const stream = req.reportProgress
    ? next(req)
    : next(req).pipe(
        filter((event) => event.type === HttpEventType.Response),
        timeout({ first: REQUEST_TIMEOUT_MS }),
      );

  return stream.pipe(
    catchError((error: unknown) => {
      if (!silent) {
        if (error instanceof HttpErrorResponse) {
          errors.show(describeHttpError(error, req));
        } else if (error instanceof TimeoutError) {
          errors.show(describeTimeout(req));
        } else {
          errors.show('Erro inesperado ao falar com o servidor. Tente novamente.');
        }
      }
      return throwError(() => error);
    }),
  );
};
