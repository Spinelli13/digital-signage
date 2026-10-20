import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';
import { ConfigService } from './config.service';

/**
 * Anexa o JWT (e, para o operador da plataforma, o cliente em que ele está atuando) às chamadas para a API e volta
 * ao login quando o servidor responde 401. Um 402 pode ser "assinatura suspensa": recarrega a conta, e o painel
 * leva o cliente para "Plano e cobrança".
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const config = inject(ConfigService);
  const router = inject(Router);

  const isApiCall = req.url.startsWith(config.apiBaseUrl);
  const isLogin = req.url.endsWith('/auth/login');

  let outgoing = req;
  if (isApiCall && auth.token) {
    const headers: Record<string, string> = { Authorization: `Bearer ${auth.token}` };
    const acting = auth.actingTenantId();
    if (acting !== null) {
      headers['X-Tenant-Id'] = String(acting);
    }
    outgoing = req.clone({ setHeaders: headers });
  }

  return next(outgoing).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && isApiCall && !isLogin) {
        if (error.status === 401) {
          auth.logout();
          void router.navigate(['/login']);
        } else if (error.status === 402 && !req.url.endsWith('/auth/me')) {
          // pode ser a assinatura que acabou de ser suspensa: a conta nova diz (e o painel reage)
          void auth.loadMe().catch(() => undefined);
        }
      }
      return throwError(() => error);
    }),
  );
};
