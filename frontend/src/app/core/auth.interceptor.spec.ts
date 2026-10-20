import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';
import { ConfigService } from './config.service';

const API = 'http://api.test';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: AuthService;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
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

  it('keeps the session when the login itself fails with 401', async () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    const attempt = auth.login('a@b.c', 'wrong').catch(() => 'failed');
    backend.expectOne(`${API}/api/v1/auth/login`).flush({ error: 'inválido' }, { status: 401, statusText: 'Unauthorized' });

    expect(await attempt).toBe('failed');
    expect(navigate).not.toHaveBeenCalled();
  });
});
