export type DeviceStatus = 'PENDING' | 'ACTIVE' | 'DISABLED';
export type MediaType = 'IMAGE' | 'VIDEO';

export interface Device {
  id: number;
  name: string;
  location: string | null;
  status: DeviceStatus;
  online: boolean;
  lastSeenAt: string | null;
  playerVersion: string | null;
  currentPlaylistId: number | null;
  configVersion: number;
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
  location?: string | null;
  status?: string;
  currentPlaylistId?: number | null;
}
