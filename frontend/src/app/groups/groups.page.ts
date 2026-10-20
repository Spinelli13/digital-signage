import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Device, DeviceGroup, Playlist } from '../core/models';
import { Notice } from '../core/notice';

@Component({
  selector: 'app-groups',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="head">
      <div>
        <h1>Grupos</h1>
        <p>Conjuntos de TVs para agir em várias de uma vez: escolher a playlist, atualizar o aplicativo ou filtrar.</p>
      </div>
      @if (auth.can('admin')) {
        <button class="primary" (click)="openNew()">+ Novo grupo</button>
      }
    </div>

    @if (notice.text(); as text) {
      <div class="notice" role="status">{{ text }}</div>
    }

    <div class="card">
      @if (groups().length === 0) {
        <div class="empty">
          @if (loaded()) {
            Nenhum grupo ainda. Crie um (ex.: "Recepções") e escolha quais TVs fazem parte.
          } @else {
            Carregando…
          }
        </div>
      } @else {
        <table>
          <tr><th>Grupo</th><th>TVs</th><th>Playlist para todas as TVs do grupo</th><th></th></tr>
          @for (group of groups(); track group.id) {
            <tr>
              <td>
                <b>{{ group.name }}</b>
                @if (group.description) {
                  <br /><small>{{ group.description }}</small>
                }
              </td>
              <td>
                @if (group.deviceCount > 0) {
                  <a routerLink="/dispositivos" [queryParams]="{ grupo: group.id }">{{ group.deviceCount }}</a>
                  <br /><small>{{ group.onlineCount }} online</small>
                } @else {
                  0
                }
              </td>
              <td>
                <select
                  [attr.aria-label]="'Playlist do grupo ' + group.name"
                  [disabled]="group.deviceCount === 0"
                  (change)="applyPlaylist(group, $event)"
                >
                  <option value="">Escolher…</option>
                  <option value="none">Nenhuma playlist</option>
                  @for (playlist of playlists(); track playlist.id) {
                    <option [value]="playlist.id">{{ playlist.name }}</option>
                  }
                </select>
              </td>
              <td class="row-actions">
                @if (auth.can('admin')) {
                  <button class="secondary small" (click)="openEdit(group)">Editar</button>
                  <button class="secondary small" [disabled]="group.deviceCount === 0" (click)="checkUpdate(group)">Atualizar app</button>
                  <button class="danger small" (click)="remove(group)">Excluir</button>
                }
              </td>
            </tr>
          }
        </table>
      }
    </div>

    @if (modal() !== null) {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="submit()">
          <h2>{{ modal() === 'new' ? 'Novo grupo' : 'Editar grupo' }}</h2>
          <div class="field">
            <label for="grp-name">Nome do grupo</label>
            <input id="grp-name" name="name" placeholder="Ex.: Recepções" required [ngModel]="name()" (ngModelChange)="name.set($event)" />
          </div>
          <div class="field">
            <label for="grp-desc">Descrição (opcional)</label>
            <input id="grp-desc" name="description" placeholder="Ex.: TVs da recepção de todas as lojas" [ngModel]="description()" (ngModelChange)="description.set($event)" />
          </div>
          <div class="field">
            <label>TVs do grupo ({{ members().size }} de {{ devices().length }})</label>
            @if (devices().length === 0) {
              <small>Ainda não há TVs. Ative uma em Dispositivos.</small>
            } @else {
              <div class="picker">
                @for (device of devices(); track device.id) {
                  <label class="check">
                    <input type="checkbox" [checked]="members().has(device.id)" (change)="toggleMember(device.id)" />
                    <span>{{ device.name }} <small>{{ device.location || 'Sem local' }}</small></span>
                  </label>
                }
              </div>
            }
          </div>
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">Cancelar</button>
            <button type="submit" class="primary" [disabled]="busy() || !name().trim()">Salvar</button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .row-actions { white-space: nowrap; text-align: right; }
    .row-actions button { margin-left: 6px; }
    select { min-width: 190px; width: auto; }
    .picker { max-height: 260px; overflow: auto; border: 1px solid var(--line); border-radius: 10px; padding: 6px 10px; }
  `,
})
export class GroupsPage implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly groups = signal<DeviceGroup[]>([]);
  readonly devices = signal<Device[]>([]);
  readonly playlists = signal<Playlist[]>([]);
  readonly loaded = signal(false);
  readonly notice = new Notice();

  readonly modal = signal<'new' | 'edit' | null>(null);
  readonly busy = signal(false);
  readonly name = signal('');
  readonly description = signal('');
  readonly members = signal<Set<number>>(new Set());
  private editing: DeviceGroup | null = null;

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.notice.dispose());
    this.load();
  }

  openNew(): void {
    this.editing = null;
    this.name.set('');
    this.description.set('');
    this.members.set(new Set());
    this.modal.set('new');
  }

  openEdit(group: DeviceGroup): void {
    this.editing = group;
    this.name.set(group.name);
    this.description.set(group.description ?? '');
    this.members.set(new Set(group.deviceIds));
    this.modal.set('edit');
  }

  closeModal(): void {
    this.modal.set(null);
  }

  toggleMember(deviceId: number): void {
    const next = new Set(this.members());
    if (!next.delete(deviceId)) {
      next.add(deviceId);
    }
    this.members.set(next);
  }

  submit(): void {
    const name = this.name().trim();
    if (!name) {
      return;
    }
    const body = { name, description: this.description().trim() || null, deviceIds: [...this.members()] };
    this.busy.set(true);
    const editing = this.editing;
    const request = editing ? this.api.updateGroup(editing.id, body) : this.api.createGroup(body);
    request.subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.closeModal();
        this.notice.show(editing ? `Grupo "${saved.name}" salvo.` : `Grupo "${saved.name}" criado.`);
        this.load();
      },
      error: () => this.busy.set(false),
    });
  }

  /** Aplica a playlist escolhida a todas as TVs do grupo (ou tira a playlist de todas). */
  applyPlaylist(group: DeviceGroup, event: Event): void {
    const select = event.target as HTMLSelectElement;
    const value = select.value;
    select.value = '';
    if (!value) {
      return;
    }
    const playlist = this.playlists().find((p) => String(p.id) === value);
    const label = value === 'none' ? 'nenhuma playlist' : `a playlist "${playlist?.name}"`;
    if (!confirm(`Aplicar ${label} às ${group.deviceCount} TV(s) do grupo "${group.name}"?`)) {
      return;
    }
    const request =
      value === 'none'
        ? this.api.bulk({ action: 'REMOVE_PLAYLIST', targetGroupId: group.id })
        : this.api.bulk({ action: 'ASSIGN_PLAYLIST', targetGroupId: group.id, playlistId: Number(value) });
    request.subscribe({
      next: ({ affected }) =>
        this.notice.show(
          value === 'none'
            ? `Playlist removida de ${affected} TV(s) do grupo "${group.name}".`
            : `Playlist aplicada a ${affected} TV(s) do grupo "${group.name}". Elas atualizam em até 1 minuto.`,
        ),
      error: () => undefined,
    });
  }

  checkUpdate(group: DeviceGroup): void {
    this.api.bulk({ action: 'CHECK_UPDATE', targetGroupId: group.id }).subscribe({
      next: ({ affected }) =>
        this.notice.show(
          affected === 0
            ? 'Nenhuma TV ativa neste grupo para avisar.'
            : `Pedido enviado a ${affected} TV(s) do grupo "${group.name}". Se houver versão nova, alguém precisa confirmar em cada TV.`,
        ),
      error: () => undefined,
    });
  }

  remove(group: DeviceGroup): void {
    if (!confirm(`Excluir o grupo "${group.name}"? As TVs não são apagadas, só deixam de fazer parte do grupo.`)) {
      return;
    }
    this.api.deleteGroup(group.id).subscribe({
      next: () => {
        this.notice.show(`Grupo "${group.name}" excluído.`);
        this.load();
      },
      error: () => undefined,
    });
  }

  private load(): void {
    forkJoin({ groups: this.api.groups(), devices: this.api.devices(), playlists: this.api.playlists() }).subscribe({
      next: ({ groups, devices, playlists }) => {
        this.groups.set(groups);
        this.devices.set(devices);
        this.playlists.set(playlists);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }
}
