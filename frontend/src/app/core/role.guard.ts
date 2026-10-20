import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService, Capability } from './auth.service';

/** Só deixa entrar quem pode a capacidade pedida; os outros voltam ao dashboard (o menu nem mostra o link). */
export function capabilityGuard(capability: Capability): CanActivateFn {
  return async () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    await auth.ensureMe();
    return auth.can(capability) ? true : router.createUrlTree(['/dashboard']);
  };
}
