import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ConfigService } from './config.service';
import { MAX_ATTEMPTS, READY_MS, RETRY_MS, SLOW_MS, ServerStatusService } from './server-status.service';

const HEALTH = 'http://api.test/api/v1/health';

describe('ServerStatusService', () => {
  let service: ServerStatusService;
  let backend: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
    service = TestBed.inject(ServerStatusService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    service.stop();
    backend.verify();
    vi.useRealTimers();
  });

  const fail = () => backend.expectOne(HEALTH).error(new ProgressEvent('error'), { status: 0 });

  it('começa sem saber e vira "ok" quando o servidor responde depressa (nenhum alerta)', () => {
    expect(service.state()).toBe('unknown');

    service.probe();
    backend.expectOne(HEALTH).flush({ status: 'UP' });

    expect(service.state()).toBe('ok');
  });

  it('avisa que está acordando quando a resposta demora, sem esperar o pedido terminar', () => {
    service.probe();
    expect(service.state()).toBe('unknown');

    vi.advanceTimersByTime(SLOW_MS);

    expect(service.state()).toBe('waking');
    // o pedido continua pendente: o servidor ainda está acordando
    const pending = backend.expectOne(HEALTH);

    pending.flush({ status: 'UP' });
    expect(service.state()).toBe('ready');
  });

  it('o aviso "pronto" some sozinho', () => {
    service.probe();
    vi.advanceTimersByTime(SLOW_MS);
    backend.expectOne(HEALTH).flush({ status: 'UP' });
    expect(service.state()).toBe('ready');

    vi.advanceTimersByTime(READY_MS);

    expect(service.state()).toBe('ok');
  });

  it('se o pedido falha, avisa que está acordando e pergunta de novo até o servidor voltar', () => {
    service.probe();
    fail();
    expect(service.state()).toBe('waking');
    expect(service.attempts()).toBe(1);

    vi.advanceTimersByTime(RETRY_MS);
    fail();
    expect(service.attempts()).toBe(2);

    vi.advanceTimersByTime(RETRY_MS);
    backend.expectOne(HEALTH).flush({ status: 'UP' });
    expect(service.state()).toBe('ready');
    expect(service.attempts()).toBe(0);
  });

  it('depois de muitas tentativas diz que o servidor não respondeu (e continua tentando)', () => {
    service.probe();
    for (let i = 0; i < MAX_ATTEMPTS; i++) {
      fail();
      vi.advanceTimersByTime(RETRY_MS);
    }

    expect(service.state()).toBe('down');

    backend.expectOne(HEALTH).flush({ status: 'UP' });
    expect(service.state()).toBe('ready');
  });

  it('várias perguntas seguidas viram um pedido só', () => {
    service.probe();
    service.probe();
    service.probe();

    backend.expectOne(HEALTH).flush({ status: 'UP' });
  });

  it('suspect() confere o servidor quando tudo parecia bem, mas não repete se já está acordando', () => {
    service.probe();
    backend.expectOne(HEALTH).flush({ status: 'UP' });

    service.suspect();
    fail();
    expect(service.state()).toBe('waking');

    service.suspect(); // já sabemos: o relógio de tentativas cuida do resto
    backend.expectNone(HEALTH);
  });

  it('pergunta no endereço configurado, sem pedir aviso de erro ao usuário', () => {
    service.probe();

    const request = backend.expectOne(HEALTH);
    expect(request.request.method).toBe('GET');
    request.flush({});
  });
});
