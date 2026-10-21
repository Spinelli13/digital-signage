import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { ConfigService } from '../core/config.service';
import { errorInterceptor } from '../core/error.interceptor';
import { Promotion, PromotionLookup } from '../core/models';
import { meFixture } from '../testing/fixtures';
import { PromotionsPage } from './promotions.page';

const API = 'http://api.test/api/v1';

const promotion = (id: number, name: string, overrides: Partial<Promotion> = {}): Promotion => ({
  id,
  name,
  description: null,
  validFrom: null,
  validUntil: null,
  claimDays: 7,
  maxClaims: null,
  claimedCount: 0,
  redeemedCount: 0,
  active: true,
  state: 'ACTIVE',
  publicToken: 'a'.repeat(32),
  createdAt: '2026-10-21T10:00:00Z',
  ...overrides,
});

const lookup = (overrides: Partial<PromotionLookup> = {}): PromotionLookup => ({
  code: 'ABCD-2345',
  status: 'ISSUED',
  promotionName: '10% na primeira compra',
  promotionDescription: null,
  claimantName: 'Maria',
  claimedAt: '2026-11-05T12:00:00Z',
  expiresAt: '2026-11-12T12:00:00Z',
  redeemedAt: null,
  ...overrides,
});

describe('PromotionsPage', () => {
  let backend: HttpTestingController;
  let page: PromotionsPage;

  beforeEach(() => {
    localStorage.setItem('signage.token', 'jwt');
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting(), provideRouter([])],
    });
    TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
    TestBed.inject(AuthService).me.set(meFixture('EDITOR'));
    backend = TestBed.inject(HttpTestingController);
    page = TestBed.createComponent(PromotionsPage).componentInstance;
    page.ngOnInit();
    backend.expectOne(`${API}/promotions`).flush([promotion(1, '10% na primeira compra', { claimedCount: 3, redeemedCount: 1, maxClaims: 50 }), promotion(2, 'Black Friday', { state: 'SCHEDULED', validFrom: '2026-11-20T03:00:00Z' })]);
  });

  afterEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
    backend.verify();
  });

  it('lists the promotions with their situation', () => {
    expect(page.items().map((item) => item.name)).toEqual(['10% na primeira compra', 'Black Friday']);
    expect(page.stateLabel(page.items()[0])).toBe('No ar');
    expect(page.stateLabel(page.items()[1])).toBe('Programada');
    expect(page.validity(page.items()[0])).toBe('Sem prazo');
    expect(page.validity(page.items()[1])).toContain('a partir de');
  });

  it('creates a promotion with the dates as instants and empty limits as unlimited', () => {
    page.openNew();
    expect(page.claimDays()).toBe(7);
    page.name.set('  Frete grátis ');
    page.description.set('Acima de R$ 100');
    page.validUntil.set('2026-12-31');
    page.maxClaims.set('');

    page.submit();

    const request = backend.expectOne(`${API}/promotions`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.name).toBe('Frete grátis');
    expect(request.request.body.description).toBe('Acima de R$ 100');
    expect(request.request.body.claimDays).toBe(7);
    expect(request.request.body.maxClaims).toBeNull();
    expect(request.request.body.validFrom).toBeNull();
    expect(new Date(request.request.body.validUntil).getHours()).toBe(23);
    request.flush(promotion(3, 'Frete grátis'));
    expect(page.notice.text()).toContain('Promoção "Frete grátis" criada');
    backend.expectOne(`${API}/promotions`).flush([]);
  });

  it('does not save without a name', () => {
    page.openNew();
    page.name.set('   ');
    expect(page.canSubmit()).toBe(false);
    page.submit();
    backend.expectNone(`${API}/promotions`);
  });

  it('edits a promotion and builds the address of the QR Code from its secret', () => {
    const first = page.items()[0];
    page.openEdit(first);
    expect(page.name()).toBe('10% na primeira compra');
    expect(page.maxClaims()).toBe(50);
    expect(page.publicLink()).toBe(`${location.origin}/cupom/${'a'.repeat(32)}`);
    page.maxClaims.set(80);

    page.submit();

    const request = backend.expectOne(`${API}/promotions/1`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.maxClaims).toBe(80);
    request.flush(promotion(1, '10% na primeira compra'));
    backend.expectOne(`${API}/promotions`).flush([]);
  });

  it('lists the coupons of a promotion', () => {
    page.showCoupons(page.items()[0]);

    backend.expectOne(`${API}/promotions/1/coupons`).flush([
      { id: 9, code: 'ABCD-2345', status: 'REDEEMED', claimantName: 'Maria', claimedAt: '2026-11-05T12:00:00Z', expiresAt: null, redeemedAt: '2026-11-05T15:00:00Z', redeemedBy: 'caixa@loja.com' },
    ]);

    expect(page.modal()).toBe('coupons');
    expect(page.coupons().map((coupon) => coupon.code)).toEqual(['ABCD-2345']);
    expect(page.couponStatusLabel('REDEEMED')).toBe('Usado');
    expect(page.couponStatusLabel('EXPIRED')).toBe('Vencido');
  });

  it('deletes a promotion after confirming', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    page.remove(page.items()[1]);

    backend.expectOne(`${API}/promotions/2`).flush(null);
    expect(page.notice.text()).toBe('Promoção "Black Friday" excluída.');
    backend.expectOne(`${API}/promotions`).flush([]);
  });

  describe('o caixa', () => {
    it('checks a code first and redeems only after the cashier confirms', () => {
      page.code.set(' abcd-2345 ');

      page.check();
      const check = backend.expectOne((request) => request.url === `${API}/promotions/coupons/lookup`);
      expect(check.request.params.get('code')).toBe('abcd-2345');
      check.flush(lookup());
      expect(page.found()?.status).toBe('ISSUED');

      page.redeem();
      const request = backend.expectOne(`${API}/promotions/coupons/redeem`);
      expect(request.request.body).toEqual({ code: 'ABCD-2345' });
      request.flush(lookup({ status: 'REDEEMED', redeemedAt: '2026-11-05T15:00:00Z' }));
      expect(page.found()?.status).toBe('REDEEMED');
      expect(page.code()).toBe('');
      expect(page.notice.text()).toBe('Cupom ABCD-2345 usado. Pode aplicar o benefício.');
      backend.expectOne(`${API}/promotions`).flush([]);
    });

    it('does not offer to redeem a coupon that is already used', () => {
      page.code.set('ABCD-2345');
      page.check();
      backend.expectOne((request) => request.url === `${API}/promotions/coupons/lookup`).flush(lookup({ status: 'REDEEMED' }));

      page.redeem();

      backend.expectNone(`${API}/promotions/coupons/redeem`);
    });

    it('shows the situation of now when someone else redeemed it first', () => {
      page.code.set('ABCD-2345');
      page.check();
      backend.expectOne((request) => request.url === `${API}/promotions/coupons/lookup`).flush(lookup());

      page.redeem();
      backend.expectOne(`${API}/promotions/coupons/redeem`).flush({ error: 'Este cupom acabou de ser usado por outro caixa.' }, { status: 409, statusText: 'Conflict' });
      backend.expectOne((request) => request.url === `${API}/promotions/coupons/lookup`).flush(lookup({ status: 'REDEEMED' }));

      expect(page.found()?.status).toBe('REDEEMED');
      expect(page.busy()).toBe(false);
    });

    it('does nothing with an empty code', () => {
      page.code.set('   ');
      page.check();
      backend.expectNone((request) => request.url === `${API}/promotions/coupons/lookup`);
    });
  });
});
