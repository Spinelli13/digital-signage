import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ConfigService } from './config.service';
import { ErrorService } from './error.service';
import { errorInterceptor } from './error.interceptor';
import { REQUEST_TIMEOUT_MS, SILENT_ERRORS } from './http-errors';

const API = 'http://api.test/api/v1';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let errors: ErrorService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    errors = TestBed.inject(ErrorService);
  });

  afterEach(() => {
    vi.useRealTimers();
    backend.verify();
  });

  const messages = () => errors.errors().map((e) => e.message);

  it('shows the action and the message the API sent', () => {
    http.delete(`${API}/media/7`).subscribe({ error: () => undefined });
    backend.expectOne(`${API}/media/7`).flush({ error: 'Não é possível remover: existe outro registro' }, { status: 409, statusText: 'Conflict' });

    expect(messages()).toEqual(['Excluir mídia: Não é possível remover: existe outro registro']);
  });

  it('explains a connection failure and says what to do', () => {
    http.get(`${API}/devices`).subscribe({ error: () => undefined });
    backend.expectOne(`${API}/devices`).error(new ProgressEvent('error'), { status: 0 });

    expect(messages()[0]).toContain('Carregar dispositivos');
    expect(messages()[0]).toContain('conectar ao servidor');
    expect(messages()[0]).toContain('1 minuto');
  });

  it('describes server errors without leaking technical details', () => {
    http.post(`${API}/playlists`, {}).subscribe({ error: () => undefined });
    backend.expectOne(`${API}/playlists`).flush('<html>stack trace</html>', { status: 500, statusText: 'Server Error' });

    expect(messages()).toEqual(['Criar playlist: o servidor encontrou um erro (HTTP 500). Tente novamente em instantes.']);
  });

  it('tells the user when they lack permission', () => {
    http.put(`${API}/devices/3`, {}).subscribe({ error: () => undefined });
    backend.expectOne(`${API}/devices/3`).flush({}, { status: 403, statusText: 'Forbidden' });

    expect(messages()).toEqual(['Salvar dispositivo: você não tem permissão para esta ação.']);
  });

  it('still reports errors for requests it has no friendly name for', () => {
    http.get(`${API}/coisa-nova`).subscribe({ error: () => undefined });
    backend.expectOne(`${API}/coisa-nova`).flush({}, { status: 404, statusText: 'Not Found' });

    expect(messages()[0]).toMatch(/^Item não encontrado/);
  });

  it('stays quiet for silent background requests (image thumbnails)', () => {
    http.get(`${API}/media/9/url`, { context: new HttpContext().set(SILENT_ERRORS, true) }).subscribe({ error: () => undefined });
    backend.expectOne(`${API}/media/9/url`).flush({}, { status: 500, statusText: 'Server Error' });

    expect(messages()).toEqual([]);
  });

  it('still passes the error on to the caller', () => {
    let received: unknown;
    http.get(`${API}/devices`).subscribe({ error: (e) => (received = e) });
    backend.expectOne(`${API}/devices`).flush({}, { status: 500, statusText: 'Server Error' });

    expect(received).toBeTruthy();
  });

  it('gives up on a request that never answers and says so', () => {
    vi.useFakeTimers();
    http.get(`${API}/devices`).subscribe({ error: () => undefined });
    backend.expectOne(`${API}/devices`);

    vi.advanceTimersByTime(REQUEST_TIMEOUT_MS + 1);

    expect(messages()[0]).toContain('Carregar dispositivos');
    expect(messages()[0]).toContain('demorou demais');
  });

  it('does not time out file uploads, which can legitimately take long', () => {
    vi.useFakeTimers();
    http.post(`${API}/media`, new FormData(), { reportProgress: true, observe: 'events' }).subscribe({ error: () => undefined });
    backend.expectOne(`${API}/media`);

    vi.advanceTimersByTime(REQUEST_TIMEOUT_MS * 5);

    expect(messages()).toEqual([]);
  });
});
