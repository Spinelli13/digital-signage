import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ConfigService } from '../core/config.service';
import { errorInterceptor } from '../core/error.interceptor';
import { ErrorService } from '../core/error.service';
import { Device, TemplateCatalog, TemplateResult, WidgetPlace } from '../core/models';
import { TemplatesPage, placeLabel } from './templates.page';

const API = 'http://api.test/api/v1';

const catalog = (layouts = true, prices = true): TemplateCatalog => ({
  available: { widgets: true, prices, layouts },
  templates: [
    {
      id: 'barbearia',
      name: 'Barbearia',
      icon: 'scissors',
      description: 'Boas-vindas, promoção da semana, tabela de serviços, horário e como agendar, com relógio e previsão do tempo na lateral.',
      fields: [
        { key: 'name', label: 'Nome da barbearia', defaultValue: 'Barbearia', maxLength: 60 },
        { key: 'promo', label: 'Promoção da semana', defaultValue: 'Terça: corte + barba por R$ 49', maxLength: 120 },
      ],
      includes: ['Boas-vindas', 'Promoção da semana', 'Nossos serviços', 'Relógio'],
      hasPrices: true,
    },
    { id: 'loja', name: 'Loja', icon: 'shopping-bag', description: 'Boas-vindas, promoção, horário e contato, com relógio e previsão do tempo na lateral.', fields: [], includes: ['Boas-vindas'], hasPrices: false },
  ],
});

const device = (id: number, name: string, status: Device['status'] = 'ACTIVE'): Device => ({ id, name, status }) as Device;

const place: WidgetPlace = { name: 'São Paulo', region: 'São Paulo', country: 'Brasil', latitude: -23.55, longitude: -46.63, timezone: 'America/Sao_Paulo' };

const result = (overrides: Partial<TemplateResult> = {}): TemplateResult => ({
  templateId: 'barbearia',
  layout: { id: 3, name: 'Barbearia do Zé - Layout' },
  playlists: [{ id: 11, name: 'Barbearia do Zé - Principal' }],
  widgets: [{ id: 1, name: 'Barbearia do Zé - Boas-vindas' }, { id: 2, name: 'Barbearia do Zé - Relógio' }],
  priceList: { id: 5, name: 'Barbearia do Zé - Nossos serviços' },
  notes: ['A previsão do tempo não foi criada: escolha a cidade para incluí-la (você pode criar depois na Biblioteca).'],
  devices: 0,
  ...overrides,
});

function setup(initial: TemplateCatalog = catalog()) {
  TestBed.configureTestingModule({ providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting(), provideRouter([])] });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  const backend = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(TemplatesPage);
  fixture.detectChanges();
  backend.expectOne(`${API}/templates`).flush(initial);
  backend.expectOne((r) => r.url === `${API}/devices`).flush([device(1, 'TV Salão'), device(2, 'TV Vitrine'), device(3, 'TV nova', 'PENDING')]);
  fixture.detectChanges();
  return { fixture, page: fixture.componentInstance, backend, element: fixture.nativeElement as HTMLElement };
}

describe('TemplatesPage', () => {
  it('shows one card per segment with what it includes', async () => {
    const { fixture, element, backend } = setup();
    await fixture.whenStable();

    const cards = Array.from(element.querySelectorAll('[data-testid="template-card"]'));
    expect(cards).toHaveLength(2);
    expect(cards[0].textContent).toContain('Barbearia');
    expect(cards[0].textContent).toContain('Boas-vindas, Promoção da semana, Nossos serviços, Relógio');
    expect(element.querySelector('[data-testid="plan-note"]')).toBeNull();
    backend.verify();
  });

  it('warns what the plan leaves out of the template', async () => {
    const { fixture, element, backend } = setup(catalog(false, false));
    await fixture.whenStable();

    const note = element.querySelector('[data-testid="plan-note"]')?.textContent ?? '';
    expect(note).toContain('não inclui layouts');
    expect(note).toContain('não inclui tabelas de preços');
    backend.verify();
  });

  it('opens with the default texts, sends what was edited and shows what was created', async () => {
    const { page, fixture, element, backend } = setup();
    page.open(page.catalog()!.templates[0]);
    fixture.detectChanges();
    expect(page.values()).toEqual({ name: 'Barbearia', promo: 'Terça: corte + barba por R$ 49' });

    page.setValue('name', 'Barbearia do Zé');
    page.apply();

    const request = backend.expectOne(`${API}/templates/barbearia/apply`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ fields: { name: 'Barbearia do Zé', promo: 'Terça: corte + barba por R$ 49' } });
    request.flush(result());
    fixture.detectChanges();
    await fixture.whenStable();

    expect(page.busy()).toBe(false);
    const made = element.querySelector('[data-testid="made"]')?.textContent ?? '';
    expect(made).toContain('Barbearia do Zé - Layout');
    expect(made).toContain('Barbearia do Zé - Principal');
    expect(made).toContain('Barbearia do Zé - Nossos serviços');
    expect(made).toContain('Barbearia do Zé - Relógio');
    expect(element.textContent).toContain('previsão do tempo não foi criada');
    backend.verify();
  });

  it('searches a city and sends it with the chosen TVs (only the active ones are offered)', () => {
    const { page, fixture, backend } = setup();
    page.open(page.catalog()!.templates[0]);
    fixture.detectChanges();
    expect(page.devices().map((d) => d.name)).toEqual(['TV Salão', 'TV Vitrine']);

    page.cityQuery.set('São Paulo');
    page.searchCity();
    const search = backend.expectOne((r) => r.url === `${API}/widgets/places`);
    expect(search.request.params.get('q')).toBe('São Paulo');
    search.flush([place]);
    expect(page.cityResults()).toEqual([place]);
    page.chooseCity(place);
    expect(page.city()).toEqual(place);
    expect(page.cityResults()).toEqual([]);
    page.toggleDevice(2);
    page.toggleDevice(1);
    page.toggleDevice(1); // desmarcou

    page.apply();
    const request = backend.expectOne(`${API}/templates/barbearia/apply`);
    expect(request.request.body.city).toEqual({ name: 'São Paulo', latitude: -23.55, longitude: -46.63, timezone: 'America/Sao_Paulo' });
    expect(request.request.body.deviceIds).toEqual([2]);
    request.flush(result({ devices: 1 }));
    expect(page.result()?.devices).toBe(1);
    backend.verify();
  });

  it('does not search for a city with fewer than 2 letters', () => {
    const { page, backend } = setup();
    page.open(page.catalog()!.templates[0]);
    page.cityQuery.set('S');

    page.searchCity();

    backend.expectNone((r) => r.url === `${API}/widgets/places`);
    backend.verify();
  });

  it('keeps the form open and shows the reason when the server refuses', () => {
    const { page, backend } = setup();
    const errors = TestBed.inject(ErrorService);
    page.open(page.catalog()!.templates[0]);

    page.apply();
    backend.expectOne(`${API}/templates/barbearia/apply`).flush({ error: 'O plano Grátis não inclui Widgets. Peça um plano que inclua este recurso.' }, { status: 402, statusText: 'Payment Required' });

    expect(page.busy()).toBe(false);
    expect(page.selected()).not.toBeNull();
    expect(page.result()).toBeNull();
    expect(errors.errors()[0].message).toContain('não inclui');
    backend.verify();
  });

  it('closes and forgets the result', () => {
    const { page, backend } = setup();
    page.open(page.catalog()!.templates[0]);
    page.apply();
    backend.expectOne(`${API}/templates/barbearia/apply`).flush(result());

    page.close();

    expect(page.selected()).toBeNull();
    expect(page.result()).toBeNull();
    backend.verify();
  });

  it('writes a place in one line', () => {
    expect(placeLabel(place)).toBe('São Paulo, São Paulo, Brasil');
    expect(placeLabel({ ...place, region: '' })).toBe('São Paulo, Brasil');
  });
});
