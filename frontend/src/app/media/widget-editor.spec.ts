import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ConfigService } from '../core/config.service';
import { Media, WidgetCatalog, WidgetPlace, WidgetPreview } from '../core/models';
import { WidgetTemplates } from '../core/widgets';
import { WidgetEditorComponent } from './widget-editor';

const API = 'http://api.test/api/v1';

const CATALOG: WidgetCatalog = {
  kinds: [
    { kind: 'CLOCK', label: 'Relógio e data', defaultSeconds: 10, needsData: false },
    { kind: 'WEATHER', label: 'Previsão do tempo', defaultSeconds: 15, needsData: true },
    { kind: 'NEWS', label: 'Notícias', defaultSeconds: 20, needsData: true },
    { kind: 'RATES', label: 'Cotações', defaultSeconds: 12, needsData: true },
    { kind: 'NOTICE', label: 'Recado', defaultSeconds: 10, needsData: false },
    { kind: 'COUNTDOWN', label: 'Contagem regressiva', defaultSeconds: 10, needsData: false },
    { kind: 'PRICES', label: 'Tabela de preços', defaultSeconds: 20, needsData: true },
    { kind: 'QUEUE', label: 'Painel de chamadas', defaultSeconds: 15, needsData: true },
  ],
  newsSources: [
    { id: 'g1', name: 'G1' },
    { id: 'bbc', name: 'BBC News Brasil' },
  ],
  currencies: ['USD', 'EUR', 'GBP'],
};

const PLACE: WidgetPlace = { name: 'Campinas', region: 'São Paulo', country: 'Brasil', latitude: -22.9, longitude: -47.06, timezone: 'America/Sao_Paulo' };

/** O modelo visual não vem da rede nos testes. */
class FakeTemplates {
  pages: unknown[] = [];
  async page(init: unknown): Promise<string> {
    this.pages.push(init);
    return '<html></html>';
  }
}

const widget = (overrides: Partial<Media> = {}): Media => ({
  id: 7,
  name: 'Aviso da loja',
  type: 'WIDGET',
  mimeType: 'application/x-smartplay-widget',
  url: '',
  fileSize: 0,
  durationSeconds: 15,
  checksum: null,
  widget: { kind: 'NOTICE', settings: { theme: 'dark', lang: 'pt-BR', title: 'Fechado', message: 'Volto amanhã', accent: 'red', align: 'center' } },
  createdAt: '',
  ...overrides,
});

function setup(media: Media | null = null) {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: WidgetTemplates, useClass: FakeTemplates }],
  });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  const backend = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(WidgetEditorComponent);
  fixture.componentRef.setInput('media', media);
  const editor = fixture.componentInstance;
  const saved: Media[] = [];
  editor.saved.subscribe((value) => saved.push(value));
  const closed = vi.fn();
  editor.closed.subscribe(closed);
  fixture.detectChanges();
  backend.expectOne(`${API}/widgets/catalog`).flush(CATALOG);
  return { fixture, editor, backend, saved, closed, text: () => (fixture.nativeElement as HTMLElement).textContent ?? '' };
}

/** Passa o tempo da espera de digitação e responde a conferência do servidor. */
function answerPreview(backend: HttpTestingController, answer: Partial<WidgetPreview> | { problem: string }, count = 1) {
  vi.advanceTimersByTime(600);
  const requests = backend.match(`${API}/widgets/preview`);
  expect(requests.length).toBeGreaterThanOrEqual(count);
  const last = requests[requests.length - 1];
  if ('problem' in answer) {
    last.flush({ error: answer.problem }, { status: 400, statusText: 'Bad Request' });
  } else {
    last.flush({ kind: 'CLOCK', settings: {}, data: null, error: null, ...answer });
  }
  return last.request.body as { kind: string; settings: Record<string, unknown> };
}

describe('WidgetEditorComponent', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('starts by offering the eight kinds, each with an explanation', () => {
    const { text, fixture } = setup();

    const cards = (fixture.nativeElement as HTMLElement).querySelectorAll('.kind');
    expect(cards).toHaveLength(8);
    expect(text()).toContain('Previsão do tempo');
    expect(text()).toContain('Quanto falta para uma data');
  });

  it('choosing a kind opens its form with sensible defaults and asks the server to preview', async () => {
    const { editor, fixture, backend } = setup();

    editor.choose('CLOCK');
    fixture.detectChanges();

    expect(editor.kind()).toBe('CLOCK');
    expect(editor.seconds()).toBe(10);
    expect(editor.text('timezone')).toBe('America/Sao_Paulo');
    expect(editor.flag('showDate')).toBe(true);
    const body = answerPreview(backend, { kind: 'CLOCK', settings: { theme: 'dark' } });
    expect(body.kind).toBe('CLOCK');
    expect(body.settings['timezone']).toBe('America/Sao_Paulo');
    expect(editor.checking()).toBe(false);
    backend.verify();
  });

  it('the weather needs a city: search, choose, and the coordinates go to the server', () => {
    const { editor, fixture, backend } = setup();
    editor.choose('WEATHER');
    fixture.detectChanges();
    answerPreview(backend, { problem: 'Escolha a cidade da previsão do tempo' });
    expect(editor.problem()).toBe('Escolha a cidade da previsão do tempo');

    editor.placeQuery.set('Campin');
    editor.searchPlaces();
    const search = backend.expectOne((r) => r.url === `${API}/widgets/places`);
    expect(search.request.params.get('q')).toBe('Campin');
    search.flush([PLACE]);
    expect(editor.places()).toHaveLength(1);
    editor.choosePlace(PLACE);
    fixture.detectChanges();

    expect(editor.text('city')).toBe('Campinas, São Paulo');
    expect(editor.places()).toHaveLength(0);
    const body = answerPreview(backend, { kind: 'WEATHER' });
    expect(body.settings['latitude']).toBe(-22.9);
    expect(body.settings['longitude']).toBe(-47.06);
    expect(editor.problem()).toBeNull();
    backend.verify();
  });

  it('a city search with no result says so', () => {
    const { editor, fixture, backend, text } = setup();
    editor.choose('WEATHER');
    fixture.detectChanges();
    answerPreview(backend, { problem: 'x' });

    editor.placeQuery.set('Lugar nenhum');
    editor.searchPlaces();
    backend.expectOne((r) => r.url === `${API}/widgets/places`).flush([]);
    fixture.detectChanges();

    expect(text()).toContain('Nenhuma cidade encontrada');
  });

  it('a choice the server refuses shows why and blocks saving', () => {
    const { editor, fixture, backend, text } = setup();
    editor.choose('NOTICE');
    fixture.detectChanges();

    answerPreview(backend, { problem: 'Escreva o título ou a mensagem do aviso' });
    fixture.detectChanges();

    expect(text()).toContain('Escreva o título ou a mensagem do aviso');
    editor.save();
    backend.expectNone(`${API}/media/widget`);
  });

  it('when the data could not be fetched it warns without blocking', () => {
    const { editor, fixture, backend, text } = setup();
    editor.choose('RATES');
    fixture.detectChanges();

    answerPreview(backend, { kind: 'RATES', data: null, error: 'Não foi possível buscar as cotações agora' });
    fixture.detectChanges();

    expect(editor.problem()).toBeNull();
    expect(text()).toContain('Não foi possível buscar as cotações agora');
  });

  it('toggles the currencies', () => {
    const { editor, fixture, backend } = setup();
    editor.choose('RATES');
    fixture.detectChanges();
    answerPreview(backend, { kind: 'RATES' });

    expect(editor.hasCurrency('USD')).toBe(true);
    editor.toggleCurrency('GBP');
    editor.toggleCurrency('USD');

    expect(editor.settings()['currencies']).toEqual(['EUR', 'GBP']);
  });

  it('the price board loads the tables to choose from and sends the chosen one', () => {
    const { editor, fixture, backend, text } = setup();

    editor.choose('PRICES');
    backend.expectOne(`${API}/price-lists`).flush([
      { id: 4, name: 'Cardápio', currency: 'BRL', timezone: 'UTC', items: [], rules: [], widgetCount: 0, updatedAt: '' },
    ]);
    fixture.detectChanges();

    expect(editor.priceLists()).toHaveLength(1);
    expect(text()).toContain('Os itens e os preços');
    editor.set('priceListId', 4);
    const body = answerPreview(backend, { kind: 'PRICES' });
    expect(body.settings['priceListId']).toBe(4);
    expect(body.settings['columns']).toBe(1);
  });

  it('the call board lists the queues to choose, keeps the choices as numbers and points to the page that creates them', () => {
    const { editor, fixture, backend, text } = setup();

    editor.choose('QUEUE');
    backend.expectOne(`${API}/queues`).flush([
      { id: 1, name: 'Atendimento geral', prefix: 'A', priority: false, active: true, timezone: 'UTC', waiting: 0 },
      { id: 2, name: 'Prioritário', prefix: 'P', priority: true, active: true, timezone: 'UTC', waiting: 0 },
    ]);
    fixture.detectChanges();

    expect(editor.queueList()).toHaveLength(2);
    expect(text()).toContain('Filas mostradas');
    expect(text()).toContain('Atendimento geral');
    expect(editor.settings()['calls']).toBe(5);
    expect(editor.settings()['sound']).toBe(true);
    editor.toggleQueue(2);
    expect(editor.hasQueue(2)).toBe(true);
    expect(editor.hasQueue(1)).toBe(false);
    const body = answerPreview(backend, { kind: 'QUEUE' });
    expect(body.settings['queueIds']).toEqual([2]);
    editor.toggleQueue(2);
    expect(editor.settings()['queueIds']).toEqual([]);
  });

  it('without any queue the call board points to the page that creates them', () => {
    const { editor, fixture, backend, text } = setup();

    editor.choose('QUEUE');
    backend.expectOne(`${API}/queues`).flush([]);
    fixture.detectChanges();

    expect(text()).toContain('Você ainda não tem nenhuma fila de atendimento');
    expect(text()).toContain('Criar as filas');
  });

  it('without any price table it points to the page that creates one', () => {
    const { editor, fixture, backend, text } = setup();

    editor.choose('PRICES');
    backend.expectOne(`${API}/price-lists`).flush([]);
    fixture.detectChanges();

    expect(text()).toContain('Você ainda não tem nenhuma tabela de preços');
    expect(text()).toContain('Criar a primeira');
  });

  it('saves a new widget with the name, kind, choices and time', () => {
    const { editor, fixture, backend, saved } = setup();
    editor.choose('NOTICE');
    editor.name.set('  Fechado hoje  ');
    editor.set('title', 'Fechado');
    editor.set('message', 'Voltamos amanhã');
    editor.seconds.set(25);
    fixture.detectChanges();
    answerPreview(backend, { kind: 'NOTICE' });

    editor.save();

    const create = backend.expectOne(`${API}/media/widget`);
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({
      name: 'Fechado hoje',
      kind: 'NOTICE',
      settings: expect.objectContaining({ title: 'Fechado', message: 'Voltamos amanhã', accent: 'brand' }),
      durationSeconds: 25,
    });
    create.flush(widget());
    expect(saved).toHaveLength(1);
    expect(editor.busy()).toBe(false);
  });

  it('editing starts straight in the form, keeps the kind, and saves with a PUT', () => {
    const { editor, fixture, backend, saved } = setup(widget());
    fixture.detectChanges();

    expect(editor.editing()).toBe(true);
    expect(editor.kind()).toBe('NOTICE');
    expect(editor.text('title')).toBe('Fechado');
    expect(editor.name()).toBe('Aviso da loja');
    expect(editor.seconds()).toBe(15);
    answerPreview(backend, { kind: 'NOTICE' });

    editor.set('message', 'Volto na terça');
    answerPreview(backend, { kind: 'NOTICE' });
    editor.save();

    const update = backend.expectOne(`${API}/media/7/widget`);
    expect(update.request.method).toBe('PUT');
    expect((update.request.body as { settings: Record<string, unknown> }).settings['message']).toBe('Volto na terça');
    update.flush(widget({ name: 'Aviso da loja' }));
    expect(saved).toHaveLength(1);
  });

  it('a name equal to the default of the kind is left empty so the server keeps naming it', () => {
    const { editor, fixture, backend } = setup(widget({ name: 'Recado' }));
    fixture.detectChanges();
    answerPreview(backend, { kind: 'NOTICE' });

    expect(editor.name()).toBe('');
  });

  it('keeps the window open and does not duplicate when the save fails', () => {
    const { editor, fixture, backend, saved } = setup();
    editor.choose('CLOCK');
    fixture.detectChanges();
    answerPreview(backend, { kind: 'CLOCK' });

    editor.save();
    backend.expectOne(`${API}/media/widget`).flush({ error: 'x' }, { status: 400, statusText: 'Bad Request' });

    expect(saved).toHaveLength(0);
    expect(editor.busy()).toBe(false);
  });

  it('closing does not save anything', () => {
    const { fixture, closed, backend } = setup();

    ((fixture.nativeElement as HTMLElement).querySelector('.modal') as HTMLElement).click();

    expect(closed).toHaveBeenCalledTimes(1);
    backend.expectNone(`${API}/media/widget`);
  });
});
