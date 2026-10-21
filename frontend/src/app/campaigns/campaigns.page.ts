import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Campaign, CampaignPreview, CampaignState, Device, DeviceGroup, Location, Playlist } from '../core/models';
import { TPipe, tr } from '../core/i18n';
import { Notice } from '../core/notice';
import { IconName } from '../shared/icons';
import { EmptyStateComponent, MenuComponent, PageHeaderComponent, PillComponent, SkeletonComponent, Tone } from '../shared/ui';
import { IconComponent } from '../shared/icon';
import {
  ALL_DAYS,
  CampaignForm,
  TIMEZONES,
  dayLabel,
  describeSchedule,
  describeTargets,
  emptyForm,
  formFrom,
  stateBadge,
  toRequest,
  validateForm,
} from './campaign-form';

const REFRESH_MS = 15_000;

/** Como cada situação aparece: ícone + cor (o texto sempre acompanha, nunca só a cor). */
const STATE_LOOK: Record<CampaignState, { icon: IconName; tone: Tone }> = {
  ACTIVE: { icon: 'play', tone: 'ok' },
  WAITING: { icon: 'clock', tone: 'warn' },
  SCHEDULED: { icon: 'calendar-clock', tone: 'info' },
  ENDED: { icon: 'circle-check', tone: 'neutral' },
  PAUSED: { icon: 'circle-pause', tone: 'neutral' },
};

type IdSet = 'locationIds' | 'groupIds' | 'deviceIds';

@Component({
  selector: 'app-campaigns',
  imports: [FormsModule, TPipe, IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent, MenuComponent],
  template: `
    <app-page-header [title]="'Campanhas' | t" [subtitle]="'Uma playlist que só vale em certas datas, dias e horários, para as TVs que você escolher.' | t" icon="megaphone">
      @if (auth.can('content')) {
        <button class="primary" (click)="openNew()"><app-icon name="plus" />{{ 'Nova campanha' | t }}</button>
      }
    </app-page-header>

    @if (notice.text(); as text) {
      <div class="notice" role="status"><app-icon name="circle-check" [size]="18" /><span>{{ text }}</span></div>
    }

    <div class="card">
      @if (campaigns().length === 0) {
        @if (loaded()) {
          <app-empty-state icon="megaphone" [title]="'Nenhuma campanha ainda' | t"
            [text]="'Sem campanhas, cada TV exibe a sua playlist. Crie uma para, por exemplo, trocar o conteúdo nos fins de semana ou durante uma promoção.' | t">
            @if (auth.can('content')) {
              <button class="primary" (click)="openNew()"><app-icon name="plus" />{{ 'Nova campanha' | t }}</button>
            }
          </app-empty-state>
        } @else {
          <app-skeleton [rows]="4" />
        }
      } @else {
        <table class="stack">
          <tr><th>{{ 'Campanha' | t }}</th><th>{{ 'Quando' | t }}</th><th>{{ 'Para quem' | t }}</th><th>{{ 'Prioridade' | t }}</th><th>{{ 'Situação' | t }}</th><th></th></tr>
          @for (campaign of campaigns(); track campaign.id) {
            <tr>
              <td class="first">
                <span class="place">
                  <span class="place-icon"><app-icon name="megaphone" [size]="18" /></span>
                  <span class="place-text">
                    <b>{{ campaign.name }}</b>
                    <small><app-icon name="list-video" [size]="12" />{{ campaign.playlistName || '—' }}</small>
                  </span>
                </span>
              </td>
              <td [attr.data-label]="'Quando' | t"><span class="when"><app-icon name="calendar-clock" [size]="15" />{{ schedule(campaign) }}</span></td>
              <td [attr.data-label]="'Para quem' | t"><span>{{ targets(campaign) }}<br /><small>{{ '{n} TV(s) alcançada(s)' | t: { n: campaign.deviceCount } }}</small></span></td>
              <td [attr.data-label]="'Prioridade' | t"><span class="num prio">{{ campaign.priority }}</span></td>
              <td [attr.data-label]="'Situação' | t">
                <app-pill [tone]="look(campaign).tone" [icon]="look(campaign).icon" [title]="badge(campaign).hint">{{ badge(campaign).label }}</app-pill>
              </td>
              <td class="row-actions">
                @if (auth.can('content')) {
                  <button class="secondary small" (click)="openEdit(campaign)"><app-icon name="pencil" [size]="15" />{{ 'Editar' | t }}</button>
                  <app-menu [label]="'Mais ações' | t">
                    <button type="button" class="menu-item" (click)="togglePause(campaign)"><app-icon [name]="campaign.enabled ? 'circle-pause' : 'play'" />{{ (campaign.enabled ? 'Pausar' : 'Reativar') | t }}</button>
                    <div class="menu-sep"></div>
                    <button type="button" class="menu-item danger" (click)="remove(campaign)"><app-icon name="trash" />{{ 'Excluir' | t }}</button>
                  </app-menu>
                }
              </td>
            </tr>
          }
        </table>
      }
    </div>

    <div class="card simulator">
      <h3 class="sec"><app-icon name="monitor-play" [size]="18" />{{ 'Simular: o que uma TV exibiria?' | t }}</h3>
      <p class="label">{{ 'Escolha uma TV e um momento (no horário do seu computador) para conferir qual campanha venceria, antes de publicar.' | t }}</p>
      <div class="sim-row">
        <select [attr.aria-label]="'TV para simular' | t" [ngModel]="simDevice()" (ngModelChange)="simDevice.set($event)">
          <option [ngValue]="null">{{ 'Escolha uma TV…' | t }}</option>
          @for (device of devices(); track device.id) {
            <option [ngValue]="device.id">{{ device.name }}</option>
          }
        </select>
        <input type="datetime-local" [attr.aria-label]="'Momento da simulação' | t" [ngModel]="simAt()" (ngModelChange)="simAt.set($event)" />
        <button class="secondary" [disabled]="simDevice() === null" (click)="simulate()"><app-icon name="play" [size]="16" />{{ 'Simular' | t }}</button>
      </div>
      @if (simResult(); as result) {
        <div class="sim-result" role="status">
          <app-icon [name]="result.source === 'CAMPAIGN' ? 'megaphone' : 'info'" [size]="18" />
          <span>
            @if (result.source === 'CAMPAIGN') {
              {{ 'A TV exibiria' | t }} <b>{{ result.playlistName }}</b>, {{ 'da campanha' | t }} <b>{{ result.campaignName }}</b>.
            } @else if (result.source === 'BASE') {
              {{ 'Nenhuma campanha vale nesse momento: a TV exibiria a playlist dela,' | t }} <b>{{ result.playlistName }}</b>.
            } @else {
              {{ 'Nenhuma campanha vale nesse momento e a TV não tem playlist: ela mostraria "Aguardando conteúdo".' | t }}
            }
          </span>
        </div>
      }
    </div>

    @if (modal() !== null) {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box wide" (click)="$event.stopPropagation()" (ngSubmit)="submit()">
          <h2>{{ (modal() === 'new' ? 'Nova campanha' : 'Editar campanha') | t }}</h2>

          <div class="field">
            <label for="camp-name">{{ 'Nome' | t }}</label>
            <input id="camp-name" name="name" [placeholder]="'Ex.: Promoção de verão' | t" required [ngModel]="form().name" (ngModelChange)="set('name', $event)" />
          </div>
          <div class="field">
            <label for="camp-desc">{{ 'Descrição (opcional)' | t }}</label>
            <input id="camp-desc" name="description" [ngModel]="form().description" (ngModelChange)="set('description', $event)" />
          </div>

          <div class="row">
            <div class="field grow">
              <label for="camp-playlist">{{ 'Playlist exibida' | t }}</label>
              <select id="camp-playlist" name="playlist" [ngModel]="form().playlistId" (ngModelChange)="set('playlistId', $event)">
                <option [ngValue]="null">{{ 'Escolha…' | t }}</option>
                @for (playlist of playlists(); track playlist.id) {
                  <option [ngValue]="playlist.id">{{ playlist.name }}</option>
                }
              </select>
            </div>
            <div class="field small-field">
              <label for="camp-priority">{{ 'Prioridade' | t }}</label>
              <input id="camp-priority" name="priority" type="number" min="0" max="1000" [ngModel]="form().priority" (ngModelChange)="set('priority', $event)" />
            </div>
          </div>
          <p class="hint">{{ 'Se duas campanhas valem ao mesmo tempo para a mesma TV, vence a de' | t }} <b>{{ 'maior prioridade' | t }}</b>. {{ 'Em empate, a que mira mais de perto (TV, depois grupo, depois local, depois todas) e, por fim, a mais nova.' | t }}</p>

          <h3 class="sec"><app-icon name="calendar-clock" [size]="18" />{{ 'Quando' | t }}</h3>
          <div class="row">
            <div class="field grow">
              <label for="camp-start">{{ 'De (data)' | t }}</label>
              <input id="camp-start" name="startDate" type="date" [ngModel]="form().startDate" (ngModelChange)="set('startDate', $event)" />
            </div>
            <div class="field grow">
              <label for="camp-end">{{ 'Até (data, inclusive)' | t }}</label>
              <input id="camp-end" name="endDate" type="date" [ngModel]="form().endDate" (ngModelChange)="set('endDate', $event)" />
            </div>
          </div>
          <p class="hint">{{ 'Deixe as datas vazias para não limitar o período.' | t }}</p>

          <div class="field">
            <label>{{ 'Dias da semana' | t }}</label>
            <div class="chips">
              @for (day of allDays; track day) {
                <label class="check" [class.on]="form().days.has(day)">
                  <input type="checkbox" [checked]="form().days.has(day)" (change)="toggleDay(day)" /> {{ dayText(day) }}
                </label>
              }
            </div>
          </div>

          <div class="field">
            <label class="check"><input type="checkbox" [checked]="form().allDay" (change)="set('allDay', !form().allDay)" /> {{ 'O dia todo' | t }}</label>
            @if (!form().allDay) {
              <div class="row below">
                <div class="field grow">
                  <label for="camp-from">{{ 'Das' | t }}</label>
                  <input id="camp-from" name="startTime" type="time" [ngModel]="form().startTime" (ngModelChange)="set('startTime', $event)" />
                </div>
                <div class="field grow">
                  <label for="camp-to">{{ 'Às' | t }}</label>
                  <input id="camp-to" name="endTime" type="time" [ngModel]="form().endTime" (ngModelChange)="set('endTime', $event)" />
                </div>
              </div>
              <p class="hint">{{ 'Se o fim for antes do início (ex.: das 22:00 às 06:00), a campanha atravessa a meia-noite e conta pelo dia em que começa.' | t }}</p>
            }
          </div>

          <div class="field">
            <label for="camp-tz">{{ 'Fuso horário das datas e horas' | t }}</label>
            <select id="camp-tz" name="timezone" [ngModel]="form().timezone" (ngModelChange)="set('timezone', $event)">
              @for (zone of timezones; track zone.value) {
                <option [value]="zone.value">{{ zone.label | t }}</option>
              }
            </select>
          </div>

          <h3 class="sec"><app-icon name="monitor" [size]="18" />{{ 'Para quem' | t }}</h3>
          <div class="field">
            <label class="check"><input type="checkbox" [checked]="form().allDevices" (change)="set('allDevices', !form().allDevices)" /> {{ 'Todas as TVs' | t }}</label>
          </div>
          @if (!form().allDevices) {
            <div class="targets">
              <div>
                <b>{{ 'Locais' | t }}</b>
                @for (location of locations(); track location.id) {
                  <label class="check"><input type="checkbox" [checked]="form().locationIds.has(location.id)" (change)="toggleId('locationIds', location.id)" /> {{ location.name }}</label>
                } @empty {
                  <small>{{ 'Nenhum local criado.' | t }}</small>
                }
              </div>
              <div>
                <b>{{ 'Grupos' | t }}</b>
                @for (group of groups(); track group.id) {
                  <label class="check"><input type="checkbox" [checked]="form().groupIds.has(group.id)" (change)="toggleId('groupIds', group.id)" /> {{ group.name }}</label>
                } @empty {
                  <small>{{ 'Nenhum grupo criado.' | t }}</small>
                }
              </div>
              <div>
                <b>{{ 'TVs' | t }}</b>
                @for (device of devices(); track device.id) {
                  <label class="check"><input type="checkbox" [checked]="form().deviceIds.has(device.id)" (change)="toggleId('deviceIds', device.id)" /> {{ device.name }}</label>
                } @empty {
                  <small>{{ 'Nenhuma TV ainda.' | t }}</small>
                }
              </div>
            </div>
          }

          <div class="field">
            <label class="check"><input type="checkbox" [checked]="form().enabled" (change)="set('enabled', !form().enabled)" /> {{ 'Campanha ativada (desmarque para deixá-la pausada)' | t }}</label>
          </div>

          @if (formError(); as error) {
            <div class="error" role="alert"><app-icon name="circle-alert" [size]="18" /><span>{{ error }}</span></div>
          }
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">{{ 'Cancelar' | t }}</button>
            <button type="submit" class="primary" [disabled]="busy()">{{ 'Salvar' | t }}</button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .row-actions { white-space: nowrap; text-align: right; }
    .row-actions > * { margin-left: 6px; vertical-align: middle; }
    .place { display: flex; align-items: center; gap: 12px; min-width: 0; }
    .place-icon { flex: none; display: grid; place-items: center; width: 38px; height: 38px; border-radius: 12px; color: var(--primary); background: var(--primary-soft); border: 1px solid var(--primary-line); }
    :host-context(:root[data-theme='dark']) .place-icon { color: var(--link); }
    .place-text { display: flex; flex-direction: column; min-width: 0; }
    .place-text small { display: flex; align-items: center; gap: 5px; color: var(--muted); max-width: 230px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .when { display: inline-flex; align-items: center; gap: 7px; }
    .when app-icon { color: var(--muted); }
    .prio { display: inline-grid; place-items: center; min-width: 30px; height: 26px; padding: 0 8px; border-radius: 8px; background: var(--panel-3); font-weight: 600; }
    .sec { display: flex; align-items: center; gap: 8px; }
    .sec app-icon { color: var(--primary); }
    :host-context(:root[data-theme='dark']) .sec app-icon { color: var(--link); }
    .simulator { margin-top: 18px; }
    .sim-row { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
    .sim-row select { width: auto; min-width: 220px; }
    .sim-row input { width: auto; }
    .sim-result { display: flex; align-items: flex-start; gap: 10px; margin-top: 12px; background: var(--primary-soft); border: 1px solid var(--primary-line); border-radius: var(--r); padding: 12px 14px; }
    .sim-result app-icon { flex: none; margin-top: 1px; color: var(--primary); }
    @media (max-width: 720px) {
      .sim-row select, .sim-row input, .sim-row button { width: 100%; min-width: 0; }
      .small-field { flex: 1 1 100%; }
    }
    .modal-box.wide { max-width: 720px; }
    .row { display: flex; gap: 12px; flex-wrap: wrap; }
    .row .grow { flex: 1 1 200px; }
    .small-field { flex: 0 0 130px; }
    .hint { color: var(--muted); font-size: 13px; margin: -6px 0 14px; }
    h3 { margin: 18px 0 8px; font-size: 15px; }
    .chips { display: flex; gap: 6px; flex-wrap: wrap; }
    .chips .check { border: 1px solid var(--line); border-radius: 999px; padding: 5px 12px; }
    .chips .check.on { background: var(--primary-soft); border-color: var(--primary); }
    .below { margin-top: 8px; }
    .targets { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 14px; border: 1px solid var(--line); border-radius: 10px; padding: 12px; margin-bottom: 14px; max-height: 240px; overflow: auto; }
    .targets .check { display: flex; margin: 4px 0; }
  `,
})
export class CampaignsPage implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly campaigns = signal<Campaign[]>([]);
  readonly playlists = signal<Playlist[]>([]);
  readonly devices = signal<Device[]>([]);
  readonly locations = signal<Location[]>([]);
  readonly groups = signal<DeviceGroup[]>([]);
  readonly loaded = signal(false);
  readonly notice = new Notice();

  readonly modal = signal<'new' | 'edit' | null>(null);
  readonly busy = signal(false);
  readonly form = signal<CampaignForm>(emptyForm());
  readonly formError = signal<string | null>(null);
  private editing: Campaign | null = null;

  readonly simDevice = signal<number | null>(null);
  readonly simAt = signal('');
  readonly simResult = signal<CampaignPreview | null>(null);

  readonly allDays = ALL_DAYS;
  readonly dayText = dayLabel;
  readonly timezones = TIMEZONES;
  readonly schedule = describeSchedule;
  readonly targets = (campaign: Campaign) => describeTargets(campaign.targets);
  readonly badge = (campaign: Campaign) => stateBadge(campaign.state);
  readonly look = (campaign: Campaign) => STATE_LOOK[campaign.state];

  ngOnInit(): void {
    this.load();
    const timer = setInterval(() => this.load(), REFRESH_MS);
    this.destroyRef.onDestroy(() => {
      clearInterval(timer);
      this.notice.dispose();
    });
  }

  // ---------- formulário ----------

  openNew(): void {
    this.editing = null;
    this.form.set(emptyForm());
    this.formError.set(null);
    this.modal.set('new');
  }

  openEdit(campaign: Campaign): void {
    this.editing = campaign;
    this.form.set(formFrom(campaign));
    this.formError.set(null);
    this.modal.set('edit');
  }

  closeModal(): void {
    this.modal.set(null);
  }

  set<K extends keyof CampaignForm>(key: K, value: CampaignForm[K]): void {
    this.form.update((form) => ({ ...form, [key]: value }));
  }

  toggleDay(day: number): void {
    const days = new Set(this.form().days);
    if (!days.delete(day)) {
      days.add(day);
    }
    this.set('days', days);
  }

  toggleId(kind: IdSet, id: number): void {
    const ids = new Set(this.form()[kind]);
    if (!ids.delete(id)) {
      ids.add(id);
    }
    this.set(kind, ids);
  }

  submit(): void {
    const error = validateForm(this.form());
    this.formError.set(error);
    if (error) {
      return;
    }
    const request = toRequest(this.form());
    const editing = this.editing;
    this.busy.set(true);
    (editing ? this.api.updateCampaign(editing.id, request) : this.api.createCampaign(request)).subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.closeModal();
        this.notice.show(
          editing
            ? tr('Campanha "{name}" salva.', { name: saved.name })
            : tr('Campanha "{name}" criada. As TVs alcançadas passam a segui-la sozinhas, no horário dela.', { name: saved.name }),
        );
        this.load();
      },
      // O aviso do servidor aparece por cima da janela, que continua aberta.
      error: () => this.busy.set(false),
    });
  }

  // ---------- ações da lista ----------

  togglePause(campaign: Campaign): void {
    const request = { ...toRequest(formFrom(campaign)), enabled: !campaign.enabled };
    this.api.updateCampaign(campaign.id, request).subscribe({
      next: () => {
        this.notice.show(
          campaign.enabled ? tr('Campanha "{name}" pausada.', { name: campaign.name }) : tr('Campanha "{name}" reativada.', { name: campaign.name }),
        );
        this.load();
      },
      error: () => undefined,
    });
  }

  remove(campaign: Campaign): void {
    if (!confirm(tr('Excluir a campanha "{name}"? As TVs voltam a exibir a playlist delas (ou outra campanha que valha).', { name: campaign.name }))) {
      return;
    }
    this.api.deleteCampaign(campaign.id).subscribe({
      next: () => {
        this.notice.show(tr('Campanha "{name}" excluída.', { name: campaign.name }));
        this.load();
      },
      error: () => undefined,
    });
  }

  simulate(): void {
    const device = this.simDevice();
    if (device === null) {
      return;
    }
    // O campo "datetime-local" não tem fuso: vale o horário do computador de quem está olhando.
    const at = this.simAt() ? new Date(this.simAt()).toISOString() : undefined;
    this.api.previewCampaigns(device, at).subscribe({
      next: (result) => this.simResult.set(result),
      error: () => undefined,
    });
  }

  private load(): void {
    forkJoin({
      campaigns: this.api.campaigns(),
      playlists: this.api.playlists(),
      devices: this.api.devices(),
      locations: this.api.locations(),
      groups: this.api.groups(),
    }).subscribe({
      next: ({ campaigns, playlists, devices, locations, groups }) => {
        this.campaigns.set(campaigns);
        this.playlists.set(playlists);
        this.devices.set(devices);
        this.locations.set(locations);
        this.groups.set(groups);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }
}
