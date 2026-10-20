import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ConfigService } from '../core/config.service';
import { errorInterceptor } from '../core/error.interceptor';
import { ErrorService } from '../core/error.service';
import { ReleaseInfo } from '../core/models';
import { DownloadsPage } from './downloads.page';

const DOWNLOADS = 'https://downloads.test';

const release = (versionCode: number, versionName: string): ReleaseInfo => ({
  versionCode,
  versionName,
  apk: `player-${versionName}.apk`,
  sha256: 'a'.repeat(64),
  size: 6_500_000,
  notes: `Notas ${versionName}`,
  publishedAt: '2026-10-20T12:00:00Z',
});

describe('DownloadsPage', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    TestBed.inject(ConfigService).downloadsUrl = DOWNLOADS;
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  function open(): DownloadsPage {
    const page = TestBed.createComponent(DownloadsPage).componentInstance;
    page.ngOnInit();
    return page;
  }

  it('shows the newest release first and links the APK from the downloads site', () => {
    const page = open();
    backend.expectOne(`${DOWNLOADS}/releases.json`).flush({ releases: [release(4, '0.4.0'), release(3, '0.3.0')] });

    expect(page.latest()?.versionName).toBe('0.4.0');
    expect(page.releases().length).toBe(2);
    expect(page.apkUrl(page.latest()!)).toBe(`${DOWNLOADS}/player-0.4.0.apk`);
  });

  it('points to both PDF manuals and the short link on the downloads site', () => {
    const page = open();
    backend.expectOne(`${DOWNLOADS}/releases.json`).flush({ releases: [] });

    expect(page.manualInstallUrl).toBe(`${DOWNLOADS}/manual-instalacao-tv.pdf`);
    expect(page.manualPanelUrl).toBe(`${DOWNLOADS}/manual-painel.pdf`);
    expect(page.shortLink).toBe('downloads.test/d');
  });

  it('copes with an empty list (nothing published yet)', () => {
    const page = open();
    backend.expectOne(`${DOWNLOADS}/releases.json`).flush({ releases: [] });

    expect(page.latest()).toBeNull();
    expect(page.loaded()).toBe(true);
  });

  /** Regressão: se o site de downloads estiver fora, o usuário vê um aviso (fechar manualmente), não uma tela muda. */
  it('reports a failure through the error banner and stops loading', () => {
    const errors = TestBed.inject(ErrorService);
    const page = open();
    backend.expectOne(`${DOWNLOADS}/releases.json`).flush('x', { status: 503, statusText: 'Service Unavailable' });

    expect(page.loaded()).toBe(true);
    expect(page.latest()).toBeNull();
    expect(errors.errors().length).toBe(1);
  });
});
