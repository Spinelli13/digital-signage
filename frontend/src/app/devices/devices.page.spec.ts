import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { ConfigService } from '../core/config.service';
import { Device, DeviceGroup, Layout, Location, Playlist, ReleaseInfo } from '../core/models';
import { meFixture } from '../testing/fixtures';
import { DevicesPage } from './devices.page';

const API = 'http://api.test/api/v1';
const DOWNLOADS = 'https://downloads.test';

const release = (versionName: string): ReleaseInfo => ({
  versionCode: 3,
  versionName,
  apk: `player-${versionName}.apk`,
  sha256: 'a'.repeat(64),
  size: 1,
  notes: '',
  publishedAt: '',
});

const device = (overrides: Partial<Device> = {}): Device => ({
  id: 1,
  name: 'TV Recepção',
  location: 'Térreo',
  locationId: 3,
  status: 'ACTIVE',
  connection: 'ONLINE',
  groupIds: [],
  online: true,
  lastSeenAt: '2026-10-20T10:00:00Z',
  playerVersion: '0.3.0',
  currentPlaylistId: 7,
  configVersion: 1,
  effectivePlaylistId: 7,
  campaignId: null,
  campaignName: null,
  createdAt: '',
  updatedAt: '',
  ...overrides,
});

const playlist = (id: number): Playlist => ({
  id,
  name: `Playlist ${id}`,
  description: null,
  configVersion: 1,
  items: [],
  createdAt: '',
  updatedAt: '',
});

const location = (id: number, name: string): Location => ({
  id,
  name,
  description: null,
  deviceCount: 1,
  onlineCount: 1,
  createdAt: '',
  updatedAt: '',
});

const group = (id: number, name: string): DeviceGroup => ({
  id,
  name,
  description: null,
  deviceCount: 1,
  onlineCount: 1,
  deviceIds: [],
  createdAt: '',
  updatedAt: '',
});

const layout = (id: number, name: string): Layout => ({
  id,
  name,
  description: null,
  configVersion: 2,
  zones: [],
  deviceCount: 0,
  createdAt: '',
  updatedAt: '',
});

const LOCATIONS = [location(3, 'Térreo'), location(4, 'Loja Centro')];
const GROUPS = [group(10, 'Recepções'), group(11, 'Vitrines')];

/** Um <select> "de mentira": o painel lê o valor escolhido e o devolve ao título. */
const selectEvent = (value: string) => ({ target: { value } }) as unknown as Event;

function setup(queryParams: Record<string, string> = {}) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } } },
    ],
  });
  const config = TestBed.inject(ConfigService);
  config.apiBaseUrl = 'http://api.test';
  config.downloadsUrl = DOWNLOADS;
  const backend = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(DevicesPage);
  return { page: fixture.componentInstance, backend, fixture };
}

/** Responde às cinco consultas que a tela refaz a cada atualização. */
function flushRefresh(backend: HttpTestingController, devices: Device[] = [device()], layouts: Layout[] = []): TestRequest {
  const list = backend.expectOne((r) => r.url === `${API}/devices`);
  list.flush(devices);
  backend.expectOne(`${API}/playlists`).flush([playlist(7), playlist(8)]);
  backend.expectOne(`${API}/locations`).flush(LOCATIONS);
  backend.expectOne(`${API}/groups`).flush(GROUPS);
  backend.expectOne(`${API}/layouts`).flush(layouts);
  return list;
}

function start(page: DevicesPage, backend: HttpTestingController, devices: Device[] = [device()]): TestRequest {
  page.ngOnInit();
  backend.expectOne(`${DOWNLOADS}/releases.json`).flush({ releases: [release('0.3.0')] });
  return flushRefresh(backend, devices);
}

describe('DevicesPage — playlist assignment', () => {
  let page: DevicesPage;
  let backend: HttpTestingController;

  beforeEach(() => {
    ({ page, backend } = setup());
    start(page, backend);
  });

  afterEach(() => backend.verify());

  it('flags a TV running an older Player than the latest published one', () => {
    expect(page.outdated(device({ playerVersion: '0.2.0' }))).toBe(true);
    expect(page.outdated(device({ playerVersion: '0.3.0' }))).toBe(false);
    expect(page.outdated(device({ playerVersion: null }))).toBe(false);
  });

  /** Regressão: a API trata currentPlaylistId ausente como "não mudar", então "nenhuma" precisa de um DELETE próprio. */
  it('removes the playlist with a DELETE when "Nenhuma playlist" is chosen', () => {
    page.assignPlaylist(device(), null);

    const request = backend.expectOne(`${API}/devices/1/playlist`);
    expect(request.request.method).toBe('DELETE');
    request.flush(device({ currentPlaylistId: null }));

    flushRefresh(backend, [device({ currentPlaylistId: null })]);
  });

  it('switches to another playlist with a PUT that sends only the name and the playlist', () => {
    page.assignPlaylist(device(), 8);

    const request = backend.expectOne(`${API}/devices/1`);
    expect(request.request.method).toBe('PUT');
    // local e grupos ficam de fora: ausente significa "não mudar"
    expect(request.request.body).toEqual({ name: 'TV Recepção', currentPlaylistId: 8 });
    request.flush(device({ currentPlaylistId: 8 }));
    flushRefresh(backend, [device({ currentPlaylistId: 8 })]);
  });

  it('asks one TV to check for a Player update and confirms to the user', () => {
    page.checkUpdate(device());

    const request = backend.expectOne(`${API}/devices/1/update-check`);
    expect(request.request.method).toBe('POST');
    request.flush(device());

    expect(page.notice.text()).toContain('TV Recepção');
  });

  it('asks every active TV and says how many were reached', () => {
    page.updateAll();

    const request = backend.expectOne(`${API}/devices/update-check`);
    expect(request.request.method).toBe('POST');
    request.flush({ requested: 3 });

    expect(page.notice.text()).toContain('3 TV(s)');
  });

  it('tells the user when there is no active TV to ask', () => {
    page.updateAll();
    backend.expectOne(`${API}/devices/update-check`).flush({ requested: 0 });

    expect(page.notice.text()).toBe('Nenhuma TV ativa para avisar.');
  });

  it('does nothing when the chosen playlist is already the current one', () => {
    page.assignPlaylist(device(), 7);

    backend.expectNone(`${API}/devices/1`);
    backend.expectNone(`${API}/devices/1/playlist`);
  });

  it('does nothing when "Nenhuma playlist" is chosen for a device that has none', () => {
    page.assignPlaylist(device({ currentPlaylistId: null }), null);

    backend.expectNone(`${API}/devices/1/playlist`);
  });

  it('only changes the status, keeping the rest of the TV as it is', () => {
    page.setStatus(device(), 'disabled');

    const request = backend.expectOne(`${API}/devices/1`);
    expect(request.request.body).toEqual({ name: 'TV Recepção', status: 'disabled' });
    request.flush(device({ status: 'DISABLED', connection: 'DISABLED' }));
    flushRefresh(backend);
  });

  it('shows the group names of each TV', () => {
    expect(page.groupsOf(device({ groupIds: [11, 10, 99] })).map((g) => g.name)).toEqual(['Recepções', 'Vitrines']);
  });
});

describe('DevicesPage — filters', () => {
  let page: DevicesPage;
  let backend: HttpTestingController;

  beforeEach(() => {
    ({ page, backend } = setup());
    start(page, backend);
  });

  afterEach(() => {
    vi.useRealTimers();
    backend.verify();
  });

  function nextList(): TestRequest {
    return backend.expectOne((r) => r.url === `${API}/devices`);
  }

  it('asks the server for the chosen connection state', () => {
    page.setConnection('OFFLINE');

    const request = nextList();
    expect(request.request.params.get('connection')).toBe('OFFLINE');
    request.flush([]);
    backend.expectOne(`${API}/playlists`).flush([]);
    backend.expectOne(`${API}/locations`).flush(LOCATIONS);
    backend.expectOne(`${API}/groups`).flush(GROUPS);
    backend.expectOne(`${API}/layouts`).flush([]);
    expect(page.filtersActive()).toBe(true);
  });

  it('filters by location, including the TVs without a location', () => {
    page.setLocationFilter('4');
    const byId = nextList();
    expect(byId.request.params.get('locationId')).toBe('4');
    byId.flush([]);
    backend.expectOne(`${API}/playlists`).flush([]);
    backend.expectOne(`${API}/locations`).flush(LOCATIONS);
    backend.expectOne(`${API}/groups`).flush(GROUPS);
    backend.expectOne(`${API}/layouts`).flush([]);

    page.setLocationFilter('none');
    const none = nextList();
    expect(none.request.params.get('noLocation')).toBe('true');
    expect(none.request.params.has('locationId')).toBe(false);
    none.flush([]);
    backend.expectOne(`${API}/playlists`).flush([]);
    backend.expectOne(`${API}/locations`).flush(LOCATIONS);
    backend.expectOne(`${API}/groups`).flush(GROUPS);
    backend.expectOne(`${API}/layouts`).flush([]);
  });

  it('filters by group and clears everything with one click', () => {
    page.setGroupFilter('10');
    const request = nextList();
    expect(request.request.params.get('groupId')).toBe('10');
    request.flush([]);
    backend.expectOne(`${API}/playlists`).flush([]);
    backend.expectOne(`${API}/locations`).flush(LOCATIONS);
    backend.expectOne(`${API}/groups`).flush(GROUPS);
    backend.expectOne(`${API}/layouts`).flush([]);

    page.clearFilters();
    const cleared = nextList();
    expect(cleared.request.params.keys()).toEqual([]);
    cleared.flush([device()]);
    backend.expectOne(`${API}/playlists`).flush([]);
    backend.expectOne(`${API}/locations`).flush(LOCATIONS);
    backend.expectOne(`${API}/groups`).flush(GROUPS);
    backend.expectOne(`${API}/layouts`).flush([]);
    expect(page.filtersActive()).toBe(false);
  });

  /** Digitar não pode disparar uma consulta por letra: espera a pessoa parar de digitar. */
  it('waits for the user to stop typing before searching', () => {
    vi.useFakeTimers();

    page.onSearch('r');
    page.onSearch('re');
    page.onSearch('rec');
    backend.expectNone((r) => r.url === `${API}/devices`);

    vi.advanceTimersByTime(300);

    const request = nextList();
    expect(request.request.params.get('q')).toBe('rec');
    request.flush([]);
    backend.expectOne(`${API}/playlists`).flush([]);
    backend.expectOne(`${API}/locations`).flush(LOCATIONS);
    backend.expectOne(`${API}/groups`).flush(GROUPS);
    backend.expectOne(`${API}/layouts`).flush([]);
  });
});

describe('DevicesPage — filters coming from links (dashboard, locations, groups)', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('starts already filtered by the link', () => {
    const { page, backend } = setup({ local: '4', grupo: '10', situacao: 'offline' });

    page.ngOnInit();
    backend.expectOne(`${DOWNLOADS}/releases.json`).flush({ releases: [] });
    const request = backend.expectOne((r) => r.url === `${API}/devices`);

    expect(request.request.params.get('locationId')).toBe('4');
    expect(request.request.params.get('groupId')).toBe('10');
    expect(request.request.params.get('connection')).toBe('OFFLINE');
    request.flush([]);
    backend.expectOne(`${API}/playlists`).flush([]);
    backend.expectOne(`${API}/locations`).flush(LOCATIONS);
    backend.expectOne(`${API}/groups`).flush(GROUPS);
    backend.expectOne(`${API}/layouts`).flush([]);
  });

  it('understands "sem local" and ignores nonsense in the link', () => {
    const { page, backend } = setup({ local: 'none', grupo: 'abc', situacao: 'quebrada' });

    page.ngOnInit();
    backend.expectOne(`${DOWNLOADS}/releases.json`).flush({ releases: [] });
    const request = backend.expectOne((r) => r.url === `${API}/devices`);

    expect(request.request.params.get('noLocation')).toBe('true');
    expect(request.request.params.has('groupId')).toBe(false);
    expect(request.request.params.has('connection')).toBe(false);
    request.flush([]);
    backend.expectOne(`${API}/playlists`).flush([]);
    backend.expectOne(`${API}/locations`).flush(LOCATIONS);
    backend.expectOne(`${API}/groups`).flush(GROUPS);
    backend.expectOne(`${API}/layouts`).flush([]);
  });
});

describe('DevicesPage — selection and bulk actions', () => {
  let page: DevicesPage;
  let backend: HttpTestingController;
  const tvs = [device({ id: 1 }), device({ id: 2, name: 'TV Vitrine' }), device({ id: 3, name: 'TV Estoque' })];

  beforeEach(() => {
    ({ page, backend } = setup());
    start(page, backend, tvs);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    backend.verify();
  });

  function bulkRequest(): TestRequest {
    return backend.expectOne(`${API}/devices/bulk`);
  }

  it('selects and unselects TVs one by one', () => {
    page.toggle(1);
    page.toggle(2);
    expect(page.selectedCount()).toBe(2);
    expect(page.isSelected(1)).toBe(true);

    page.toggle(1);
    expect(page.isSelected(1)).toBe(false);
    expect(page.selectedCount()).toBe(1);
  });

  it('"select all" marks every TV in the list and a second click clears them', () => {
    page.toggleAll();
    expect(page.selectedCount()).toBe(3);
    expect(page.allSelected()).toBe(true);

    page.toggleAll();
    expect(page.selectedCount()).toBe(0);
  });

  it('drops from the selection the TVs that left the list after a refresh', () => {
    page.toggleAll();

    page.setConnection('OFFLINE');
    flushRefresh(backend, [tvs[1]]);

    expect([...page.selected()]).toEqual([2]);
  });

  it('assigns the chosen playlist to the selected TVs and says how many were reached', () => {
    page.toggle(1);
    page.toggle(2);
    const select = { value: '8' };

    page.onBulkPlaylist({ target: select } as unknown as Event);

    const request = bulkRequest();
    expect(request.request.body).toEqual({ action: 'ASSIGN_PLAYLIST', playlistId: 8, deviceIds: [1, 2] });
    expect(select.value).toBe(''); // o seletor volta ao título
    request.flush({ affected: 2 });
    expect(page.notice.text()).toContain('Playlist aplicada a 2 TV(s)');
    flushRefresh(backend, tvs);
  });

  it('removes the playlist from the selected TVs', () => {
    page.toggle(1);

    page.onBulkPlaylist(selectEvent('none'));

    expect(bulkRequest().request.body).toEqual({ action: 'REMOVE_PLAYLIST', deviceIds: [1] });
  });

  it('moves the selected TVs to a location or clears it', () => {
    page.toggle(1);
    page.onBulkLocation(selectEvent('4'));
    const move = bulkRequest();
    expect(move.request.body).toEqual({ action: 'SET_LOCATION', locationId: 4, deviceIds: [1] });
    move.flush({ affected: 1 });
    flushRefresh(backend, tvs);

    page.onBulkLocation(selectEvent('none'));
    expect(bulkRequest().request.body).toEqual({ action: 'CLEAR_LOCATION', deviceIds: [1] });
  });

  it('adds the selected TVs to a group and takes them out again', () => {
    page.toggle(2);

    page.onBulkGroup(selectEvent('10'), true);
    const add = bulkRequest();
    expect(add.request.body).toEqual({ action: 'ADD_TO_GROUP', groupId: 10, deviceIds: [2] });
    add.flush({ affected: 1 });
    flushRefresh(backend, tvs);

    page.onBulkGroup(selectEvent('10'), false);
    expect(bulkRequest().request.body).toEqual({ action: 'REMOVE_FROM_GROUP', groupId: 10, deviceIds: [2] });
  });

  it('ignores a chosen "title" option of the selectors', () => {
    page.toggle(1);

    page.onBulkPlaylist(selectEvent(''));
    page.onBulkLocation(selectEvent(''));
    page.onBulkGroup(selectEvent(''), true);

    backend.expectNone(`${API}/devices/bulk`);
  });

  it('asks for confirmation before disabling and does nothing if the user backs out', () => {
    page.toggleAll();
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(false);

    page.bulkSimple('DISABLE');

    expect(confirmation).toHaveBeenCalledWith(expect.stringContaining('3 TV(s)'));
    backend.expectNone(`${API}/devices/bulk`);
  });

  it('disables after the user confirms', () => {
    page.toggleAll();
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    page.bulkSimple('DISABLE');

    const request = bulkRequest();
    expect(request.request.body).toEqual({ action: 'DISABLE', deviceIds: [1, 2, 3] });
    request.flush({ affected: 2 });
    expect(page.notice.text()).toContain('2 TV(s) desativada(s)');
    expect(page.notice.text()).toContain('1 já estavam no estado pedido');
    flushRefresh(backend, tvs);
  });

  it('enables and asks for updates without a confirmation window', () => {
    page.toggle(1);
    const confirmation = vi.spyOn(window, 'confirm');

    page.bulkSimple('ENABLE');
    bulkRequest().flush({ affected: 1 });
    flushRefresh(backend, tvs);
    page.bulkSimple('CHECK_UPDATE');
    expect(bulkRequest().request.body).toEqual({ action: 'CHECK_UPDATE', deviceIds: [1] });

    expect(confirmation).not.toHaveBeenCalled();
  });

  it('explains when the action reached nobody', () => {
    page.toggle(1);

    page.bulkSimple('ENABLE');
    bulkRequest().flush({ affected: 0 });

    expect(page.notice.text()).toContain('Nenhuma das TVs selecionadas');
    flushRefresh(backend, tvs);
  });

  it('does nothing without a selection', () => {
    page.bulkSimple('CHECK_UPDATE');

    backend.expectNone(`${API}/devices/bulk`);
  });
});

describe('DevicesPage — activating and editing', () => {
  let page: DevicesPage;
  let backend: HttpTestingController;

  beforeEach(() => {
    ({ page, backend } = setup());
    start(page, backend);
  });

  afterEach(() => backend.verify());

  function fillActivation(choice: string, newLocation = '') {
    page.openActivate();
    page.code.set('K7M2QX');
    page.name.set('  TV Nova ');
    page.locationChoice.set(choice);
    page.newLocation.set(newLocation);
  }

  it('activates with an existing location chosen from the list (by id) and the chosen groups', () => {
    fillActivation('4');
    page.toggleModalGroup(10);

    page.submitModal();

    const request = backend.expectOne(`${API}/devices/activate`);
    expect(request.request.body).toEqual({ activationCode: 'K7M2QX', name: 'TV Nova', groupIds: [10], locationId: 4 });
    request.flush(device());
    flushRefresh(backend);
    expect(page.modal()).toBeNull();
  });

  it('activates with a brand new location typed by the user (by name; the server creates it)', () => {
    fillActivation('new', ' Loja Nova ');

    page.submitModal();

    const request = backend.expectOne(`${API}/devices/activate`);
    expect(request.request.body).toEqual({ activationCode: 'K7M2QX', name: 'TV Nova', groupIds: [], location: 'Loja Nova' });
    request.flush(device());
    flushRefresh(backend);
  });

  it('activates without a location when none is chosen', () => {
    fillActivation('');

    page.submitModal();

    const request = backend.expectOne(`${API}/devices/activate`);
    expect(request.request.body).toEqual({ activationCode: 'K7M2QX', name: 'TV Nova', groupIds: [] });
    request.flush(device());
    flushRefresh(backend);
  });

  it('treats "new location" left blank as no location', () => {
    fillActivation('new', '   ');

    page.submitModal();

    const request = backend.expectOne(`${API}/devices/activate`);
    expect(request.request.body).toEqual({ activationCode: 'K7M2QX', name: 'TV Nova', groupIds: [] });
    request.flush(device());
    flushRefresh(backend);
  });

  it('keeps the window open and stops loading when the server refuses', () => {
    fillActivation('');

    page.submitModal();
    backend.expectOne(`${API}/devices/activate`).flush({ error: 'Código de ativação inválido ou expirado' }, { status: 404, statusText: 'Not Found' });

    expect(page.busy()).toBe(false);
    expect(page.modal()).toBe('activate');
  });

  it('opens the edit window with the current location and groups', () => {
    page.openEdit(device({ locationId: 4, groupIds: [11] }));

    expect(page.modal()).toBe('edit');
    expect(page.locationChoice()).toBe('4');
    expect([...page.modalGroups()]).toEqual([11]);
  });

  it('saves a changed location by id and the full list of groups', () => {
    page.openEdit(device({ locationId: 3, groupIds: [10] }));
    page.locationChoice.set('4');
    page.toggleModalGroup(10);
    page.toggleModalGroup(11);

    page.submitModal();

    const request = backend.expectOne(`${API}/devices/1`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ name: 'TV Recepção', groupIds: [11], locationId: 4 });
    request.flush(device());
    flushRefresh(backend);
  });

  /** Ao editar, "sem local" precisa ser dito explicitamente; omitir significaria "não mudar". */
  it('says "no location" explicitly when editing a TV that should have none', () => {
    page.openEdit(device({ locationId: 3 }));
    page.locationChoice.set('');

    page.submitModal();

    const request = backend.expectOne(`${API}/devices/1`);
    expect(request.request.body).toEqual({ name: 'TV Recepção', groupIds: [], location: '' });
    request.flush(device());
    flushRefresh(backend);
  });
});

describe('DevicesPage — editing as an Editor', () => {
  /** O Editor só escolhe o layout: nome, local e grupos seriam recusados (403), então nem são enviados. */
  it('sends only the name and the layout', () => {
    const { page, backend } = setup();
    TestBed.inject(AuthService).me.set(meFixture('EDITOR'));
    page.ngOnInit();
    backend.expectOne(`${DOWNLOADS}/releases.json`).flush({ releases: [release('0.3.0')] });
    flushRefresh(backend, [device({ locationId: 3, groupIds: [10] })], [layout(20, 'Lateral')]);

    page.openEdit(device({ locationId: 3, groupIds: [10] }));
    page.layoutChoice.set('20');
    page.submitModal();

    const request = backend.expectOne(`${API}/devices/1`);
    expect(request.request.body).toEqual({ name: 'TV Recepção', layoutId: 20 });
    request.flush(device());
    flushRefresh(backend);
    backend.verify();
  });
});

describe('DevicesPage — monitoring hints', () => {
  it('shows what the Player says is on screen and links each TV to its history', async () => {
    const { backend, fixture } = setup();
    // desenhando, é o primeiro detectChanges que chama o ngOnInit
    fixture.detectChanges();
    backend.expectOne(`${DOWNLOADS}/releases.json`).flush({ releases: [release('0.3.0')] });
    flushRefresh(backend, [device({ currentMediaName: 'Promoção de Natal' })]);
    fixture.detectChanges();
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Na tela: Promoção de Natal');
    expect(element.querySelector('a[href="/dispositivos/1/historico"]')?.textContent).toContain('Histórico');
    backend.verify();
  });

  it('shows nothing about the screen when the Player has not reported a media yet', async () => {
    const { backend, fixture } = setup();
    fixture.detectChanges();
    backend.expectOne(`${DOWNLOADS}/releases.json`).flush({ releases: [] });
    flushRefresh(backend, [device({ currentMediaName: null })]);
    fixture.detectChanges();
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Na tela');
    backend.verify();
  });
});

describe('DevicesPage — layouts', () => {
  let page: DevicesPage;
  let backend: HttpTestingController;

  const LAYOUTS = [layout(20, 'Lateral'), layout(21, 'Rodapé')];

  beforeEach(() => {
    ({ page, backend } = setup());
    page.ngOnInit();
    backend.expectOne(`${DOWNLOADS}/releases.json`).flush({ releases: [] });
    flushRefresh(backend, [device({ id: 1, layoutId: 20, layoutName: 'Lateral' }), device({ id: 2, name: 'TV 2' })], LAYOUTS);
  });

  afterEach(() => backend.verify());

  it('knows the layouts and which one each TV uses', () => {
    expect(page.layouts().map((l) => l.name)).toEqual(['Lateral', 'Rodapé']);
    expect(page.devices()[0].layoutName).toBe('Lateral');
  });

  it('chooses the layout in the edit window and sends it only when one is picked', () => {
    page.openEdit(device({ id: 2, name: 'TV 2' }));
    expect(page.layoutChoice()).toBe('');
    page.layoutChoice.set('21');

    page.submitModal();

    const request = backend.expectOne(`${API}/devices/2`);
    expect(request.request.body.layoutId).toBe(21);
    request.flush(device({ id: 2 }));
    flushRefresh(backend, [], LAYOUTS);
  });

  it('shows the current layout when editing and does not resend it when nothing changed', () => {
    page.openEdit(device({ id: 1, layoutId: 20, layoutName: 'Lateral' }));
    expect(page.layoutChoice()).toBe('20');

    page.submitModal();

    const request = backend.expectOne(`${API}/devices/1`);
    expect(request.request.body.layoutId).toBe(20);
    request.flush(device());
    flushRefresh(backend, [], LAYOUTS);
  });

  /** layoutId ausente no PUT significa "não mudar": voltar à tela cheia precisa do DELETE próprio. */
  it('goes back to full screen with a DELETE after the save when "Tela cheia" is chosen', () => {
    page.openEdit(device({ id: 1, layoutId: 20, layoutName: 'Lateral' }));
    page.layoutChoice.set('');

    page.submitModal();

    const put = backend.expectOne(`${API}/devices/1`);
    expect('layoutId' in put.request.body).toBe(false);
    put.flush(device());
    const remove = backend.expectOne(`${API}/devices/1/layout`);
    expect(remove.request.method).toBe('DELETE');
    remove.flush(device());
    expect(page.modal()).toBeNull();
    flushRefresh(backend, [], LAYOUTS);
  });

  it('does not call the DELETE when the TV never had a layout', () => {
    page.openEdit(device({ id: 2, name: 'TV 2' }));

    page.submitModal();

    backend.expectOne(`${API}/devices/2`).flush(device({ id: 2 }));
    backend.expectNone(`${API}/devices/2/layout`);
    flushRefresh(backend, [], LAYOUTS);
  });

  it('applies a layout, or full screen, to the selected TVs', () => {
    page.toggle(1);
    page.toggle(2);

    page.onBulkLayout(selectEvent('21'));
    const assign = backend.expectOne(`${API}/devices/bulk`);
    expect(assign.request.body).toEqual({ action: 'ASSIGN_LAYOUT', layoutId: 21, deviceIds: [1, 2] });
    assign.flush({ affected: 2 });
    expect(page.notice.text()).toContain('Layout aplicado a 2 TV(s)');
    flushRefresh(backend, [device({ id: 1 }), device({ id: 2, name: 'TV 2' })], LAYOUTS);

    page.onBulkLayout(selectEvent('none'));
    const remove = backend.expectOne(`${API}/devices/bulk`);
    expect(remove.request.body).toEqual({ action: 'REMOVE_LAYOUT', deviceIds: [1, 2] });
    remove.flush({ affected: 2 });
    expect(page.notice.text()).toContain('voltaram para a tela cheia');
    flushRefresh(backend, [device({ id: 1 }), device({ id: 2, name: 'TV 2' })], LAYOUTS);

    page.onBulkLayout(selectEvent(''));
    backend.expectNone(`${API}/devices/bulk`);
  });

  it('says "sincronizando" when the TV has not caught up with the version the server wants', () => {
    expect(page.syncing(device({ configVersion: 3, desiredConfigVersion: 3 }))).toBe(false);
    expect(page.syncing(device({ configVersion: 3, desiredConfigVersion: 4 }))).toBe(true);
    // uma versão combinada de layout pode ser "menor" que a anterior: o que vale é ser diferente
    expect(page.syncing(device({ configVersion: 900, desiredConfigVersion: 5 }))).toBe(true);
    expect(page.syncing(device({ status: 'DISABLED', configVersion: 1, desiredConfigVersion: 2 }))).toBe(false);
  });

  it('falls back to comparing with the playlist version when the server does not send the wanted version', () => {
    // playlist 7 está na versão 1 (ver flushRefresh)
    expect(page.syncing(device({ configVersion: 0, desiredConfigVersion: undefined }))).toBe(true);
    expect(page.syncing(device({ configVersion: 1, desiredConfigVersion: undefined }))).toBe(false);
    expect(page.syncing(device({ currentPlaylistId: null, configVersion: 0, desiredConfigVersion: undefined }))).toBe(false);
  });
});

describe('DevicesPage — layout display', () => {
  it('renders the layout chip and the bulk selector once a layout exists', async () => {
    const ctx = setup();
    const LAYOUTS = [layout(20, 'Lateral')];
    ctx.fixture.detectChanges();
    ctx.backend.expectOne(`${DOWNLOADS}/releases.json`).flush({ releases: [] });
    flushRefresh(ctx.backend, [device({ id: 1, layoutId: 20, layoutName: 'Lateral' })], LAYOUTS);
    ctx.page.toggle(1);
    ctx.fixture.detectChanges();
    await ctx.fixture.whenStable();

    const element = ctx.fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.tv-tags')?.textContent).toContain('Lateral');
    expect(element.querySelector('select[aria-label="Layout das selecionadas"]')).not.toBeNull();
    ctx.backend.verify();
  });
});

describe('DevicesPage — screen orientation', () => {
  it('sends the orientation only when the administrator changed it, and sends it alone with the rest of the form', () => {
    const { page, backend } = setup();
    start(page, backend);
    page.openEdit(device({ id: 3, name: 'Totem' }));
    expect(page.orientationChoice()).toBe('AUTO');

    page.submitModal();
    const unchanged = backend.expectOne(`${API}/devices/3`);
    expect('orientation' in unchanged.request.body).toBe(false);
    unchanged.flush(device({ id: 3 }));
    flushRefresh(backend);

    page.openEdit(device({ id: 3, name: 'Totem' }));
    page.orientationChoice.set('PORTRAIT_TOP_RIGHT');
    page.submitModal();
    const changed = backend.expectOne(`${API}/devices/3`);
    expect(changed.request.body.orientation).toBe('PORTRAIT_TOP_RIGHT');
    changed.flush(device({ id: 3 }));
    flushRefresh(backend);
    backend.verify();
  });

  it('opens the edit window with the current orientation and draws the TV turned as installed', () => {
    const { page, backend } = setup();
    start(page, backend);

    page.openEdit(device({ id: 3, orientation: 'PORTRAIT_TOP_LEFT' }));

    expect(page.orientationChoice()).toBe('PORTRAIT_TOP_LEFT');
    expect(page.orientationTurn()).toBe(270);
    expect(page.orientationHint()).toContain('anti-horário');
    page.orientationChoice.set('LANDSCAPE_FLIPPED');
    expect(page.orientationTurn()).toBe(180);
    backend.verify();
  });

  it('applies an orientation to the selected TVs and says how many were reached', () => {
    const { page, backend } = setup();
    start(page, backend, [device({ id: 1 }), device({ id: 2, name: 'TV 2' })]);
    page.toggle(1);
    page.toggle(2);

    page.onBulkOrientation(selectEvent('PORTRAIT_TOP_RIGHT'));

    const request = backend.expectOne(`${API}/devices/bulk`);
    expect(request.request.body).toEqual({ action: 'SET_ORIENTATION', orientation: 'PORTRAIT_TOP_RIGHT', deviceIds: [1, 2] });
    request.flush({ affected: 2 });
    expect(page.notice.text()).toContain('Orientação aplicada a 2 TV(s)');
    flushRefresh(backend, [device({ id: 1 }), device({ id: 2, name: 'TV 2' })]);

    page.onBulkOrientation(selectEvent(''));
    backend.expectNone(`${API}/devices/bulk`);
    backend.verify();
  });

  it('marks the TVs that are not in the normal position and says nothing about the automatic ones', async () => {
    const ctx = setup();
    ctx.fixture.detectChanges();
    ctx.backend.expectOne(`${DOWNLOADS}/releases.json`).flush({ releases: [] });
    flushRefresh(ctx.backend, [
      device({ id: 1, name: 'Totem', orientation: 'PORTRAIT_TOP_RIGHT' }),
      device({ id: 2, name: 'Teto', orientation: 'LANDSCAPE_FLIPPED' }),
      device({ id: 3, name: 'Normal', orientation: 'AUTO' }),
      device({ id: 4, name: 'Antigo' }),
    ]);
    await ctx.fixture.whenStable();
    ctx.fixture.detectChanges();

    const rows = Array.from((ctx.fixture.nativeElement as HTMLElement).querySelectorAll('.tv-tags')).map((tags) => tags.textContent?.trim());
    expect(rows.filter((text) => text?.includes('Vertical'))).toHaveLength(1);
    expect(rows.filter((text) => text?.includes('De cabeça para baixo'))).toHaveLength(1);
    ctx.backend.verify();
  });
});
