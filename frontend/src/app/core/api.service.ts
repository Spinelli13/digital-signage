import { HttpClient, HttpContext, HttpEvent, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { ConfigService } from './config.service';
import { SILENT_ERRORS } from './http-errors';
import { filtersToParams } from './device-filters';
import {
  ActivateRequest,
  PairingInfo,
  DeviceDiagnostic,
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
  BillingInfo,
  Invoice,
  AttendanceOverview,
  CounterInfo,
  CounterRequest,
  Layout,
  LayoutRequest,
  PublicQueue,
  PublicTicket,
  QueueInfo,
  QueueRequest,
  Ticket,
  TicketAction,
  LayoutTemplate,
  Location,
  LocationRequest,
  Me,
  Media,
  MediaUrl,
  MonitoringSettings,
  MonitoringSettingsRequest,
  ScreenAlert,
  ResultsResponse,
  ScreenAlertRequest,
  PlatformOverview,
  Coupon,
  CouponBatch,
  CouponBatchRequest,
  CouponRedemption,
  CouponRequest,
  Manager,
  Promotion,
  PromotionCoupon,
  PromotionLookup,
  PromotionRequest,
  PublicPromoCoupon,
  PublicPromotion,
  ManagerRequest,
  ManagerUpdate,
  PlatformPlan,
  PlatformPlanRequest,
  PlatformSettings,
  PrivacyErase,
  PrivacyRequestEntry,
  PrivacySearch,
  PrivacySettings,
  PublicPrivacy,
  PlatformTenantRequest,
  PlatformTenantUpdate,
  Playlist,
  PlaylistRequest,
  PlaysGroupBy,
  PlaysReport,
  PriceBoardData,
  PriceImportResult,
  PriceList,
  PriceListRequest,
  ProofDaily,
  ProofFilters,
  ProofReport,
  ProofVerification,
  ReleaseInfo,
  ReportRange,
  Summary,
  RedeemedCoupon,
  TeamMember,
  TeamMemberRequest,
  TeamMemberUpdate,
  TenantSummary,
  UptimeReport,
  WebContentRequest,
  WidgetCatalog,
  WidgetKind,
  WidgetPlace,
  WidgetPreview,
  WidgetRequest,
  WidgetSettings,
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

  /** O último relatório de diagnóstico da TV. */
  deviceDiagnostic(id: number): Observable<DeviceDiagnostic> {
    return this.http.get<DeviceDiagnostic>(this.url(`/devices/${id}/diagnostic`));
  }

  /** Pede que a TV mande o diagnóstico agora (ela responde no próximo contato, em até 30 segundos). */
  requestDeviceDiagnostic(id: number): Observable<DeviceDiagnostic> {
    return this.http.post<DeviceDiagnostic>(this.url(`/devices/${id}/diagnostic-request`), {});
  }

  /** Confere o código que o QR Code da TV trouxe (404 se não existe, expirou ou já foi pareado). */
  devicePairing(code: string): Observable<PairingInfo> {
    return this.http.get<PairingInfo>(this.url(`/devices/pairing/${encodeURIComponent(code)}`));
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

  // ---------- equipe, conta e cobrança (fase 6) ----------
  me(): Observable<Me> {
    return this.http.get<Me>(this.url('/auth/me'));
  }

  /** Cada pessoa troca a própria senha (precisa da atual). */
  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.put<void>(this.url('/auth/password'), { currentPassword, newPassword });
  }

  team(): Observable<TeamMember[]> {
    return this.http.get<TeamMember[]>(this.url('/users'));
  }

  createTeamMember(body: TeamMemberRequest): Observable<TeamMember> {
    return this.http.post<TeamMember>(this.url('/users'), body);
  }

  updateTeamMember(id: number, body: TeamMemberUpdate): Observable<TeamMember> {
    return this.http.put<TeamMember>(this.url(`/users/${id}`), body);
  }

  deleteTeamMember(id: number): Observable<void> {
    return this.http.delete<void>(this.url(`/users/${id}`));
  }

  billing(): Observable<BillingInfo> {
    return this.http.get<BillingInfo>(this.url('/billing'));
  }

  updateBillingProfile(body: { name?: string; billingEmail?: string; document?: string }): Observable<TenantSummary> {
    return this.http.put<TenantSummary>(this.url('/billing/profile'), body);
  }

  // ---------- operação da plataforma ----------
  platformOverview(): Observable<PlatformOverview> {
    return this.http.get<PlatformOverview>(this.url('/platform/overview'));
  }

  platformTenants(): Observable<TenantSummary[]> {
    return this.http.get<TenantSummary[]>(this.url('/platform/tenants'));
  }

  createPlatformTenant(body: PlatformTenantRequest): Observable<TenantSummary> {
    return this.http.post<TenantSummary>(this.url('/platform/tenants'), body);
  }

  updatePlatformTenant(id: number, body: PlatformTenantUpdate): Observable<TenantSummary> {
    return this.http.put<TenantSummary>(this.url(`/platform/tenants/${id}`), body);
  }

  platformPlans(): Observable<PlatformPlan[]> {
    return this.http.get<PlatformPlan[]>(this.url('/platform/plans'));
  }

  createPlatformPlan(body: PlatformPlanRequest): Observable<PlatformPlan> {
    return this.http.post<PlatformPlan>(this.url('/platform/plans'), body);
  }

  updatePlatformPlan(id: number, body: PlatformPlanRequest): Observable<PlatformPlan> {
    return this.http.put<PlatformPlan>(this.url(`/platform/plans/${id}`), body);
  }

  deletePlatformPlan(id: number): Observable<void> {
    return this.http.delete<void>(this.url(`/platform/plans/${id}`));
  }

  redeemCoupon(code: string): Observable<RedeemedCoupon> {
    return this.http.post<RedeemedCoupon>(this.url('/billing/coupon'), { code });
  }

  platformCoupons(): Observable<Coupon[]> {
    return this.http.get<Coupon[]>(this.url('/platform/coupons'));
  }

  createCoupon(body: CouponRequest): Observable<Coupon> {
    return this.http.post<Coupon>(this.url('/platform/coupons'), body);
  }

  updateCoupon(id: number, body: CouponRequest): Observable<Coupon> {
    return this.http.put<Coupon>(this.url(`/platform/coupons/${id}`), body);
  }

  deleteCoupon(id: number): Observable<void> {
    return this.http.delete<void>(this.url(`/platform/coupons/${id}`));
  }

  createCouponBatch(body: CouponBatchRequest): Observable<CouponBatch> {
    return this.http.post<CouponBatch>(this.url('/platform/coupons/batch'), body);
  }

  couponRedemptions(id: number): Observable<CouponRedemption[]> {
    return this.http.get<CouponRedemption[]>(this.url(`/platform/coupons/${id}/redemptions`));
  }

  managers(): Observable<Manager[]> {
    return this.http.get<Manager[]>(this.url('/platform/managers'));
  }

  createManager(body: ManagerRequest): Observable<Manager> {
    return this.http.post<Manager>(this.url('/platform/managers'), body);
  }

  updateManager(id: number, body: ManagerUpdate): Observable<Manager> {
    return this.http.put<Manager>(this.url(`/platform/managers/${id}`), body);
  }

  deleteManager(id: number): Observable<void> {
    return this.http.delete<void>(this.url(`/platform/managers/${id}`));
  }

  platformInvoices(status?: string, tenantId?: number | null): Observable<Invoice[]> {
    return this.http.get<Invoice[]>(this.url('/platform/invoices'), { params: queryParams({ status, tenantId }) });
  }

  createPlatformInvoice(tenantId: number, body: { description: string; amountCents: number; dueDate: string }): Observable<Invoice> {
    return this.http.post<Invoice>(this.url(`/platform/tenants/${tenantId}/invoices`), body);
  }

  payInvoice(id: number, method: string, note: string): Observable<Invoice> {
    return this.http.post<Invoice>(this.url(`/platform/invoices/${id}/pay`), { method, note });
  }

  voidInvoice(id: number): Observable<Invoice> {
    return this.http.post<Invoice>(this.url(`/platform/invoices/${id}/void`), {});
  }

  /** Roda o ciclo de cobrança agora (o sistema também roda sozinho a cada hora). */
  runBilling(): Observable<{ status: string }> {
    return this.http.post<{ status: string }>(this.url('/platform/billing/run'), {});
  }

  platformSettings(): Observable<PlatformSettings> {
    return this.http.get<PlatformSettings>(this.url('/platform/settings'));
  }

  updatePlatformSettings(body: Partial<PlatformSettings>): Observable<PlatformSettings> {
    return this.http.put<PlatformSettings>(this.url('/platform/settings'), body);
  }

  // ---------- privacidade e dados pessoais (LGPD) ----------

  /** Quem responde pelo tratamento de dados (Política de Privacidade e Termos de Uso: páginas abertas, sem login). */
  publicPrivacy(): Observable<PublicPrivacy> {
    return this.http.get<PublicPrivacy>(this.url('/public/privacy'), { context: new HttpContext().set(SILENT_ERRORS, true) });
  }

  privacySettings(): Observable<PrivacySettings> {
    return this.http.get<PrivacySettings>(this.url('/privacy/settings'));
  }

  updatePrivacySettings(body: Partial<PrivacySettings>): Observable<PrivacySettings> {
    return this.http.put<PrivacySettings>(this.url('/privacy/settings'), body);
  }

  privacySearch(term: string): Observable<PrivacySearch> {
    return this.http.get<PrivacySearch>(this.url('/privacy/search'), { params: { q: term } });
  }

  /** A cópia dos dados da pessoa (arquivo JSON); o pedido fica registrado no servidor. */
  privacyExport(term: string, note: string | null): Observable<Blob> {
    return this.http.post(this.url('/privacy/export'), { term, note }, { responseType: 'blob' });
  }

  privacyErase(term: string, note: string | null): Observable<PrivacyErase> {
    return this.http.post<PrivacyErase>(this.url('/privacy/erase'), { term, note });
  }

  privacyRequests(): Observable<PrivacyRequestEntry[]> {
    return this.http.get<PrivacyRequestEntry[]>(this.url('/privacy/requests'));
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

  // ---------- tabelas de preços ----------

  priceLists(): Observable<PriceList[]> {
    return this.http.get<PriceList[]>(this.url('/price-lists'));
  }

  priceList(id: number): Observable<PriceList> {
    return this.http.get<PriceList>(this.url(`/price-lists/${id}`));
  }

  createPriceList(body: PriceListRequest): Observable<PriceList> {
    return this.http.post<PriceList>(this.url('/price-lists'), body);
  }

  updatePriceList(id: number, body: PriceListRequest): Observable<PriceList> {
    return this.http.put<PriceList>(this.url(`/price-lists/${id}`), body);
  }

  deletePriceList(id: number): Observable<void> {
    return this.http.delete<void>(this.url(`/price-lists/${id}`));
  }

  /** Os preços das próximas 24 horas de uma tabela ainda não salva. Silencioso: o motivo vira texto ao lado da pré-visualização. */
  previewPriceList(body: PriceListRequest): Observable<PriceBoardData> {
    return this.http.post<PriceBoardData>(this.url('/price-lists/preview'), body, { context: new HttpContext().set(SILENT_ERRORS, true) });
  }

  importPriceList(id: number, file: File, mode: 'replace' | 'append'): Observable<PriceImportResult> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<PriceImportResult>(this.url(`/price-lists/${id}/import`), form, { params: queryParams({ mode }) });
  }

  exportPriceList(id: number): Observable<Blob> {
    return this.http.get(this.url(`/price-lists/${id}/export`), { responseType: 'blob' });
  }

  // ---------- widgets ----------

  createWidget(body: WidgetRequest): Observable<Media> {
    return this.http.post<Media>(this.url('/media/widget'), body);
  }

  updateWidget(id: number, body: WidgetRequest): Observable<Media> {
    return this.http.put<Media>(this.url(`/media/${id}/widget`), body);
  }

  widgetCatalog(): Observable<WidgetCatalog> {
    return this.http.get<WidgetCatalog>(this.url('/widgets/catalog'));
  }

  /** Busca de cidade (clima). Silencioso: uma busca que falha não deve abrir o aviso de erro da tela. */
  widgetPlaces(query: string, lang: string): Observable<WidgetPlace[]> {
    return this.http.get<WidgetPlace[]>(this.url('/widgets/places'), {
      params: queryParams({ q: query, lang }),
      context: new HttpContext().set(SILENT_ERRORS, true),
    });
  }

  /** Confere as escolhas e traz os dados de agora, sem salvar. Silencioso: o motivo vira texto ao lado da pré-visualização. */
  widgetPreview(kind: WidgetKind, settings: WidgetSettings): Observable<WidgetPreview> {
    return this.http.post<WidgetPreview>(this.url('/widgets/preview'), { kind, settings }, { context: new HttpContext().set(SILENT_ERRORS, true) });
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

  /** Manda um e-mail de teste aos endereços dos avisos. */
  testAlertEmail(): Observable<WebhookTestResult> {
    return this.http.post<WebhookTestResult>(this.url('/alerts/settings/test-email'), {});
  }

  /** "Alertar agora": os avisos em tela cheia no ar e os recentes. */
  screenAlerts(): Observable<ScreenAlert[]> {
    return this.http.get<ScreenAlert[]>(this.url('/screen-alerts'));
  }

  createScreenAlert(body: ScreenAlertRequest): Observable<ScreenAlert> {
    return this.http.post<ScreenAlert>(this.url('/screen-alerts'), body);
  }

  endScreenAlert(id: number): Observable<ScreenAlert> {
    return this.http.post<ScreenAlert>(this.url(`/screen-alerts/${id}/end`), {});
  }

  /** "Esqueci a senha": a resposta é a mesma exista ou não uma conta com o e-mail. */
  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>(this.url('/auth/forgot-password'), { email });
  }

  /** Escolhe a senha nova com o link recebido por e-mail. */
  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(this.url('/auth/reset-password'), { token, newPassword });
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

  proofReport(range: ReportRange, filters: ProofFilters, offset: number, limit: number): Observable<ProofReport> {
    return this.http.get<ProofReport>(this.url('/reports/proof'), {
      params: queryParams({ from: range.from, to: range.to, ...filters, offset, limit }),
    });
  }

  proofVerify(range: ReportRange, deviceId?: number | null): Observable<ProofVerification> {
    return this.http.get<ProofVerification>(this.url('/reports/proof/verify'), { params: queryParams({ from: range.from, to: range.to, deviceId }) });
  }

  proofDaily(range: ReportRange, deviceId?: number | null): Observable<ProofDaily[]> {
    return this.http.get<ProofDaily[]>(this.url('/reports/proof/daily'), { params: queryParams({ from: range.from, to: range.to, deviceId }) });
  }

  /** O mesmo relatório em CSV, baixado com o login do painel (o endereço sozinho não abriria sem o token). */
  reportCsv(report: 'uptime' | 'plays' | 'inventory' | 'events' | 'proof', values: Record<string, string | number | null | undefined>): Observable<Blob> {
    return this.http.get(this.url(`/reports/${report}`), {
      params: queryParams({ ...values, format: 'csv' }),
      responseType: 'blob',
    });
  }

  /** "Resultados": exibições do conteúdo, QR Codes escaneados e cupons, por dia, TV e promoção. */
  results(range: ReportRange, promotionId?: number | null): Observable<ResultsResponse> {
    return this.http.get<ResultsResponse>(this.url('/results'), { params: queryParams({ from: range.from, to: range.to, promotionId }) });
  }

  resultsCsv(range: ReportRange, by: 'day' | 'tv' | 'promotion', promotionId?: number | null): Observable<Blob> {
    return this.http.get(this.url('/results'), { params: queryParams({ from: range.from, to: range.to, promotionId, by, format: 'csv' }), responseType: 'blob' });
  }

  // resumo do dashboard
  summary(silent = false): Observable<Summary> {
    return this.http.get<Summary>(this.url('/summary'), { context: new HttpContext().set(SILENT_ERRORS, silent) });
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

  // ---------- atendimento (filas, guichês e senhas) ----------

  /** Silencioso: a tela consulta a cada poucos segundos, e um tropeço de rede não pode encher a tela de avisos. */
  attendanceOverview(): Observable<AttendanceOverview> {
    return this.http.get<AttendanceOverview>(this.url(`/attendance/overview`), { context: new HttpContext().set(SILENT_ERRORS, true) });
  }

  queues(): Observable<QueueInfo[]> {
    return this.http.get<QueueInfo[]>(this.url(`/queues`));
  }

  createQueue(body: QueueRequest): Observable<QueueInfo> {
    return this.http.post<QueueInfo>(this.url(`/queues`), body);
  }

  updateQueue(id: number, body: QueueRequest): Observable<QueueInfo> {
    return this.http.put<QueueInfo>(this.url(`/queues/${id}`), body);
  }

  deleteQueue(id: number): Observable<void> {
    return this.http.delete<void>(this.url(`/queues/${id}`));
  }

  createCounter(body: CounterRequest): Observable<CounterInfo> {
    return this.http.post<CounterInfo>(this.url(`/counters`), body);
  }

  updateCounter(id: number, body: CounterRequest): Observable<CounterInfo> {
    return this.http.put<CounterInfo>(this.url(`/counters/${id}`), body);
  }

  deleteCounter(id: number): Observable<void> {
    return this.http.delete<void>(this.url(`/counters/${id}`));
  }

  issueTicket(queueId: number, customerName: string | null): Observable<Ticket> {
    return this.http.post<Ticket>(this.url(`/attendance/tickets`), { queueId, customerName });
  }

  callNextTicket(counterId: number): Observable<Ticket> {
    return this.http.post<Ticket>(this.url(`/attendance/counters/${counterId}/call-next`), {});
  }

  ticketAction(id: number, action: TicketAction): Observable<Ticket> {
    return this.http.post<Ticket>(this.url(`/attendance/tickets/${id}/${action}`), {});
  }

  // ---------- senha pelo celular (páginas públicas: sem login, erros tratados pela própria página) ----------

  publicQueue(token: string): Observable<PublicQueue> {
    return this.http.get<PublicQueue>(this.url(`/public/queues/${token}`), { context: new HttpContext().set(SILENT_ERRORS, true) });
  }

  /** O código do aparelho vai junto: o servidor limita as senhas por aparelho sem confundir os clientes que dividem o mesmo Wi-Fi. */
  takePublicTicket(token: string, customerName: string | null, deviceId: string, consent = false): Observable<PublicTicket> {
    return this.http.post<PublicTicket>(this.url(`/public/queues/${token}/tickets`), { customerName, consent }, {
      context: new HttpContext().set(SILENT_ERRORS, true),
      headers: { 'X-Client-Id': deviceId },
    });
  }

  publicTicket(token: string): Observable<PublicTicket> {
    return this.http.get<PublicTicket>(this.url(`/public/tickets/${token}`), { context: new HttpContext().set(SILENT_ERRORS, true) });
  }

  leavePublicQueue(token: string): Observable<PublicTicket> {
    return this.http.post<PublicTicket>(this.url(`/public/tickets/${token}/cancel`), {}, { context: new HttpContext().set(SILENT_ERRORS, true) });
  }

  // ---------- cupons para os clientes da loja ----------

  promotions(): Observable<Promotion[]> {
    return this.http.get<Promotion[]>(this.url('/promotions'));
  }

  createPromotion(body: PromotionRequest): Observable<Promotion> {
    return this.http.post<Promotion>(this.url('/promotions'), body);
  }

  updatePromotion(id: number, body: PromotionRequest): Observable<Promotion> {
    return this.http.put<Promotion>(this.url(`/promotions/${id}`), body);
  }

  deletePromotion(id: number): Observable<void> {
    return this.http.delete<void>(this.url(`/promotions/${id}`));
  }

  promotionCoupons(id: number): Observable<PromotionCoupon[]> {
    return this.http.get<PromotionCoupon[]>(this.url(`/promotions/${id}/coupons`));
  }

  /** O caixa confere um código sem dar baixa. */
  lookupPromotionCoupon(code: string): Observable<PromotionLookup> {
    return this.http.get<PromotionLookup>(this.url('/promotions/coupons/lookup'), { params: { code } });
  }

  redeemPromotionCoupon(code: string): Observable<PromotionLookup> {
    return this.http.post<PromotionLookup>(this.url('/promotions/coupons/redeem'), { code });
  }

  // ---------- cupom pelo celular (páginas públicas: sem login, erros tratados pela própria página) ----------

  /** {@code origin}: de que QR Code veio ("tv-12", "balcao"), para contar o escaneamento por origem. */
  publicPromotion(token: string, origin: string | null = null, deviceId: string | null = null): Observable<PublicPromotion> {
    return this.http.get<PublicPromotion>(this.url(`/public/promotions/${token}`), {
      context: new HttpContext().set(SILENT_ERRORS, true),
      params: queryParams({ o: origin }),
      ...(deviceId ? { headers: { 'X-Client-Id': deviceId } } : {}),
    });
  }

  claimPublicCoupon(token: string, customerName: string | null, deviceId: string, consent = false, origin: string | null = null): Observable<PublicPromoCoupon> {
    return this.http.post<PublicPromoCoupon>(this.url(`/public/promotions/${token}/coupons`), { customerName, consent, ...(origin ? { origin } : {}) }, {
      context: new HttpContext().set(SILENT_ERRORS, true),
      headers: { 'X-Client-Id': deviceId },
    });
  }

  publicPromoCoupon(token: string): Observable<PublicPromoCoupon> {
    return this.http.get<PublicPromoCoupon>(this.url(`/public/coupons/${token}`), { context: new HttpContext().set(SILENT_ERRORS, true) });
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
