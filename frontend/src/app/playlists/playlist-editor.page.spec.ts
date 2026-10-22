import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { ConfigService } from '../core/config.service';
import { Playlist, PlaylistItem } from '../core/models';
import { PlaylistEditorPage } from './playlist-editor.page';

const API = 'http://api.test/api/v1';

const item = (mediaId: number, extra: Partial<PlaylistItem> = {}): PlaylistItem => ({
  mediaId,
  mediaName: `Mídia ${mediaId}`,
  mediaType: 'IMAGE',
  position: mediaId - 1,
  durationSeconds: 10,
  ...extra,
});

const playlist = (items: PlaylistItem[]): Playlist => ({
  id: 7,
  name: 'Loja',
  description: null,
  configVersion: 3,
  items,
  createdAt: '',
  updatedAt: '',
});

function setup(items: PlaylistItem[]) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '7' }) } } },
    ],
  });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  const backend = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(PlaylistEditorPage);
  fixture.detectChanges();
  backend.expectOne(`${API}/media`).flush([]);
  backend.expectOne(`${API}/playlists/7`).flush(playlist(items));
  fixture.detectChanges();
  return { fixture, page: fixture.componentInstance, backend, element: fixture.nativeElement as HTMLElement };
}

/** As miniaturas pedem o endereço de cada mídia: respondidas aqui para o resto do teste conferir só o que importa. */
function settle(backend: HttpTestingController): void {
  backend.match((request) => /\/media\/\d+\/url$/.test(request.url)).forEach((request) => request.flush({ url: '', requiresAuth: false }));
  backend.verify();
}

describe('PlaylistEditorPage — validade por item', () => {
  it('shows "Sempre" for items without rules and keeps the rule editor closed', async () => {
    const { fixture, element, backend } = setup([item(1), item(2)]);
    await fixture.whenStable();

    const buttons = Array.from(element.querySelectorAll('.when-btn'));
    expect(buttons).toHaveLength(2);
    expect(buttons[0].textContent).toContain('Sempre');
    expect(element.querySelector('[data-testid="rule-editor"]')).toBeNull();
    expect(element.querySelector('[data-testid="nothing-today"]')).toBeNull();
    settle(backend);
  });

  it('opens the editor of one item, sets days and dates, and publishes them', () => {
    const { page, fixture, element, backend } = setup([item(1), item(2)]);

    page.toggleRule(0);
    fixture.detectChanges();
    expect(element.querySelector('[data-testid="rule-editor"]')).not.toBeNull();
    expect(element.querySelectorAll('.day')).toHaveLength(7);

    page.changeRule(0, { validFrom: '2026-11-01', validUntil: '2099-12-31' });
    page.flipDay(0, 2);
    fixture.detectChanges();
    expect(page.items()[0].days).toEqual([1, 3, 4, 5, 6, 7]);
    expect(element.querySelector('.when-btn.on')?.textContent).toContain('de ');

    page.save();
    const request = backend.expectOne(`${API}/playlists/7`);
    expect(request.request.method).toBe('PUT');
    expect((request.request.body as { items: object[] }).items[0]).toEqual({
      mediaId: 1,
      durationSeconds: 10,
      validFrom: '2026-11-01',
      validUntil: '2099-12-31',
      days: [1, 3, 4, 5, 6, 7],
    });
    expect((request.request.body as { items: object[] }).items[1]).toEqual({ mediaId: 2, durationSeconds: 10 });
    request.flush(playlist(page.items()));
    settle(backend);
  });

  it('blocks publishing while a start day is after the end day and says why', () => {
    const { page, fixture, element, backend } = setup([item(1)]);
    page.toggleRule(0);
    page.changeRule(0, { validFrom: '2099-12-31', validUntil: '2099-12-01' });
    fixture.detectChanges();

    expect(page.canSave()).toBe(false);
    expect(element.querySelector('.problem')?.textContent).toContain('início');

    page.changeRule(0, { validFrom: '2099-12-01' });
    expect(page.canSave()).toBe(true);
    settle(backend);
  });

  it('marks an item that is not valid today and warns when nothing is valid today', () => {
    const { fixture, element, backend } = setup([item(1, { validUntil: '2020-01-01' }), item(2, { validFrom: '2099-01-01' })]);

    expect(element.querySelectorAll('app-pill').length).toBeGreaterThanOrEqual(2);
    expect(element.textContent).toContain('Fora da validade hoje');
    expect(element.querySelector('[data-testid="nothing-today"]')).not.toBeNull();
    fixture.destroy();
    settle(backend);
  });

  it('does not warn when at least one item is valid today', () => {
    const { element, backend } = setup([item(1, { validUntil: '2020-01-01' }), item(2)]);

    expect(element.querySelector('[data-testid="nothing-today"]')).toBeNull();
    settle(backend);
  });

  it('goes back to "always" with one click', () => {
    const { page, backend } = setup([item(1, { validFrom: '2026-11-01', days: [2] })]);

    page.clearRule(0);

    expect(page.items()[0].validFrom).toBeNull();
    expect(page.items()[0].days).toBeNull();
    settle(backend);
  });
});
