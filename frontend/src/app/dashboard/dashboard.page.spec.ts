import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { ConfigService } from '../core/config.service';
import { Summary } from '../core/models';
import { meFixture } from '../testing/fixtures';
import { DashboardPage } from './dashboard.page';

const API = 'http://api.test/api/v1';

const SUMMARY: Summary = {
  devices: { total: 6, online: 2, offline: 1, never: 1, pending: 1, disabled: 1, withContent: 2 },
  locations: 2,
  groups: 1,
  media: 4,
  playlists: 2,
  campaigns: { total: 3, activeNow: 1 },
  openAlerts: 2,
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
  describe('checklist de primeiros passos', () => {
    const render = async (summary: Summary, role: 'ADMIN' | 'VIEWER' = 'ADMIN') => {
      TestBed.inject(AuthService).me.set(meFixture(role));
      const fixture = TestBed.createComponent(DashboardPage);
      fixture.detectChanges();
      backend.expectOne(`${API}/summary`).flush(summary);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      const root = fixture.nativeElement as HTMLElement;
      return { page: fixture.componentInstance, root, text: (root.textContent ?? '').replace(/\s+/g, ' ') };
    };

    it('shows what is done, what is left and a shortcut to the next step, while a step is missing', async () => {
      // TV ativada e mídia enviada; falta a playlist e escolher o que a TV exibe
      const { page, root, text } = await render({ ...SUMMARY, playlists: 0, devices: { ...SUMMARY.devices, withContent: 0 } });

      expect(page.pendingCount()).toBe(2);
      expect(page.nextStep()?.id).toBe('playlist');
      expect(text).toContain('Primeiros passos');
      expect(text).toContain('Faltam 2 de 4 passos');
      expect(root.querySelectorAll('.steps li.done')).toHaveLength(2);
      expect(root.querySelector('.steps li.next a.primary')?.getAttribute('href')).toBe('/playlists/nova');
      // a rede continua aparecendo embaixo (já há TVs)
      expect(text).toContain('Saúde da rede');
    });

    it('takes the place of the empty network panel when there is no TV yet', async () => {
      const { page, text } = await render({ ...SUMMARY, devices: { total: 0, online: 0, offline: 0, never: 0, pending: 0, disabled: 0, withContent: 0 }, media: 0, playlists: 0 });

      expect(page.pendingCount()).toBe(4);
      expect(text).toContain('Vamos colocar a sua primeira tela no ar');
      expect(text).not.toContain('Saúde da rede');
    });

    it('counts a TV that is still waiting for its code as not activated yet', async () => {
      const { page } = await render({ ...SUMMARY, devices: { total: 1, online: 0, offline: 0, never: 0, pending: 1, disabled: 0, withContent: 0 } });

      expect(page.steps()[0].done).toBe(false);
    });

    it('disappears when the four steps are done', async () => {
      const { page, root } = await render(SUMMARY);

      expect(page.pendingCount()).toBe(0);
      expect(root.querySelector('[data-testid="checklist"]')).toBeNull();
    });

    it('is not shown to a Viewer, who cannot set anything up', async () => {
      const { root } = await render({ ...SUMMARY, media: 0, playlists: 0 }, 'VIEWER');

      expect(root.querySelector('[data-testid="checklist"]')).toBeNull();
    });
  });
});
