import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { meFixture } from '../testing/fixtures';
import { AuthService } from './auth.service';
import { ConfigService } from './config.service';
import { Role } from './models';
import { capabilityGuard } from './role.guard';

const API = 'http://api.test/api/v1';

/** Cria o serviço como se o painel tivesse sido aberto com (ou sem) uma sessão guardada. */
function open(token: string | null): { auth: AuthService; backend: HttpTestingController } {
  localStorage.clear();
  sessionStorage.clear();
  if (token) {
    localStorage.setItem('signage.token', token);
  }
  TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  return { auth: TestBed.inject(AuthService), backend: TestBed.inject(HttpTestingController) };
}

describe('AuthService roles', () => {
  function as(auth: AuthService, role: Role): void {
    auth.me.set(meFixture(role));
  }

  it('is optimistic while the account has not arrived, except for the platform', () => {
    const { auth } = open(null);

    expect(auth.can('content')).toBe(true);
    expect(auth.can('admin')).toBe(true);
    expect(auth.can('platform')).toBe(false);
  });

  it('lets a viewer only look', () => {
    const { auth } = open('jwt');
    as(auth, 'VIEWER');

    expect(auth.can('content')).toBe(false);
    expect(auth.can('admin')).toBe(false);
    expect(auth.can('platform')).toBe(false);
  });

  it('lets an editor change content but not administer', () => {
    const { auth } = open('jwt');
    as(auth, 'EDITOR');

    expect(auth.can('content')).toBe(true);
    expect(auth.can('admin')).toBe(false);
  });

  it('lets an admin do everything but operate the platform', () => {
    const { auth } = open('jwt');
    as(auth, 'ADMIN');

    expect(auth.can('content')).toBe(true);
    expect(auth.can('admin')).toBe(true);
    expect(auth.can('platform')).toBe(false);
  });

  it('lets the platform operator do everything', () => {
    const { auth } = open('jwt');
    as(auth, 'PLATFORM_ADMIN');

    expect(auth.can('content')).toBe(true);
    expect(auth.can('admin')).toBe(true);
    expect(auth.can('platform')).toBe(true);
  });

  it('remembers the client in this tab only (sessionStorage) and forgets it on logout', async () => {
    const { auth, backend } = open('jwt');
    as(auth, 'PLATFORM_ADMIN');
    expect(auth.isVisiting()).toBe(false);

    const entering = auth.enterTenant(9);
    backend.expectOne(`${API}/auth/me`).flush(meFixture('PLATFORM_ADMIN'));
    await entering;

    expect(sessionStorage.getItem('signage.acting-tenant')).toBe('9');
    expect(auth.actingTenantId()).toBe(9);
    expect(auth.isVisiting()).toBe(true);

    auth.logout();

    expect(sessionStorage.getItem('signage.acting-tenant')).toBeNull();
    expect(auth.actingTenantId()).toBeNull();
    expect(auth.me()).toBeNull();
    expect(auth.isLoggedIn()).toBe(false);
    backend.verify();
  });

  it('restores the client chosen in this tab after a reload', () => {
    sessionStorage.setItem('signage.acting-tenant', '4');
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });

    expect(TestBed.inject(AuthService).actingTenantId()).toBe(4);
  });

  it('leaves the visited client and reloads the account', async () => {
    const { auth, backend } = open('jwt');
    as(auth, 'PLATFORM_ADMIN');
    const entering = auth.enterTenant(9);
    backend.expectOne(`${API}/auth/me`).flush(meFixture('PLATFORM_ADMIN'));
    await entering;

    const leaving = auth.leaveTenant();
    backend.expectOne(`${API}/auth/me`).flush(meFixture('PLATFORM_ADMIN'));
    await leaving;

    expect(auth.actingTenantId()).toBeNull();
    expect(auth.isVisiting()).toBe(false);
    backend.verify();
  });

  it('loads the account once and reuses it', async () => {
    const { auth, backend } = open('jwt');

    const first = auth.ensureMe();
    backend.expectOne(`${API}/auth/me`).flush(meFixture('EDITOR'));
    expect((await first)?.role).toBe('EDITOR');

    expect((await auth.ensureMe())?.role).toBe('EDITOR');
    backend.expectNone(`${API}/auth/me`);
  });

  it('does not call the server without a session', async () => {
    const { auth, backend } = open(null);

    expect(await auth.loadMe()).toBeNull();
    backend.expectNone(`${API}/auth/me`);
  });

  it('gives up quietly when the account cannot be loaded (the guards keep going)', async () => {
    const { auth, backend } = open('jwt');

    const result = auth.ensureMe();
    backend.expectOne(`${API}/auth/me`).flush({}, { status: 500, statusText: 'Server Error' });

    expect(await result).toBeNull();
  });
});

describe('capabilityGuard', () => {
  const run = (capability: 'content' | 'admin' | 'platform') =>
    TestBed.runInInjectionContext(() =>
      capabilityGuard(capability)({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as Promise<boolean | UrlTree>;

  it('lets the right role through', async () => {
    const { auth } = open('jwt');
    auth.me.set(meFixture('ADMIN'));

    expect(await run('admin')).toBe(true);
  });

  it('sends everybody else back to the dashboard', async () => {
    const { auth } = open('jwt');
    auth.me.set(meFixture('EDITOR'));

    const result = await run('admin');

    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/dashboard');
  });

  it('keeps non-operators out of the platform area', async () => {
    const { auth } = open('jwt');
    auth.me.set(meFixture('ADMIN'));

    expect(await run('platform')).toBeInstanceOf(UrlTree);
  });
});
