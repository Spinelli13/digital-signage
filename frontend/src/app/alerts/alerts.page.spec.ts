import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ConfigService } from '../core/config.service';
import { errorInterceptor } from '../core/error.interceptor';
import { ErrorService } from '../core/error.service';
import { Alert, MonitoringSettings } from '../core/models';
import { AlertsPage } from './alerts.page';

const API = 'http://api.test/api/v1';

const alert = (overrides: Partial<Alert> = {}): Alert => ({
  id: 1,
  deviceId: 5,
  deviceName: 'TV Vitrine',
  location: 'Centro',
  type: 'OFFLINE',
  message: 'A TV "TV Vitrine" (Centro) está sem contato com o servidor há mais de 10 min',
  openedAt: '2026-11-05T12:10:00Z',
  resolvedAt: null,
  open: true,
  notified: false,
  ...overrides,
});

const settings = (overrides: Partial<MonitoringSettings> = {}): MonitoringSettings => ({
  offlineAfterMinutes: 10,
  neverConnectedAfterHours: 24,
  errorBurstCount: 5,
  webhookConfigured: false,
  webhookHint: null,
  notifyOnResolve: true,
  eventsRetentionDays: 90,
  playLogRetentionDays: 14,
  alertEmail: null,
  emailAvailable: true,
  ...overrides,
});

function setup(initialSettings: MonitoringSettings = settings(), initialAlerts: Alert[] = [alert()], render = false) {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting(), provideRouter([])],
  });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  const backend = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(AlertsPage);
  const page = fixture.componentInstance;
  // Sem desenhar a tela chamamos o ngOnInit à mão; desenhando, é o primeiro detectChanges que o chama.
  if (render) {
    fixture.detectChanges();
  } else {
    page.ngOnInit();
  }
  backend.expectOne(`${API}/alerts/settings`).flush(initialSettings);
  alertsRequest(backend, 'open').flush(initialAlerts);
  return { page, backend, fixture };
}

function alertsRequest(backend: HttpTestingController, status: 'open' | null): TestRequest {
  return backend.expectOne((r) => r.url === `${API}/alerts` && r.params.get('status') === status);
}

describe('AlertsPage', () => {
  afterEach(() => vi.restoreAllMocks());

  it('lists the open alerts and fills the form with the saved thresholds', () => {
    const { page, backend } = setup(settings({ offlineAfterMinutes: 15, errorBurstCount: 8 }));

    expect(page.alerts().map((a) => a.deviceName)).toEqual(['TV Vitrine']);
    expect(page.offlineAfterMinutes()).toBe(15);
    expect(page.errorBurstCount()).toBe(8);
    expect(page.loaded()).toBe(true);
    backend.verify();
  });

  it('shows what happened, since when and a link to the TV history', async () => {
    const { fixture, backend } = setup(settings(), [alert()], true);
    fixture.detectChanges();
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('TV sem contato — TV Vitrine');
    expect(element.textContent).toContain('Aberto');
    expect(element.querySelector('a[href="/dispositivos/5/historico"]')).not.toBeNull();
    backend.verify();
  });

  it('asks for the resolved alerts too when the "only open" box is cleared', () => {
    const { page, backend } = setup();

    page.setOnlyOpen(false);

    alertsRequest(backend, null).flush([alert(), alert({ id: 2, open: false, resolvedAt: '2026-11-05T12:30:00Z' })]);
    expect(page.alerts()).toHaveLength(2);
    backend.verify();
  });

  it('says so when there is nothing open', async () => {
    const { fixture, backend } = setup(settings(), [], true);
    fixture.detectChanges();
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nenhum alerta aberto');
    backend.verify();
  });

  it('saves the thresholds without touching the webhook when the address field is empty', () => {
    const { page, backend } = setup(settings({ webhookConfigured: true, webhookHint: 'hooks.slack.com/…1234' }));
    page.offlineAfterMinutes.set(20);

    page.save();

    const request = backend.expectOne(`${API}/alerts/settings`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      offlineAfterMinutes: 20,
      neverConnectedAfterHours: 24,
      errorBurstCount: 5,
      eventsRetentionDays: 90,
      playLogRetentionDays: 14,
      notifyOnResolve: true,
      alertEmail: '',
    });
    request.flush(settings({ offlineAfterMinutes: 20, webhookConfigured: true, webhookHint: 'hooks.slack.com/…1234' }));
    expect(page.notice.text()).toBe('Configurações salvas.');
    backend.verify();
  });

  it('saves how long each play (proof of play) is kept', () => {
    const { page, backend } = setup(settings({ playLogRetentionDays: 30 }));
    expect(page.playLogRetentionDays()).toBe(30);
    page.playLogRetentionDays.set(60);

    page.save();

    const request = backend.expectOne(`${API}/alerts/settings`);
    expect((request.request.body as { playLogRetentionDays: number }).playLogRetentionDays).toBe(60);
    request.flush(settings({ playLogRetentionDays: 60 }));
    backend.verify();
  });

  it('sends the webhook address only when the person typed one, then forgets it', () => {
    const { page, backend } = setup();
    page.webhookUrl.set('  https://hooks.slack.com/services/T000/B000/XXXX  ');

    page.save();

    const request = backend.expectOne(`${API}/alerts/settings`);
    expect((request.request.body as { webhookUrl: string }).webhookUrl).toBe('https://hooks.slack.com/services/T000/B000/XXXX');
    request.flush(settings({ webhookConfigured: true, webhookHint: 'hooks.slack.com/…XXXX' }));
    expect(page.webhookUrl()).toBe('');
    expect(page.settings()?.webhookHint).toBe('hooks.slack.com/…XXXX');
    expect(page.notice.text()).toContain('webhook foi atualizado');
    backend.verify();
  });

  it('does not send anything while a threshold is blank or not a whole number', () => {
    const { page, backend } = setup();
    page.errorBurstCount.set(null);

    page.save();
    page.errorBurstCount.set(2.5);
    page.save();

    backend.expectNone(`${API}/alerts/settings`);
    expect(page.notice.text()).toContain('números inteiros');
    backend.verify();
  });

  it('shows the server explanation and stays editable when the settings are refused', () => {
    const { page, backend } = setup();
    const errors = TestBed.inject(ErrorService);
    page.webhookUrl.set('http://insecure.example.com/hook');

    page.save();
    backend
      .expectOne(`${API}/alerts/settings`)
      .flush({ error: 'O endereço do webhook precisa começar com https://' }, { status: 400, statusText: 'Bad Request' });

    expect(page.saving()).toBe(false);
    expect(page.webhookUrl()).toBe('http://insecure.example.com/hook');
    expect(errors.errors()[0].message).toContain('https://');
    backend.verify();
  });

  it('removes the webhook only after confirmation', () => {
    const { page, backend } = setup(settings({ webhookConfigured: true, webhookHint: 'x/…1' }));
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);

    page.removeWebhook();
    backend.expectNone(`${API}/alerts/settings`);
    page.removeWebhook();

    const request = backend.expectOne(`${API}/alerts/settings`);
    expect(request.request.body).toEqual({ webhookUrl: '' });
    request.flush(settings());
    expect(page.notice.text()).toBe('Webhook removido.');
    expect(confirmation).toHaveBeenCalledTimes(2);
    backend.verify();
  });

  it('reports the outcome of the webhook test', () => {
    const { page, backend } = setup(settings({ webhookConfigured: true, webhookHint: 'x/…1' }));

    page.test();
    backend.expectOne(`${API}/alerts/settings/test`).flush({ ok: false, status: 404, error: 'O webhook respondeu HTTP 404' });

    expect(page.testing()).toBe(false);
    expect(page.testResult()).toEqual({ ok: false, status: 404, error: 'O webhook respondeu HTTP 404' });
    page.test();
    backend.expectOne(`${API}/alerts/settings/test`).flush({ ok: true, status: 200, error: null });
    expect(page.testResult()?.ok).toBe(true);
    backend.verify();
  });

  it('offers the test and remove buttons only when a webhook is configured', async () => {
    const without = setup(settings(), [alert()], true);
    without.fixture.detectChanges();
    await without.fixture.whenStable();
    expect((without.fixture.nativeElement as HTMLElement).textContent).not.toContain('Enviar teste');
    without.backend.verify();
  });

  describe('e-mail dos avisos', () => {
    it('loads the saved address and sends the typed one (trimmed) with the other settings', () => {
      const { page, backend } = setup(settings({ alertEmail: 'dono@loja.com' }));
      expect(page.alertEmail()).toBe('dono@loja.com');
      page.alertEmail.set('  dono@loja.com, gerente@loja.com ');

      page.save();

      const request = backend.expectOne(`${API}/alerts/settings`);
      expect((request.request.body as { alertEmail: string }).alertEmail).toBe('dono@loja.com, gerente@loja.com');
      request.flush(settings({ alertEmail: 'dono@loja.com, gerente@loja.com' }));
      expect(page.alertEmail()).toBe('dono@loja.com, gerente@loja.com');
      backend.verify();
    });

    it('removes the address by sending it empty', () => {
      const { page, backend } = setup(settings({ alertEmail: 'dono@loja.com' }));
      page.alertEmail.set('');

      page.save();

      const request = backend.expectOne(`${API}/alerts/settings`);
      expect((request.request.body as { alertEmail: string }).alertEmail).toBe('');
      request.flush(settings());
      expect(page.alertEmail()).toBe('');
      backend.verify();
    });

    it('keeps what was typed and shows the reason when the server refuses an address', () => {
      const { page, backend } = setup();
      const errors = TestBed.inject(ErrorService);
      page.alertEmail.set('sem-arroba');

      page.save();
      backend.expectOne(`${API}/alerts/settings`).flush({ error: 'E-mail inválido: sem-arroba' }, { status: 400, statusText: 'Bad Request' });

      expect(page.alertEmail()).toBe('sem-arroba');
      expect(errors.errors()[0].message).toContain('sem-arroba');
      backend.verify();
    });

    it('reports the outcome of the e-mail test', () => {
      const { page, backend } = setup(settings({ alertEmail: 'dono@loja.com' }));

      page.testEmail();
      expect(page.testingEmail()).toBe(true);
      backend.expectOne(`${API}/alerts/settings/test-email`).flush({ ok: true, status: 200, error: null });

      expect(page.testingEmail()).toBe(false);
      expect(page.emailTestResult()?.ok).toBe(true);
      expect(page.emailTestMessage({ ok: true, status: 200, error: null })).toContain('E-mail de teste enviado');
      page.testEmail();
      backend.expectOne(`${API}/alerts/settings/test-email`).flush({ ok: false, status: 0, error: 'O provedor de e-mail recusou o envio' });
      expect(page.emailTestResult()?.ok).toBe(false);
      expect(page.emailTestMessage(page.emailTestResult()!)).toContain('recusou');
      backend.verify();
    });

    it('warns that sending is off when the server has no e-mail provider, and hides the test button', async () => {
      const { fixture, backend } = setup(settings({ alertEmail: 'dono@loja.com', emailAvailable: false }), [alert()], true);
      fixture.detectChanges();
      await fixture.whenStable();
      const element = fixture.nativeElement as HTMLElement;

      expect(element.querySelector('[data-testid="email-unavailable"]')).not.toBeNull();
      expect(element.textContent).not.toContain('Enviar e-mail de teste');
      backend.verify();
    });

    it('offers the test button only when an address is saved and sending is on', async () => {
      const saved = setup(settings({ alertEmail: 'dono@loja.com' }), [alert()], true);
      saved.fixture.detectChanges();
      await saved.fixture.whenStable();
      const element = saved.fixture.nativeElement as HTMLElement;

      expect(element.querySelector('[data-testid="email-unavailable"]')).toBeNull();
      expect(element.textContent).toContain('Enviar e-mail de teste');
      saved.backend.verify();
      TestBed.resetTestingModule();

      const empty = setup(settings(), [alert()], true);
      empty.fixture.detectChanges();
      await empty.fixture.whenStable();
      expect((empty.fixture.nativeElement as HTMLElement).textContent).not.toContain('Enviar e-mail de teste');
      empty.backend.verify();
    });
  });
});
