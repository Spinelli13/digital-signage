import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { ConfigService } from '../core/config.service';
import { errorInterceptor } from '../core/error.interceptor';
import { ErrorService } from '../core/error.service';
import { Invoice, Manager, PlatformOverview, PlatformPlan, PlatformSettings } from '../core/models';
import { meFixture, tenantFixture } from '../testing/fixtures';
import { PlatformClientsPage } from './platform-clients.page';
import { PlatformInvoicesPage } from './platform-invoices.page';
import { PlatformManagersPage } from './platform-managers.page';
import { PlatformOverviewPage } from './platform-overview.page';
import { PlatformPlansPage } from './platform-plans.page';
import { PlatformSettingsPage } from './platform-settings.page';

const API = 'http://api.test/api/v1';

const plan = (id: number, name: string, overrides: Partial<PlatformPlan> = {}): PlatformPlan => ({
  id,
  code: name.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase(),
  name,
  description: null,
  priceCents: 4900,
  currency: 'BRL',
  maxDevices: 5,
  maxStorageMb: 2048,
  maxUsers: 5,
  maxUploadMb: null,
  features: ['CAMPAIGNS', 'LAYOUTS', 'WIDGETS', 'PRICES', 'QUEUE', 'PROOF_OF_PLAY', 'COUPONS'],
  active: true,
  sortOrder: 10,
  tenantCount: 0,
  ...overrides,
});

const invoice = (id: number, overrides: Partial<Invoice> = {}): Invoice => ({
  id,
  tenantId: 2,
  tenantName: 'Minha Empresa',
  number: `2026-000${id}`,
  description: 'Mensalidade',
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

function configure(): HttpTestingController {
  localStorage.setItem('signage.token', 'jwt');
  TestBed.configureTestingModule({
    providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting(), provideRouter([])],
  });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  return TestBed.inject(HttpTestingController);
}

describe('PlatformOverviewPage', () => {
  const OVERVIEW: PlatformOverview = {
    tenants: 3,
    tenantsByStatus: { TRIAL: 1, ACTIVE: 2 },
    devices: 12,
    monthlyRecurringCents: 19800,
    openInvoices: 2,
    openInvoicesCents: 9800,
    overdueInvoices: 1,
    overdueInvoicesCents: 4900,
  };
  let backend: HttpTestingController;

  beforeEach(() => (backend = configure()));
  afterEach(() => {
    localStorage.clear();
    backend.verify();
  });

  it('shows the numbers calculated by the server', async () => {
    const fixture = TestBed.createComponent(PlatformOverviewPage);
    fixture.detectChanges();
    backend.expectOne(`${API}/platform/overview`).flush(OVERVIEW);
    fixture.detectChanges();
    await fixture.whenStable();

    const text = (fixture.nativeElement as HTMLElement).textContent?.replace(/\s/g, ' ') ?? '';
    expect(text).toContain('R$ 198,00');
    expect(text).toContain('R$ 98,00');
    expect(text).toContain('Em teste');
    expect(text).toContain('Em dia');
  });

  it('runs the billing cycle on demand and reloads the numbers', () => {
    const fixture = TestBed.createComponent(PlatformOverviewPage);
    const page = fixture.componentInstance;
    page.ngOnInit();
    backend.expectOne(`${API}/platform/overview`).flush(OVERVIEW);

    page.runBilling();
    const request = backend.expectOne(`${API}/platform/billing/run`);
    expect(request.request.method).toBe('POST');
    request.flush({ status: 'ok' });

    expect(page.notice.text()).toContain('Ciclo de cobrança executado');
    expect(page.running()).toBe(false);
    backend.expectOne(`${API}/platform/overview`).flush(OVERVIEW);
  });
});

describe('PlatformClientsPage', () => {
  let backend: HttpTestingController;
  let page: PlatformClientsPage;

  beforeEach(() => {
    backend = configure();
    TestBed.inject(AuthService).me.set(meFixture('PLATFORM_ADMIN'));
    page = TestBed.createComponent(PlatformClientsPage).componentInstance;
    page.ngOnInit();
    backend.expectOne(`${API}/platform/plans`).flush([plan(3, 'Básico'), plan(4, 'Antigo', { active: false }), plan(5, 'Pro')]);
    backend.expectOne(`${API}/platform/tenants`).flush([tenantFixture({ id: 2, name: 'Minha Empresa' })]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
    backend.verify();
  });

  it('lists the clients', () => {
    expect(page.items().map((t) => t.name)).toEqual(['Minha Empresa']);
  });

  it('offers only active plans to a new client', () => {
    page.openNew();

    expect(page.planOptions().map((p) => p.name)).toEqual(['Básico', 'Pro']);
    expect(page.planId()).toBe(3);
  });

  it('keeps a deactivated plan in the list while a client is still on it', () => {
    page.openEdit(tenantFixture({ plan: { ...tenantFixture().plan, id: 4 } }));

    expect(page.planOptions().map((p) => p.name)).toEqual(['Básico', 'Antigo', 'Pro']);
  });

  it('creates a client together with its first administrator', () => {
    page.openNew();
    page.name.set(' Padaria Doce ');
    page.adminName.set('Maria');
    page.adminEmail.set('maria@padaria.com');
    page.adminPassword.set('curta');
    expect(page.canSubmit()).toBe(false);
    page.adminPassword.set('senha-forte-1');
    page.billingEmail.set('');

    page.submit();

    const request = backend.expectOne(`${API}/platform/tenants`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      name: 'Padaria Doce',
      planId: 3,
      adminName: 'Maria',
      adminEmail: 'maria@padaria.com',
      adminPassword: 'senha-forte-1',
      billingEmail: null,
      document: null,
    });
    request.flush(tenantFixture({ id: 9, name: 'Padaria Doce' }));
    expect(page.notice.text()).toBe('Cliente "Padaria Doce" cadastrado.');
    backend.expectOne(`${API}/platform/tenants`).flush([]);
  });

  it('keeps the window open and shows the server message when the name is taken', () => {
    const errors = TestBed.inject(ErrorService);
    page.openNew();
    page.name.set('Minha Empresa');
    page.adminName.set('Maria');
    page.adminEmail.set('maria@x.com');
    page.adminPassword.set('senha-forte-1');

    page.submit();
    backend.expectOne(`${API}/platform/tenants`).flush({ error: 'Já existe um cliente chamado "Minha Empresa"' }, { status: 409, statusText: 'Conflict' });

    expect(page.modal()).toBe('new');
    expect(errors.errors()[0].message).toContain('Já existe um cliente');
  });

  it('edits status, plan and dates, sending dates as end-of-day instants', () => {
    page.openEdit(page.items()[0]);
    page.status.set('SUSPENDED');
    page.planId.set(5);
    page.periodEnd.set('2026-12-31');

    page.submit();

    const request = backend.expectOne(`${API}/platform/tenants/2`);
    expect(request.request.method).toBe('PUT');
    const body = request.request.body as Record<string, unknown>;
    expect(body['status']).toBe('SUSPENDED');
    expect(body['planId']).toBe(5);
    expect(new Date(body['currentPeriodEnd'] as string).getFullYear()).toBe(2026);
    expect(body).not.toHaveProperty('trialEndsAt');
    expect(body).not.toHaveProperty('document');
    request.flush(tenantFixture({ status: 'SUSPENDED' }));
    backend.expectOne(`${API}/platform/tenants`).flush([]);
  });

  it('enters a client: acts on it and opens its dashboard', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const entering = page.enter(page.items()[0]);
    backend.expectOne(`${API}/auth/me`).flush(meFixture('PLATFORM_ADMIN'));
    await entering;

    expect(TestBed.inject(AuthService).actingTenantId()).toBe(2);
    expect(navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('creates a manual invoice in cents', () => {
    page.openInvoice(page.items()[0]);
    page.invoiceDescription.set('Instalação');
    page.invoiceAmount.set('150,50');
    page.invoiceDue.set('2026-11-10');
    expect(page.canSubmitInvoice()).toBe(true);

    page.submitInvoice();

    const request = backend.expectOne(`${API}/platform/tenants/2/invoices`);
    expect(request.request.body).toEqual({ description: 'Instalação', amountCents: 15050, dueDate: '2026-11-10' });
    request.flush(invoice(7, { number: '2026-0007' }));
    expect(page.notice.text()).toContain('2026-0007');
  });

  it('does not create an invoice with an invalid value', () => {
    page.openInvoice(page.items()[0]);
    page.invoiceDescription.set('Instalação');
    page.invoiceAmount.set('abc');
    page.invoiceDue.set('2026-11-10');

    expect(page.canSubmitInvoice()).toBe(false);
    page.submitInvoice();
    backend.expectNone(`${API}/platform/tenants/2/invoices`);
  });
});

describe('PlatformPlansPage', () => {
  let backend: HttpTestingController;
  let page: PlatformPlansPage;

  beforeEach(() => {
    backend = configure();
    page = TestBed.createComponent(PlatformPlansPage).componentInstance;
    page.ngOnInit();
    backend.expectOne(`${API}/platform/plans`).flush([plan(3, 'Básico', { tenantCount: 2 }), plan(5, 'Pro', { sortOrder: 20, maxDevices: null })]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
    backend.verify();
  });

  it('lists the plans', () => {
    expect(page.items().map((p) => p.name)).toEqual(['Básico', 'Pro']);
    expect(page.limit(null)).toBe('ilimitado');
    expect(page.storage(2048)).toBe('2,0 GB');
  });

  it('picks which features the plan includes and the maximum size of a file', () => {
    page.openNew();
    expect(page.features().length).toBe(7);
    page.toggleFeature('QUEUE', false);
    page.toggleFeature('PROOF_OF_PLAY', false);
    page.toggleFeature('QUEUE', true);
    expect(page.hasFeature('QUEUE')).toBe(true);
    expect(page.hasFeature('PROOF_OF_PLAY')).toBe(false);
    page.name.set('Vitrine');
    page.code.set('vitrine');
    page.price.set('10');
    page.maxUploadMb.set(25);

    page.submit();

    const request = backend.expectOne(`${API}/platform/plans`);
    expect(request.request.body.maxUploadMb).toBe(25);
    expect([...request.request.body.features].sort()).toEqual(['CAMPAIGNS', 'COUPONS', 'LAYOUTS', 'PRICES', 'QUEUE', 'WIDGETS']);
    request.flush(plan(9, 'Vitrine'));
    backend.expectOne(`${API}/platform/plans`).flush([]);
  });

  it('opens a plan with its own features and describes them', () => {
    const only = plan(7, 'Mínimo', { features: ['CAMPAIGNS'], maxUploadMb: 10 });
    page.openEdit(only);
    expect(page.features()).toEqual(['CAMPAIGNS']);
    expect(page.maxUploadMb()).toBe(10);
    expect(page.featureNames(only)).toBe('Campanhas');
    expect(page.featureNames(plan(8, 'Nada', { features: [] }))).toBe('Nenhum recurso opcional');
  });

  it('creates a plan: price in cents and empty limits as unlimited', () => {
    page.openNew();
    expect(page.sortOrder()).toBe(30);
    page.name.set('Rede');
    page.code.set('rede');
    page.price.set('299,90');
    page.maxDevices.set('');
    page.maxStorageMb.set(10240);
    page.maxUsers.set('50');

    page.submit();

    const request = backend.expectOne(`${API}/platform/plans`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      code: 'rede',
      name: 'Rede',
      description: null,
      priceCents: 29990,
      maxDevices: null,
      maxStorageMb: 10240,
      maxUsers: 50,
      maxUploadMb: null,
      features: ['CAMPAIGNS', 'LAYOUTS', 'WIDGETS', 'PRICES', 'QUEUE', 'PROOF_OF_PLAY', 'COUPONS'],
      active: true,
      sortOrder: 30,
    });
    request.flush(plan(8, 'Rede'));
    expect(page.notice.text()).toBe('Plano "Rede" criado.');
    backend.expectOne(`${API}/platform/plans`).flush([]);
  });

  it('only accepts lowercase codes with letters, digits and dashes, and a valid price', () => {
    page.openNew();
    page.name.set('Rede');
    page.price.set('10');
    page.code.set('Rede Grande');
    expect(page.canSubmit()).toBe(false);
    page.code.set('rede-grande');
    expect(page.canSubmit()).toBe(true);
    page.price.set('grátis');
    expect(page.canSubmit()).toBe(false);
  });

  it('edits a plan with a PUT', () => {
    page.openEdit(page.items()[0]);
    expect(page.price()).toBe('49,00');
    page.price.set('59,00');
    page.active.set(false);

    page.submit();

    const request = backend.expectOne(`${API}/platform/plans/3`);
    expect(request.request.method).toBe('PUT');
    expect((request.request.body as { priceCents: number; active: boolean }).priceCents).toBe(5900);
    expect((request.request.body as { active: boolean }).active).toBe(false);
    request.flush(plan(3, 'Básico'));
    backend.expectOne(`${API}/platform/plans`).flush([]);
  });

  it('deletes a plan after confirming', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    page.remove(page.items()[1]);

    const request = backend.expectOne(`${API}/platform/plans/5`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    expect(page.notice.text()).toBe('Plano "Pro" excluído.');
    backend.expectOne(`${API}/platform/plans`).flush([]);
  });
});

describe('PlatformInvoicesPage', () => {
  let backend: HttpTestingController;
  let page: PlatformInvoicesPage;

  beforeEach(() => {
    backend = configure();
    page = TestBed.createComponent(PlatformInvoicesPage).componentInstance;
    page.ngOnInit();
    backend.expectOne((r) => r.url === `${API}/platform/invoices` && r.params.get('status') === 'OPEN').flush([invoice(1), invoice(2, { overdue: true })]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
    backend.verify();
  });

  it('starts on the open invoices', () => {
    expect(page.filter()).toBe('OPEN');
    expect(page.items()).toHaveLength(2);
    expect(page.look(page.items()[0]).tone).toBe('warn');
    expect(page.look(page.items()[1]).tone).toBe('err');
  });

  it('shows everything when the filter is cleared (no status in the query)', () => {
    page.setFilter('');

    const request = backend.expectOne((r) => r.url === `${API}/platform/invoices`);
    expect(request.request.params.has('status')).toBe(false);
    request.flush([]);
  });

  it('registers a payment with the chosen method and note', () => {
    page.openPay(page.items()[0]);
    expect(page.method()).toBe('PIX');
    page.method.set('BOLETO');
    page.note.set('  comprovante por e-mail ');

    page.submitPay();

    const request = backend.expectOne(`${API}/platform/invoices/1/pay`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ method: 'BOLETO', note: 'comprovante por e-mail' });
    request.flush(invoice(1, { status: 'PAID' }));
    expect(page.notice.text()).toBe('Pagamento da fatura 2026-0001 registrado.');
    expect(page.paying()).toBeNull();
    backend.expectOne((r) => r.url === `${API}/platform/invoices`).flush([]);
  });

  it('cancels an invoice only after confirming', () => {
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);

    page.voidInvoice(page.items()[0]);
    backend.expectNone(`${API}/platform/invoices/1/void`);

    page.voidInvoice(page.items()[0]);
    const request = backend.expectOne(`${API}/platform/invoices/1/void`);
    expect(request.request.method).toBe('POST');
    request.flush(invoice(1, { status: 'VOID' }));
    expect(confirmation).toHaveBeenCalledTimes(2);
    expect(page.notice.text()).toBe('Fatura 2026-0001 cancelada.');
    backend.expectOne((r) => r.url === `${API}/platform/invoices`).flush([]);
  });

  it('runs the billing cycle and reloads', () => {
    page.runBilling();
    backend.expectOne(`${API}/platform/billing/run`).flush({ status: 'ok' });

    expect(page.notice.text()).toBe('Ciclo de cobrança executado.');
    backend.expectOne((r) => r.url === `${API}/platform/invoices`).flush([]);
  });
});

describe('PlatformSettingsPage', () => {
  const SETTINGS: PlatformSettings = {
    companyName: 'Minha Plataforma',
    paymentInstructions: 'PIX 123',
    supportEmail: 'suporte@x.com',
    legalName: null,
    legalDocument: null,
    privacyContactName: null,
    privacyContactEmail: null,
    trialDays: 14,
    graceDays: 7,
    invoiceLeadDays: 5,
  };
  let backend: HttpTestingController;
  let page: PlatformSettingsPage;

  beforeEach(() => {
    backend = configure();
    page = TestBed.createComponent(PlatformSettingsPage).componentInstance;
    page.ngOnInit();
    backend.expectOne(`${API}/platform/settings`).flush(SETTINGS);
  });

  afterEach(() => {
    localStorage.clear();
    backend.verify();
  });

  it('fills the form with the current settings', () => {
    expect(page.companyName()).toBe('Minha Plataforma');
    expect(page.paymentInstructions()).toBe('PIX 123');
    expect(page.graceDays()).toBe(7);
  });

  it('saves the changes', () => {
    page.graceDays.set(10);
    page.paymentInstructions.set('  PIX 456 ');

    page.save();

    const request = backend.expectOne(`${API}/platform/settings`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      companyName: 'Minha Plataforma',
      supportEmail: 'suporte@x.com',
      paymentInstructions: 'PIX 456',
      legalName: '',
      legalDocument: '',
      privacyContactName: '',
      privacyContactEmail: '',
      trialDays: 14,
      graceDays: 10,
      invoiceLeadDays: 5,
    });
    request.flush({ ...SETTINGS, graceDays: 10 });
    expect(page.notice.text()).toBe('Configurações salvas.');
  });

  it('does not save without a company name', () => {
    page.companyName.set(' ');

    page.save();

    backend.expectNone(`${API}/platform/settings`);
  });
});

describe('PlatformManagersPage', () => {
  const manager = (id: number, name: string, overrides: Partial<Manager> = {}): Manager => ({
    id,
    name,
    email: `${name.toLowerCase()}@x.com`,
    active: true,
    mustChangePassword: false,
    createdAt: '2026-10-21T10:00:00Z',
    ...overrides,
  });
  let backend: HttpTestingController;
  let page: PlatformManagersPage;

  beforeEach(() => {
    backend = configure();
    TestBed.inject(AuthService).me.set(meFixture('PLATFORM_ADMIN', { id: 1 }));
    page = TestBed.createComponent(PlatformManagersPage).componentInstance;
    page.ngOnInit();
    backend.expectOne(`${API}/platform/managers`).flush([manager(1, 'Ana'), manager(2, 'Beto')]);
  });

  afterEach(() => {
    localStorage.clear();
    backend.verify();
  });

  it('lists the managers and recognizes the logged-in one', () => {
    expect(page.items().map((item) => item.name)).toEqual(['Ana', 'Beto']);
    expect(page.isMe(page.items()[0])).toBe(true);
    expect(page.isMe(page.items()[1])).toBe(false);
    expect(page.initials('Maria da Silva')).toBe('MD');
  });

  it('adds a manager only with name, email and a password of 8+ characters', () => {
    page.openNew();
    page.name.set('Sócio');
    page.email.set('socio@x.com');
    page.password.set('curta');
    expect(page.canSubmit()).toBe(false);
    page.password.set('senha-forte-1');
    expect(page.canSubmit()).toBe(true);

    page.submit();

    const request = backend.expectOne(`${API}/platform/managers`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Sócio', email: 'socio@x.com', password: 'senha-forte-1' });
    request.flush(manager(3, 'Sócio'));
    expect(page.notice.text()).toBe('Gestor "Sócio" adicionado.');
    backend.expectOne(`${API}/platform/managers`).flush([]);
  });

  it('accepts a short temporary password for someone else, but the own password keeps the normal rule', () => {
    page.openNew();
    page.name.set('Sócio');
    page.email.set('socio@x.com');
    page.password.set('123456');
    expect(page.canSubmit()).toBe(true);
    page.password.set('12345');
    expect(page.canSubmit()).toBe(false);

    page.openEdit(page.items()[0]);
    page.password.set('123456');
    expect(page.canSubmit()).toBe(false);
    page.password.set('12345678');
    expect(page.canSubmit()).toBe(true);
    page.openEdit(page.items()[1]);
    page.password.set('123456');
    expect(page.canSubmit()).toBe(true);
  });

  it('changes only what was typed and never lets a manager turn off their own access', () => {
    page.openEdit(page.items()[0]);
    expect(page.editingSelf()).toBe(true);
    page.name.set('Ana Maria');

    page.submit();

    const request = backend.expectOne(`${API}/platform/managers/1`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ name: 'Ana Maria', active: true });
    request.flush(manager(1, 'Ana Maria'));
    backend.expectOne(`${API}/platform/managers`).flush([manager(1, 'Ana Maria'), manager(2, 'Beto')]);

    page.openEdit(page.items()[1]);
    expect(page.editingSelf()).toBe(false);
    page.password.set('nova-senha-2');
    page.active.set(false);
    page.submit();
    const other = backend.expectOne(`${API}/platform/managers/2`);
    expect(other.request.body).toEqual({ name: 'Beto', active: false, password: 'nova-senha-2' });
    other.flush(manager(2, 'Beto', { active: false }));
    backend.expectOne(`${API}/platform/managers`).flush([]);
  });

  it('deletes a manager after confirming', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    page.remove(page.items()[1]);

    backend.expectOne(`${API}/platform/managers/2`).flush(null);
    expect(page.notice.text()).toBe('Gestor "Beto" excluído.');
    backend.expectOne(`${API}/platform/managers`).flush([]);
  });
});
