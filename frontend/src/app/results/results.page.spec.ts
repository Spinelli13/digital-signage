import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ConfigService } from '../core/config.service';
import { FileSaver } from '../core/download';
import { Promotion, ResultsResponse } from '../core/models';
import { ResultsPage } from './results.page';
import { activeDays, barHeights, formatRate, headline, originLabel, usageRate } from './results';

const API = 'http://api.test/api/v1';

const response = (overrides: Partial<ResultsResponse['report']> = {}, couponsEnabled = true): ResultsResponse => ({
  couponsEnabled,
  report: {
    from: '2026-11-03',
    to: '2026-11-05',
    timezone: 'America/Sao_Paulo',
    plays: 1240,
    devicesWithPlays: 2,
    scans: 80,
    claimed: 35,
    redeemed: 14,
    days: [
      { day: '2026-11-03', plays: 0, scans: 0, claimed: 0, redeemed: 0 },
      { day: '2026-11-04', plays: 400, scans: 30, claimed: 10, redeemed: 4 },
      { day: '2026-11-05', plays: 840, scans: 50, claimed: 25, redeemed: 10 },
    ],
    tvs: [
      { deviceId: 1, name: 'TV Entrada', plays: 900, scans: 60, claimed: 28, redeemed: 12 },
      { deviceId: 2, name: null, plays: 340, scans: 5, claimed: 1, redeemed: 0 },
    ],
    origins: [
      { origin: 'balcao', scans: 10, claimed: 5, redeemed: 2 },
      { origin: '', scans: 5, claimed: 1, redeemed: 0 },
    ],
    promotions: [{ id: 7, name: 'Corte grátis', scans: 80, claimed: 35, redeemed: 14 }],
    topMedia: [{ name: 'Promo de terça', type: 'IMAGE', plays: 700 }],
    ...overrides,
  },
});

const promotion = (id: number, name: string): Promotion => ({ id, name }) as Promotion;

function setup(initial: ResultsResponse = response()) {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  const backend = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(ResultsPage);
  fixture.detectChanges();
  backend.expectOne(`${API}/promotions`).flush([promotion(7, 'Corte grátis'), promotion(8, 'Outra')]);
  backend.expectOne((r) => r.url === `${API}/results` && !r.params.has('format')).flush(initial);
  fixture.detectChanges();
  return { fixture, page: fixture.componentInstance, backend, element: fixture.nativeElement as HTMLElement };
}

describe('ResultsPage', () => {
  it('tells it in one sentence and shows the four numbers', async () => {
    const { fixture, element, backend } = setup();
    await fixture.whenStable();

    expect(element.querySelector('[data-testid="headline"]')?.textContent).toContain('rodou');
    expect(element.querySelector('[data-testid="headline"]')?.textContent).toContain('14');
    expect(element.querySelector('[data-testid="kpi-plays"]')?.textContent).toContain('1.240');
    expect(element.querySelector('[data-testid="kpi-scans"]')?.textContent).toContain('80');
    expect(element.querySelector('[data-testid="kpi-claimed"]')?.textContent).toContain('35');
    expect(element.querySelector('[data-testid="kpi-redeemed"]')?.textContent).toContain('14');
    expect(element.textContent).toContain('40,0%'); // 14 de 35 cupons usados
    backend.verify();
  });

  it('lists per day (only the days with something), per TV, per other QR Code and per promotion', async () => {
    const { fixture, element, backend } = setup();
    await fixture.whenStable();

    expect(element.querySelectorAll('[data-testid="day-row"]')).toHaveLength(2);
    const tvs = Array.from(element.querySelectorAll('[data-testid="tv-row"]')).map((row) => row.textContent ?? '');
    expect(tvs[0]).toContain('TV Entrada');
    expect(tvs[1]).toContain('TV excluída');
    const origins = Array.from(element.querySelectorAll('[data-testid="origin-row"]')).map((row) => row.textContent ?? '');
    expect(origins[0]).toContain('QR Code: balcao');
    expect(origins[1]).toContain('QR Code geral (sem origem)');
    expect(element.querySelector('[data-testid="promotion-row"]')?.textContent).toContain('Corte grátis');
    backend.verify();
  });

  it('hides everything about coupons when the plan does not include them', async () => {
    const { fixture, element, backend } = setup(response({}, false));
    await fixture.whenStable();

    expect(element.querySelector('[data-testid="kpi-plays"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="kpi-scans"]')).toBeNull();
    expect(element.querySelector('[data-testid="origin-row"]')).toBeNull();
    expect(element.querySelector('[data-testid="promotion-row"]')).toBeNull();
    expect(element.textContent).not.toContain('Por promoção');
    backend.verify();
  });

  it('says so when nothing happened in the period', async () => {
    const { fixture, element, backend } = setup(response({ plays: 0, scans: 0, claimed: 0, redeemed: 0, days: [], tvs: [], origins: [], promotions: [], topMedia: [] }));
    await fixture.whenStable();

    expect(element.textContent).toContain('Sem movimento neste período');
    expect(element.querySelector('[data-testid="day-row"]')).toBeNull();
    backend.verify();
  });

  it('reloads when the period or the promotion changes, and sends the chosen filters', () => {
    const { page, backend } = setup();

    page.setPeriod('7');
    const week = backend.expectOne((r) => r.url === `${API}/results`);
    expect(week.request.params.get('from')).toBe(page.from());
    expect(week.request.params.get('to')).toBe(page.to());
    week.flush(response());

    page.setPromotion(7);
    const filtered = backend.expectOne((r) => r.url === `${API}/results`);
    expect(filtered.request.params.get('promotionId')).toBe('7');
    filtered.flush(response());

    page.setPeriod('custom'); // só escolher "datas" não pede nada: espera as datas
    backend.expectNone(`${API}/results`);
    page.changeDates('2026-11-10', '2026-11-01'); // início depois do fim: não pede
    backend.expectNone((r) => r.url === `${API}/results`);
    page.changeDates('2026-11-01', '2026-11-10');
    backend.expectOne((r) => r.url === `${API}/results` && r.params.get('from') === '2026-11-01').flush(response());
    backend.verify();
  });

  it('downloads the CSV with the same period, by day, TV or promotion', () => {
    const { page, backend } = setup();
    const saved: string[] = [];
    vi.spyOn(TestBed.inject(FileSaver), 'save').mockImplementation((_blob, name) => void saved.push(name));

    page.download('tv');
    const request = backend.expectOne((r) => r.url === `${API}/results` && r.params.get('format') === 'csv');
    expect(request.request.params.get('by')).toBe('tv');
    expect(request.request.params.get('from')).toBe(page.from());
    request.flush(new Blob(['x']));

    expect(page.downloading()).toBe(false);
    expect(saved[0]).toMatch(/^resultados-por-tv-\d{4}-\d{2}-\d{2}-a-\d{4}-\d{2}-\d{2}\.csv$/);
    backend.verify();
  });
});

describe('results helpers', () => {
  it('computes the usage rate and shows a dash when no coupon was claimed', () => {
    expect(usageRate(35, 14)).toBe(40);
    expect(usageRate(3, 1)).toBe(33.3);
    expect(usageRate(0, 0)).toBeNull();
    expect(formatRate(0, 0)).toBe('—');
    expect(formatRate(35, 14)).toBe('40,0%');
  });

  it('scales the bars to the biggest day and keeps tiny values visible', () => {
    expect(barHeights([0, 50, 100])).toEqual([0, 50, 100]);
    expect(barHeights([0, 0])).toEqual([0, 0]);
    expect(barHeights([1, 1000])).toEqual([4, 100]);
    expect(barHeights([])).toEqual([]);
  });

  it('keeps only the days with activity, newest first', () => {
    const days = [
      { day: '2026-11-03', plays: 0, scans: 0, claimed: 0, redeemed: 0 },
      { day: '2026-11-04', plays: 5, scans: 0, claimed: 0, redeemed: 0 },
      { day: '2026-11-05', plays: 0, scans: 1, claimed: 0, redeemed: 0 },
    ];

    expect(activeDays(days).map((d) => d.day)).toEqual(['2026-11-05', '2026-11-04']);
  });

  it('names the QR Codes that are not a TV and writes the headline', () => {
    expect(originLabel({ origin: '', scans: 0, claimed: 0, redeemed: 0 })).toBe('QR Code geral (sem origem)');
    expect(originLabel({ origin: 'panfleto', scans: 0, claimed: 0, redeemed: 0 })).toBe('QR Code: panfleto');
    expect(headline(1240, 2, 80, 35, 14, true)).toHaveLength(2);
    expect(headline(1240, 2, 80, 35, 14, false)).toHaveLength(1);
    expect(headline(0, 0, 0, 0, 0, true)).toEqual(['Ainda não há exibições registradas neste período.']);
  });
});
