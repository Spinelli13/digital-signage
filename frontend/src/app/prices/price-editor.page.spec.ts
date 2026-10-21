import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';

import { ConfigService } from '../core/config.service';
import { FileSaver } from '../core/download';
import { PriceBoardData, PriceList } from '../core/models';
import { WidgetTemplates } from '../core/widgets';
import { PriceEditorPage } from './price-editor.page';

const API = 'http://api.test/api/v1';

const LIST: PriceList = {
  id: 5,
  name: 'Cardápio',
  currency: 'BRL',
  timezone: 'America/Sao_Paulo',
  widgetCount: 0,
  updatedAt: '2026-11-05T12:00:00Z',
  items: [
    { id: 'beer', category: 'Bebidas', name: 'Cerveja', description: null, price: 10, unit: null, available: true, highlight: false },
    { id: 'juice', category: 'Bebidas', name: 'Suco', description: null, price: 8, unit: null, available: true, highlight: false },
  ],
  rules: [{ name: 'Happy hour', days: [1, 2, 3, 4, 5], start: '17:00', end: '20:00', percent: -20, itemIds: ['beer'], itemPrices: {} }],
};

const BOARD: PriceBoardData = {
  list: { id: null, name: 'Cardápio', currency: 'BRL' },
  timezone: 'America/Sao_Paulo',
  fetchedAtMs: 0,
  segments: [
    { fromMs: Date.parse('2026-11-05T15:00:00Z'), toMs: Date.parse('2026-11-05T20:00:00Z'), rule: null, items: [] },
    { fromMs: Date.parse('2026-11-05T20:00:00Z'), toMs: Date.parse('2026-11-05T23:00:00Z'), rule: 'Happy hour', items: [] },
  ],
};

class FakeTemplates {
  async page(): Promise<string> {
    return '<html></html>';
  }
}

const save = vi.fn();

function setup(id: string | null = '5') {
  save.mockReset();
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(id ? { id } : {}) } } },
      { provide: FileSaver, useValue: { save } },
      { provide: WidgetTemplates, useClass: FakeTemplates },
    ],
  });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  const backend = TestBed.inject(HttpTestingController);
  const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  const fixture = TestBed.createComponent(PriceEditorPage);
  const page = fixture.componentInstance;
  fixture.detectChanges();
  return { fixture, page, backend, navigate, text: () => (fixture.nativeElement as HTMLElement).textContent ?? '' };
}

function load(ctx: ReturnType<typeof setup>, list: PriceList = LIST): void {
  ctx.backend.expectOne(`${API}/price-lists/${list.id}`).flush(list);
}

function previewRequests(backend: HttpTestingController): TestRequest[] {
  vi.advanceTimersByTime(700);
  return backend.match(`${API}/price-lists/preview`);
}

describe('PriceEditorPage', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('opens a new table with one empty row and does not call the server', () => {
    const { page, backend } = setup(null);

    expect(page.id()).toBeNull();
    expect(page.form().items).toHaveLength(1);
    expect(page.dirty()).toBe(false);
    backend.verify();
  });

  it('loads an existing table and shows its items and rules', () => {
    const ctx = setup();
    load(ctx);
    ctx.fixture.detectChanges();

    expect(ctx.page.id()).toBe(5);
    expect(ctx.page.form().name).toBe('Cardápio');
    expect(ctx.page.form().items).toHaveLength(2);
    expect(ctx.page.form().rules[0].name).toBe('Happy hour');
    expect(ctx.page.dirty()).toBe(false);
    expect(ctx.fixture.nativeElement.querySelectorAll('.irow:not(.head)')).toHaveLength(2);
    expect(ctx.fixture.nativeElement.querySelectorAll('.rule')).toHaveLength(1);
  });

  it('goes back to the list when the table does not exist', () => {
    const ctx = setup('99');
    ctx.backend.expectOne(`${API}/price-lists/99`).flush({ error: 'x' }, { status: 404, statusText: 'Not Found' });

    expect(ctx.navigate).toHaveBeenCalledWith(['/precos']);
  });

  it('knows when there are unsaved changes and when everything is saved', () => {
    const ctx = setup();
    load(ctx);

    ctx.page.setItem(0, { price: 12 });
    expect(ctx.page.dirty()).toBe(true);

    ctx.page.setItem(0, { price: 10 });
    expect(ctx.page.dirty()).toBe(false);
  });

  it('adds an item in the same category as the last one and removes it', () => {
    const ctx = setup();
    load(ctx);

    ctx.page.addItem();
    expect(ctx.page.form().items).toHaveLength(3);
    expect(ctx.page.form().items[2].category).toBe('Bebidas');

    ctx.page.removeItem(2);
    expect(ctx.page.form().items).toHaveLength(2);
  });

  it('removing an item also takes it out of the rules that cite it', () => {
    const ctx = setup();
    load(ctx);
    ctx.page.setRule(0, { scope: 'some', itemIds: ['beer'] });

    ctx.page.removeItem(0);

    expect(ctx.page.form().rules[0].itemIds).toEqual([]);
  });

  it('moves items and rules up and down, never past the ends', () => {
    const ctx = setup();
    load(ctx);

    ctx.page.moveItem(0, 1);
    expect(ctx.page.form().items.map((item) => item.id)).toEqual(['juice', 'beer']);
    ctx.page.moveItem(0, -1);
    expect(ctx.page.form().items.map((item) => item.id)).toEqual(['juice', 'beer']);
    ctx.page.addRule();
    ctx.page.moveRule(1, -1);
    expect(ctx.page.form().rules[1].name).toBe('Happy hour');
  });

  it('edits the days, the items and the fixed prices of a rule', () => {
    const ctx = setup();
    load(ctx);

    ctx.page.toggleDay(0, 6);
    expect(ctx.page.form().rules[0].days).toContain(6);
    ctx.page.toggleDay(0, 1);
    expect(ctx.page.form().rules[0].days).not.toContain(1);

    ctx.page.toggleRuleItem(0, 'juice');
    expect(ctx.page.form().rules[0].itemIds).toEqual(['beer', 'juice']);
    ctx.page.toggleRuleItem(0, 'beer');
    expect(ctx.page.form().rules[0].itemIds).toEqual(['juice']);

    ctx.page.setFixed(0, 'beer', '7.5');
    expect(ctx.page.form().rules[0].fixed['beer']).toBe(7.5);
    ctx.page.setFixed(0, 'beer', '');
    expect(ctx.page.form().rules[0].fixed['beer']).toBeUndefined();
  });

  it('asks the server for the prices of the next 24 hours after a pause in typing, once', () => {
    const ctx = setup();
    load(ctx);
    ctx.fixture.detectChanges();

    ctx.page.setItem(0, { price: 11 });
    ctx.page.setItem(0, { price: 12 });
    const requests = previewRequests(ctx.backend);

    expect(requests).toHaveLength(1);
    expect((requests[0].request.body as { items: { price: number }[] }).items[0].price).toBe(12);
    requests[0].flush(BOARD);
    expect(ctx.page.segments()).toHaveLength(2);
  });

  it('does not bother the server while the form has problems', () => {
    const ctx = setup();
    load(ctx);

    ctx.page.setField('name', '');

    expect(ctx.page.problemList()).toEqual(['Dê um nome à tabela.']);
    expect(previewRequests(ctx.backend)).toHaveLength(0);
  });

  it('simulates a time of day by choosing one of the intervals', () => {
    const ctx = setup();
    load(ctx);
    ctx.page.setItem(0, { price: 11 });
    previewRequests(ctx.backend)[0].flush(BOARD);

    expect(ctx.page.previewNow()).toBeNull();
    ctx.page.simulate.set(1);

    expect(ctx.page.previewNow()).toBe(BOARD.segments[1].fromMs + 1000);
    expect(ctx.page.segmentLabel(BOARD.segments[1])).toContain('Happy hour: 17:00 – 20:00');
    expect(ctx.page.segmentLabel(BOARD.segments[0])).toContain('Preço normal');
  });

  it('shows why the preview failed without blocking anything else', () => {
    const ctx = setup();
    load(ctx);
    ctx.page.setItem(0, { price: 11 });

    previewRequests(ctx.backend)[0].flush({ error: 'Moeda não disponível' }, { status: 400, statusText: 'Bad Request' });
    ctx.fixture.detectChanges();

    expect(ctx.page.previewProblem()).toBe('Moeda não disponível');
    expect(ctx.text()).toContain('Moeda não disponível');
  });

  it('saves a new table, goes to its page and tells how long the TVs take', () => {
    const ctx = setup(null);
    ctx.page.setField('name', 'Novo');
    ctx.page.setItem(0, { name: 'Café', price: 5 });

    ctx.page.save();

    const create = ctx.backend.expectOne(`${API}/price-lists`);
    expect(create.request.method).toBe('POST');
    expect((create.request.body as { items: { name: string }[] }).items[0].name).toBe('Café');
    create.flush({ ...LIST, id: 9, name: 'Novo' });
    expect(ctx.page.id()).toBe(9);
    expect(ctx.navigate).toHaveBeenCalledWith(['/precos', 9], { replaceUrl: true });
    expect(ctx.page.notice.text()).toContain('recebem os novos preços em cerca de um minuto');
    expect(ctx.page.dirty()).toBe(false);
  });

  it('saves an existing table with a PUT', () => {
    const ctx = setup();
    load(ctx);
    ctx.page.setItem(0, { price: 12 });

    ctx.page.save();

    const update = ctx.backend.expectOne(`${API}/price-lists/5`);
    expect(update.request.method).toBe('PUT');
    update.flush({ ...LIST, items: [{ ...LIST.items[0], price: 12 }, LIST.items[1]] });
    expect(ctx.page.dirty()).toBe(false);
    expect(ctx.navigate).not.toHaveBeenCalled();
  });

  it('does not save while something is missing', () => {
    const ctx = setup(null);

    ctx.page.save();

    ctx.backend.expectNone(`${API}/price-lists`);
    expect(ctx.page.touched()).toBe(true);
  });

  it('imports a spreadsheet, shows what was skipped and reloads the table', () => {
    const ctx = setup();
    load(ctx);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const csv = new File(['Nome;Preço\nCafé;5'], 'precos.csv', { type: 'text/csv' });
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', { value: [csv] });

    ctx.page.chooseImport('append');
    ctx.page.onFile({ target: input } as unknown as Event);

    const request = ctx.backend.expectOne((r) => r.url === `${API}/price-lists/5/import`);
    expect(request.request.params.get('mode')).toBe('append');
    expect(request.request.body instanceof FormData).toBe(true);
    request.flush({ list: { ...LIST, items: [LIST.items[0]] }, imported: 1, skipped: ['Linha 3: sem nome'] });

    expect(ctx.page.form().items).toHaveLength(1);
    expect(ctx.page.skipped()).toEqual(['Linha 3: sem nome']);
    expect(ctx.page.notice.text()).toContain('1 item(ns) importado(s)');
  });

  it('exports the spreadsheet named after the table', () => {
    const ctx = setup();
    load(ctx);

    ctx.page.exportCsv();
    ctx.backend.expectOne(`${API}/price-lists/5/export`).flush(new Blob(['x']));

    expect(save).toHaveBeenCalledWith(expect.any(Blob), 'precos-cardapio.csv');
  });

  it('deletes the table after confirming and goes back to the list', () => {
    const ctx = setup();
    load(ctx);
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    ctx.page.remove();
    ctx.backend.expectOne(`${API}/price-lists/5`).flush(null);

    expect(ctx.navigate).toHaveBeenCalledWith(['/precos']);
  });

  it('does nothing when the deletion is not confirmed', () => {
    const ctx = setup();
    load(ctx);
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    ctx.page.remove();

    ctx.backend.expectNone(`${API}/price-lists/5`);
  });
});
