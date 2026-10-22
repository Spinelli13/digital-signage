import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ConfigService } from '../core/config.service';
import { FileSaver } from '../core/download';
import { daysAgo } from '../core/format';
import { EventRow, InventoryRow, PlaysReport, UptimeReport } from '../core/models';
import { ReportsPage } from './reports.page';

const API = 'http://api.test/api/v1';

const UPTIME: UptimeReport = {
  from: '2026-11-05',
  to: '2026-11-05',
  timezone: 'America/Sao_Paulo',
  fleetUptimePercent: 83.5,
  fleetOnlineSeconds: 7320,
  fleetObservedSeconds: 10920,
  devices: [
    { deviceId: 5, name: 'TV Vitrine', location: 'Centro', uptimePercent: 67, onlineSeconds: 7320, offlineSeconds: 3600, observedSeconds: 10920, incidents: 1 },
    { deviceId: 6, name: 'TV Nova', location: null, uptimePercent: null, onlineSeconds: 0, offlineSeconds: 0, observedSeconds: 0, incidents: 0 },
  ],
};

const PLAYS: PlaysReport = {
  from: '2026-11-05',
  to: '2026-11-06',
  groupBy: 'media',
  totalPlays: 22,
  totalSeconds: 260,
  rows: [
    { id: 1, label: 'Logo', day: null, plays: 18, seconds: 180, devices: 2 },
    { id: 2, label: 'Oferta', day: null, plays: 4, seconds: 80, devices: 1 },
  ],
};

const INVENTORY: InventoryRow[] = [
  {
    id: 5,
    name: 'TV Vitrine',
    location: 'Centro',
    groups: ['Vitrines'],
    status: 'ACTIVE',
    connection: 'ONLINE',
    lastSeenAt: '2026-11-05T12:00:00Z',
    playerVersion: '0.5.0',
    playlist: 'Promo',
    campaign: 'Happy hour',
    currentMedia: 'Promoção',
    activatedAt: '2026-11-01T10:00:00Z',
  },
];

const EVENTS: EventRow[] = [
  { id: 2, deviceId: 5, deviceName: 'TV Vitrine', type: 'PLAYER_LOG', level: 'ERROR', message: 'Falha ao baixar a mídia', occurredAt: '2026-11-05T12:00:00Z' },
  { id: 1, deviceId: 5, deviceName: 'TV Vitrine', type: 'ONLINE', level: 'INFO', message: 'Primeiro contato', occurredAt: '2026-11-05T11:00:00Z' },
];

const save = vi.fn();

function setup() {
  save.mockReset();
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: FileSaver, useValue: { save } }],
  });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  const backend = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(ReportsPage);
  return { page: fixture.componentInstance, backend, fixture };
}

function request(backend: HttpTestingController, path: string, params: Record<string, string> = {}): TestRequest {
  return backend.expectOne((r) => r.url === `${API}${path}` && Object.entries(params).every(([key, value]) => r.params.get(key) === value));
}

/** Abre a tela: as listas dos filtros e o relatório de disponibilidade (a primeira aba). */
function start(render = false) {
  const ctx = setup();
  // Sem desenhar a tela chamamos o ngOnInit à mão; desenhando, é o primeiro detectChanges que o chama.
  if (render) {
    ctx.fixture.detectChanges();
  } else {
    ctx.page.ngOnInit();
  }
  ctx.backend.expectOne(`${API}/locations`).flush([{ id: 1, name: 'Centro' }]);
  ctx.backend.expectOne(`${API}/groups`).flush([{ id: 10, name: 'Vitrines' }]);
  ctx.backend.expectOne((r) => r.url === `${API}/devices`).flush([{ id: 5, name: 'TV Vitrine' }]);
  request(ctx.backend, '/reports/uptime', { from: daysAgo(6), to: daysAgo(0) }).flush(UPTIME);
  return ctx;
}

describe('ReportsPage', () => {
  it('opens on the availability of the last 7 days', () => {
    const { page, backend } = start();

    expect(page.tab()).toBe('uptime');
    expect(page.uptime()?.fleetUptimePercent).toBe(83.5);
    backend.verify();
  });

  it('shows the fleet percentage, an honest "sem dados" and the durations', async () => {
    const { fixture, backend } = start(true);
    fixture.detectChanges();
    await fixture.whenStable();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('83,5%');
    expect(text).toContain('67,0%');
    expect(text).toContain('2 h 2 min');
    expect(text).toContain('1 h');
    expect(text).toContain('sem dados');
    backend.verify();
  });

  it('refetches with the new period when the dates change or a preset is clicked', () => {
    const { page, backend } = start();

    page.setFrom('2026-11-01');
    request(backend, '/reports/uptime', { from: '2026-11-01', to: daysAgo(0) }).flush(UPTIME);
    page.setTo('2026-11-03');
    request(backend, '/reports/uptime', { from: '2026-11-01', to: '2026-11-03' }).flush(UPTIME);
    page.preset(29);
    request(backend, '/reports/uptime', { from: daysAgo(29), to: daysAgo(0) }).flush(UPTIME);

    expect(page.from()).toBe(daysAgo(29));
    backend.verify();
  });

  it('filters the availability by location and group', () => {
    const { page, backend } = start();

    page.setLocation(1);
    request(backend, '/reports/uptime', { locationId: '1' }).flush(UPTIME);
    page.setGroup(10);
    request(backend, '/reports/uptime', { locationId: '1', groupId: '10' }).flush(UPTIME);

    backend.verify();
  });

  it('shows the plays grouped as asked and explains an empty period', async () => {
    const { page, fixture, backend } = start(true);

    page.select('plays');
    request(backend, '/reports/plays', { groupBy: 'media' }).flush(PLAYS);
    fixture.detectChanges();
    await fixture.whenStable();
    let text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Logo');
    expect(text).toContain('18');
    expect(page.playsColumn()).toBe('Mídia');

    page.setGroupBy('day');
    request(backend, '/reports/plays', { groupBy: 'day' }).flush({ ...PLAYS, groupBy: 'day', totalPlays: 0, totalSeconds: 0, rows: [] });
    fixture.detectChanges();
    await fixture.whenStable();
    text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Nenhuma exibição registrada');
    expect(page.playsColumn()).toBe('Dia');
    backend.verify();
  });

  it('opens the proof of play tab with the period of the page, and leaves the CSV to the tab itself', async () => {
    const { page, fixture, backend } = start(true);

    page.select('proof');
    fixture.detectChanges();
    await fixture.whenStable();
    backend.match(`${API}/media`).forEach((r) => r.flush([]));
    backend.match(`${API}/campaigns`).forEach((r) => r.flush([]));
    const proof = backend.match((r) => r.url === `${API}/reports/proof`);
    expect(proof.length).toBeGreaterThan(0);
    expect(proof[0].request.params.get('from')).toBe(daysAgo(6));
    proof.forEach((r) => r.flush({ from: '', to: '', timezone: 'America/Sao_Paulo', total: 0, completed: 0, totalDurationMs: 0, devices: 0, offset: 0, limit: 100, rows: [] }));
    backend.match((r) => r.url === `${API}/reports/proof/daily`).forEach((r) => r.flush([]));
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('app-proof-report')).not.toBeNull();
    // o botão de CSV do cabeçalho some: a aba tem o seu, com os filtros dela
    expect(element.querySelector('app-page-header button')).toBeNull();
    backend.verify();
  });

  it('shows the equipment and the notes in the inventory, and nothing for a TV that has none yet', async () => {
    const { page, fixture, backend } = start(true);

    page.select('inventory');
    request(backend, '/reports/inventory').flush([
      { ...INVENTORY[0], hardwareModel: 'TCL 55S546', androidVersion: '11', ramMb: 1800, serialNumber: 'SN-1', comodatoNote: 'Comodato Completo' },
      { ...INVENTORY[0], id: 6, name: 'TV Balcão' },
    ]);
    fixture.detectChanges();
    await fixture.whenStable();

    const text = ((fixture.nativeElement as HTMLElement).textContent ?? '').replace(/\s+/g, ' ');
    expect(text).toContain('Equipamento');
    expect(text).toContain('TCL 55S546');
    expect(text).toContain('Android 11 · 1800 MB de memória');
    expect(text).toContain('Série SN-1');
    expect(text).toContain('Comodato Completo');
    backend.verify();
  });

  it('shows the inventory without a period filter', async () => {
    const { page, fixture, backend } = start(true);

    page.select('inventory');
    request(backend, '/reports/inventory').flush(INVENTORY);
    fixture.detectChanges();
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Happy hour');
    expect(element.textContent).toContain('Vitrines');
    expect(element.textContent).toContain('Promoção');
    expect(element.querySelector('input[type="date"]')).toBeNull();
    backend.verify();
  });

  it('filters the event report by TV, type and level', () => {
    const { page, backend } = start();

    page.select('events');
    request(backend, '/reports/events').flush(EVENTS);
    page.setEventDevice(5);
    request(backend, '/reports/events', { deviceId: '5' }).flush(EVENTS);
    page.setEventType('PLAYER_LOG');
    request(backend, '/reports/events', { deviceId: '5', type: 'PLAYER_LOG' }).flush([EVENTS[0]]);
    page.setEventLevel('ERROR');
    request(backend, '/reports/events', { deviceId: '5', type: 'PLAYER_LOG', level: 'ERROR' }).flush([EVENTS[0]]);

    expect(page.events()).toHaveLength(1);
    backend.verify();
  });

  it('downloads the CSV of the report on screen with the same filters, named after the period', () => {
    const { page, backend } = start();
    page.setLocation(1);
    request(backend, '/reports/uptime', { locationId: '1' }).flush(UPTIME);

    page.downloadCsv();
    const csv = request(backend, '/reports/uptime', { format: 'csv', locationId: '1', from: daysAgo(6), to: daysAgo(0) });
    expect(csv.request.responseType).toBe('blob');
    expect(page.downloading()).toBe(true);
    const blob = new Blob(['TV;Local'], { type: 'text/csv' });
    csv.flush(blob);

    expect(page.downloading()).toBe(false);
    expect(save).toHaveBeenCalledWith(expect.any(Blob), `disponibilidade-${daysAgo(6)}-a-${daysAgo(0)}.csv`);
    backend.verify();
  });

  it('names the inventory CSV after today only', () => {
    const { page, backend } = start();
    page.select('inventory');
    request(backend, '/reports/inventory').flush(INVENTORY);

    page.downloadCsv();
    request(backend, '/reports/inventory', { format: 'csv' }).flush(new Blob(['x']));

    expect(save).toHaveBeenCalledWith(expect.any(Blob), `inventario-${daysAgo(0)}.csv`);
    backend.verify();
  });

  it('does not save anything and re-enables the button when the CSV fails', () => {
    const { page, backend } = start();

    page.downloadCsv();
    request(backend, '/reports/uptime', { format: 'csv' }).flush(new Blob(['x']), { status: 400, statusText: 'Bad Request' });

    expect(save).not.toHaveBeenCalled();
    expect(page.downloading()).toBe(false);
    backend.verify();
  });
});
