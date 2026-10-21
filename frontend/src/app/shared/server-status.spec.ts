import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ConfigService } from '../core/config.service';
import { resetLangForTests, setLang } from '../core/i18n';
import { MAX_ATTEMPTS, RETRY_MS, SLOW_MS, ServerStatusService } from '../core/server-status.service';
import { ServerStatusBanner } from './server-status';

const HEALTH = 'http://api.test/api/v1/health';

describe('ServerStatusBanner', () => {
  let service: ServerStatusService;
  let backend: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    resetLangForTests('pt');
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
    service = TestBed.inject(ServerStatusService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    service.stop();
    backend.verify();
    resetLangForTests('pt');
    vi.useRealTimers();
  });

  function render() {
    const fixture = TestBed.createComponent(ServerStatusBanner);
    fixture.detectChanges();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  }

  it('fica escondido enquanto o servidor responde normalmente', () => {
    service.probe();
    backend.expectOne(HEALTH).flush({});
    const { element } = render();

    expect(element.querySelector('[data-testid="server-status"]')).toBeNull();
  });

  it('mostra o alerta de "acordando" e o tira quando o servidor volta (com um aviso curto de pronto)', () => {
    service.probe();
    vi.advanceTimersByTime(SLOW_MS);
    const { fixture, element } = render();

    const alert = element.querySelector('[data-testid="server-status"]') as HTMLElement;
    expect(alert.getAttribute('role')).toBe('status');
    expect(alert.textContent).toContain('O servidor está acordando');
    expect(alert.textContent).toContain('1 minuto');
    expect(alert.classList.contains('waking')).toBe(true);

    backend.expectOne(HEALTH).flush({});
    fixture.detectChanges();
    expect(element.querySelector('[data-testid="server-status"]')?.textContent).toContain('Servidor pronto');
  });

  it('depois de muito tempo sem resposta mostra o botão para tentar de novo', () => {
    service.probe();
    for (let i = 0; i < MAX_ATTEMPTS; i++) {
      backend.expectOne(HEALTH).error(new ProgressEvent('error'), { status: 0 });
      vi.advanceTimersByTime(RETRY_MS);
    }
    const { fixture, element } = render();

    expect(element.textContent).toContain('O servidor não respondeu');
    const retry = element.querySelector('button') as HTMLButtonElement;
    expect(retry.textContent).toContain('Tentar de novo');

    retry.click();
    const open = backend.match(HEALTH);
    expect(open).toHaveLength(1); // já havia uma pergunta pendente: não abre outra
    open[0].flush({});
    fixture.detectChanges();
  });

  it('acompanha o idioma', () => {
    setLang('en');
    service.probe();
    vi.advanceTimersByTime(SLOW_MS);
    const { element } = render();

    expect(element.textContent).toContain('The server is waking up');
    backend.expectOne(HEALTH).flush({});
  });
});
