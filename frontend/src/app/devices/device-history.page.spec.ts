import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { ConfigService } from '../core/config.service';
import { Device, DeviceEvent } from '../core/models';
import { DeviceHistoryPage } from './device-history.page';

const API = 'http://api.test/api/v1';

const tv = (overrides: Partial<Device> = {}): Device => ({
  id: 5,
  name: 'TV Vitrine',
  location: 'Centro',
  locationId: 1,
  status: 'ACTIVE',
  connection: 'ONLINE',
  groupIds: [],
  online: true,
  lastSeenAt: '2026-11-05T12:00:00Z',
  playerVersion: '0.5.0',
  currentPlaylistId: 7,
  configVersion: 1,
  effectivePlaylistId: 7,
  campaignId: null,
  campaignName: null,
  currentMediaName: 'Promoção',
  createdAt: '',
  updatedAt: '',
  ...overrides,
});

const event = (id: number, overrides: Partial<DeviceEvent> = {}): DeviceEvent => ({
  id,
  deviceId: 5,
  type: 'ONLINE',
  level: 'INFO',
  message: `evento ${id}`,
  occurredAt: '2026-11-05T12:00:00Z',
  ...overrides,
});

function setup() {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '5' }) } } },
    ],
  });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  const backend = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(DeviceHistoryPage);
  return { page: fixture.componentInstance, backend, fixture };
}

function eventsRequest(backend: HttpTestingController, query: Record<string, string> = {}): TestRequest {
  return backend.expectOne((r) => {
    if (r.url !== `${API}/devices/5/events`) {
      return false;
    }
    return Object.entries({ limit: '50', ...query }).every(([key, value]) => r.params.get(key) === value);
  });
}

function start(events: DeviceEvent[] = [event(2), event(1)], render = false) {
  const ctx = setup();
  // Sem desenhar a tela chamamos o ngOnInit à mão; desenhando, é o primeiro detectChanges que o chama.
  if (render) {
    ctx.fixture.detectChanges();
  } else {
    ctx.page.ngOnInit();
  }
  ctx.backend.expectOne(`${API}/devices/5`).flush(tv());
  eventsRequest(ctx.backend).flush(events);
  return ctx;
}

describe('DeviceHistoryPage', () => {
  it('loads the TV and its timeline, newest first', () => {
    const { page, backend } = start();

    expect(page.device()?.name).toBe('TV Vitrine');
    expect(page.events().map((e) => e.id)).toEqual([2, 1]);
    expect(page.loaded()).toBe(true);
    expect(page.hasMore()).toBe(false);
    backend.verify();
  });

  it('renders the icon, the label, the message and highlights errors', async () => {
    const { fixture, backend } = start([
      event(3, { type: 'PLAYER_LOG', level: 'ERROR', message: 'Falha ao baixar a mídia' }),
      event(2, { type: 'OFFLINE', level: 'WARN', message: 'TV ficou sem contato com o servidor' }),
      event(1, { type: 'ONLINE', message: 'Primeiro contato da TV com o servidor' }),
    ], true);
    fixture.detectChanges();
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelectorAll('ol.timeline li')).toHaveLength(3);
    expect(element.textContent).toContain('Registro do Player');
    expect(element.textContent).toContain('Falha ao baixar a mídia');
    // erro e aviso aparecem como selo (cor + texto) e o ícone do tipo vem do mapa de eventos
    const levels = Array.from(element.querySelectorAll('app-pill.lvl')).map((pill) => pill.textContent?.trim());
    expect(levels).toEqual(['Erro', 'Aviso']);
    expect(element.querySelectorAll('ol.timeline .ev svg')).toHaveLength(3);
    expect(element.querySelector('ol.timeline .ev.err')).not.toBeNull();
    expect(element.querySelector('ol.timeline .ev.warn')).not.toBeNull();
    expect(element.textContent).toContain('na tela: Promoção');
    backend.verify();
  });

  it('says there is no history yet, or that the filters found nothing', async () => {
    const { page, fixture, backend } = start([], true);
    fixture.detectChanges();
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Ainda não há nada no histórico');

    page.setLevel('ERROR');
    eventsRequest(backend, { level: 'ERROR' }).flush([]);
    fixture.detectChanges();
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nenhum evento com esses filtros');
    backend.verify();
  });

  it('filters by type and level on the server and starts again from the newest', () => {
    const { page, backend } = start();

    page.setType('PLAYER_LOG');
    eventsRequest(backend, { type: 'PLAYER_LOG' }).flush([event(9, { type: 'PLAYER_LOG' })]);
    page.setLevel('ERROR');
    eventsRequest(backend, { type: 'PLAYER_LOG', level: 'ERROR' }).flush([]);

    expect(page.events()).toEqual([]);
    backend.verify();
  });

  it('offers "load more" only when a full page came back and appends the older events', () => {
    const full = Array.from({ length: 50 }, (_, i) => event(100 - i));
    const { page, backend } = start(full);
    expect(page.hasMore()).toBe(true);

    page.loadMore();
    eventsRequest(backend, { beforeId: '51' }).flush([event(50), event(49)]);

    expect(page.events()).toHaveLength(52);
    expect(page.events()[51].id).toBe(49);
    expect(page.hasMore()).toBe(false);
    expect(page.loadingMore()).toBe(false);
    backend.verify();
  });

  it('keeps what is on screen when the next page fails', () => {
    const full = Array.from({ length: 50 }, (_, i) => event(100 - i));
    const { page, backend } = start(full);

    page.loadMore();
    eventsRequest(backend, { beforeId: '51' }).flush('x', { status: 503, statusText: 'Service Unavailable' });

    expect(page.events()).toHaveLength(50);
    expect(page.loadingMore()).toBe(false);
    backend.verify();
  });

  it('reloads the first page on demand', () => {
    const { page, backend } = start();

    page.reload();
    eventsRequest(backend).flush([event(3), event(2), event(1)]);

    expect(page.events()).toHaveLength(3);
    backend.verify();
  });
});
