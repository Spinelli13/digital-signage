import { HttpClient, HttpContext, HttpEvent, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { ConfigService } from './config.service';
import { SILENT_ERRORS } from './http-errors';
import { filtersToParams } from './device-filters';
import {
  ActivateRequest,
  Alert,
  BulkRequest,
  Campaign,
  CampaignPreview,
  CampaignRequest,
  Device,
  DeviceEvent,
  DeviceEventQuery,
  DeviceFilters,
  DeviceGroup,
  DeviceGroupRequest,
  DeviceUpdateRequest,
  EventLevel,
  EventRow,
  EventType,
  InventoryRow,
  Layout,
  LayoutRequest,
  LayoutTemplate,
  Location,
  LocationRequest,
  Media,
  MediaUrl,
  MonitoringSettings,
  MonitoringSettingsRequest,
  Playlist,
  PlaylistRequest,
  PlaysGroupBy,
  PlaysReport,
  ReleaseInfo,
  ReportRange,
  Summary,
  UptimeReport,
  WebContentRequest,
  WebhookTestResult,
} from './models';

/** Os parâmetros preenchidos de um pedido; vazio é descartado. */
function queryParams(values: Record<string, string | number | null | undefined>): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined && value !== null && value !== '') {
      params = params.set(key, String(value));
    }
  }
  return params;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ConfigService);

  private url(path: string): string {
    return `${this.config.apiBaseUrl}/api/v1${path}`;
  }

  // dispositivos
  devices(filters: DeviceFilters = {}): Observable<Device[]> {
    return this.http.get<Device[]>(this.url('/devices'), { params: filtersToParams(filters) });
  }

  activateDevice(body: ActivateRequest): Observable<Device> {
    return this.http.post<Device>(this.url('/devices/activate'), body);
  }

  /** Aplica uma ação a várias TVs de uma vez (marcadas, de um grupo ou de um local). Devolve quantas foram alcançadas. */
  bulk(body: BulkRequest): Observable<{ affected: number }> {
    return this.http.post<{ affected: number }>(this.url('/devices/bulk'), body);
  }

  // locais
  locations(): Observable<Location[]> {
    return this.http.get<Location[]>(this.url('/locations'));
  }

  createLocation(body: LocationRequest): Observable<Location> {
    return this.http.post<Location>(this.url('/locations'), body);
  }

  updateLocation(id: number, body: LocationRequest): Observable<Location> {
    return this.http.put<Location>(this.url(`/locations/${id}`), body);
  }

  /** Apagar um local não apaga as TVs: elas ficam sem local. */
  deleteLocation(id: number): Observable<void> {
    return this.http.delete<void>(this.url(`/locations/${id}`));
  }

  // grupos
  groups(): Observable<DeviceGroup[]> {
    return this.http.get<DeviceGroup[]>(this.url('/groups'));
  }

  createGroup(body: DeviceGroupRequest): Observable<DeviceGroup> {
    return this.http.post<DeviceGroup>(this.url('/groups'), body);
  }

  updateGroup(id: number, body: DeviceGroupRequest): Observable<DeviceGroup> {
    return this.http.put<DeviceGroup>(this.url(`/groups/${id}`), body);
  }

  /** Apagar um grupo não apaga as TVs, só o vínculo. */
  deleteGroup(id: number): Observable<void> {
    return this.http.delete<void>(this.url(`/groups/${id}`));
  }

  // campanhas
  campaigns(): Observable<Campaign[]> {
    return this.http.get<Campaign[]>(this.url('/campaigns'));
  }

  createCampaign(body: CampaignRequest): Observable<Campaign> {
    return this.http.post<Campaign>(this.url('/campaigns'), body);
  }

  updateCampaign(id: number, body: CampaignRequest): Observable<Campaign> {
    return this.http.put<Campaign>(this.url(`/campaigns/${id}`), body);
  }

  deleteCampaign(id: number): Observable<void> {
    return this.http.delete<void>(this.url(`/campaigns/${id}`));
  }

  /** "O que esta TV exibiria neste momento?" (instante ISO; vazio = agora). */
  previewCampaigns(deviceId: number, at?: string): Observable<CampaignPreview> {
    const query = at ? `?deviceId=${deviceId}&at=${encodeURIComponent(at)}` : `?deviceId=${deviceId}`;
    return this.http.get<CampaignPreview>(this.url(`/campaigns/preview${query}`));
  }

  // layouts (fase 5)
  layouts(): Observable<Layout[]> {
    return this.http.get<Layout[]>(this.url('/layouts'));
  }

  layoutTemplates(): Observable<LayoutTemplate[]> {
    return this.http.get<LayoutTemplate[]>(this.url('/layouts/templates'));
  }

  createLayout(body: LayoutRequest): Observable<Layout> {
    return this.http.post<Layout>(this.url('/layouts'), body);
  }

  /** Cria um layout com as zonas de um modelo pronto (nome vazio = o nome do modelo). */
  createLayoutFromTemplate(templateId: string, name: string | null): Observable<Layout> {
    return this.http.post<Layout>(this.url('/layouts/from-template'), { templateId, name });
  }

  updateLayout(id: number, body: LayoutRequest): Observable<Layout> {
    return this.http.put<Layout>(this.url(`/layouts/${id}`), body);
  }

  deleteLayout(id: number): Observable<void> {
    return this.http.delete<void>(this.url(`/layouts/${id}`));
  }

  /** Volta a TV para a tela cheia (PUT sem layoutId significa "não mudar"). */
  removeDeviceLayout(id: number): Observable<Device> {
    return this.http.delete<Device>(this.url(`/devices/${id}/layout`));
  }

  /** Conteúdo web: uma página (https) que o Player exibe pelo tempo escolhido. */
  createWebContent(body: WebContentRequest): Observable<Media> {
    return this.http.post<Media>(this.url('/media/web'), body);
  }

  // monitoramento (fase 4)
  device(id: number): Observable<Device> {
    return this.http.get<Device>(this.url(`/devices/${id}`));
  }

  /** A história de uma TV, da mais recente para a mais antiga. */
  deviceEvents(deviceId: number, query: DeviceEventQuery = {}): Observable<DeviceEvent[]> {
    const params = queryParams({ beforeId: query.beforeId, type: query.type, level: query.level, limit: query.limit });
    return this.http.get<DeviceEvent[]>(this.url(`/devices/${deviceId}/events`), { params });
  }

  /** Abertos primeiro. */
  alerts(onlyOpen: boolean): Observable<Alert[]> {
    return this.http.get<Alert[]>(this.url('/alerts'), { params: queryParams({ status: onlyOpen ? 'open' : '' }) });
  }

  alertSettings(): Observable<MonitoringSettings> {
    return this.http.get<MonitoringSettings>(this.url('/alerts/settings'));
  }

  updateAlertSettings(body: MonitoringSettingsRequest): Observable<MonitoringSettings> {
    return this.http.put<MonitoringSettings>(this.url('/alerts/settings'), body);
  }

  /** Manda uma mensagem de teste ao webhook configurado. */
  testWebhook(): Observable<WebhookTestResult> {
    return this.http.post<WebhookTestResult>(this.url('/alerts/settings/test'), {});
  }

  uptimeReport(range: ReportRange, locationId?: number | null, groupId?: number | null): Observable<UptimeReport> {
    return this.http.get<UptimeReport>(this.url('/reports/uptime'), {
      params: queryParams({ from: range.from, to: range.to, locationId, groupId }),
    });
  }

  playsReport(range: ReportRange, groupBy: PlaysGroupBy, locationId?: number | null): Observable<PlaysReport> {
    return this.http.get<PlaysReport>(this.url('/reports/plays'), {
      params: queryParams({ from: range.from, to: range.to, groupBy, locationId }),
    });
  }

  inventoryReport(): Observable<InventoryRow[]> {
    return this.http.get<InventoryRow[]>(this.url('/reports/inventory'));
  }

  eventsReport(range: ReportRange, deviceId?: number | null, type?: EventType | '', level?: EventLevel | ''): Observable<EventRow[]> {
    return this.http.get<EventRow[]>(this.url('/reports/events'), {
      params: queryParams({ from: range.from, to: range.to, deviceId, type, level }),
    });
  }

  /** O mesmo relatório em CSV, baixado com o login do painel (o endereço sozinho não abriria sem o token). */
  reportCsv(report: 'uptime' | 'plays' | 'inventory' | 'events', values: Record<string, string | number | null | undefined>): Observable<Blob> {
    return this.http.get(this.url(`/reports/${report}`), {
      params: queryParams({ ...values, format: 'csv' }),
      responseType: 'blob',
    });
  }

  // resumo do dashboard
  summary(): Observable<Summary> {
    return this.http.get<Summary>(this.url('/summary'));
  }

  updateDevice(id: number, body: DeviceUpdateRequest): Observable<Device> {
    return this.http.put<Device>(this.url(`/devices/${id}`), body);
  }

  /** Tira a playlist da TV (ela passa a mostrar "aguardando conteúdo"). A playlist em si não é apagada. */
  removeDevicePlaylist(id: number): Observable<Device> {
    return this.http.delete<Device>(this.url(`/devices/${id}/playlist`));
  }

  /** Manda uma TV verificar agora se há versão nova do Player (ela percebe no próximo heartbeat, até ~30 s). */
  requestUpdateCheck(id: number): Observable<Device> {
    return this.http.post<Device>(this.url(`/devices/${id}/update-check`), {});
  }

  /** O mesmo para todas as TVs ativas; devolve quantas foram avisadas. */
  requestUpdateCheckAll(): Observable<{ requested: number }> {
    return this.http.post<{ requested: number }>(this.url('/devices/update-check'), {});
  }

  deleteDevice(id: number): Observable<void> {
    return this.http.delete<void>(this.url(`/devices/${id}`));
  }

  // mídia
  mediaList(): Observable<Media[]> {
    return this.http.get<Media[]>(this.url('/media'));
  }

  uploadMedia(file: File, durationSeconds: number | null): Observable<HttpEvent<Media>> {
    const form = new FormData();
    form.append('file', file);
    if (durationSeconds !== null) {
      form.append('durationSeconds', String(durationSeconds));
    }
    return this.http.post<Media>(this.url('/media'), form, { reportProgress: true, observe: 'events' });
  }

  deleteMedia(id: number): Observable<void> {
    return this.http.delete<void>(this.url(`/media/${id}`));
  }

  /** Miniaturas pedem em modo silencioso: uma imagem que não carrega não precisa de um aviso na tela. */
  mediaUrl(id: number, silent = false): Observable<MediaUrl> {
    return this.http.get<MediaUrl>(this.url(`/media/${id}/url`), { context: new HttpContext().set(SILENT_ERRORS, silent) });
  }

  /** Baixa o arquivo pelo backend (necessário quando a mídia fica em disco local e exige o JWT). */
  mediaBlob(relativeUrl: string, silent = false): Observable<Blob> {
    return this.http.get(`${this.config.apiBaseUrl}${relativeUrl}`, {
      responseType: 'blob',
      context: new HttpContext().set(SILENT_ERRORS, silent),
    });
  }

  // aplicativo da TV (site de downloads, público)
  /** Versões publicadas do aplicativo da TV, da mais nova para a mais antiga. */
  releases(silent = false): Observable<ReleaseInfo[]> {
    return this.http
      .get<{ releases: ReleaseInfo[] }>(`${this.config.downloadsUrl}/releases.json`, {
        context: new HttpContext().set(SILENT_ERRORS, silent),
      })
      .pipe(map((body) => body.releases ?? []));
  }

  // playlists
  playlists(): Observable<Playlist[]> {
    return this.http.get<Playlist[]>(this.url('/playlists'));
  }

  playlist(id: number): Observable<Playlist> {
    return this.http.get<Playlist>(this.url(`/playlists/${id}`));
  }

  createPlaylist(body: PlaylistRequest): Observable<Playlist> {
    return this.http.post<Playlist>(this.url('/playlists'), body);
  }

  updatePlaylist(id: number, body: PlaylistRequest): Observable<Playlist> {
    return this.http.put<Playlist>(this.url(`/playlists/${id}`), body);
  }

  deletePlaylist(id: number): Observable<void> {
    return this.http.delete<void>(this.url(`/playlists/${id}`));
  }
}
