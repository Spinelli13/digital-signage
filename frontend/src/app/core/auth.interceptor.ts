import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';
import { ConfigService } from './config.service';

/** Anexa o JWT às chamadas para a API e volta ao login quando o servidor responde 401. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const config = inject(ConfigService);
  const router = inject(Router);

  const isApiCall = req.url.startsWith(config.apiBaseUrl);
  const isLogin = req.url.endsWith('/auth/login');

  const outgoing =
    isApiCall && auth.token ? req.clone({ setHeaders: { Authorization: `Bearer ${auth.token}` } }) : req;

  return next(outgoing).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && isApiCall && !isLogin) {
        auth.logout();
        void router.navigate(['/login']);
      }
      return throwError(() => error);
    }),
  );
};
