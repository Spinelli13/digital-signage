import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthService } from '../core/auth.service';
import { ConfigService } from '../core/config.service';
import { BillingInfo, Invoice } from '../core/models';
import { meFixture, tenantFixture } from '../testing/fixtures';
import { BillingPage } from './billing.page';

const API = 'http://api.test/api/v1';

const invoice = (id: number, overrides: Partial<Invoice> = {}): Invoice => ({
  id,
  tenantId: 2,
  tenantName: 'Minha Empresa',
  number: `2026-000${id}`,
  description: 'Plano Básico — 11/2026',
  periodStart: null,
  periodEnd: null,
  amountCents: 4900,
  currency: 'BRL',
  status: 'OPEN',
  dueDate: '2026-11-05',
  overdue: false,
  paidAt: null,
  paymentMethod: null,
  paymentNote: null,
  createdAt: '',
  ...overrides,
});

const billing = (overrides: Partial<BillingInfo> = {}): BillingInfo => ({
  tenant: tenantFixture(),
  document: '12.345.678/0001-90',
  invoices: [invoice(1), invoice(2, { status: 'PAID', paidAt: '2026-10-01T10:00:00Z', paymentMethod: 'PIX' })],
  companyName: 'Minha Plataforma',
  paymentInstructions: 'PIX: 00.000.000/0001-00\nEnvie o comprovante.',
  supportEmail: 'suporte@plataforma.com',
  plans: [
    { id: 3, code: 'basico', name: 'Básico', description: null, priceCents: 4900, currency: 'BRL', maxDevices: 5, maxStorageMb: 2048, maxUsers: 5 },
    { id: 4, code: 'pro', name: 'Profissional', description: 'Para redes', priceCents: 14900, currency: 'BRL', maxDevices: null, maxStorageMb: null, maxUsers: 20 },
  ],
  ...overrides,
});

describe('BillingPage', () => {
  let backend: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<BillingPage>>;
  let page: BillingPage;

  beforeEach(() => {
    localStorage.setItem('signage.token', 'jwt');
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
    TestBed.inject(AuthService).me.set(meFixture('ADMIN'));
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(BillingPage);
    page = fixture.componentInstance;
  });

  afterEach(() => {
    localStorage.clear();
    backend.verify();
  });

  async function show(info: BillingInfo): Promise<HTMLElement> {
    fixture.detectChanges();
    backend.expectOne(`${API}/billing`).flush(info);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the plan, its price and the usage against the limits', async () => {
    const element = await show(billing());

    expect(element.textContent).toContain('Básico');
    expect(element.textContent?.replace(/\s/g, ' ')).toContain('R$ 49,00');
    expect(element.textContent).toContain('2 de 5');
    expect(element.textContent).toContain('300 MB de 2,0 GB');
    expect(page.rows()).toHaveLength(3);
  });

  it('lists the invoices with their situation', async () => {
    const element = await show(billing({ invoices: [invoice(1, { overdue: true }), invoice(2, { status: 'PAID', paidAt: '2026-10-01T10:00:00Z' })] }));

    const text = element.textContent ?? '';
    expect(text).toContain('2026-0001');
    expect(text).toContain('Vencida');
    expect(text).toContain('Paga');
  });

  it('tells how to pay and how to reach support', async () => {
    const element = await show(billing());

    expect(element.querySelector('.instructions')?.textContent).toContain('PIX: 00.000.000/0001-00');
    expect(element.querySelector('a[href="mailto:suporte@plataforma.com"]')).not.toBeNull();
  });

  it('admits when the payment instructions are still missing', async () => {
    const element = await show(billing({ paymentInstructions: null }));

    expect(element.textContent).toContain('ainda não cadastrou as instruções de pagamento');
  });

  it('compares the available plans and marks the current one', async () => {
    const element = await show(billing());

    const cards = Array.from(element.querySelectorAll('.plan-card'));
    expect(cards).toHaveLength(2);
    expect(cards[0].classList.contains('current')).toBe(true);
    expect(cards[1].textContent).toContain('TVs: ilimitado');
    expect(cards[1].textContent).toContain('Espaço de mídias: ilimitado');
    expect(cards[1].querySelector('a')?.getAttribute('href')).toContain('mailto:suporte@plataforma.com');
  });

  it('builds a plan-change e-mail naming both plans', async () => {
    const info = billing();
    await show(info);

    const link = decodeURIComponent(page.changeLink(info.plans[1], info));
    expect(link).toContain('Troca de plano — Minha Empresa');
    expect(link).toContain('de "Básico" para "Profissional"');
  });

  it('explains a suspended subscription', async () => {
    await show(billing({ tenant: tenantFixture({ status: 'SUSPENDED' }) }));

    expect(page.blockedText()).toContain('suspensa por falta de pagamento');
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')).not.toBeNull();
  });

  it('explains a canceled subscription', async () => {
    await show(billing({ tenant: tenantFixture({ status: 'CANCELED' }) }));

    expect(page.blockedText()).toContain('encerrada');
  });

  it('shows no warning for an active subscription', async () => {
    await show(billing());

    expect(page.blockedText()).toBeNull();
  });

  it('counts the days left of the trial', async () => {
    const ends = new Date(Date.now() + 5 * 86_400_000).toISOString();
    await show(billing({ tenant: tenantFixture({ status: 'TRIAL', trialEndsAt: ends }) }));

    expect(page.trialDays()).toBe(5);
  });

  it('saves the account data and refreshes both the page and the account', async () => {
    await show(billing());
    expect(page.name()).toBe('Minha Empresa');
    expect(page.document()).toBe('12.345.678/0001-90');
    page.name.set('  Empresa Nova ');
    page.billingEmail.set('financeiro@empresa.com');

    page.saveProfile();

    const request = backend.expectOne(`${API}/billing/profile`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ name: 'Empresa Nova', billingEmail: 'financeiro@empresa.com', document: '12.345.678/0001-90' });
    request.flush(tenantFixture({ name: 'Empresa Nova' }));
    expect(page.notice.text()).toBe('Dados da conta salvos.');
    backend.expectOne(`${API}/billing`).flush(billing());
    backend.expectOne(`${API}/auth/me`).flush(meFixture('ADMIN'));
  });

  it('does not save without a name', async () => {
    await show(billing());
    page.name.set('   ');

    page.saveProfile();

    backend.expectNone(`${API}/billing/profile`);
  });
});
