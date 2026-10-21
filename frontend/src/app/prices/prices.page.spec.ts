import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ConfigService } from '../core/config.service';
import { PriceList } from '../core/models';
import { PricesPage } from './prices.page';

const API = 'http://api.test/api/v1';

const list = (id: number, name: string, overrides: Partial<PriceList> = {}): PriceList => ({
  id,
  name,
  currency: 'BRL',
  timezone: 'America/Sao_Paulo',
  items: [],
  rules: [],
  widgetCount: 0,
  updatedAt: '2026-11-05T12:00:00Z',
  ...overrides,
});

function setup(lists: PriceList[]) {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  const backend = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(PricesPage);
  fixture.detectChanges();
  backend.expectOne(`${API}/price-lists`).flush(lists);
  return { fixture, page: fixture.componentInstance, backend };
}

describe('PricesPage', () => {
  afterEach(() => vi.restoreAllMocks());

  it('explains what to do when there is no table yet', async () => {
    const { fixture } = setup([]);
    fixture.detectChanges();
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nenhuma tabela de preços ainda');
  });

  it('shows each table with its items, time-based changes and widgets', async () => {
    const item = { id: 'a', category: null, name: 'X', description: null, price: 1, unit: null, available: true, highlight: false };
    const rule = { name: 'Happy', days: [], start: null, end: null, percent: -10, itemIds: [], itemPrices: {} };
    const { fixture } = setup([list(1, 'Bar', { items: [item, item], rules: [rule], widgetCount: 2 }), list(2, 'Salão', { items: [item] })]);
    fixture.detectChanges();
    await fixture.whenStable();

    const cards = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.item')).map((el) => el.textContent ?? '');
    expect(cards).toHaveLength(2);
    expect(cards[0]).toContain('Bar');
    expect(cards[0]).toContain('2 itens');
    expect(cards[0]).toContain('1 variação por horário');
    expect(cards[0]).toContain('em 2 widgets');
    expect(cards[1]).toContain('1 item');
    expect(cards[1]).not.toContain('widgets');
  });

  it('deletes after confirming and reloads', () => {
    const { page, backend } = setup([list(1, 'Bar')]);
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    page.remove(list(1, 'Bar'));
    backend.expectOne(`${API}/price-lists/1`).flush(null);
    backend.expectOne(`${API}/price-lists`).flush([]);

    expect(page.lists()).toHaveLength(0);
    expect(page.notice.text()).toContain('Tabela "Bar" excluída');
  });

  it('keeps everything when the table is still used by a widget (the server refuses)', () => {
    const { page, backend } = setup([list(1, 'Bar', { widgetCount: 1 })]);
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    page.remove(list(1, 'Bar'));
    backend.expectOne(`${API}/price-lists/1`).flush({ error: 'em uso' }, { status: 400, statusText: 'Bad Request' });

    expect(page.lists()).toHaveLength(1);
    backend.verify();
  });
});
