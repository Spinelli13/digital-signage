import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ConfigService } from '../core/config.service';
import { Device, Playlist, ReleaseInfo } from '../core/models';
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
  status: 'ACTIVE',
  online: true,
  lastSeenAt: '2026-10-20T10:00:00Z',
  playerVersion: '0.3.0',
  currentPlaylistId: 7,
  configVersion: 1,
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

describe('DevicesPage — playlist assignment', () => {
  let page: DevicesPage;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
    backend = TestBed.inject(HttpTestingController);

    TestBed.inject(ConfigService).downloadsUrl = DOWNLOADS;

    page = TestBed.createComponent(DevicesPage).componentInstance;
    page.ngOnInit();
    backend.expectOne(`${DOWNLOADS}/releases.json`).flush({ releases: [release('0.3.0')] });
    backend.expectOne(`${API}/devices`).flush([device()]);
    backend.expectOne(`${API}/playlists`).flush([playlist(7), playlist(8)]);
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

    // depois de remover, a lista é recarregada
    backend.expectOne(`${API}/devices`).flush([device({ currentPlaylistId: null })]);
    backend.expectOne(`${API}/playlists`).flush([playlist(7), playlist(8)]);
  });

  it('switches to another playlist with a PUT that keeps name and location', () => {
    page.assignPlaylist(device(), 8);

    const request = backend.expectOne(`${API}/devices/1`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ name: 'TV Recepção', location: 'Térreo', currentPlaylistId: 8 });
    request.flush(device({ currentPlaylistId: 8 }));
    backend.expectOne(`${API}/devices`).flush([device({ currentPlaylistId: 8 })]);
    backend.expectOne(`${API}/playlists`).flush([playlist(7), playlist(8)]);
  });

  it('does nothing when the chosen playlist is already the current one', () => {
    page.assignPlaylist(device(), 7);

    backend.expectNone(`${API}/devices/1`);
    backend.expectNone(`${API}/devices/1/playlist`);
  });

  it('asks one TV to check for a Player update and confirms to the user', () => {
    page.checkUpdate(device());

    const request = backend.expectOne(`${API}/devices/1/update-check`);
    expect(request.request.method).toBe('POST');
    request.flush(device());

    expect(page.notice()).toContain('TV Recepção');
  });

  it('asks every active TV and says how many were reached', () => {
    page.updateAll();

    const request = backend.expectOne(`${API}/devices/update-check`);
    expect(request.request.method).toBe('POST');
    request.flush({ requested: 3 });

    expect(page.notice()).toContain('3 TV(s)');
  });

  it('tells the user when there is no active TV to ask', () => {
    page.updateAll();
    backend.expectOne(`${API}/devices/update-check`).flush({ requested: 0 });

    expect(page.notice()).toBe('Nenhuma TV ativa para avisar.');
  });

  it('does nothing when "Nenhuma playlist" is chosen for a device that has none', () => {
    page.assignPlaylist(device({ currentPlaylistId: null }), null);

    backend.expectNone(`${API}/devices/1/playlist`);
  });
});
