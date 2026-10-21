export type DeviceStatus = 'PENDING' | 'ACTIVE' | 'DISABLED';
export type MediaType = 'IMAGE' | 'VIDEO' | 'WEB' | 'WIDGET';
/** Situação de comunicação calculada pelo servidor (mesma regra em todo o sistema). */
export type Connection = 'ONLINE' | 'OFFLINE' | 'NEVER' | 'PENDING' | 'DISABLED';

export interface Device {
  id: number;
  name: string;
  /** Nome do local (mantido por compatibilidade); o vínculo de verdade é locationId. */
  location: string | null;
  locationId: number | null;
  status: DeviceStatus;
  connection: Connection;
  groupIds: number[];
  online: boolean;
  lastSeenAt: string | null;
  playerVersion: string | null;
  currentPlaylistId: number | null;
  configVersion: number;
  /** O que a TV exibe AGORA: a playlist de uma campanha ativa ou, sem campanha, a base (currentPlaylistId). */
  effectivePlaylistId: number | null;
  campaignId: number | null;
  campaignName: string | null;
  /** A mídia que o Player disse estar na tela no último contato (ausente em respostas de servidores antigos). */
  currentMediaId?: number | null;
  currentMediaName?: string | null;
  /** Layout da tela (vazio = tela cheia). Ausentes em respostas de servidores antigos. */
  layoutId?: number | null;
  layoutName?: string | null;
  /** Como a TV foi instalada (ausente em respostas de servidores antigos = AUTO). */
  orientation?: ScreenOrientation;
  /** A versão do conteúdo que a TV deveria ter sincronizado; diferente de configVersion = ainda sincronizando. */
  desiredConfigVersion?: number;
  /** Até quando a agenda offline guardada na TV vale (ausente = a TV não tem agenda, ou usa um Player antigo). */
  offlinePlanUntil?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Media {
  id: number;
  name: string;
  type: MediaType;
  mimeType: string;
  url: string;
  fileSize: number;
  durationSeconds: number | null;
  checksum: string | null;
  /** Só nos widgets: o tipo e as escolhas feitas no painel. */
  widget?: WidgetInfo | null;
  createdAt: string;
}

export interface MediaUrl {
  url: string;
  requiresAuth: boolean;
}

export interface PlaylistItem {
  mediaId: number;
  mediaName: string;
  mediaType: MediaType;
  position: number;
  durationSeconds: number;
}

export interface Playlist {
  id: number;
  name: string;
  description: string | null;
  configVersion: number;
  items: PlaylistItem[];
  createdAt: string;
  updatedAt: string;
}

export interface PlaylistRequest {
  name: string;
  description: string | null;
  items: { mediaId: number; durationSeconds: number }[];
}

/** Uma versão do aplicativo da TV, como publicada no site de downloads (releases.json). */
export interface ReleaseInfo {
  versionCode: number;
  versionName: string;
  apk: string;
  sha256: string;
  size: number;
  notes: string;
  publishedAt: string;
}

/** Como a TV foi instalada: o Player gira o conteúdo conforme isto. AUTO segue a tela do aparelho. */
export type ScreenOrientation = 'AUTO' | 'LANDSCAPE' | 'LANDSCAPE_FLIPPED' | 'PORTRAIT_TOP_RIGHT' | 'PORTRAIT_TOP_LEFT';

export interface DeviceUpdateRequest {
  name: string;
  /** Nome do local: um nome novo cria o local; vazio tira a TV do local; ausente não muda. */
  location?: string | null;
  locationId?: number | null;
  status?: string;
  currentPlaylistId?: number | null;
  /** Lista completa de grupos da TV; ausente não muda, lista vazia tira de todos. */
  groupIds?: number[];
  /** Layout da tela; ausente não muda (para voltar à tela cheia use removeDeviceLayout). */
  layoutId?: number;
  /** Como a TV foi instalada; ausente não muda (só o Administrador). */
  orientation?: ScreenOrientation;
}

export interface ActivateRequest {
  activationCode: string;
  name: string;
  location?: string | null;
  locationId?: number | null;
  groupIds?: number[];
}

/** Ponto físico onde há TVs ("Pontos & Locais"). */
export interface Location {
  id: number;
  name: string;
  description: string | null;
  deviceCount: number;
  onlineCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface LocationRequest {
  name: string;
  description: string | null;
}

/** Conjunto livre de TVs, para agir em várias de uma vez. */
export interface DeviceGroup {
  id: number;
  name: string;
  description: string | null;
  deviceCount: number;
  onlineCount: number;
  deviceIds: number[];
  createdAt: string;
  updatedAt: string;
}

export interface DeviceGroupRequest {
  name: string;
  description: string | null;
  /** Ausente = não mexe nos membros. */
  deviceIds?: number[];
}

/** Filtros da lista de TVs. Vazio/nulo = sem filtro. */
export interface DeviceFilters {
  connection?: Connection | '';
  /** Id de um local ou 'none' para as TVs sem local. */
  location?: number | 'none' | null;
  groupId?: number | null;
  q?: string;
}

export type BulkAction =
  | 'ASSIGN_PLAYLIST'
  | 'REMOVE_PLAYLIST'
  | 'ENABLE'
  | 'DISABLE'
  | 'CHECK_UPDATE'
  | 'SET_LOCATION'
  | 'CLEAR_LOCATION'
  | 'ADD_TO_GROUP'
  | 'REMOVE_FROM_GROUP'
  | 'ASSIGN_LAYOUT'
  | 'REMOVE_LAYOUT'
  | 'SET_ORIENTATION';

/** Escolha exatamente UM alvo: deviceIds, targetGroupId ou targetLocationId. */
export interface BulkRequest {
  action: BulkAction;
  deviceIds?: number[];
  targetGroupId?: number;
  targetLocationId?: number;
  playlistId?: number;
  locationId?: number;
  groupId?: number;
  layoutId?: number;
  orientation?: ScreenOrientation;
}

export interface Summary {
  devices: { total: number; online: number; offline: number; never: number; pending: number; disabled: number };
  locations: number;
  groups: number;
  media: number;
  playlists: number;
  campaigns: { total: number; activeNow: number };
  /** Alertas de monitoramento ainda abertos. */
  openAlerts: number;
  /** id nulo = TVs sem local. */
  byLocation: { id: number | null; name: string; total: number; online: number }[];
  attention: { id: number; name: string; location: string | null; connection: Connection; lastSeenAt: string | null }[];
}

// ---------- campanhas (fase 3) ----------

export type CampaignState = 'ACTIVE' | 'WAITING' | 'SCHEDULED' | 'ENDED' | 'PAUSED';
export type TargetType = 'ALL' | 'LOCATION' | 'GROUP' | 'DEVICE';

export interface CampaignTargetInfo {
  type: TargetType;
  /** Id da TV, do local ou do grupo; nulo em "todas as TVs". */
  id: number | null;
  name: string | null;
}

export interface Campaign {
  id: number;
  name: string;
  description: string | null;
  playlistId: number;
  playlistName: string | null;
  /** Maior número vence quando duas campanhas alcançam a mesma TV ao mesmo tempo. */
  priority: number;
  enabled: boolean;
  startDate: string | null;
  endDate: string | null;
  /** "HH:mm:ss" (ou nulo = o dia todo). */
  startTime: string | null;
  endTime: string | null;
  /** 1 = segunda ... 7 = domingo. */
  daysOfWeek: number[];
  timezone: string;
  targets: CampaignTargetInfo[];
  state: CampaignState;
  deviceCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CampaignRequest {
  name: string;
  description: string | null;
  playlistId: number;
  priority: number;
  enabled: boolean;
  startDate: string | null;
  endDate: string | null;
  startTime: string | null;
  endTime: string | null;
  daysOfWeek: number[];
  timezone: string;
  targets: { type: TargetType; id: number | null }[];
}

/** O que uma TV exibiria num momento (simulação da agenda). */
export interface CampaignPreview {
  deviceId: number;
  at: string;
  playlistId: number | null;
  playlistName: string | null;
  source: 'CAMPAIGN' | 'BASE' | 'NONE';
  campaignId: number | null;
  campaignName: string | null;
}

// ---------- monitoramento (fase 4) ----------

export type EventType =
  | 'ACTIVATED'
  | 'ONLINE'
  | 'OFFLINE'
  | 'DISABLED'
  | 'ENABLED'
  | 'PLAYLIST_CHANGED'
  | 'LAYOUT_CHANGED'
  | 'ORIENTATION_CHANGED'
  | 'UPDATE_REQUESTED'
  | 'PLAYER_UPDATED'
  | 'PLAYER_LOG';
export type EventLevel = 'INFO' | 'WARN' | 'ERROR';

/** Uma linha da história de uma TV. */
export interface DeviceEvent {
  id: number;
  deviceId: number;
  type: EventType;
  level: EventLevel;
  message: string;
  occurredAt: string;
}

export interface DeviceEventQuery {
  /** Id do último evento recebido: traz os anteriores a ele (próxima página). */
  beforeId?: number;
  type?: EventType | '';
  level?: EventLevel | '';
  limit?: number;
}

export type AlertType = 'OFFLINE' | 'NEVER_CONNECTED' | 'PLAYER_ERRORS';

export interface Alert {
  id: number;
  deviceId: number;
  deviceName: string | null;
  location: string | null;
  type: AlertType;
  message: string;
  openedAt: string;
  resolvedAt: string | null;
  open: boolean;
  /** O aviso já foi entregue ao webhook. */
  notified: boolean;
}

export interface MonitoringSettings {
  offlineAfterMinutes: number;
  neverConnectedAfterHours: number;
  errorBurstCount: number;
  webhookConfigured: boolean;
  /** Só o endereço do servidor e o final: o webhook completo é um segredo e nunca volta do servidor. */
  webhookHint: string | null;
  notifyOnResolve: boolean;
  eventsRetentionDays: number;
  /** Quantos dias o detalhe de cada exibição (comprovação) fica guardado. */
  playLogRetentionDays: number;
}

/** Campos ausentes não mudam. webhookUrl: vazio remove o webhook. */
export interface MonitoringSettingsRequest {
  offlineAfterMinutes?: number;
  neverConnectedAfterHours?: number;
  errorBurstCount?: number;
  webhookUrl?: string;
  notifyOnResolve?: boolean;
  eventsRetentionDays?: number;
  playLogRetentionDays?: number;
}

export interface WebhookTestResult {
  ok: boolean;
  status: number;
  error: string | null;
}

/** Datas no formato AAAA-MM-DD, no fuso do relatório; vazias = últimos 7 dias. */
export interface ReportRange {
  from?: string;
  to?: string;
}

export interface UptimeRow {
  deviceId: number;
  name: string;
  location: string | null;
  uptimePercent: number | null;
  onlineSeconds: number;
  offlineSeconds: number;
  observedSeconds: number;
  incidents: number;
}

export interface UptimeReport {
  from: string;
  to: string;
  timezone: string;
  fleetUptimePercent: number | null;
  fleetOnlineSeconds: number;
  fleetObservedSeconds: number;
  devices: UptimeRow[];
}

export interface InventoryRow {
  id: number;
  name: string;
  location: string | null;
  groups: string[];
  status: DeviceStatus;
  connection: Connection;
  lastSeenAt: string | null;
  playerVersion: string | null;
  playlist: string | null;
  campaign: string | null;
  currentMedia: string | null;
  activatedAt: string | null;
}

export interface EventRow {
  id: number;
  deviceId: number;
  deviceName: string | null;
  type: EventType;
  level: EventLevel;
  message: string;
  occurredAt: string;
}

export type PlaysGroupBy = 'media' | 'device' | 'day';

export interface PlayRow {
  id: number | null;
  label: string | null;
  day: string | null;
  plays: number;
  seconds: number;
  devices: number;
}

/** Uma exibição medida pela própria TV: início e duração em milissegundos, mais o elo da corrente de hashes. */
export interface ProofRow {
  id: number;
  deviceId: number;
  deviceName: string;
  seq: number;
  mediaId: number | null;
  mediaName: string;
  mediaType: string;
  zoneName: string | null;
  playlistId: number | null;
  campaignId: number | null;
  campaignName: string | null;
  /** Instante de início, com milissegundos, já corrigido pelo relógio do servidor. */
  startedAt: string;
  durationMs: number;
  plannedMs: number | null;
  /** Foi até o fim (true) ou foi interrompida (false). */
  completed: boolean;
  hash: string;
  hashValid: boolean;
  continuous: boolean;
  clockOffsetMs: number;
}

export interface ProofReport {
  from: string;
  to: string;
  timezone: string;
  total: number;
  completed: number;
  totalDurationMs: number;
  devices: number;
  offset: number;
  limit: number;
  rows: ProofRow[];
}

export interface ProofFilters {
  deviceId?: number | null;
  mediaId?: number | null;
  campaignId?: number | null;
}

export interface ProofCheck {
  deviceId: number;
  deviceName: string;
  records: number;
  firstSeq: number;
  lastSeq: number;
  missing: number;
  brokenLinks: number;
  hashMismatches: number;
  flaggedAtIngest: number;
  digest: string;
  status: 'OK' | 'ATENCAO';
}

export interface ProofVerification {
  from: string;
  to: string;
  timezone: string;
  ok: boolean;
  /** O resumo (SHA-256) do período inteiro: guardá-lo permite provar, depois, que nada mudou. */
  digest: string | null;
  devices: ProofCheck[];
}

export interface ProofDaily {
  deviceId: number;
  deviceName: string;
  day: string;
  plays: number;
  completed: number;
  totalMs: number;
  firstSeq: number;
  lastSeq: number;
  lastHash: string;
}

export interface PlaysReport {
  from: string;
  to: string;
  groupBy: PlaysGroupBy;
  totalPlays: number;
  totalSeconds: number;
  rows: PlayRow[];
}

// ---------- layouts e conteúdo web (fase 5) ----------

/** Um retângulo da tela, em % (0-100) a partir do canto superior esquerdo. */
export interface LayoutZone {
  id: number | null;
  name: string;
  x: number;
  y: number;
  width: number;
  height: number;
  /** A zona principal exibe a playlist da TV (e a das campanhas); as outras têm playlist fixa. */
  main: boolean;
  playlistId: number | null;
  playlistName?: string | null;
  /** CONTAIN: a mídia cabe inteira (com faixas); COVER: preenche a zona, cortando as bordas. */
  fit?: 'CONTAIN' | 'COVER';
}

export interface Layout {
  id: number;
  name: string;
  description: string | null;
  configVersion: number;
  zones: LayoutZone[];
  /** A versão para TV em pé (vazia = o layout não tem; a TV em pé usa a horizontal). Ausente em servidores antigos. */
  portraitZones?: LayoutZone[];
  /** Quantas TVs usam este layout. */
  deviceCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface LayoutZoneRequest {
  name: string;
  x: number;
  y: number;
  width: number;
  height: number;
  main: boolean;
  playlistId: number | null;
  fit?: 'CONTAIN' | 'COVER';
}

export interface LayoutRequest {
  name: string;
  description: string | null;
  zones: LayoutZoneRequest[];
  /** A versão para TV em pé (opcional); vazio ou ausente = sem versão em pé. */
  portraitZones?: LayoutZoneRequest[];
}

/** Modelo pronto de layout para começar de um deles. */
export interface LayoutTemplate {
  id: string;
  name: string;
  description: string;
  zones: LayoutZoneRequest[];
  /** A sugestão do modelo para TV em pé (vazio = serve como está nas duas orientações). */
  portraitZones?: LayoutZoneRequest[];
}

// ---------- widgets (conteúdo dinâmico) ----------

export type WidgetKind = 'CLOCK' | 'WEATHER' | 'NEWS' | 'RATES' | 'NOTICE' | 'COUNTDOWN' | 'PRICES' | 'QUEUE';

/** As escolhas do painel para um widget (o servidor confere e limpa; chaves de cada tipo em docs/DECISIONS). */
export type WidgetSettings = Record<string, string | number | boolean | string[] | number[] | null | undefined>;

// ---------- atendimento (filas e senhas) ----------

export type TicketStatus = 'WAITING' | 'CALLING' | 'SERVING' | 'DONE' | 'NO_SHOW' | 'CANCELED';

export interface QueueInfo {
  id: number;
  name: string;
  /** A letra (ou letras) que começa a senha: A001. */
  prefix: string;
  priority: boolean;
  active: boolean;
  timezone: string;
  /** Quantas senhas esperam agora. */
  waiting: number;
  /** Aberta a quem escaneia o QR Code (senha pelo celular). Ausente em servidores antigos. */
  remoteEnabled?: boolean;
  /** O segredo do endereço público da fila (só o Administrador recebe). */
  publicToken?: string | null;
}

export interface QueueRequest {
  name: string;
  prefix: string;
  priority: boolean;
  active: boolean;
  timezone?: string;
  remoteEnabled?: boolean;
}

/** O que a pessoa vê ao escanear o QR Code da fila. */
export interface PublicQueue {
  name: string;
  open: boolean;
  waiting: number;
}

/** A própria senha de quem a pegou pelo celular (sem nome nem dados de outras pessoas). */
export interface PublicTicket {
  ticketToken: string | null;
  label: string;
  queueName: string;
  status: TicketStatus;
  counter: string | null;
  /** Quantas pessoas estão na frente (só enquanto a senha espera). */
  ahead: number;
  waiting: number;
  calledAtMs: number | null;
  callCount: number;
}


export interface Ticket {
  id: number;
  label: string;
  queueId: number;
  queueName: string | null;
  priority: boolean;
  status: TicketStatus;
  counterId: number | null;
  counterName: string | null;
  customerName: string | null;
  source: string;
  issuedAt: string;
  calledAt: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  callCount: number;
}

export interface CounterInfo {
  id: number;
  name: string;
  /** As filas que o guichê atende (vazio = todas). */
  queueIds: number[];
  active: boolean;
  /** A senha que o guichê está chamando ou atendendo agora. */
  current: Ticket | null;
}

export interface CounterRequest {
  name: string;
  queueIds: number[];
  active: boolean;
}

export interface WaitingTicket {
  id: number;
  label: string;
  queueId: number;
  queueName: string;
  priority: boolean;
  customerName: string | null;
  source: string;
  issuedAt: string;
  waitingSeconds: number;
}

export interface AttendanceToday {
  issued: number;
  waiting: number;
  serving: number;
  served: number;
  noShow: number;
  canceled: number;
  averageWaitSeconds: number | null;
  averageServiceSeconds: number | null;
}

export interface AttendanceOverview {
  queues: QueueInfo[];
  counters: CounterInfo[];
  waiting: WaitingTicket[];
  today: AttendanceToday;
  serverTimeMs: number;
}

export type TicketAction = 'recall' | 'start' | 'finish' | 'no-show' | 'cancel';

export interface WidgetInfo {
  kind: WidgetKind;
  settings: WidgetSettings;
}

export interface WidgetRequest {
  name: string | null;
  kind: WidgetKind;
  settings: WidgetSettings;
  durationSeconds: number | null;
}

export interface WidgetCatalog {
  kinds: { kind: WidgetKind; label: string; defaultSeconds: number; needsData: boolean }[];
  newsSources: { id: string; name: string }[];
  currencies: string[];
}

export interface WidgetPlace {
  name: string;
  region: string;
  country: string;
  latitude: number;
  longitude: number;
  timezone: string;
}

/** O que a TV mostraria agora: as escolhas já limpas pelo servidor e os dados (nulos se não deu para buscar; error diz por quê). */
export interface WidgetPreview {
  kind: WidgetKind;
  settings: WidgetSettings;
  data: Record<string, unknown> | null;
  error: string | null;
}

// ---------- tabelas de preços ----------

export interface PriceItem {
  id: string;
  category: string | null;
  name: string;
  description: string | null;
  price: number;
  unit: string | null;
  available: boolean;
  highlight: boolean;
}

/** Uma variação de preço por horário (dias 1 = segunda ... 7 = domingo; vazio = todos). */
export interface PriceRule {
  name: string;
  days: number[];
  start: string | null;
  end: string | null;
  percent: number;
  itemIds: string[];
  itemPrices: Record<string, number>;
}

export interface PriceList {
  id: number;
  name: string;
  currency: string;
  timezone: string;
  items: PriceItem[];
  rules: PriceRule[];
  /** Quantos widgets da biblioteca mostram esta tabela. */
  widgetCount: number;
  updatedAt: string;
}

export interface PriceListRequest {
  name: string;
  currency: string;
  timezone: string;
  items: PriceItem[];
  rules: PriceRule[];
}

/** Um item já com o preço de um instante; basePrice só vem quando uma variação mudou o preço. */
export interface PriceLine extends PriceItem {
  basePrice: number | null;
  rule: string | null;
}

export interface PriceSegment {
  fromMs: number;
  toMs: number;
  rule: string | null;
  items: PriceLine[];
}

/** Os preços das próximas 24 horas (o que a TV recebe e o painel usa na pré-visualização). */
export interface PriceBoardData {
  list: { id: number | null; name: string; currency: string };
  timezone: string;
  segments: PriceSegment[];
  fetchedAtMs: number;
}

export interface PriceImportResult {
  list: PriceList;
  imported: number;
  skipped: string[];
}

export interface WebContentRequest {
  name: string | null;
  url: string;
  durationSeconds: number;
}

// ---------- clientes, equipe, planos e cobrança (fase 6) ----------

export type Role = 'VIEWER' | 'EDITOR' | 'ADMIN' | 'PLATFORM_ADMIN';
export type TenantStatus = 'SYSTEM' | 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'SUSPENDED' | 'CANCELED';

/** Limite nulo = sem limite. */
/** Recursos que um plano pode incluir ou não (o servidor barra o que o plano não tem). */
export type FeatureCode = 'CAMPAIGNS' | 'LAYOUTS' | 'WIDGETS' | 'PRICES' | 'QUEUE' | 'PROOF_OF_PLAY';

export interface PlanInfo {
  id: number;
  code: string;
  name: string;
  priceCents: number;
  currency: string;
  maxDevices: number | null;
  maxStorageMb: number | null;
  maxUsers: number | null;
  /** Tamanho máximo de cada arquivo enviado (MB); nulo = sem limite. */
  maxUploadMb: number | null;
  features: FeatureCode[];
}

/** O que o cliente está usando do plano (TVs ativadas, espaço das mídias em bytes, usuários ativos). */
export interface Usage {
  devices: number;
  storageBytes: number;
  users: number;
}

export interface TenantSummary {
  id: number;
  name: string;
  slug: string;
  status: TenantStatus;
  plan: PlanInfo;
  usage: Usage;
  trialEndsAt: string | null;
  currentPeriodEnd: string | null;
  billingEmail: string | null;
}

/** Quem está logado e em que cliente está atuando. */
export interface Me {
  id: number;
  name: string;
  email: string;
  role: Role;
  platformAdmin: boolean;
  tenant: TenantSummary;
}

export interface TeamMember {
  id: number;
  name: string;
  email: string;
  role: Role;
  active: boolean;
  createdAt: string;
}

export interface TeamMemberRequest {
  name: string;
  email: string;
  password: string;
  role: Role;
}

/** Campos ausentes não mudam. */
export interface TeamMemberUpdate {
  name?: string;
  role?: Role;
  active?: boolean;
  password?: string;
}

/** Gestor da plataforma (quem opera clientes, planos, cupons e faturas). */
export interface Manager {
  id: number;
  name: string;
  email: string;
  active: boolean;
  createdAt: string;
}

export interface ManagerRequest {
  name: string;
  email: string;
  password: string;
}

/** Campos ausentes não mudam. */
export interface ManagerUpdate {
  name?: string;
  active?: boolean;
  password?: string;
}

export type InvoiceStatus = 'OPEN' | 'PAID' | 'VOID';

export interface Invoice {
  id: number;
  tenantId: number;
  tenantName: string | null;
  number: string;
  description: string;
  periodStart: string | null;
  periodEnd: string | null;
  amountCents: number;
  currency: string;
  status: InvoiceStatus;
  dueDate: string;
  /** Aberta e já vencida. */
  overdue: boolean;
  paidAt: string | null;
  paymentMethod: string | null;
  paymentNote: string | null;
  createdAt: string;
}

export interface PlanOption {
  id: number;
  code: string;
  name: string;
  description: string | null;
  priceCents: number;
  currency: string;
  maxDevices: number | null;
  maxStorageMb: number | null;
  maxUsers: number | null;
  maxUploadMb: number | null;
  features: FeatureCode[];
}

/** "Plano e cobrança" do cliente. */
export interface BillingInfo {
  tenant: TenantSummary;
  document: string | null;
  invoices: Invoice[];
  companyName: string;
  paymentInstructions: string | null;
  supportEmail: string | null;
  plans: PlanOption[];
}

export interface PlatformPlan extends PlanOption {
  active: boolean;
  sortOrder: number;
  tenantCount: number;
}

export interface PlatformPlanRequest {
  code: string;
  name: string;
  description: string | null;
  priceCents: number;
  maxDevices: number | null;
  maxStorageMb: number | null;
  maxUsers: number | null;
  maxUploadMb: number | null;
  features: FeatureCode[];
  active: boolean;
  sortOrder: number;
}

export interface PlatformTenantRequest {
  name: string;
  planId: number;
  adminName: string;
  adminEmail: string;
  adminPassword: string;
  billingEmail: string | null;
  document: string | null;
}

/** Campos ausentes não mudam. */
export interface PlatformTenantUpdate {
  name?: string;
  planId?: number;
  status?: TenantStatus;
  billingEmail?: string;
  document?: string;
  notes?: string;
  trialEndsAt?: string;
  currentPeriodEnd?: string;
}

export interface PlatformSettings {
  companyName: string;
  paymentInstructions: string | null;
  supportEmail: string | null;
  trialDays: number;
  graceDays: number;
  invoiceLeadDays: number;
}

export interface PlatformOverview {
  tenants: number;
  tenantsByStatus: Partial<Record<TenantStatus, number>>;
  devices: number;
  monthlyRecurringCents: number;
  openInvoices: number;
  openInvoicesCents: number;
  overdueInvoices: number;
  overdueInvoicesCents: number;
}
