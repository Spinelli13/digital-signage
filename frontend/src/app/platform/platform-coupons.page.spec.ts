import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ConfigService } from '../core/config.service';
import { errorInterceptor } from '../core/error.interceptor';
import { Coupon } from '../core/models';
import { PlatformCouponsPage, dateInputToIso, isoToDateInput, randomCouponCode } from './platform-coupons.page';

const API = 'http://api.test/api/v1';

const coupon = (id: number, code: string, overrides: Partial<Coupon> = {}): Coupon => ({
  id,
  code,
  description: null,
  kind: 'PERCENT_OFF',
  value: 20,
  durationMonths: 3,
  upgradePlanId: null,
  upgradePlanName: null,
  validFrom: null,
  validUntil: null,
  maxRedemptions: null,
  redeemedCount: 0,
  active: true,
  batch: null,
  state: 'ACTIVE',
  createdAt: '2026-10-21T10:00:00Z',
  ...overrides,
});

describe('PlatformCouponsPage', () => {
  let backend: HttpTestingController;
  let page: PlatformCouponsPage;

  beforeEach(() => {
    localStorage.setItem('signage.token', 'jwt');
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting(), provideRouter([])],
    });
    TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
    backend = TestBed.inject(HttpTestingController);
    page = TestBed.createComponent(PlatformCouponsPage).componentInstance;
    page.ngOnInit();
    backend.expectOne(`${API}/platform/coupons`).flush([coupon(1, 'DEZ20'), coupon(2, 'USADO', { redeemedCount: 2, maxRedemptions: 2, state: 'EXHAUSTED', kind: 'FREE_DAYS', value: 7, durationMonths: null })]);
    backend.expectOne(`${API}/platform/plans`).flush([
      { id: 4, code: 'pro', name: 'Profissional', active: true },
      { id: 9, code: 'velho', name: 'Velho', active: false },
    ]);
  });

  afterEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
    backend.verify();
  });

  it('lists the coupons saying what each gives and how it stands', () => {
    expect(page.items().map((item) => item.code)).toEqual(['DEZ20', 'USADO']);
    expect(page.benefit(page.items()[0])).toBe('20% de desconto em 3 fatura(s)');
    expect(page.benefit(page.items()[1])).toBe('7 dia(s) grátis');
    expect(page.stateLabel(page.items()[1])).toBe('Esgotado');
    expect(page.validity(page.items()[0])).toBe('Sem prazo');
    expect(page.plans().map((plan) => plan.name)).toEqual(['Profissional']); // só planos ligados
  });

  it('creates a coupon: value in the right unit and empty limits as unlimited', () => {
    page.openNew();
    expect(page.code()).toMatch(/^[A-Z2-9]{4}-[A-Z2-9]{4}$/);
    page.code.set('bemvindo20');
    page.kind.set('AMOUNT_OFF');
    page.valueText.set('12,50');
    page.durationMonths.set(2);
    page.validUntil.set('2026-12-31');

    expect(page.canSubmit()).toBe(true);
    page.submit();

    const request = backend.expectOne(`${API}/platform/coupons`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      code: 'bemvindo20',
      description: null,
      kind: 'AMOUNT_OFF',
      value: 1250,
      durationMonths: 2,
      upgradePlanId: null,
      validFrom: null,
      validUntil: dateInputToIso('2026-12-31', true),
      maxRedemptions: null,
      active: true,
    });
    request.flush(coupon(3, 'BEMVINDO20'));
    expect(page.notice.text()).toBe('Cupom BEMVINDO20 criado.');
    backend.expectOne(`${API}/platform/coupons`).flush([]);
  });

  it('checks the value before sending: percentage up to 100, a plan for the unlock, whole numbers', () => {
    page.openNew();
    page.code.set('TESTE');
    page.kind.set('PERCENT_OFF');
    page.valueText.set('101');
    expect(page.canSubmit()).toBe(false);
    page.valueText.set('100');
    expect(page.canSubmit()).toBe(true);
    page.valueText.set('2,5');
    expect(page.canSubmit()).toBe(false);

    page.kind.set('PLAN_UPGRADE');
    page.valueText.set('30');
    expect(page.canSubmit()).toBe(false); // falta escolher o plano
    page.upgradePlanId.set(4);
    expect(page.canSubmit()).toBe(true);

    page.code.set('AB');
    expect(page.canSubmit()).toBe(false);
  });

  it('keeps what a used coupon gives locked, and lets the limits change', () => {
    page.openEdit(page.items()[1]);
    expect(page.locked()).toBe(true);
    page.maxRedemptions.set(5);

    page.submit();

    const request = backend.expectOne(`${API}/platform/coupons/2`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.maxRedemptions).toBe(5);
    expect(request.request.body.code).toBe('USADO');
    request.flush(coupon(2, 'USADO'));
    backend.expectOne(`${API}/platform/coupons`).flush([]);

    page.openEdit(page.items()[0] ?? coupon(1, 'DEZ20'));
  });

  it('generates a batch and offers the codes', () => {
    page.openBatch();
    page.quantity.set(3);
    page.prefix.set('feira');
    page.batchName.set('Feira de outubro');
    page.kind.set('FREE_DAYS');
    page.valueText.set('15');
    expect(page.canSubmitBatch()).toBe(true);

    page.submitBatch();

    const request = backend.expectOne(`${API}/platform/coupons/batch`);
    expect(request.request.body).toMatchObject({ prefix: 'feira', quantity: 3, batch: 'Feira de outubro', kind: 'FREE_DAYS', value: 15, durationMonths: null });
    request.flush({ batch: 'Feira de outubro', coupons: [coupon(10, 'FEIRA-AAAA-BBBB'), coupon(11, 'FEIRA-CCCC-DDDD'), coupon(12, 'FEIRA-EEEE-FFFF')] });
    expect(page.batchCodes()).toBe('FEIRA-AAAA-BBBB\nFEIRA-CCCC-DDDD\nFEIRA-EEEE-FFFF');
    backend.expectOne(`${API}/platform/coupons`).flush([]);
  });

  it('refuses a batch that is too big or with a prefix that is not letters and numbers', () => {
    page.openBatch();
    page.kind.set('FREE_DAYS');
    page.valueText.set('15');
    page.quantity.set(501);
    expect(page.canSubmitBatch()).toBe(false);
    page.quantity.set(500);
    expect(page.canSubmitBatch()).toBe(true);
    page.prefix.set('a b');
    expect(page.canSubmitBatch()).toBe(false);
  });

  it('shows who used a coupon', () => {
    page.showUses(page.items()[1]);

    backend.expectOne(`${API}/platform/coupons/2/redemptions`).flush([{ tenantId: 5, tenantName: 'Loja do Zé', redeemedBy: 'ze@x.com', redeemedAt: '2026-10-21T10:00:00Z' }]);

    expect(page.modal()).toBe('uses');
    expect(page.uses().map((use) => use.tenantName)).toEqual(['Loja do Zé']);
  });

  it('deletes an unused coupon after confirming', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    page.remove(page.items()[0]);

    backend.expectOne(`${API}/platform/coupons/1`).flush(null);
    expect(page.notice.text()).toBe('Cupom DEZ20 excluído.');
    backend.expectOne(`${API}/platform/coupons`).flush([]);
  });

  describe('helpers', () => {
    it('makes readable codes without look-alike characters', () => {
      const codes = Array.from({ length: 200 }, () => randomCouponCode());
      expect(codes.every((code) => /^[A-HJKMNP-Z2-9]{4}-[A-HJKMNP-Z2-9]{4}$/.test(code))).toBe(true);
      expect(new Set(codes).size).toBeGreaterThan(190);
    });

    it('converts the date field to the start or the end of the day and back', () => {
      expect(dateInputToIso('', true)).toBeNull();
      expect(dateInputToIso('nope', false)).toBeNull();
      const end = dateInputToIso('2026-12-31', true) as string;
      expect(new Date(end).getHours()).toBe(23);
      expect(isoToDateInput(end)).toBe('2026-12-31');
      expect(isoToDateInput(dateInputToIso('2026-01-02', false))).toBe('2026-01-02');
      expect(isoToDateInput(null)).toBe('');
    });
  });
});
