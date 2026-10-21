import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { TPipe, tr } from '../core/i18n';
import { Device, DeviceGroup, Playlist } from '../core/models';
import { Notice } from '../core/notice';

@Component({
  selector: 'app-groups',
  imports: [FormsModule, RouterLink, TPipe],
  template: `
    <div class="head">
      <div>
        <h1>{{ 'Grupos' | t }}</h1>
        <p>{{ 'Conjuntos de TVs para agir em várias de uma vez: escolher a playlist, atualizar o aplicativo ou filtrar.' | t }}</p>
      </div>
      @if (auth.can('admin')) {
        <button class="primary" (click)="openNew()">{{ '+ Novo grupo' | t }}</button>
      }
    </div>

    @if (notice.text(); as text) {
      <div class="notice" role="status">{{ text }}</div>
    }

    <div class="card">
      @if (groups().length === 0) {
        <div class="empty">
          @if (loaded()) {
            {{ 'Nenhum grupo ainda. Crie um (ex.: "Recepções") e escolha quais TVs fazem parte.' | t }}
          } @else {
            {{ 'Carregando…' | t }}
          }
        </div>
      } @else {
        <table class="stack">
          <tr><th>{{ 'Grupo' | t }}</th><th>{{ 'TVs' | t }}</th><th>{{ 'Playlist para todas as TVs do grupo' | t }}</th><th></th></tr>
          @for (group of groups(); track group.id) {
            <tr>
              <td class="first">
                <b>{{ group.name }}</b>
                @if (group.description) {
                  <br /><small>{{ group.description }}</small>
                }
              </td>
              <td [attr.data-label]="'TVs' | t">
                @if (group.deviceCount > 0) {
                  <span>
                    <a routerLink="/dispositivos" [queryParams]="{ grupo: group.id }">{{ group.deviceCount }}</a>
                    <br /><small>{{ '{n} online' | t: { n: group.onlineCount } }}</small>
                  </span>
                } @else {
                  0
                }
              </td>
              <td [attr.data-label]="'Playlist' | t">
                <select
                  [attr.aria-label]="'Playlist do grupo {name}' | t: { name: group.name }"
                  [disabled]="group.deviceCount === 0"
                  (change)="applyPlaylist(group, $event)"
                >
                  <option value="">{{ 'Escolher…' | t }}</option>
                  <option value="none">{{ 'Nenhuma playlist' | t }}</option>
                  @for (playlist of playlists(); track playlist.id) {
                    <option [value]="playlist.id">{{ playlist.name }}</option>
                  }
                </select>
              </td>
              <td class="row-actions">
                @if (auth.can('admin')) {
                  <button class="secondary small" (click)="openEdit(group)">{{ 'Editar' | t }}</button>
                  <button class="secondary small" [disabled]="group.deviceCount === 0" (click)="checkUpdate(group)">{{ 'Atualizar app' | t }}</button>
                  <button class="danger small" (click)="remove(group)">{{ 'Excluir' | t }}</button>
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
          <h2>{{ (modal() === 'new' ? 'Novo grupo' : 'Editar grupo') | t }}</h2>
          <div class="field">
            <label for="grp-name">{{ 'Nome do grupo' | t }}</label>
            <input id="grp-name" name="name" [placeholder]="'Ex.: Recepções' | t" required [ngModel]="name()" (ngModelChange)="name.set($event)" />
          </div>
          <div class="field">
            <label for="grp-desc">{{ 'Descrição (opcional)' | t }}</label>
            <input id="grp-desc" name="description" [placeholder]="'Ex.: TVs da recepção de todas as lojas' | t" [ngModel]="description()" (ngModelChange)="description.set($event)" />
          </div>
          <div class="field">
            <label>{{ 'TVs do grupo ({selected} de {total})' | t: { selected: members().size, total: devices().length } }}</label>
            @if (devices().length === 0) {
              <small>{{ 'Ainda não há TVs. Ative uma em Dispositivos.' | t }}</small>
            } @else {
              <div class="picker">
                @for (device of devices(); track device.id) {
                  <label class="check">
                    <input type="checkbox" [checked]="members().has(device.id)" (change)="toggleMember(device.id)" />
                    <span>{{ device.name }} <small>{{ device.location || ('Sem local' | t) }}</small></span>
                  </label>
                }
              </div>
            }
          </div>
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">{{ 'Cancelar' | t }}</button>
            <button type="submit" class="primary" [disabled]="busy() || !name().trim()">{{ 'Salvar' | t }}</button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .row-actions { white-space: nowrap; text-align: right; }
    .row-actions button { margin-left: 6px; }
    select { min-width: 190px; width: auto; }
    @media (max-width: 720px) { select { min-width: 0; width: 100%; } }
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
        this.notice.show(editing ? tr('Grupo "{name}" salvo.', { name: saved.name }) : tr('Grupo "{name}" criado.', { name: saved.name }));
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
    const question =
      value === 'none'
        ? tr('Aplicar nenhuma playlist às {n} TV(s) do grupo "{group}"?', { n: group.deviceCount, group: group.name })
        : tr('Aplicar a playlist "{playlist}" às {n} TV(s) do grupo "{group}"?', {
            playlist: playlist?.name,
            n: group.deviceCount,
            group: group.name,
          });
    if (!confirm(question)) {
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
            ? tr('Playlist removida de {n} TV(s) do grupo "{group}".', { n: affected, group: group.name })
            : tr('Playlist aplicada a {n} TV(s) do grupo "{group}". Elas atualizam em até 1 minuto.', { n: affected, group: group.name }),
        ),
      error: () => undefined,
    });
  }

  checkUpdate(group: DeviceGroup): void {
    this.api.bulk({ action: 'CHECK_UPDATE', targetGroupId: group.id }).subscribe({
      next: ({ affected }) =>
        this.notice.show(
          affected === 0
            ? tr('Nenhuma TV ativa neste grupo para avisar.')
            : tr('Pedido enviado a {n} TV(s) do grupo "{group}". Se houver versão nova, alguém precisa confirmar em cada TV.', {
                n: affected,
                group: group.name,
              }),
        ),
      error: () => undefined,
    });
  }

  remove(group: DeviceGroup): void {
    if (!confirm(tr('Excluir o grupo "{name}"? As TVs não são apagadas, só deixam de fazer parte do grupo.', { name: group.name }))) {
      return;
    }
    this.api.deleteGroup(group.id).subscribe({
      next: () => {
        this.notice.show(tr('Grupo "{name}" excluído.', { name: group.name }));
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
