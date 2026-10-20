import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ConfigService } from '../core/config.service';
import { Device, DeviceGroup, Playlist } from '../core/models';
import { GroupsPage } from './groups.page';

const API = 'http://api.test/api/v1';

const group = (id: number, name: string, deviceIds: number[] = []): DeviceGroup => ({
  id,
  name,
  description: null,
  deviceCount: deviceIds.length,
  onlineCount: 0,
  deviceIds,
  createdAt: '',
  updatedAt: '',
});

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

const playlist = (id: number, name: string): Playlist => ({
  id,
  name,
  description: null,
  configVersion: 1,
  items: [],
  createdAt: '',
  updatedAt: '',
});

const selectEvent = (value: string) => ({ target: { value } }) as unknown as Event;

describe('GroupsPage', () => {
  let page: GroupsPage;
  let backend: HttpTestingController;

  function load(groups: DeviceGroup[]) {
    backend.expectOne(`${API}/groups`).flush(groups);
    backend.expectOne(`${API}/devices`).flush([device(1, 'TV A'), device(2, 'TV B'), device(3, 'TV C')]);
    backend.expectOne(`${API}/playlists`).flush([playlist(7, 'Promo'), playlist(8, 'Cardápio')]);
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
    backend = TestBed.inject(HttpTestingController);
    page = TestBed.createComponent(GroupsPage).componentInstance;
    page.ngOnInit();
    load([group(10, 'Recepções', [1, 2])]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    backend.verify();
  });

  it('loads groups, TVs and playlists', () => {
    expect(page.groups().map((g) => g.name)).toEqual(['Recepções']);
    expect(page.devices().length).toBe(3);
    expect(page.playlists().length).toBe(2);
  });

  it('creates a group with the chosen TVs', () => {
    page.openNew();
    page.name.set(' Vitrines ');
    page.toggleMember(3);
    page.toggleMember(1);

    page.submit();

    const request = backend.expectOne(`${API}/groups`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Vitrines', description: null, deviceIds: [3, 1] });
    request.flush(group(11, 'Vitrines', [3, 1]));
    expect(page.notice.text()).toBe('Grupo "Vitrines" criado.');
    load([]);
  });

  it('opens an existing group with its members already ticked and saves the changed list', () => {
    page.openEdit(page.groups()[0]);
    expect([...page.members()]).toEqual([1, 2]);
    page.toggleMember(2);
    page.toggleMember(3);

    page.submit();

    const request = backend.expectOne(`${API}/groups/10`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ name: 'Recepções', description: null, deviceIds: [1, 3] });
    request.flush(group(10, 'Recepções', [1, 3]));
    load([]);
  });

  it('applies a playlist to every TV of the group after the user confirms', () => {
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const select = { value: '8' };

    page.applyPlaylist(page.groups()[0], { target: select } as unknown as Event);

    expect(confirmation).toHaveBeenCalledWith(expect.stringContaining('"Cardápio"'));
    const request = backend.expectOne(`${API}/devices/bulk`);
    expect(request.request.body).toEqual({ action: 'ASSIGN_PLAYLIST', targetGroupId: 10, playlistId: 8 });
    expect(select.value).toBe('');
    request.flush({ affected: 2 });
    expect(page.notice.text()).toContain('Playlist aplicada a 2 TV(s) do grupo "Recepções"');
  });

  it('removes the playlist of the whole group', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    page.applyPlaylist(page.groups()[0], selectEvent('none'));

    const request = backend.expectOne(`${API}/devices/bulk`);
    expect(request.request.body).toEqual({ action: 'REMOVE_PLAYLIST', targetGroupId: 10 });
    request.flush({ affected: 2 });
  });

  it('does nothing when the user backs out of the confirmation or picks the title option', () => {
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(false);

    page.applyPlaylist(page.groups()[0], selectEvent('7'));
    page.applyPlaylist(page.groups()[0], selectEvent(''));

    expect(confirmation).toHaveBeenCalledTimes(1);
    backend.expectNone(`${API}/devices/bulk`);
  });

  it('asks the TVs of the group to check for Player updates', () => {
    page.checkUpdate(page.groups()[0]);

    const request = backend.expectOne(`${API}/devices/bulk`);
    expect(request.request.body).toEqual({ action: 'CHECK_UPDATE', targetGroupId: 10 });
    request.flush({ affected: 2 });
    expect(page.notice.text()).toContain('2 TV(s) do grupo "Recepções"');
  });

  it('says so when no active TV could be asked', () => {
    page.checkUpdate(page.groups()[0]);
    backend.expectOne(`${API}/devices/bulk`).flush({ affected: 0 });

    expect(page.notice.text()).toBe('Nenhuma TV ativa neste grupo para avisar.');
  });

  /** Apagar o grupo só desfaz o vínculo: o aviso precisa dizer isso. */
  it('reassures that the TVs are kept when deleting a group', () => {
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(true);

    page.remove(page.groups()[0]);

    expect(confirmation).toHaveBeenCalledWith(expect.stringContaining('As TVs não são apagadas'));
    const request = backend.expectOne(`${API}/groups/10`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    load([]);
  });
});
