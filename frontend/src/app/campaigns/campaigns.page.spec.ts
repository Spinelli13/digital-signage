import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ConfigService } from '../core/config.service';
import { errorInterceptor } from '../core/error.interceptor';
import { ErrorService } from '../core/error.service';
import { Campaign, Device, DeviceGroup, Location, Playlist } from '../core/models';
import { CampaignsPage } from './campaigns.page';

const API = 'http://api.test/api/v1';

const campaign = (overrides: Partial<Campaign> = {}): Campaign => ({
  id: 1,
  name: 'Happy hour',
  description: null,
  playlistId: 7,
  playlistName: 'Promo',
  priority: 80,
  enabled: true,
  startDate: null,
  endDate: null,
  startTime: '18:00:00',
  endTime: '23:00:00',
  daysOfWeek: [1, 2, 3, 4, 5, 6, 7],
  timezone: 'America/Sao_Paulo',
  targets: [{ type: 'ALL', id: null, name: null }],
  state: 'ACTIVE',
  deviceCount: 2,
  createdAt: '',
  updatedAt: '',
  ...overrides,
});

const playlist = (id: number, name: string): Playlist => ({ id, name, description: null, configVersion: 1, items: [], createdAt: '', updatedAt: '' });
const location = (id: number, name: string): Location => ({ id, name, description: null, deviceCount: 0, onlineCount: 0, createdAt: '', updatedAt: '' });
const group = (id: number, name: string): DeviceGroup => ({ id, name, description: null, deviceCount: 0, onlineCount: 0, deviceIds: [], createdAt: '', updatedAt: '' });
const device = (id: number, name: string): Device => ({
  id,
  name,
  location: null,
  locationId: null,
  status: 'ACTIVE',
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
});

describe('CampaignsPage', () => {
  let page: CampaignsPage;
  let backend: HttpTestingController;

  function flushLoad(campaigns: Campaign[]) {
    backend.expectOne(`${API}/campaigns`).flush(campaigns);
    backend.expectOne(`${API}/playlists`).flush([playlist(7, 'Promo'), playlist(8, 'Cardápio')]);
    backend.expectOne((r) => r.url === `${API}/devices`).flush([device(1, 'TV Recepção'), device(2, 'TV Vitrine')]);
    backend.expectOne(`${API}/locations`).flush([location(3, 'Loja Centro')]);
    backend.expectOne(`${API}/groups`).flush([group(10, 'Recepções')]);
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()] });
    TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
    backend = TestBed.inject(HttpTestingController);
    page = TestBed.createComponent(CampaignsPage).componentInstance;
    page.ngOnInit();
    flushLoad([campaign()]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    backend.verify();
  });

  it('loads campaigns and the lists needed to edit them', () => {
    expect(page.campaigns().map((c) => c.name)).toEqual(['Happy hour']);
    expect(page.playlists().length).toBe(2);
    expect(page.devices().length).toBe(2);
    expect(page.locations().length).toBe(1);
    expect(page.groups().length).toBe(1);
    expect(page.loaded()).toBe(true);
  });

  it('describes a campaign in plain words', () => {
    const c = page.campaigns()[0];

    expect(page.schedule(c)).toBe('Todos os dias, 18:00–23:00');
    expect(page.targets(c)).toBe('Todas as TVs');
    expect(page.badge(c).label).toBe('Ativa agora');
  });

  it('opens a new campaign with the default form', () => {
    page.openNew();

    expect(page.modal()).toBe('new');
    expect(page.form().priority).toBe(50);
    expect(page.form().allDevices).toBe(true);
    expect(page.form().allDay).toBe(true);
  });

  it('creates a campaign with the chosen playlist, hours, weekdays and targets', () => {
    page.openNew();
    page.set('name', ' Promoção de verão ');
    page.set('playlistId', 8);
    page.set('allDay', false);
    page.set('startTime', '18:00');
    page.set('endTime', '23:00');
    page.toggleDay(7);
    page.toggleDay(6);
    page.set('allDevices', false);
    page.toggleId('locationIds', 3);
    page.toggleId('groupIds', 10);

    page.submit();

    const request = backend.expectOne(`${API}/campaigns`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toMatchObject({
      name: 'Promoção de verão',
      playlistId: 8,
      startTime: '18:00',
      endTime: '23:00',
      daysOfWeek: [1, 2, 3, 4, 5],
      targets: [
        { type: 'LOCATION', id: 3 },
        { type: 'GROUP', id: 10 },
      ],
    });
    request.flush(campaign({ id: 2, name: 'Promoção de verão' }));
    expect(page.notice.text()).toContain('Campanha "Promoção de verão" criada');
    expect(page.modal()).toBeNull();
    flushLoad([]);
  });

  it('keeps the window open with a clear message when the form is incomplete, without calling the server', () => {
    page.openNew();

    page.submit();
    expect(page.formError()).toBe('Dê um nome à campanha.');

    page.set('name', 'Promo');
    page.submit();
    expect(page.formError()).toBe('Escolha a playlist da campanha.');

    backend.expectNone(`${API}/campaigns`);
    expect(page.modal()).toBe('new');
  });

  it('opens an existing campaign filled in and saves it with a PUT', () => {
    page.openEdit(page.campaigns()[0]);
    expect(page.form().name).toBe('Happy hour');
    expect(page.form().allDay).toBe(false);
    expect(page.form().startTime).toBe('18:00');

    page.set('priority', 90);
    page.submit();

    const request = backend.expectOne(`${API}/campaigns/1`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.priority).toBe(90);
    request.flush(campaign({ priority: 90 }));
    expect(page.notice.text()).toBe('Campanha "Happy hour" salva.');
    flushLoad([campaign({ priority: 90 })]);
  });

  it('shows the server message when it refuses the campaign and keeps the window open', () => {
    const errors = TestBed.inject(ErrorService);
    page.openEdit(page.campaigns()[0]);

    page.submit();
    backend.expectOne(`${API}/campaigns/1`).flush({ error: 'Playlist não encontrada: 7' }, { status: 404, statusText: 'Not Found' });

    expect(page.modal()).toBe('edit');
    expect(page.busy()).toBe(false);
    expect(errors.errors()[0].message).toContain('Playlist não encontrada: 7');
  });

  it('pauses an active campaign and reactivates a paused one, keeping everything else as it was', () => {
    page.togglePause(page.campaigns()[0]);

    const pause = backend.expectOne(`${API}/campaigns/1`);
    expect(pause.request.body).toMatchObject({ enabled: false, name: 'Happy hour', playlistId: 7, startTime: '18:00', endTime: '23:00', priority: 80 });
    pause.flush(campaign({ enabled: false, state: 'PAUSED' }));
    expect(page.notice.text()).toBe('Campanha "Happy hour" pausada.');
    flushLoad([campaign({ enabled: false, state: 'PAUSED' })]);

    page.togglePause(page.campaigns()[0]);
    const resume = backend.expectOne(`${API}/campaigns/1`);
    expect(resume.request.body.enabled).toBe(true);
    resume.flush(campaign());
    flushLoad([campaign()]);
  });

  it('deletes only after the user confirms', () => {
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(false);
    page.remove(page.campaigns()[0]);
    expect(confirmation).toHaveBeenCalledWith(expect.stringContaining('voltam a exibir a playlist delas'));
    backend.expectNone(`${API}/campaigns/1`);

    confirmation.mockReturnValue(true);
    page.remove(page.campaigns()[0]);
    const request = backend.expectOne(`${API}/campaigns/1`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    flushLoad([]);
  });

  describe('simulation', () => {
    it('does nothing until a TV is chosen', () => {
      page.simulate();

      backend.expectNone((r) => r.url.includes('/campaigns/preview'));
    });

    it('asks what the TV would show now when no moment is chosen', () => {
      page.simDevice.set(1);

      page.simulate();

      const request = backend.expectOne(`${API}/campaigns/preview?deviceId=1`);
      request.flush({ deviceId: 1, at: '', playlistId: 7, playlistName: 'Promo', source: 'CAMPAIGN', campaignId: 1, campaignName: 'Happy hour' });
      expect(page.simResult()?.source).toBe('CAMPAIGN');
    });

    it('sends the chosen moment as an instant', () => {
      page.simDevice.set(2);
      page.simAt.set('2026-11-05T18:30');

      page.simulate();

      const request = backend.expectOne((r) => r.url.startsWith(`${API}/campaigns/preview?deviceId=2&at=`));
      expect(decodeURIComponent(request.request.url)).toContain(new Date('2026-11-05T18:30').toISOString());
      request.flush({ deviceId: 2, at: '', playlistId: null, playlistName: null, source: 'NONE', campaignId: null, campaignName: null });
      expect(page.simResult()?.source).toBe('NONE');
    });
  });
});
