import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ConfigService } from '../core/config.service';
import { Summary } from '../core/models';
import { DashboardPage } from './dashboard.page';

const API = 'http://api.test/api/v1';

const SUMMARY: Summary = {
  devices: { total: 6, online: 2, offline: 1, never: 1, pending: 1, disabled: 1 },
  locations: 2,
  groups: 1,
  media: 4,
  playlists: 2,
  byLocation: [
    { id: 1, name: 'Centro', total: 3, online: 2 },
    { id: null, name: 'Sem local', total: 3, online: 0 },
  ],
  attention: [{ id: 5, name: 'TV Vitrine', location: 'Centro', connection: 'OFFLINE', lastSeenAt: null }],
};

describe('DashboardPage', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('shows the numbers calculated by the server', () => {
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.componentInstance.ngOnInit();
    backend.expectOne(`${API}/summary`).flush(SUMMARY);

    expect(fixture.componentInstance.summary().devices.online).toBe(2);
    expect(fixture.componentInstance.summary().byLocation).toHaveLength(2);
    expect(fixture.componentInstance.summary().attention[0].name).toBe('TV Vitrine');
  });

  it('starts with zeros while the first answer has not arrived', () => {
    const fixture = TestBed.createComponent(DashboardPage);

    expect(fixture.componentInstance.summary().devices.total).toBe(0);
    expect(fixture.componentInstance.summary().byLocation).toEqual([]);
  });

  it('renders the cards, the per-location table and the attention list', async () => {
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();
    backend.expectOne(`${API}/summary`).flush(SUMMARY);
    fixture.detectChanges();
    await fixture.whenStable();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Centro');
    expect(text).toContain('Sem local');
    expect(text).toContain('2 de 3');
    expect(text).toContain('TV Vitrine');
    expect(text).toContain('Offline');
  });

  it('keeps the page alive when the server fails (the error banner explains it)', () => {
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.componentInstance.ngOnInit();

    backend.expectOne(`${API}/summary`).flush('x', { status: 503, statusText: 'Service Unavailable' });

    expect(fixture.componentInstance.summary().devices.total).toBe(0);
  });
});
