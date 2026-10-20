import { Type } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AlertsPage } from './alerts/alerts.page';
import { CampaignsPage } from './campaigns/campaigns.page';
import { AuthService } from './core/auth.service';
import { ConfigService } from './core/config.service';
import { Role } from './core/models';
import { DevicesPage } from './devices/devices.page';
import { GroupsPage } from './groups/groups.page';
import { LayoutsPage } from './layouts/layouts.page';
import { LocationsPage } from './locations/locations.page';
import { MediaPage } from './media/media.page';
import { PlaylistsPage } from './playlists/playlists.page';
import { meFixture } from './testing/fixtures';

/**
 * O servidor é quem decide (403), mas o painel não deve oferecer o botão que vai falhar: cada tela mostra só o
 * que o papel do usuário pode fazer.
 */
async function render<T>(page: Type<T>, role: Role, responses: Record<string, object> = {}): Promise<string> {
  localStorage.setItem('signage.token', 'jwt');
  TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  TestBed.inject(AuthService).me.set(meFixture(role));
  const backend = TestBed.inject(HttpTestingController);

  const fixture = TestBed.createComponent(page);
  fixture.detectChanges();
  for (const request of backend.match(() => true)) {
    const key = Object.keys(responses).find((part) => request.request.url.includes(part));
    request.flush(key ? responses[key] : []);
  }
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}

const device = {
  id: 1,
  name: 'TV Recepção',
  status: 'ACTIVE',
  location: null,
  locationId: null,
  groupIds: [],
  currentPlaylistId: null,
  currentPlaylistName: null,
  lastSeenAt: null,
  playerVersion: null,
  connection: 'NEVER',
  layoutId: null,
  layoutName: null,
  campaignName: null,
  currentMediaName: null,
  activationCode: null,
};

const row = (id: number, name: string) => ({ id, name, description: null, deviceCount: 0, onlineCount: 0, items: [], enabled: true });

describe('role gating of the existing pages', () => {
  afterEach(() => {
    localStorage.clear();
    TestBed.inject(HttpTestingController).verify();
  });

  describe('Dispositivos', () => {
    it('gives the administrator every action', async () => {
      const text = await render(DevicesPage, 'ADMIN', { '/devices': [device] });

      expect(text).toContain('+ Adicionar dispositivo');
      expect(text).toContain('Atualizar todas as TVs');
      expect(text).toContain('Editar');
      expect(text).toContain('Desativar');
      expect(text).toContain('Excluir');
    });

    it('lets the editor edit (layout) but not add, disable or delete TVs', async () => {
      const text = await render(DevicesPage, 'EDITOR', { '/devices': [device] });

      expect(text).not.toContain('+ Adicionar dispositivo');
      expect(text).not.toContain('Atualizar todas as TVs');
      expect(text).toContain('Editar');
      expect(text).not.toContain('Desativar');
      expect(text).not.toContain('Excluir');
    });

    it('lets the viewer only look', async () => {
      const text = await render(DevicesPage, 'VIEWER', { '/devices': [device] });

      expect(text).toContain('TV Recepção');
      expect(text).toContain('Histórico');
      expect(text).not.toContain('Editar');
      expect(text).not.toContain('Adicionar dispositivo');
      expect(text).not.toContain('Excluir');
    });
  });

  describe('Biblioteca', () => {
    it('lets editors upload but not viewers', async () => {
      expect(await render(MediaPage, 'EDITOR')).toContain('Enviar mídia');
      TestBed.resetTestingModule();
      expect(await render(MediaPage, 'VIEWER')).not.toContain('Enviar mídia');
    });
  });

  describe('Playlists', () => {
    it('hides creating and deleting from a viewer, who can only open a playlist', async () => {
      const text = await render(PlaylistsPage, 'VIEWER', { '/playlists': [row(1, 'Manhã')] });

      expect(text).toContain('Manhã');
      expect(text).not.toContain('+ Nova playlist');
      expect(text).not.toContain('Excluir');
      expect(text).toContain('Ver');
    });

    it('offers editing to an editor', async () => {
      const text = await render(PlaylistsPage, 'EDITOR', { '/playlists': [row(1, 'Manhã')] });

      expect(text).toContain('+ Nova playlist');
      expect(text).toContain('Excluir');
    });
  });

  describe('Campanhas e layouts', () => {
    it('hides the campaign button from a viewer and shows it to an editor', async () => {
      expect(await render(CampaignsPage, 'VIEWER')).not.toContain('+ Nova campanha');
      TestBed.resetTestingModule();
      expect(await render(CampaignsPage, 'EDITOR')).toContain('+ Nova campanha');
    });

    it('hides the layout button from a viewer and shows it to an editor', async () => {
      expect(await render(LayoutsPage, 'VIEWER')).not.toContain('+ Novo layout');
      TestBed.resetTestingModule();
      expect(await render(LayoutsPage, 'EDITOR')).toContain('+ Novo layout');
    });
  });

  describe('Locais e grupos (administrador)', () => {
    it('keeps editors out of locations', async () => {
      const text = await render(LocationsPage, 'EDITOR', { '/locations': [row(1, 'Loja Centro')] });

      expect(text).toContain('Loja Centro');
      expect(text).not.toContain('+ Novo local');
      expect(text).not.toContain('Excluir');
    });

    it('lets the administrator manage locations', async () => {
      const text = await render(LocationsPage, 'ADMIN', { '/locations': [row(1, 'Loja Centro')] });

      expect(text).toContain('+ Novo local');
      expect(text).toContain('Excluir');
    });

    it('keeps editors out of groups', async () => {
      const text = await render(GroupsPage, 'EDITOR', { '/groups': [row(1, 'Lojas')] });

      expect(text).toContain('Lojas');
      expect(text).not.toContain('+ Novo grupo');
      expect(text).not.toContain('Atualizar app');
    });
  });

  describe('Alertas', () => {
    const settings = {
      offlineAfterMinutes: 5,
      neverConnectedAfterHours: 24,
      errorBurstCount: 5,
      eventsRetentionDays: 90,
      webhookConfigured: false,
      webhookHint: null,
      notifyOnResolve: false,
    };

    it('shows the alert rules only to the administrator', async () => {
      expect(await render(AlertsPage, 'ADMIN', { '/alerts/settings': settings })).toContain('Quando avisar');
      TestBed.resetTestingModule();
      expect(await render(AlertsPage, 'EDITOR', { '/alerts/settings': settings })).not.toContain('Quando avisar');
    });
  });
});
