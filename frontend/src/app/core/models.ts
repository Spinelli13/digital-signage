export type DeviceStatus = 'PENDING' | 'ACTIVE' | 'DISABLED';
export type MediaType = 'IMAGE' | 'VIDEO';
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

export interface DeviceUpdateRequest {
  name: string;
  /** Nome do local: um nome novo cria o local; vazio tira a TV do local; ausente não muda. */
  location?: string | null;
  locationId?: number | null;
  status?: string;
  currentPlaylistId?: number | null;
  /** Lista completa de grupos da TV; ausente não muda, lista vazia tira de todos. */
  groupIds?: number[];
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
  | 'REMOVE_FROM_GROUP';

/** Escolha exatamente UM alvo: deviceIds, targetGroupId ou targetLocationId. */
export interface BulkRequest {
  action: BulkAction;
  deviceIds?: number[];
  targetGroupId?: number;
  targetLocationId?: number;
  playlistId?: number;
  locationId?: number;
  groupId?: number;
}

export interface Summary {
  devices: { total: number; online: number; offline: number; never: number; pending: number; disabled: number };
  locations: number;
  groups: number;
  media: number;
  playlists: number;
  campaigns: { total: number; activeNow: number };
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
