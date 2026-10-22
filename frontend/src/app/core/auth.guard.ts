import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

/**
 * Quem não entrou vai para o login e, depois de entrar, volta ao endereço que tentou abrir (o QR Code de pareamento da TV
 * abre direto numa página de dentro do painel).
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  if (auth.isLoggedIn()) {
    return true;
  }
  const target = state?.url;
  const comeBackTo = target && target !== '/' && target !== '/dashboard' ? { queryParams: { returnUrl: target } } : {};
  return inject(Router).createUrlTree(['/login'], comeBackTo);
};
