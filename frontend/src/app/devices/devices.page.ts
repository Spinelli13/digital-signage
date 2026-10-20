import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { ApiService } from '../core/api.service';
import { deviceState, timeAgo } from '../core/format';
import { Device, Playlist, ReleaseInfo } from '../core/models';
import { isOutdated } from '../core/versions';

const REFRESH_MS = 10_000;

@Component({
  selector: 'app-devices',
  imports: [FormsModule],
  template: `
    <div class="head">
      <div><h1>Dispositivos</h1><p>Gerencie todas as telas e Players conectados.</p></div>
      <button class="primary" (click)="openActivate()">+ Adicionar dispositivo</button>
    </div>

    <div class="card">
      @if (devices().length === 0) {
        <div class="empty">
          Nenhum dispositivo ainda. Abra o app Player na TV: ele mostra um código de 6 caracteres.
          Clique em <b>Adicionar dispositivo</b> e digite esse código.
        </div>
      } @else {
        <table>
          <tr>
            <th>Dispositivo</th><th>Status</th><th>Último contato</th><th>Playlist</th><th></th>
          </tr>
          @for (device of devices(); track device.id) {
            <tr>
              <td>
                <b>{{ device.name }}</b><br /><small>{{ device.location || 'Sem local' }}</small>
              </td>
              <td><span class="dot" [class]="state(device).tone"></span>{{ state(device).label }}</td>
              <td>{{ ago(device.lastSeenAt) }}<br /><small>{{ device.playerVersion ? 'v' + device.playerVersion : '' }}</small>
                @if (outdated(device)) {
                  <br /><small class="stale" title="Há uma versão mais nova do Player; ele se atualiza sozinho (alguém precisa confirmar na TV).">⬆ desatualizado (última: v{{ latest()?.versionName }})</small>
                }</td>
              <td>
                @if (device.status === 'ACTIVE') {
                  <select [ngModel]="device.currentPlaylistId" (ngModelChange)="assignPlaylist(device, $event)">
                    <option [ngValue]="null">Nenhuma playlist</option>
                    @for (playlist of playlists(); track playlist.id) {
                      <option [ngValue]="playlist.id">{{ playlist.name }}</option>
                    }
                  </select>
                  @if (device.currentPlaylistId !== null && device.configVersion < versionOf(device.currentPlaylistId)) {
                    <small>sincronizando…</small>
                  }
                } @else {
                  <span class="muted">—</span>
                }
              </td>
              <td class="row-actions">
                <button class="secondary small" (click)="openEdit(device)">Editar</button>
                @if (device.status === 'ACTIVE') {
                  <button class="secondary small" (click)="setStatus(device, 'disabled')">Desativar</button>
                } @else if (device.status === 'DISABLED') {
                  <button class="secondary small" (click)="setStatus(device, 'active')">Reativar</button>
                }
                <button class="danger small" (click)="remove(device)">Excluir</button>
              </td>
            </tr>
          }
        </table>
      }
    </div>

    @if (modal() !== null) {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="submitModal()">
          @if (modal() === 'activate') {
            <h2>Adicionar dispositivo</h2>
            <p class="label">Vincule um novo Player usando o código exibido na TV.</p>
            <div class="field">
              <label for="code">Código de ativação</label>
              <input id="code" name="code" class="mono" maxlength="6" placeholder="Ex.: 8F32A9" required
                [ngModel]="code()" (ngModelChange)="code.set($event.toUpperCase())" />
            </div>
          } @else {
            <h2>Editar dispositivo</h2>
          }
          <div class="field">
            <label for="name">Nome do dispositivo</label>
            <input id="name" name="name" placeholder="Ex.: TV Recepção" required
              [ngModel]="name()" (ngModelChange)="name.set($event)" />
          </div>
          <div class="field">
            <label for="location">Local</label>
            <input id="location" name="location" placeholder="Ex.: Loja Centro"
              [ngModel]="location()" (ngModelChange)="location.set($event)" />
          </div>
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">Cancelar</button>
            <button type="submit" class="primary" [disabled]="busy()">
              {{ modal() === 'activate' ? 'Ativar dispositivo' : 'Salvar' }}
            </button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .row-actions { white-space: nowrap; text-align: right; }
    .row-actions button { margin-left: 6px; }
    select { min-width: 170px; }
    .stale { color: var(--orange); font-weight: 600; }
  `,
})
export class DevicesPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly devices = signal<Device[]>([]);
  readonly playlists = signal<Playlist[]>([]);
  /** Versão mais recente do Player publicada, para destacar TVs desatualizadas. */
  readonly latest = signal<ReleaseInfo | null>(null);

  readonly modal = signal<'activate' | 'edit' | null>(null);
  readonly busy = signal(false);
  readonly code = signal('');
  readonly name = signal('');
  readonly location = signal('');
  private editing: Device | null = null;

  readonly state = deviceState;
  readonly ago = (iso: string | null) => timeAgo(iso);

  outdated(device: Device): boolean {
    return isOutdated(device.playerVersion, this.latest()?.versionName);
  }

  ngOnInit(): void {
    // Em silêncio: sem o site de downloads só deixamos de destacar TVs desatualizadas.
    this.api.releases(true).subscribe({ next: (list) => this.latest.set(list[0] ?? null), error: () => undefined });
    this.refresh();
    const timer = setInterval(() => this.refresh(), REFRESH_MS);
    this.destroyRef.onDestroy(() => clearInterval(timer));
  }

  versionOf(playlistId: number): number {
    return this.playlists().find((p) => p.id === playlistId)?.configVersion ?? 0;
  }

  openActivate(): void {
    this.code.set('');
    this.name.set('');
    this.location.set('');
    this.editing = null;
    this.modal.set('activate');
  }

  openEdit(device: Device): void {
    this.name.set(device.name);
    this.location.set(device.location ?? '');
    this.editing = device;
    this.modal.set('edit');
  }

  closeModal(): void {
    this.modal.set(null);
  }

  submitModal(): void {
    this.busy.set(true);
    const location = this.location().trim() || null;

    const request =
      this.modal() === 'activate'
        ? this.api.activateDevice({ activationCode: this.code().trim(), name: this.name().trim(), location })
        : this.api.updateDevice(this.editing!.id, { name: this.name().trim(), location });

    request.subscribe({
      next: () => {
        this.busy.set(false);
        this.closeModal();
        this.refresh();
      },
      // O aviso aparece por cima do formulário (interceptor de erros); o formulário continua aberto.
      error: () => this.busy.set(false),
    });
  }

  assignPlaylist(device: Device, playlistId: number | null): void {
    if (playlistId === device.currentPlaylistId) {
      return;
    }
    const request =
      playlistId === null
        ? this.api.removeDevicePlaylist(device.id)
        : this.api.updateDevice(device.id, { name: device.name, location: device.location, currentPlaylistId: playlistId });
    request.subscribe({
      next: () => this.refresh(),
      error: () => this.refresh(),
    });
  }

  setStatus(device: Device, status: 'active' | 'disabled'): void {
    this.api.updateDevice(device.id, { name: device.name, location: device.location, status }).subscribe({
      next: () => this.refresh(),
      error: () => undefined,
    });
  }

  remove(device: Device): void {
    if (!confirm(`Excluir "${device.name}"? A TV volta a pedir um novo código de ativação.`)) {
      return;
    }
    this.api.deleteDevice(device.id).subscribe({
      next: () => this.refresh(),
      error: () => undefined,
    });
  }

  private refresh(): void {
    forkJoin({ devices: this.api.devices(), playlists: this.api.playlists() }).subscribe({
      next: ({ devices, playlists }) => {
        this.devices.set(devices);
        this.playlists.set(playlists);
      },
      error: () => undefined,
    });
  }
}
