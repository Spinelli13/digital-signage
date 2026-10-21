import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { TPipe, tr } from '../core/i18n';
import { hasFilters } from '../core/device-filters';
import { deviceState, timeAgo } from '../core/format';
import {
  BulkAction,
  BulkRequest,
  Connection,
  Device,
  DeviceFilters,
  DeviceGroup,
  Layout,
  Location,
  Playlist,
  ReleaseInfo,
} from '../core/models';
import { Notice } from '../core/notice';
import { isOutdated } from '../core/versions';

const REFRESH_MS = 10_000;
const SEARCH_DEBOUNCE_MS = 300;

/** Escolha do local no formulário: '' = sem local, 'new' = digitar um local novo, ou o id de um local. */
type LocationChoice = string;

@Component({
  selector: 'app-devices',
  imports: [FormsModule, RouterLink, TPipe],
  template: `
    <div class="head">
      <div><h1>{{ 'Dispositivos' | t }}</h1><p>{{ 'Gerencie todas as telas e Players conectados.' | t }}</p></div>
      @if (auth.can('admin')) {
        <div class="head-actions">
          <button class="secondary" (click)="updateAll()" [title]="'Pede a todas as TVs que verifiquem agora se há versão nova do Player' | t">{{ '⬆ Atualizar todas as TVs' | t }}</button>
          <button class="primary" (click)="openActivate()">{{ '+ Adicionar dispositivo' | t }}</button>
        </div>
      }
    </div>

    @if (notice.text(); as text) {
      <div class="notice" role="status">{{ text }}</div>
    }

    <div class="card filters">
      <input
        type="search"
        class="search"
        [attr.aria-label]="'Buscar TV' | t"
        [placeholder]="'Buscar por nome ou local…' | t"
        [ngModel]="filters().q ?? ''"
        (ngModelChange)="onSearch($event)"
      />
      <select [attr.aria-label]="'Situação' | t" [ngModel]="filters().connection ?? ''" (ngModelChange)="setConnection($event)">
        <option value="">{{ 'Todas as situações' | t }}</option>
        <option value="ONLINE">{{ 'Online' | t }}</option>
        <option value="OFFLINE">{{ 'Offline' | t }}</option>
        <option value="NEVER">{{ 'Nunca conectou' | t }}</option>
        <option value="PENDING">{{ 'Aguardando ativação' | t }}</option>
        <option value="DISABLED">{{ 'Desativado' | t }}</option>
      </select>
      <select [attr.aria-label]="'Local' | t" [ngModel]="locationFilterValue()" (ngModelChange)="setLocationFilter($event)">
        <option value="">{{ 'Todos os locais' | t }}</option>
        <option value="none">{{ 'Sem local' | t }}</option>
        @for (location of locations(); track location.id) {
          <option [value]="location.id">{{ location.name }}</option>
        }
      </select>
      <select [attr.aria-label]="'Grupo' | t" [ngModel]="groupFilterValue()" (ngModelChange)="setGroupFilter($event)">
        <option value="">{{ 'Todos os grupos' | t }}</option>
        @for (group of groups(); track group.id) {
          <option [value]="group.id">{{ group.name }}</option>
        }
      </select>
      @if (filtersActive()) {
        <button class="secondary small" (click)="clearFilters()">{{ 'Limpar filtros' | t }}</button>
      }
      <span class="count">{{ '{n} TV(s)' | t: { n: devices().length } }}</span>
    </div>

    @if (selectedCount() > 0 && auth.can('content')) {
      <div class="bulk" role="region" [attr.aria-label]="'Ações nas TVs selecionadas' | t">
        <b>{{ '{n} selecionada(s)' | t: { n: selectedCount() } }}</b>
        <select [attr.aria-label]="'Playlist das selecionadas' | t" (change)="onBulkPlaylist($event)">
          <option value="">{{ 'Escolher playlist…' | t }}</option>
          <option value="none">{{ 'Nenhuma playlist' | t }}</option>
          @for (playlist of playlists(); track playlist.id) {
            <option [value]="playlist.id">{{ playlist.name }}</option>
          }
        </select>
        @if (layouts().length > 0) {
          <select [attr.aria-label]="'Layout das selecionadas' | t" (change)="onBulkLayout($event)">
            <option value="">{{ 'Aplicar layout…' | t }}</option>
            <option value="none">{{ 'Tela cheia' | t }}</option>
            @for (layout of layouts(); track layout.id) {
              <option [value]="layout.id">{{ layout.name }}</option>
            }
          </select>
        }
        @if (auth.can('admin')) {
          <button class="secondary small" (click)="bulkSimple('DISABLE')">{{ 'Desativar' | t }}</button>
          <button class="secondary small" (click)="bulkSimple('ENABLE')">{{ 'Reativar' | t }}</button>
          <button class="secondary small" (click)="bulkSimple('CHECK_UPDATE')">{{ 'Atualizar app' | t }}</button>
          <select [attr.aria-label]="'Mover as selecionadas para um local' | t" (change)="onBulkLocation($event)">
            <option value="">{{ 'Mover para local…' | t }}</option>
            <option value="none">{{ 'Sem local' | t }}</option>
            @for (location of locations(); track location.id) {
              <option [value]="location.id">{{ location.name }}</option>
            }
          </select>
          @if (groups().length > 0) {
            <select [attr.aria-label]="'Adicionar as selecionadas a um grupo' | t" (change)="onBulkGroup($event, true)">
              <option value="">{{ 'Adicionar ao grupo…' | t }}</option>
              @for (group of groups(); track group.id) {
                <option [value]="group.id">{{ group.name }}</option>
              }
            </select>
            <select [attr.aria-label]="'Tirar as selecionadas de um grupo' | t" (change)="onBulkGroup($event, false)">
              <option value="">{{ 'Tirar do grupo…' | t }}</option>
              @for (group of groups(); track group.id) {
                <option [value]="group.id">{{ group.name }}</option>
              }
            </select>
          }
        }
        <button class="secondary small" (click)="clearSelection()">{{ 'Limpar seleção' | t }}</button>
      </div>
    }

    <div class="card">
      @if (devices().length === 0) {
        <div class="empty">
          @if (filtersActive()) {
            {{ 'Nenhuma TV com esses filtros.' | t }}
          } @else if (loaded()) {
            {{ 'Nenhum dispositivo ainda. Abra o app Player na TV: ele mostra um código de 6 caracteres.' | t }}
            {{ 'Clique em' | t }} <b>{{ 'Adicionar dispositivo' | t }}</b> {{ 'e digite esse código.' | t }}
          } @else {
            {{ 'Carregando…' | t }}
          }
        </div>
      } @else {
        <table class="stack">
          <tr>
            <th class="check-col">
              <input type="checkbox" [attr.aria-label]="'Selecionar todas as TVs da lista' | t" [checked]="allSelected()" (change)="toggleAll()" />
            </th>
            <th>{{ 'Dispositivo' | t }}</th><th>{{ 'Status' | t }}</th><th>{{ 'Último contato' | t }}</th><th>{{ 'Playlist' | t }}</th><th></th>
          </tr>
          @for (device of devices(); track device.id) {
            <tr [class.selected]="isSelected(device.id)">
              <td class="check-col">
                <input type="checkbox" [attr.aria-label]="'Selecionar {name}' | t: { name: device.name }" [checked]="isSelected(device.id)" (change)="toggle(device.id)" />
              </td>
              <td class="first">
                <b>{{ device.name }}</b><br /><small>{{ device.location || ('Sem local' | t) }}</small>
                @for (group of groupsOf(device); track group.id) {
                  <span class="chip">{{ group.name }}</span>
                }
                @if (device.layoutName) {
                  <span class="chip layout" [title]="'Layout da tela desta TV' | t">🧩 {{ device.layoutName }}</span>
                }
              </td>
              <td [attr.data-label]="'Status' | t"><span class="st"><span class="dot" [class]="state(device).tone"></span>{{ state(device).label }}</span></td>
              <td [attr.data-label]="'Último contato' | t"><span>{{ ago(device.lastSeenAt) }}<br /><small>{{ device.playerVersion ? 'v' + device.playerVersion : '' }}</small>
                @if (outdated(device)) {
                  <br /><small class="stale" [title]="'Há uma versão mais nova do Player; ele se atualiza sozinho (alguém precisa confirmar na TV).' | t">{{ '⬆ desatualizado (última: v{version})' | t: { version: latest()?.versionName } }}</small>
                }</span></td>
              <td [attr.data-label]="'Playlist' | t">
                @if (device.status === 'ACTIVE') {
                  <span class="plist">
                    <select [ngModel]="device.currentPlaylistId" [disabled]="!auth.can('content')" (ngModelChange)="assignPlaylist(device, $event)">
                      <option [ngValue]="null">{{ 'Nenhuma playlist' | t }}</option>
                      @for (playlist of playlists(); track playlist.id) {
                        <option [ngValue]="playlist.id">{{ playlist.name }}</option>
                      }
                    </select>
                    @if (device.campaignName) {
                      <br /><small class="campaign" [title]="'Uma campanha vale agora para esta TV e tem prioridade sobre a playlist dela.' | t">{{ '📣 Agora: campanha {name}' | t: { name: device.campaignName } }}</small>
                    }
                    @if (device.currentMediaName) {
                      <br /><small class="onscreen" [title]="'A mídia que o Player disse estar exibindo no último contato.' | t">{{ '▶ Na tela: {name}' | t: { name: device.currentMediaName } }}</small>
                    }
                    @if (syncing(device)) {
                      <br /><small>{{ 'sincronizando…' | t }}</small>
                    }
                  </span>
                } @else {
                  <span class="muted">—</span>
                }
              </td>
              <td class="row-actions">
                <a class="secondary small btn" [routerLink]="['/dispositivos', device.id, 'historico']" [title]="'Quando conectou, caiu, mudou de conteúdo e o que o Player relatou' | t">{{ 'Histórico' | t }}</a>
                @if (auth.can('content')) {
                  <button class="secondary small" (click)="openEdit(device)">{{ 'Editar' | t }}</button>
                }
                @if (auth.can('admin')) {
                  @if (device.status === 'ACTIVE') {
                    <button class="secondary small" (click)="checkUpdate(device)" [title]="'Pede que esta TV verifique agora se há versão nova do Player' | t">{{ 'Atualizar app' | t }}</button>
                    <button class="secondary small" (click)="setStatus(device, 'disabled')">{{ 'Desativar' | t }}</button>
                  } @else if (device.status === 'DISABLED') {
                    <button class="secondary small" (click)="setStatus(device, 'active')">{{ 'Reativar' | t }}</button>
                  }
                  <button class="danger small" (click)="remove(device)">{{ 'Excluir' | t }}</button>
                }
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
            <h2>{{ 'Adicionar dispositivo' | t }}</h2>
            <p class="label">{{ 'Vincule um novo Player usando o código exibido na TV.' | t }}</p>
            <div class="field">
              <label for="code">{{ 'Código de ativação' | t }}</label>
              <input id="code" name="code" class="mono" maxlength="6" [placeholder]="'Ex.: 8F32A9' | t" required
                [ngModel]="code()" (ngModelChange)="code.set($event.toUpperCase())" />
            </div>
          } @else {
            <h2>{{ 'Editar dispositivo' | t }}</h2>
          }
          @if (auth.can('admin')) {
          <div class="field">
            <label for="name">{{ 'Nome do dispositivo' | t }}</label>
            <input id="name" name="name" [placeholder]="'Ex.: TV Recepção' | t" required
              [ngModel]="name()" (ngModelChange)="name.set($event)" />
          </div>
          <div class="field">
            <label for="location">{{ 'Local' | t }}</label>
            <select id="location" name="location" [ngModel]="locationChoice()" (ngModelChange)="locationChoice.set($event)">
              <option value="">{{ 'Sem local' | t }}</option>
              @for (location of locations(); track location.id) {
                <option [value]="location.id">{{ location.name }}</option>
              }
              <option value="new">{{ '+ Novo local…' | t }}</option>
            </select>
            @if (locationChoice() === 'new') {
              <input id="new-location" name="newLocation" class="below" [placeholder]="'Nome do novo local (ex.: Loja Centro)' | t"
                [ngModel]="newLocation()" (ngModelChange)="newLocation.set($event)" />
            }
          </div>
          } @else {
            <p class="label">{{ '{name} — como Editor, você escolhe o layout da tela. Nome, local e grupos são do Administrador.' | t: { name: name() } }}</p>
          }
          @if (modal() === 'edit' && layouts().length > 0) {
            <div class="field">
              <label for="layout">{{ 'Layout da tela' | t }}</label>
              <select id="layout" name="layout" [ngModel]="layoutChoice()" (ngModelChange)="layoutChoice.set($event)">
                <option value="">{{ 'Tela cheia (um conteúdo só)' | t }}</option>
                @for (layout of layouts(); track layout.id) {
                  <option [value]="layout.id">{{ layout.name }}</option>
                }
              </select>
            </div>
          }
          @if (groups().length > 0 && auth.can('admin')) {
            <div class="field">
              <label>{{ 'Grupos' | t }}</label>
              <div class="chips">
                @for (group of groups(); track group.id) {
                  <label class="check">
                    <input type="checkbox" [checked]="modalGroups().has(group.id)" (change)="toggleModalGroup(group.id)" />
                    {{ group.name }}
                  </label>
                }
              </div>
            </div>
          }
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">{{ 'Cancelar' | t }}</button>
            <button type="submit" class="primary" [disabled]="busy()">
              {{ (modal() === 'activate' ? 'Ativar dispositivo' : 'Salvar') | t }}
            </button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .row-actions { white-space: nowrap; text-align: right; }
    .row-actions button, .row-actions .btn { margin-left: 6px; }
    select { min-width: 170px; }
    .stale { color: var(--orange); font-weight: 600; }
    .campaign { color: var(--link); font-weight: 600; }
    .onscreen { color: var(--muted); }
    .btn { display: inline-block; margin-left: 6px; border: 1px solid var(--line); border-radius: 10px; font-weight: 600; color: var(--text); }
    .btn:hover { background: var(--primary-soft); }
    .head-actions { display: flex; gap: 10px; flex-wrap: wrap; }
    .filters { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; margin-bottom: 14px; padding: 14px; }
    .filters .search { flex: 1 1 220px; min-width: 180px; width: auto; }
    .filters select { min-width: 150px; width: auto; }
    .count { color: var(--muted); font-size: 13px; margin-left: auto; }
    .bulk { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; background: var(--primary-soft); border: 1px solid var(--primary-line); border-radius: 12px; padding: 10px 14px; margin-bottom: 14px; }
    .bulk select { min-width: 140px; width: auto; }
    .check-col { width: 34px; }
    tr.selected { background: var(--primary-soft); }
    .chip.layout { background: var(--ok-bg); color: var(--ok-fg); }
    .chip { display: inline-block; background: var(--panel-2); color: var(--muted); border: 1px solid var(--line); border-radius: 999px; padding: 1px 9px; font-size: 12px; margin: 4px 4px 0 0; }
    .chips { display: flex; gap: 6px 14px; flex-wrap: wrap; }
    .below { margin-top: 8px; }
    @media (max-width: 720px) {
      .filters { flex-direction: column; align-items: stretch; }
      .filters .search, .filters select { width: 100%; min-width: 0; flex: none; }
      .count { margin-left: 0; }
      .bulk { flex-direction: column; align-items: stretch; }
      .bulk select { width: 100%; }
      .plist select { min-width: 0; width: 100%; }
      table.stack td .plist { display: block; width: 100%; text-align: right; }
      table.stack tr.selected { border-color: var(--primary); }
      table.stack td.first { padding-right: 36px; }
      table.stack td .btn { margin-left: 0; }
    }
  `,
})
export class DevicesPage implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);

  readonly devices = signal<Device[]>([]);
  readonly playlists = signal<Playlist[]>([]);
  readonly locations = signal<Location[]>([]);
  readonly groups = signal<DeviceGroup[]>([]);
  readonly layouts = signal<Layout[]>([]);
  /** Versão mais recente do Player publicada, para destacar TVs desatualizadas. */
  readonly latest = signal<ReleaseInfo | null>(null);
  readonly loaded = signal(false);
  readonly notice = new Notice();

  readonly filters = signal<DeviceFilters>({});
  readonly filtersActive = computed(() => hasFilters(this.filters()));
  readonly locationFilterValue = computed(() => String(this.filters().location ?? ''));
  readonly groupFilterValue = computed(() => String(this.filters().groupId ?? ''));
  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  readonly selected = signal<Set<number>>(new Set());
  readonly selectedCount = computed(() => this.selected().size);
  readonly allSelected = computed(() => this.devices().length > 0 && this.devices().every((d) => this.selected().has(d.id)));

  readonly modal = signal<'activate' | 'edit' | null>(null);
  readonly busy = signal(false);
  readonly code = signal('');
  readonly name = signal('');
  readonly locationChoice = signal<LocationChoice>('');
  readonly newLocation = signal('');
  /** Layout escolhido no formulário: '' = tela cheia, ou o id de um layout. */
  readonly layoutChoice = signal('');
  readonly modalGroups = signal<Set<number>>(new Set());
  private editing: Device | null = null;

  readonly state = deviceState;
  readonly ago = (iso: string | null) => timeAgo(iso);

  outdated(device: Device): boolean {
    return isOutdated(device.playerVersion, this.latest()?.versionName);
  }

  /** A TV ainda não sincronizou o conteúdo que deveria estar exibindo. Servidor antigo (sem a versão desejada): compara com a playlist. */
  syncing(device: Device): boolean {
    if (device.status !== 'ACTIVE') {
      return false;
    }
    if (device.desiredConfigVersion !== undefined) {
      return device.configVersion !== device.desiredConfigVersion;
    }
    return device.currentPlaylistId !== null && device.configVersion < this.versionOf(device.effectivePlaylistId ?? device.currentPlaylistId);
  }

  groupsOf(device: Device): DeviceGroup[] {
    return this.groups().filter((group) => device.groupIds.includes(group.id));
  }

  ngOnInit(): void {
    this.readQueryParams();
    // Em silêncio: sem o site de downloads só deixamos de destacar TVs desatualizadas.
    this.api.releases(true).subscribe({ next: (list) => this.latest.set(list[0] ?? null), error: () => undefined });
    this.refresh();
    const timer = setInterval(() => this.refresh(), REFRESH_MS);
    this.destroyRef.onDestroy(() => {
      clearInterval(timer);
      clearTimeout(this.searchTimer);
      this.notice.dispose();
    });
  }

  /** Links do dashboard, dos locais e dos grupos chegam aqui já filtrados (?local=3, ?grupo=2, ?situacao=OFFLINE). */
  private readQueryParams(): void {
    const params = this.route.snapshot.queryParamMap;
    const filters: DeviceFilters = {};
    const local = params.get('local');
    if (local === 'none') {
      filters.location = 'none';
    } else if (local && !Number.isNaN(Number(local))) {
      filters.location = Number(local);
    }
    const grupo = params.get('grupo');
    if (grupo && !Number.isNaN(Number(grupo))) {
      filters.groupId = Number(grupo);
    }
    const situacao = params.get('situacao')?.toUpperCase();
    if (situacao && ['ONLINE', 'OFFLINE', 'NEVER', 'PENDING', 'DISABLED'].includes(situacao)) {
      filters.connection = situacao as Connection;
    }
    this.filters.set(filters);
  }

  versionOf(playlistId: number): number {
    return this.playlists().find((p) => p.id === playlistId)?.configVersion ?? 0;
  }

  // ---------- filtros ----------

  onSearch(text: string): void {
    this.filters.update((f) => ({ ...f, q: text }));
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.refresh(), SEARCH_DEBOUNCE_MS);
  }

  setConnection(value: Connection | ''): void {
    this.filters.update((f) => ({ ...f, connection: value }));
    this.refresh();
  }

  setLocationFilter(value: string): void {
    const location = value === '' ? null : value === 'none' ? 'none' : Number(value);
    this.filters.update((f) => ({ ...f, location }));
    this.refresh();
  }

  setGroupFilter(value: string): void {
    this.filters.update((f) => ({ ...f, groupId: value === '' ? null : Number(value) }));
    this.refresh();
  }

  clearFilters(): void {
    this.filters.set({});
    this.refresh();
  }

  // ---------- seleção ----------

  isSelected(id: number): boolean {
    return this.selected().has(id);
  }

  toggle(id: number): void {
    const next = new Set(this.selected());
    if (!next.delete(id)) {
      next.add(id);
    }
    this.selected.set(next);
  }

  /** Marca todas as TVs da lista atual (respeitando os filtros); se já estão todas marcadas, desmarca. */
  toggleAll(): void {
    this.selected.set(this.allSelected() ? new Set() : new Set(this.devices().map((d) => d.id)));
  }

  clearSelection(): void {
    this.selected.set(new Set());
  }

  // ---------- ações em massa ----------

  onBulkPlaylist(event: Event): void {
    const value = this.takeValue(event);
    if (!value) {
      return;
    }
    if (value === 'none') {
      this.runBulk({ action: 'REMOVE_PLAYLIST' });
    } else {
      this.runBulk({ action: 'ASSIGN_PLAYLIST', playlistId: Number(value) });
    }
  }

  onBulkLocation(event: Event): void {
    const value = this.takeValue(event);
    if (!value) {
      return;
    }
    if (value === 'none') {
      this.runBulk({ action: 'CLEAR_LOCATION' });
    } else {
      this.runBulk({ action: 'SET_LOCATION', locationId: Number(value) });
    }
  }

  onBulkLayout(event: Event): void {
    const value = this.takeValue(event);
    if (!value) {
      return;
    }
    if (value === 'none') {
      this.runBulk({ action: 'REMOVE_LAYOUT' });
    } else {
      this.runBulk({ action: 'ASSIGN_LAYOUT', layoutId: Number(value) });
    }
  }

  onBulkGroup(event: Event, add: boolean): void {
    const value = this.takeValue(event);
    if (!value) {
      return;
    }
    this.runBulk({ action: add ? 'ADD_TO_GROUP' : 'REMOVE_FROM_GROUP', groupId: Number(value) });
  }

  bulkSimple(action: 'DISABLE' | 'ENABLE' | 'CHECK_UPDATE'): void {
    if (action === 'DISABLE' && !confirm(tr('Desativar {n} TV(s)? Elas param de exibir até serem reativadas.', { n: this.selectedCount() }))) {
      return;
    }
    this.runBulk({ action });
  }

  private takeValue(event: Event): string {
    const select = event.target as HTMLSelectElement;
    const value = select.value;
    select.value = ''; // volta para o título ("Escolher…") depois de usar
    return value;
  }

  private runBulk(request: Omit<BulkRequest, 'deviceIds'>): void {
    const ids = [...this.selected()];
    if (ids.length === 0) {
      return;
    }
    this.api.bulk({ ...request, deviceIds: ids }).subscribe({
      next: ({ affected }) => {
        this.notice.show(this.bulkMessage(request.action, affected, ids.length));
        this.refresh();
      },
      error: () => undefined,
    });
  }

  private bulkMessage(action: BulkAction, affected: number, selected: number): string {
    if (affected === 0) {
      return tr('Nenhuma das TVs selecionadas estava no estado certo para esta ação.');
    }
    const partial =
      affected < selected ? ' ' + tr('({n} já estavam no estado pedido ou não se aplicam)', { n: selected - affected }) : '';
    switch (action) {
      case 'ASSIGN_PLAYLIST':
        return tr('Playlist aplicada a {n} TV(s). Elas atualizam em até 1 minuto.', { n: affected });
      case 'REMOVE_PLAYLIST':
        return tr('Playlist removida de {n} TV(s).', { n: affected });
      case 'ENABLE':
        return tr('{n} TV(s) reativada(s){partial}.', { n: affected, partial });
      case 'DISABLE':
        return tr('{n} TV(s) desativada(s){partial}.', { n: affected, partial });
      case 'CHECK_UPDATE':
        return tr('Pedido enviado a {n} TV(s){partial}. Se houver versão nova, alguém precisa confirmar em cada TV.', { n: affected, partial });
      case 'SET_LOCATION':
        return tr('{n} TV(s) movida(s) de local.', { n: affected });
      case 'CLEAR_LOCATION':
        return tr('{n} TV(s) agora sem local.', { n: affected });
      case 'ADD_TO_GROUP':
        return tr('{n} TV(s) adicionada(s) ao grupo.', { n: affected });
      case 'REMOVE_FROM_GROUP':
        return tr('{n} TV(s) retirada(s) do grupo.', { n: affected });
      case 'ASSIGN_LAYOUT':
        return tr('Layout aplicado a {n} TV(s). Elas atualizam em até 1 minuto.', { n: affected });
      case 'REMOVE_LAYOUT':
        return tr('{n} TV(s) voltaram para a tela cheia.', { n: affected });
    }
  }

  // ---------- janela de ativar/editar ----------

  openActivate(): void {
    this.code.set('');
    this.name.set('');
    this.locationChoice.set('');
    this.newLocation.set('');
    this.modalGroups.set(new Set());
    this.editing = null;
    this.modal.set('activate');
  }

  openEdit(device: Device): void {
    this.name.set(device.name);
    this.locationChoice.set(device.locationId === null ? '' : String(device.locationId));
    this.newLocation.set('');
    this.modalGroups.set(new Set(device.groupIds));
    this.layoutChoice.set(device.layoutId == null ? '' : String(device.layoutId));
    this.editing = device;
    this.modal.set('edit');
  }

  closeModal(): void {
    this.modal.set(null);
  }

  toggleModalGroup(id: number): void {
    const next = new Set(this.modalGroups());
    if (!next.delete(id)) {
      next.add(id);
    }
    this.modalGroups.set(next);
  }

  submitModal(): void {
    this.busy.set(true);
    const choice = this.locationChoice();
    const typed = this.newLocation().trim();
    const groupIds = [...this.modalGroups()];

    // Local escolhido na lista = por id; digitado = por nome (o servidor cria se ainda não existir).
    // Ao editar, "sem local" precisa ser dito (location vazio); ao ativar, basta omitir.
    const noLocation = choice === '' || (choice === 'new' && !typed);
    const locationFields = noLocation ? { location: '' } : choice === 'new' ? { location: typed } : { locationId: Number(choice) };

    const request =
      this.modal() === 'activate'
        ? this.api.activateDevice({
            activationCode: this.code().trim(),
            name: this.name().trim(),
            groupIds,
            ...(noLocation ? {} : locationFields),
          })
        : this.api.updateDevice(this.editing!.id, {
            name: this.name().trim(),
            // O Editor só escolhe o layout: nome, local e grupos seriam recusados pelo servidor (403).
            ...(this.auth.can('admin') ? { groupIds, ...locationFields } : {}),
            ...(this.layoutChoice() === '' ? {} : { layoutId: Number(this.layoutChoice()) }),
          });

    request.subscribe({
      next: () => {
        // "Tela cheia" precisa de uma chamada própria: layoutId ausente no PUT significa "não mudar".
        const leavingLayout = this.modal() === 'edit' && this.layoutChoice() === '' && this.editing?.layoutId != null;
        if (leavingLayout) {
          this.api.removeDeviceLayout(this.editing!.id).subscribe({ next: () => this.finishModal(), error: () => this.finishModal() });
        } else {
          this.finishModal();
        }
      },
      // O aviso aparece por cima do formulário (interceptor de erros); o formulário continua aberto.
      error: () => this.busy.set(false),
    });
  }

  private finishModal(): void {
    this.busy.set(false);
    this.closeModal();
    this.refresh();
  }

  // ---------- ações de uma TV ----------

  assignPlaylist(device: Device, playlistId: number | null): void {
    if (playlistId === device.currentPlaylistId) {
      return;
    }
    const request =
      playlistId === null
        ? this.api.removeDevicePlaylist(device.id)
        : this.api.updateDevice(device.id, { name: device.name, currentPlaylistId: playlistId });
    request.subscribe({
      next: () => this.refresh(),
      error: () => this.refresh(),
    });
  }

  setStatus(device: Device, status: 'active' | 'disabled'): void {
    this.api.updateDevice(device.id, { name: device.name, status }).subscribe({
      next: () => this.refresh(),
      error: () => undefined,
    });
  }

  checkUpdate(device: Device): void {
    this.api.requestUpdateCheck(device.id).subscribe({
      next: () =>
        this.notice.show(
          tr('Pedido enviado a "{name}". Ela verifica em até 30 segundos; se houver versão nova, alguém precisa confirmar na TV.', { name: device.name }),
        ),
      error: () => undefined,
    });
  }

  updateAll(): void {
    this.api.requestUpdateCheckAll().subscribe({
      next: ({ requested }) =>
        this.notice.show(
          requested === 0
            ? tr('Nenhuma TV ativa para avisar.')
            : tr('Pedido enviado a {n} TV(s). Elas verificam em até 30 segundos; se houver versão nova, alguém precisa confirmar em cada TV.', {
                n: requested,
              }),
        ),
      error: () => undefined,
    });
  }

  remove(device: Device): void {
    if (!confirm(tr('Excluir "{name}"? A TV volta a pedir um novo código de ativação.', { name: device.name }))) {
      return;
    }
    this.api.deleteDevice(device.id).subscribe({
      next: () => this.refresh(),
      error: () => undefined,
    });
  }

  private refresh(): void {
    forkJoin({
      devices: this.api.devices(this.filters()),
      playlists: this.api.playlists(),
      locations: this.api.locations(),
      groups: this.api.groups(),
      layouts: this.api.layouts(),
    }).subscribe({
      next: ({ devices, playlists, locations, groups, layouts }) => {
        this.devices.set(devices);
        this.playlists.set(playlists);
        this.locations.set(locations);
        this.groups.set(groups);
        this.layouts.set(layouts);
        this.loaded.set(true);
        // TVs que saíram da lista (filtro novo, exclusão) deixam de estar selecionadas
        const visible = new Set(devices.map((d) => d.id));
        const kept = [...this.selected()].filter((id) => visible.has(id));
        if (kept.length !== this.selected().size) {
          this.selected.set(new Set(kept));
        }
      },
      error: () => this.loaded.set(true),
    });
  }
}
