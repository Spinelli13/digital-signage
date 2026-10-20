import { HttpClient, HttpEvent } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ConfigService } from './config.service';
import { Device, DeviceUpdateRequest, Media, MediaUrl, Playlist, PlaylistRequest } from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ConfigService);

  private url(path: string): string {
    return `${this.config.apiBaseUrl}/api/v1${path}`;
  }

  // dispositivos
  devices(): Observable<Device[]> {
    return this.http.get<Device[]>(this.url('/devices'));
  }

  activateDevice(body: { activationCode: string; name: string; location: string | null }): Observable<Device> {
    return this.http.post<Device>(this.url('/devices/activate'), body);
  }

  updateDevice(id: number, body: DeviceUpdateRequest): Observable<Device> {
    return this.http.put<Device>(this.url(`/devices/${id}`), body);
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

  mediaUrl(id: number): Observable<MediaUrl> {
    return this.http.get<MediaUrl>(this.url(`/media/${id}/url`));
  }

  /** Baixa o arquivo pelo backend (necessário quando a mídia fica em disco local e exige o JWT). */
  mediaBlob(relativeUrl: string): Observable<Blob> {
    return this.http.get(`${this.config.apiBaseUrl}${relativeUrl}`, { responseType: 'blob' });
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
