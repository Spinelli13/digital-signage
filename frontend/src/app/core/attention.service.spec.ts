import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AttentionService } from './attention.service';
import { ConfigService } from './config.service';

const API = 'http://api.test/api/v1';

describe('AttentionService', () => {
  let backend: HttpTestingController;
  let service: AttentionService;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
    backend = TestBed.inject(HttpTestingController);
    service = TestBed.inject(AttentionService);
  });

  afterEach(() => {
    service.stop();
    backend.verify();
    vi.useRealTimers();
  });

  it('busca os alertas abertos ao começar e repete a cada 30 segundos', () => {
    service.start();
    backend.expectOne(`${API}/summary`).flush({ openAlerts: 2 });
    expect(service.openAlerts()).toBe(2);

    vi.advanceTimersByTime(29_000);
    backend.expectNone(`${API}/summary`);
    vi.advanceTimersByTime(1_000);
    backend.expectOne(`${API}/summary`).flush({ openAlerts: 0 });
    expect(service.openAlerts()).toBe(0);
  });

  it('para de consultar quando o menu sai da tela', () => {
    service.start();
    backend.expectOne(`${API}/summary`).flush({ openAlerts: 1 });

    service.stop();
    vi.advanceTimersByTime(120_000);

    backend.expectNone(`${API}/summary`);
  });

  it('começar de novo não deixa dois relógios rodando', () => {
    service.start();
    backend.expectOne(`${API}/summary`).flush({ openAlerts: 1 });
    service.start();
    backend.expectOne(`${API}/summary`).flush({ openAlerts: 1 });

    vi.advanceTimersByTime(30_000);

    backend.expectOne(`${API}/summary`).flush({ openAlerts: 1 });
  });

  it('um erro do servidor não mostra aviso nem zera o contador (a consulta é em silêncio)', () => {
    service.start();
    backend.expectOne(`${API}/summary`).flush({ openAlerts: 4 });

    service.refresh();
    backend.expectOne(`${API}/summary`).flush(null, { status: 500, statusText: 'Server Error' });

    expect(service.openAlerts()).toBe(4);
  });
});
