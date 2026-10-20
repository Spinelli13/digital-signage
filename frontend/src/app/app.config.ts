import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  ErrorHandler,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';
import { ConfigService } from './core/config.service';
import { errorInterceptor } from './core/error.interceptor';
import { GlobalErrorHandler } from './core/global-error-handler';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    { provide: ErrorHandler, useClass: GlobalErrorHandler },
    provideRouter(routes),
    // A ordem importa: o erro sobe do interceptor de erros (mostra o aviso) para o de autenticação (trata o 401).
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    // Carrega /config.json antes de o painel falar com a API.
    provideAppInitializer(() => inject(ConfigService).load()),
  ],
};
