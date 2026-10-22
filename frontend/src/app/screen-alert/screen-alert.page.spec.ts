import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ConfigService } from '../core/config.service';
import { errorInterceptor } from '../core/error.interceptor';
import { ErrorService } from '../core/error.service';
import { Device, DeviceGroup, ScreenAlert } from '../core/models';
import { ScreenAlertPage, remainingLabel } from './screen-alert.page';

const API = 'http://api.test/api/v1';

const alert = (overrides: Partial<ScreenAlert> = {}): ScreenAlert => ({
  id: 1,
  title: null,
  message: 'Evacuação: sigam para a saída',
  tone: 'URGENT',
  targetType: 'ALL',
  targetId: null,
  targetName: null,
  createdBy: 'Ana',
  createdAt: '2026-11-05T15:00:00Z',
  expiresAt: new Date(Date.now() + 10 * 60_000).toISOString(),
  endedAt: null,
  endedBy: null,
  state: 'ACTIVE',
  devices: 3,
  ...overrides,
});

const group = (id: number, name: string, deviceCount: number): DeviceGroup => ({
  id,
  name,
  description: null,
  deviceCount,
  onlineCount: 0,
  deviceIds: [],
  createdAt: '',
  updatedAt: '',
});

const device = (id: number, name: string, status: Device['status'] = 'ACTIVE'): Device => ({
  id,
  name,
  location: null,
  locationId: null,
  status,
  connection: 'ONLINE',
  groupIds: [],
  online: true,
  lastSeenAt: null,
  playerVersion: null,
  currentPlaylistId: null,
  configVersion: 0,
  effectivePlaylistId: null,
  campaignId: null,
  campaignName: null,
  createdAt: '',
  updatedAt: '',
}) as Device;

function setup(items: ScreenAlert[] = [], render = false) {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting(), provideRouter([])],
  });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  const backend = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(ScreenAlertPage);
  const page = fixture.componentInstance;
  if (render) {
    fixture.detectChanges();
  } else {
    page.ngOnInit();
  }
  backend.expectOne(`${API}/screen-alerts`).flush(items);
  backend.expectOne(`${API}/groups`).flush([group(7, 'Recepções', 2), group(8, 'Vitrines', 1)]);
  backend.expectOne((request) => request.url === `${API}/devices`).flush([device(1, 'TV Entrada'), device(2, 'TV Vitrine'), device(3, 'TV nova', 'PENDING')]);
  return { page, backend, fixture };
}

describe('ScreenAlertPage', () => {
  it('lists what is on air apart from what already ended or expired', () => {
    const { page, backend } = setup([
      alert({ id: 3 }),
      alert({ id: 2, state: 'ENDED', endedBy: 'Bia', endedAt: '2026-11-05T15:05:00Z', devices: null }),
      alert({ id: 1, state: 'EXPIRED', devices: null }),
    ]);

    expect(page.active().map((item) => item.id)).toEqual([3]);
    expect(page.past().map((item) => item.id)).toEqual([2, 1]);
    expect(page.outcome(page.past()[0])).toBe('Encerrado por Bia');
    expect(page.outcome(page.past()[1])).toBe('O prazo acabou');
    backend.verify();
  });

  it('treats an alert whose time already ran out as past even before the next refresh', () => {
    const { page, backend } = setup([alert({ id: 1, expiresAt: new Date(Date.now() - 1000).toISOString() })]);

    expect(page.active()).toHaveLength(0);
    expect(page.past()).toHaveLength(1);
    backend.verify();
  });

  it('only lets it go when there is a message', () => {
    const { page, backend } = setup();
    expect(page.canSend()).toBe(false);
    page.message.set('   ');
    expect(page.canSend()).toBe(false);
    page.message.set('Fechamos mais cedo hoje');
    expect(page.canSend()).toBe(true);
    page.message.set('x'.repeat(301));
    expect(page.canSend()).toBe(false);
    backend.verify();
  });

  it('sends to all TVs with one call and clears the text but keeps the choices', () => {
    const { page, backend } = setup();
    page.title.set('  ATENÇÃO ');
    page.message.set('  Chame o gerente  ');
    page.tone.set('WARNING');
    page.duration.set(30);

    page.send();

    const request = backend.expectOne(`${API}/screen-alerts`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ title: 'ATENÇÃO', message: 'Chame o gerente', tone: 'WARNING', targetType: 'ALL', targetId: null, durationMinutes: 30 });
    request.flush(alert({ id: 9 }));
    backend.expectOne(`${API}/screen-alerts`).flush([alert({ id: 9 })]);
    expect(page.message()).toBe('');
    expect(page.title()).toBe('');
    expect(page.tone()).toBe('WARNING');
    expect(page.notice.text()).toContain('até 30 segundos');
    expect(page.busy()).toBe(false);
    backend.verify();
  });

  it('leaves the title out when it is blank', () => {
    const { page, backend } = setup();
    page.message.set('Aviso');

    page.send();

    const request = backend.expectOne(`${API}/screen-alerts`);
    expect('title' in (request.request.body as object)).toBe(false);
    request.flush(alert());
    backend.expectOne(`${API}/screen-alerts`).flush([]);
    backend.verify();
  });

  it('requires a group or a TV to be chosen and sends the chosen one', () => {
    const { page, backend } = setup();
    page.message.set('Fechamos mais cedo');

    page.setTarget('GROUP');
    expect(page.canSend()).toBe(false);
    expect(page.reachText()).toContain('Escolha o grupo');
    page.targetId.set(7);
    expect(page.canSend()).toBe(true);
    expect(page.reachText()).toContain('Recepções');
    expect(page.reachText()).toContain('2 TV');

    page.send();
    const request = backend.expectOne(`${API}/screen-alerts`);
    expect(request.request.body).toMatchObject({ targetType: 'GROUP', targetId: 7 });
    request.flush(alert({ targetType: 'GROUP', targetId: 7 }));
    backend.expectOne(`${API}/screen-alerts`).flush([]);

    page.setTarget('DEVICE');
    expect(page.targetId()).toBeNull();
    expect(page.canSend()).toBe(false);
    page.message.set('Chame o gerente');
    page.targetId.set(2);
    expect(page.reachText()).toContain('TV Vitrine');
    backend.verify();
  });

  it('counts only the active TVs as the reach of "all TVs"', () => {
    const { page, backend } = setup();

    expect(page.devices().map((tv) => tv.name)).toEqual(['TV Entrada', 'TV Vitrine']);
    expect(page.reachText()).toContain('(2)');
    backend.verify();
  });

  it('keeps what was typed and shows the reason when the server refuses', () => {
    const { page, backend } = setup();
    const errors = TestBed.inject(ErrorService);
    page.message.set('Aviso');

    page.send();
    backend.expectOne(`${API}/screen-alerts`).flush({ error: 'Já há 5 avisos no ar. Encerre algum antes de mandar outro' }, { status: 409, statusText: 'Conflict' });

    expect(page.busy()).toBe(false);
    expect(page.message()).toBe('Aviso');
    expect(errors.errors()[0].message).toContain('5 avisos no ar');
    backend.verify();
  });

  it('ends an alert and refreshes the list', () => {
    const { page, backend } = setup([alert({ id: 4 })]);

    page.end(page.active()[0]);
    expect(page.ending()).toBe(4);
    const request = backend.expectOne(`${API}/screen-alerts/4/end`);
    expect(request.request.method).toBe('POST');
    request.flush(alert({ id: 4, state: 'ENDED', devices: null }));
    backend.expectOne(`${API}/screen-alerts`).flush([alert({ id: 4, state: 'ENDED', devices: null })]);

    expect(page.ending()).toBeNull();
    expect(page.active()).toHaveLength(0);
    expect(page.notice.text()).toContain('Aviso encerrado');
    backend.verify();
  });

  it('draws the live alert with its reach and an end button', async () => {
    const { fixture, backend } = setup([alert({ id: 5, title: 'ATENÇÃO', targetType: 'GROUP', targetId: 7, targetName: 'Recepções' })], true);
    fixture.detectChanges();
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    const live = element.querySelector('[data-testid="live"]');
    expect(live?.textContent).toContain('ATENÇÃO');
    expect(live?.textContent).toContain('Evacuação: sigam para a saída');
    expect(live?.textContent).toContain('Grupo Recepções');
    expect(live?.textContent).toContain('TVs: 3');
    expect(live?.textContent).toContain('faltam');
    expect(live?.textContent).toContain('Encerrar agora');
    expect(element.querySelector('[data-testid="send"]')?.hasAttribute('disabled')).toBe(true);
    backend.verify();
  });

  it('says so when nothing is on air', async () => {
    const { fixture, backend } = setup([], true);
    fixture.detectChanges();
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nenhum aviso no ar');
    backend.verify();
  });
});

describe('remainingLabel', () => {
  const at = Date.parse('2026-11-05T15:00:00Z');

  it('speaks in seconds, minutes and hours', () => {
    expect(remainingLabel('2026-11-05T15:00:30Z', at)).toBe('faltam 30 s');
    expect(remainingLabel('2026-11-05T15:12:00Z', at)).toBe('faltam 12 min');
    expect(remainingLabel('2026-11-05T15:12:10Z', at)).toBe('faltam 13 min');
    expect(remainingLabel('2026-11-05T17:00:00Z', at)).toBe('faltam 2 h');
    expect(remainingLabel('2026-11-05T17:05:00Z', at)).toBe('faltam 2 h 5 min');
  });

  it('never goes below zero', () => {
    expect(remainingLabel('2026-11-05T14:00:00Z', at)).toBe('faltam 0 s');
  });
});
