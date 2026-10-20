import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';
import { ConfigService } from './config.service';
import { meFixture } from '../testing/fixtures';

const API = 'http://api.test';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: AuthService;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    TestBed.inject(ConfigService).apiBaseUrl = API;
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  afterEach(() => backend.verify());

  async function logIn(): Promise<void> {
    const login = auth.login('a@b.c', 'x');
    backend.expectOne(`${API}/api/v1/auth/login`).flush({ token: 'jwt-123' });
    await vi.waitFor(() => backend.expectOne(`${API}/api/v1/auth/me`).flush(meFixture('ADMIN')));
    await login;
  }

  it('sends the JWT to the API', async () => {
    await logIn();

    http.get(`${API}/api/v1/devices`).subscribe();

    expect(backend.expectOne(`${API}/api/v1/devices`).request.headers.get('Authorization')).toBe('Bearer jwt-123');
  });

  it('does not leak the JWT to other hosts (e.g. signed storage URLs)', async () => {
    await logIn();

    http.get('https://storage.example/file.png').subscribe();

    expect(backend.expectOne('https://storage.example/file.png').request.headers.has('Authorization')).toBe(false);
  });

  it('logs out and returns to /login on 401', async () => {
    await logIn();
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    http.get(`${API}/api/v1/devices`).subscribe({ error: () => undefined });
    backend.expectOne(`${API}/api/v1/devices`).flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isLoggedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('sends the client the platform operator is acting on (and only to the API)', async () => {
    await logIn();
    const entering = auth.enterTenant(7);
    backend.expectOne(`${API}/api/v1/auth/me`).flush(meFixture('PLATFORM_ADMIN'));
    await entering;

    http.get(`${API}/api/v1/devices`).subscribe();
    http.get('https://storage.example/file.png').subscribe();

    expect(backend.expectOne(`${API}/api/v1/devices`).request.headers.get('X-Tenant-Id')).toBe('7');
    expect(backend.expectOne('https://storage.example/file.png').request.headers.has('X-Tenant-Id')).toBe(false);
  });

  it('does not send X-Tenant-Id when the operator is back on their own client', async () => {
    await logIn();
    const entering = auth.enterTenant(7);
    backend.expectOne(`${API}/api/v1/auth/me`).flush(meFixture('PLATFORM_ADMIN'));
    await entering;
    const leaving = auth.leaveTenant();
    backend.expectOne(`${API}/api/v1/auth/me`).flush(meFixture('PLATFORM_ADMIN'));
    await leaving;

    http.get(`${API}/api/v1/devices`).subscribe();

    expect(backend.expectOne(`${API}/api/v1/devices`).request.headers.has('X-Tenant-Id')).toBe(false);
  });

  /** 402 = plano/assinatura: pode ser a suspensão que acabou de acontecer, então a conta é recarregada. */
  it('reloads the account on a 402 (payment required) without logging out', async () => {
    await logIn();

    http.get(`${API}/api/v1/devices`).subscribe({ error: () => undefined });
    backend.expectOne(`${API}/api/v1/devices`).flush({ error: 'suspenso' }, { status: 402, statusText: 'Payment Required' });

    backend.expectOne(`${API}/api/v1/auth/me`).flush(meFixture('ADMIN', { tenant: { ...meFixture().tenant, status: 'SUSPENDED' } }));
    expect(auth.isLoggedIn()).toBe(true);
    await vi.waitFor(() => expect(auth.me()?.tenant.status).toBe('SUSPENDED'));
  });

  it('keeps the session when the login itself fails with 401', async () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    const attempt = auth.login('a@b.c', 'wrong').catch(() => 'failed');
    backend.expectOne(`${API}/api/v1/auth/login`).flush({ error: 'inválido' }, { status: 401, statusText: 'Unauthorized' });

    expect(await attempt).toBe('failed');
    expect(navigate).not.toHaveBeenCalled();
  });
});
