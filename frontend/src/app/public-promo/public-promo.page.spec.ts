import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';

import { ConfigService } from '../core/config.service';
import { errorInterceptor } from '../core/error.interceptor';
import { PublicPromoCoupon, PublicPromotion } from '../core/models';
import { deviceId } from '../public-ticket/public-ticket.page';
import { MyCouponPage, PromoClaimPage, rememberedCoupon } from './public-promo.page';

const API = 'http://api.test/api/v1';
const PROMO_TOKEN = 'a'.repeat(32);
const COUPON_TOKEN = 'c'.repeat(32);

const promotion = (overrides: Partial<PublicPromotion> = {}): PublicPromotion => ({
  name: '10% na primeira compra',
  description: 'Válido para compras acima de R$ 50',
  open: true,
  validUntil: null,
  claimDays: 7,
  ...overrides,
});

const coupon = (overrides: Partial<PublicPromoCoupon> = {}): PublicPromoCoupon => ({
  code: 'ABCD-2345',
  status: 'ISSUED',
  promotionName: '10% na primeira compra',
  promotionDescription: 'Válido para compras acima de R$ 50',
  expiresAt: '2026-11-12T12:00:00Z',
  redeemedAt: null,
  token: COUPON_TOKEN,
  ...overrides,
});

function setup<T>(component: new () => T, token: string, render = false) {
  localStorage.clear();
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ token }) } } },
    ],
  });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  const backend = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(component);
  if (render) {
    fixture.detectChanges();
  }
  return { page: fixture.componentInstance, fixture, backend, navigate };
}

const textOf = (fixture: { nativeElement: unknown }) => ((fixture.nativeElement as HTMLElement).textContent ?? '').replace(/\s+/g, ' ');

describe('PromoClaimPage (QR Code da promoção)', () => {
  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
  });

  it('shows the promotion and its rules, and claims a coupon with the typed name and this device code', async () => {
    const { page, fixture, backend, navigate } = setup(PromoClaimPage, PROMO_TOKEN, true);
    backend.expectOne(`${API}/public/promotions/${PROMO_TOKEN}`).flush(promotion());
    fixture.detectChanges();
    await fixture.whenStable();

    expect(textOf(fixture)).toContain('10% na primeira compra');
    expect(textOf(fixture)).toContain('Válido para compras acima de R$ 50');
    expect(textOf(fixture)).toContain('O cupom vale por 7 dia(s) depois de pego.');
    page.customerName.set('  Maria ');
    page.claim();

    const request = backend.expectOne(`${API}/public/promotions/${PROMO_TOKEN}/coupons`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ customerName: 'Maria' });
    expect(request.request.headers.get('X-Client-Id')).toBe(deviceId());
    request.flush(coupon());
    expect(rememberedCoupon(PROMO_TOKEN)).toBe(COUPON_TOKEN);
    expect(navigate).toHaveBeenCalledWith(['/meu-cupom', COUPON_TOKEN]);
    backend.verify();
  });

  it('sends no name when the field is empty', () => {
    const { page, backend } = setup(PromoClaimPage, PROMO_TOKEN);
    page.ngOnInit();
    backend.expectOne(`${API}/public/promotions/${PROMO_TOKEN}`).flush(promotion());

    page.claim();

    expect(backend.expectOne(`${API}/public/promotions/${PROMO_TOKEN}/coupons`).request.body).toEqual({ customerName: null });
  });

  it('goes straight to the coupon already claimed on this phone (scanning again does not issue another)', () => {
    const { page, backend, navigate } = setup(PromoClaimPage, PROMO_TOKEN);
    localStorage.setItem('smartplay.mycoupon.' + PROMO_TOKEN, COUPON_TOKEN);

    page.ngOnInit();

    expect(navigate).toHaveBeenCalledWith(['/meu-cupom', COUPON_TOKEN], { replaceUrl: true });
    backend.expectNone(`${API}/public/promotions/${PROMO_TOKEN}`);
  });

  it('says so when the promotion is closed and offers no button', async () => {
    const { fixture, backend } = setup(PromoClaimPage, PROMO_TOKEN, true);
    backend.expectOne(`${API}/public/promotions/${PROMO_TOKEN}`).flush(promotion({ open: false }));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(textOf(fixture)).toContain('Esta promoção não está disponível agora');
    expect(fixture.nativeElement.querySelector('button.primary')).toBeNull();
  });

  it('shows the reason the server gave when the coupons ran out', async () => {
    const { page, fixture, backend } = setup(PromoClaimPage, PROMO_TOKEN, true);
    backend.expectOne(`${API}/public/promotions/${PROMO_TOKEN}`).flush(promotion());
    fixture.detectChanges();

    page.claim();
    backend.expectOne(`${API}/public/promotions/${PROMO_TOKEN}/coupons`).flush({ error: 'Os cupons desta promoção acabaram.' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(page.problem()).toBe('Os cupons desta promoção acabaram.');
    expect(page.busy()).toBe(false);
    expect(textOf(fixture)).toContain('Os cupons desta promoção acabaram.');
  });

  it('explains an invalid QR Code', async () => {
    const { page, backend } = setup(PromoClaimPage, PROMO_TOKEN);
    page.ngOnInit();
    backend.expectOne(`${API}/public/promotions/${PROMO_TOKEN}`).flush({ error: 'Este QR Code não é válido. Procure o atendimento da loja.' }, { status: 404, statusText: 'Not Found' });

    expect(page.problem()).toBe('Este QR Code não é válido. Procure o atendimento da loja.');
  });
});

describe('MyCouponPage (o cupom do cliente)', () => {
  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
  });

  it('shows the code to show at the cashier and until when it is valid', async () => {
    const { fixture, backend } = setup(MyCouponPage, COUPON_TOKEN, true);
    backend.expectOne(`${API}/public/coupons/${COUPON_TOKEN}`).flush(coupon());
    fixture.detectChanges();
    await fixture.whenStable();

    const text = textOf(fixture);
    expect(text).toContain('ABCD-2345');
    expect(text).toContain('Mostre este código no caixa.');
    expect(text).toContain('Vale até');
    expect(text).toContain('10% na primeira compra');
  });

  it('asks again while it is valid, and stops once it was used', () => {
    vi.useFakeTimers();
    const { page, backend } = setup(MyCouponPage, COUPON_TOKEN);
    page.ngOnInit();
    backend.expectOne(`${API}/public/coupons/${COUPON_TOKEN}`).flush(coupon());

    vi.advanceTimersByTime(10_000);
    backend.expectOne(`${API}/public/coupons/${COUPON_TOKEN}`).flush(coupon({ status: 'REDEEMED', redeemedAt: '2026-11-05T15:00:00Z' }));
    expect(page.coupon()?.status).toBe('REDEEMED');

    vi.advanceTimersByTime(60_000);
    backend.expectNone(`${API}/public/coupons/${COUPON_TOKEN}`);
    backend.verify();
  });

  it('says the coupon was used or expired', async () => {
    const used = setup(MyCouponPage, COUPON_TOKEN, true);
    used.backend.expectOne(`${API}/public/coupons/${COUPON_TOKEN}`).flush(coupon({ status: 'REDEEMED', redeemedAt: '2026-11-05T15:00:00Z' }));
    used.fixture.detectChanges();
    await used.fixture.whenStable();
    expect(textOf(used.fixture)).toContain('Cupom já usado.');
  });

  it('says an expired coupon expired', async () => {
    const expired = setup(MyCouponPage, COUPON_TOKEN, true);
    expired.backend.expectOne(`${API}/public/coupons/${COUPON_TOKEN}`).flush(coupon({ status: 'EXPIRED' }));
    expired.fixture.detectChanges();
    await expired.fixture.whenStable();
    expect(textOf(expired.fixture)).toContain('Este cupom venceu.');
  });

  it('keeps the coupon on screen when the network fails and stops asking when it no longer exists', () => {
    vi.useFakeTimers();
    const { page, backend } = setup(MyCouponPage, COUPON_TOKEN);
    page.ngOnInit();
    backend.expectOne(`${API}/public/coupons/${COUPON_TOKEN}`).flush(coupon());

    vi.advanceTimersByTime(10_000);
    backend.expectOne(`${API}/public/coupons/${COUPON_TOKEN}`).error(new ProgressEvent('error'));
    expect(page.coupon()?.code).toBe('ABCD-2345');
    expect(page.problem()).not.toBeNull();

    vi.advanceTimersByTime(10_000);
    backend.expectOne(`${API}/public/coupons/${COUPON_TOKEN}`).flush({ error: 'Este cupom não foi encontrado.' }, { status: 404, statusText: 'Not Found' });
    vi.advanceTimersByTime(60_000);
    backend.expectNone(`${API}/public/coupons/${COUPON_TOKEN}`);
    // a falha de rede faz o painel conferir se o servidor está de pé
    backend.match(`${API}/health`).forEach((request) => request.flush({ status: 'UP' }));
    backend.verify();
  });
});
