import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
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
  imports: [FormsModule, RouterLink],
  template: `
    <div class="head">
      <div><h1>Dispositivos</h1><p>Gerencie todas as telas e Players conectados.</p></div>
      @if (auth.can('admin')) {
        <div class="head-actions">
          <button class="secondary" (click)="updateAll()" title="Pede a todas as TVs que verifiquem agora se há versão nova do Player">⬆ Atualizar todas as TVs</button>
          <button class="primary" (click)="openActivate()">+ Adicionar dispositivo</button>
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
        aria-label="Buscar TV"
        placeholder="Buscar por nome ou local…"
        [ngModel]="filters().q ?? ''"
        (ngModelChange)="onSearch($event)"
      />
      <select aria-label="Situação" [ngModel]="filters().connection ?? ''" (ngModelChange)="setConnection($event)">
        <option value="">Todas as situações</option>
        <option value="ONLINE">Online</option>
        <option value="OFFLINE">Offline</option>
        <option value="NEVER">Nunca conectou</option>
        <option value="PENDING">Aguardando ativação</option>
        <option value="DISABLED">Desativado</option>
      </select>
      <select aria-label="Local" [ngModel]="locationFilterValue()" (ngModelChange)="setLocationFilter($event)">
        <option value="">Todos os locais</option>
        <option value="none">Sem local</option>
        @for (location of locations(); track location.id) {
          <option [value]="location.id">{{ location.name }}</option>
        }
      </select>
      <select aria-label="Grupo" [ngModel]="groupFilterValue()" (ngModelChange)="setGroupFilter($event)">
        <option value="">Todos os grupos</option>
        @for (group of groups(); track group.id) {
          <option [value]="group.id">{{ group.name }}</option>
        }
      </select>
      @if (filtersActive()) {
        <button class="secondary small" (click)="clearFilters()">Limpar filtros</button>
      }
      <span class="count">{{ devices().length }} TV(s)</span>
    </div>

    @if (selectedCount() > 0 && auth.can('content')) {
      <div class="bulk" role="region" aria-label="Ações nas TVs selecionadas">
        <b>{{ selectedCount() }} selecionada(s)</b>
        <select aria-label="Playlist das selecionadas" (change)="onBulkPlaylist($event)">
          <option value="">Escolher playlist…</option>
          <option value="none">Nenhuma playlist</option>
          @for (playlist of playlists(); track playlist.id) {
            <option [value]="playlist.id">{{ playlist.name }}</option>
          }
        </select>
        @if (layouts().length > 0) {
          <select aria-label="Layout das selecionadas" (change)="onBulkLayout($event)">
            <option value="">Aplicar layout…</option>
            <option value="none">Tela cheia</option>
            @for (layout of layouts(); track layout.id) {
              <option [value]="layout.id">{{ layout.name }}</option>
            }
          </select>
        }
        @if (auth.can('admin')) {
          <button class="secondary small" (click)="bulkSimple('DISABLE')">Desativar</button>
          <button class="secondary small" (click)="bulkSimple('ENABLE')">Reativar</button>
          <button class="secondary small" (click)="bulkSimple('CHECK_UPDATE')">Atualizar app</button>
          <select aria-label="Mover as selecionadas para um local" (change)="onBulkLocation($event)">
            <option value="">Mover para local…</option>
            <option value="none">Sem local</option>
            @for (location of locations(); track location.id) {
              <option [value]="location.id">{{ location.name }}</option>
            }
          </select>
          @if (groups().length > 0) {
            <select aria-label="Adicionar as selecionadas a um grupo" (change)="onBulkGroup($event, true)">
              <option value="">Adicionar ao grupo…</option>
              @for (group of groups(); track group.id) {
                <option [value]="group.id">{{ group.name }}</option>
              }
            </select>
            <select aria-label="Tirar as selecionadas de um grupo" (change)="onBulkGroup($event, false)">
              <option value="">Tirar do grupo…</option>
              @for (group of groups(); track group.id) {
                <option [value]="group.id">{{ group.name }}</option>
              }
            </select>
          }
        }
        <button class="secondary small" (click)="clearSelection()">Limpar seleção</button>
      </div>
    }

    <div class="card">
      @if (devices().length === 0) {
        <div class="empty">
          @if (filtersActive()) {
            Nenhuma TV com esses filtros.
          } @else if (loaded()) {
            Nenhum dispositivo ainda. Abra o app Player na TV: ele mostra um código de 6 caracteres.
            Clique em <b>Adicionar dispositivo</b> e digite esse código.
          } @else {
            Carregando…
          }
        </div>
      } @else {
        <table>
          <tr>
            <th class="check-col">
              <input type="checkbox" aria-label="Selecionar todas as TVs da lista" [checked]="allSelected()" (change)="toggleAll()" />
            </th>
            <th>Dispositivo</th><th>Status</th><th>Último contato</th><th>Playlist</th><th></th>
          </tr>
          @for (device of devices(); track device.id) {
            <tr [class.selected]="isSelected(device.id)">
              <td class="check-col">
                <input type="checkbox" [attr.aria-label]="'Selecionar ' + device.name" [checked]="isSelected(device.id)" (change)="toggle(device.id)" />
              </td>
              <td>
                <b>{{ device.name }}</b><br /><small>{{ device.location || 'Sem local' }}</small>
                @for (group of groupsOf(device); track group.id) {
                  <span class="chip">{{ group.name }}</span>
                }
                @if (device.layoutName) {
                  <span class="chip layout" title="Layout da tela desta TV">🧩 {{ device.layoutName }}</span>
                }
              </td>
              <td><span class="dot" [class]="state(device).tone"></span>{{ state(device).label }}</td>
              <td>{{ ago(device.lastSeenAt) }}<br /><small>{{ device.playerVersion ? 'v' + device.playerVersion : '' }}</small>
                @if (outdated(device)) {
                  <br /><small class="stale" title="Há uma versão mais nova do Player; ele se atualiza sozinho (alguém precisa confirmar na TV).">⬆ desatualizado (última: v{{ latest()?.versionName }})</small>
                }</td>
              <td>
                @if (device.status === 'ACTIVE') {
                  <select [ngModel]="device.currentPlaylistId" [disabled]="!auth.can('content')" (ngModelChange)="assignPlaylist(device, $event)">
                    <option [ngValue]="null">Nenhuma playlist</option>
                    @for (playlist of playlists(); track playlist.id) {
                      <option [ngValue]="playlist.id">{{ playlist.name }}</option>
                    }
                  </select>
                  @if (device.campaignName) {
                    <br /><small class="campaign" title="Uma campanha vale agora para esta TV e tem prioridade sobre a playlist dela.">📣 Agora: campanha {{ device.campaignName }}</small>
                  }
                  @if (device.currentMediaName) {
                    <br /><small class="onscreen" title="A mídia que o Player disse estar exibindo no último contato.">▶ Na tela: {{ device.currentMediaName }}</small>
                  }
                  @if (syncing(device)) {
                    <br /><small>sincronizando…</small>
                  }
                } @else {
                  <span class="muted">—</span>
                }
              </td>
              <td class="row-actions">
                <a class="secondary small btn" [routerLink]="['/dispositivos', device.id, 'historico']" title="Quando conectou, caiu, mudou de conteúdo e o que o Player relatou">Histórico</a>
                @if (auth.can('content')) {
                  <button class="secondary small" (click)="openEdit(device)">Editar</button>
                }
                @if (auth.can('admin')) {
                  @if (device.status === 'ACTIVE') {
                    <button class="secondary small" (click)="checkUpdate(device)" title="Pede que esta TV verifique agora se há versão nova do Player">Atualizar app</button>
                    <button class="secondary small" (click)="setStatus(device, 'disabled')">Desativar</button>
                  } @else if (device.status === 'DISABLED') {
                    <button class="secondary small" (click)="setStatus(device, 'active')">Reativar</button>
                  }
                  <button class="danger small" (click)="remove(device)">Excluir</button>
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
          @if (auth.can('admin')) {
          <div class="field">
            <label for="name">Nome do dispositivo</label>
            <input id="name" name="name" placeholder="Ex.: TV Recepção" required
              [ngModel]="name()" (ngModelChange)="name.set($event)" />
          </div>
          <div class="field">
            <label for="location">Local</label>
            <select id="location" name="location" [ngModel]="locationChoice()" (ngModelChange)="locationChoice.set($event)">
              <option value="">Sem local</option>
              @for (location of locations(); track location.id) {
                <option [value]="location.id">{{ location.name }}</option>
              }
              <option value="new">+ Novo local…</option>
            </select>
            @if (locationChoice() === 'new') {
              <input id="new-location" name="newLocation" class="below" placeholder="Nome do novo local (ex.: Loja Centro)"
                [ngModel]="newLocation()" (ngModelChange)="newLocation.set($event)" />
            }
          </div>
          } @else {
            <p class="label">{{ name() }} — como Editor, você escolhe o layout da tela. Nome, local e grupos são do Administrador.</p>
          }
          @if (modal() === 'edit' && layouts().length > 0) {
            <div class="field">
              <label for="layout">Layout da tela</label>
              <select id="layout" name="layout" [ngModel]="layoutChoice()" (ngModelChange)="layoutChoice.set($event)">
                <option value="">Tela cheia (um conteúdo só)</option>
                @for (layout of layouts(); track layout.id) {
                  <option [value]="layout.id">{{ layout.name }}</option>
                }
              </select>
            </div>
          }
          @if (groups().length > 0 && auth.can('admin')) {
            <div class="field">
              <label>Grupos</label>
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
    .row-actions button, .row-actions .btn { margin-left: 6px; }
    select { min-width: 170px; }
    .stale { color: var(--orange); font-weight: 600; }
    .campaign { color: var(--blue); font-weight: 600; }
    .onscreen { color: var(--muted); }
    .btn { display: inline-block; margin-left: 6px; border: 1px solid var(--line); border-radius: 10px; font-weight: 600; color: var(--text); }
    .btn:hover { background: var(--blue2); }
    .head-actions { display: flex; gap: 10px; flex-wrap: wrap; }
    .filters { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; margin-bottom: 14px; padding: 14px; }
    .filters .search { flex: 1 1 220px; min-width: 180px; width: auto; }
    .filters select { min-width: 150px; width: auto; }
    .count { color: var(--muted); font-size: 13px; margin-left: auto; }
    .bulk { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; background: #eef3ff; border: 1px solid #c9d8ff; border-radius: 12px; padding: 10px 14px; margin-bottom: 14px; }
    .bulk select { min-width: 140px; width: auto; }
    .check-col { width: 34px; }
    tr.selected { background: #f3f7ff; }
    .chip.layout { background: #e8f7f0; color: #0f6b46; }
    .chip { display: inline-block; background: #eef1f7; color: #44506a; border-radius: 999px; padding: 1px 9px; font-size: 12px; margin: 4px 4px 0 0; }
    .chips { display: flex; gap: 6px 14px; flex-wrap: wrap; }
    .below { margin-top: 8px; }
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
    if (action === 'DISABLE' && !confirm(`Desativar ${this.selectedCount()} TV(s)? Elas param de exibir até serem reativadas.`)) {
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
      return 'Nenhuma das TVs selecionadas estava no estado certo para esta ação.';
    }
    const partial = affected < selected ? ` (${selected - affected} já estavam no estado pedido ou não se aplicam)` : '';
    switch (action) {
      case 'ASSIGN_PLAYLIST':
        return `Playlist aplicada a ${affected} TV(s). Elas atualizam em até 1 minuto.`;
      case 'REMOVE_PLAYLIST':
        return `Playlist removida de ${affected} TV(s).`;
      case 'ENABLE':
        return `${affected} TV(s) reativada(s)${partial}.`;
      case 'DISABLE':
        return `${affected} TV(s) desativada(s)${partial}.`;
      case 'CHECK_UPDATE':
        return `Pedido enviado a ${affected} TV(s)${partial}. Se houver versão nova, alguém precisa confirmar em cada TV.`;
      case 'SET_LOCATION':
        return `${affected} TV(s) movida(s) de local.`;
      case 'CLEAR_LOCATION':
        return `${affected} TV(s) agora sem local.`;
      case 'ADD_TO_GROUP':
        return `${affected} TV(s) adicionada(s) ao grupo.`;
      case 'REMOVE_FROM_GROUP':
        return `${affected} TV(s) retirada(s) do grupo.`;
      case 'ASSIGN_LAYOUT':
        return `Layout aplicado a ${affected} TV(s). Elas atualizam em até 1 minuto.`;
      case 'REMOVE_LAYOUT':
        return `${affected} TV(s) voltaram para a tela cheia.`;
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
      next: () => this.notice.show(`Pedido enviado a "${device.name}". Ela verifica em até 30 segundos; se houver versão nova, alguém precisa confirmar na TV.`),
      error: () => undefined,
    });
  }

  updateAll(): void {
    this.api.requestUpdateCheckAll().subscribe({
      next: ({ requested }) =>
        this.notice.show(
          requested === 0
            ? 'Nenhuma TV ativa para avisar.'
            : `Pedido enviado a ${requested} TV(s). Elas verificam em até 30 segundos; se houver versão nova, alguém precisa confirmar em cada TV.`,
        ),
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
