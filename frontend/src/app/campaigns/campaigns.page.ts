import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Campaign, CampaignPreview, Device, DeviceGroup, Location, Playlist } from '../core/models';
import { Notice } from '../core/notice';
import {
  ALL_DAYS,
  CampaignForm,
  DAY_LABELS,
  TIMEZONES,
  describeSchedule,
  describeTargets,
  emptyForm,
  formFrom,
  stateBadge,
  toRequest,
  validateForm,
} from './campaign-form';

const REFRESH_MS = 15_000;

type IdSet = 'locationIds' | 'groupIds' | 'deviceIds';

@Component({
  selector: 'app-campaigns',
  imports: [FormsModule],
  template: `
    <div class="head">
      <div>
        <h1>Campanhas</h1>
        <p>Uma playlist que só vale em certas datas, dias e horários, para as TVs que você escolher.</p>
      </div>
      @if (auth.can('content')) {
        <button class="primary" (click)="openNew()">+ Nova campanha</button>
      }
    </div>

    @if (notice.text(); as text) {
      <div class="notice" role="status">{{ text }}</div>
    }

    <div class="card">
      @if (campaigns().length === 0) {
        <div class="empty">
          @if (loaded()) {
            Nenhuma campanha ainda. Sem campanhas, cada TV exibe a sua playlist. Crie uma para, por exemplo, trocar o conteúdo
            nos fins de semana ou durante uma promoção.
          } @else {
            Carregando…
          }
        </div>
      } @else {
        <table>
          <tr><th>Campanha</th><th>Quando</th><th>Para quem</th><th>Prioridade</th><th>Situação</th><th></th></tr>
          @for (campaign of campaigns(); track campaign.id) {
            <tr>
              <td>
                <b>{{ campaign.name }}</b><br />
                <small>Playlist: {{ campaign.playlistName || '—' }}</small>
              </td>
              <td>{{ schedule(campaign) }}</td>
              <td>{{ targets(campaign) }}<br /><small>{{ campaign.deviceCount }} TV(s) alcançada(s)</small></td>
              <td>{{ campaign.priority }}</td>
              <td>
                <span class="dot" [class]="badge(campaign).tone"></span><span [title]="badge(campaign).hint">{{ badge(campaign).label }}</span>
              </td>
              <td class="row-actions">
                @if (auth.can('content')) {
                  <button class="secondary small" (click)="openEdit(campaign)">Editar</button>
                  <button class="secondary small" (click)="togglePause(campaign)">{{ campaign.enabled ? 'Pausar' : 'Reativar' }}</button>
                  <button class="danger small" (click)="remove(campaign)">Excluir</button>
                }
              </td>
            </tr>
          }
        </table>
      }
    </div>

    <div class="card simulator">
      <h3>Simular: o que uma TV exibiria?</h3>
      <p class="label">Escolha uma TV e um momento (no horário do seu computador) para conferir qual campanha venceria, antes de publicar.</p>
      <div class="sim-row">
        <select aria-label="TV para simular" [ngModel]="simDevice()" (ngModelChange)="simDevice.set($event)">
          <option [ngValue]="null">Escolha uma TV…</option>
          @for (device of devices(); track device.id) {
            <option [ngValue]="device.id">{{ device.name }}</option>
          }
        </select>
        <input type="datetime-local" aria-label="Momento da simulação" [ngModel]="simAt()" (ngModelChange)="simAt.set($event)" />
        <button class="secondary" [disabled]="simDevice() === null" (click)="simulate()">Simular</button>
      </div>
      @if (simResult(); as result) {
        <div class="sim-result" role="status">
          @if (result.source === 'CAMPAIGN') {
            A TV exibiria <b>{{ result.playlistName }}</b>, da campanha <b>{{ result.campaignName }}</b>.
          } @else if (result.source === 'BASE') {
            Nenhuma campanha vale nesse momento: a TV exibiria a playlist dela, <b>{{ result.playlistName }}</b>.
          } @else {
            Nenhuma campanha vale nesse momento e a TV não tem playlist: ela mostraria "Aguardando conteúdo".
          }
        </div>
      }
    </div>

    @if (modal() !== null) {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box wide" (click)="$event.stopPropagation()" (ngSubmit)="submit()">
          <h2>{{ modal() === 'new' ? 'Nova campanha' : 'Editar campanha' }}</h2>

          <div class="field">
            <label for="camp-name">Nome</label>
            <input id="camp-name" name="name" placeholder="Ex.: Promoção de verão" required [ngModel]="form().name" (ngModelChange)="set('name', $event)" />
          </div>
          <div class="field">
            <label for="camp-desc">Descrição (opcional)</label>
            <input id="camp-desc" name="description" [ngModel]="form().description" (ngModelChange)="set('description', $event)" />
          </div>

          <div class="row">
            <div class="field grow">
              <label for="camp-playlist">Playlist exibida</label>
              <select id="camp-playlist" name="playlist" [ngModel]="form().playlistId" (ngModelChange)="set('playlistId', $event)">
                <option [ngValue]="null">Escolha…</option>
                @for (playlist of playlists(); track playlist.id) {
                  <option [ngValue]="playlist.id">{{ playlist.name }}</option>
                }
              </select>
            </div>
            <div class="field small-field">
              <label for="camp-priority">Prioridade</label>
              <input id="camp-priority" name="priority" type="number" min="0" max="1000" [ngModel]="form().priority" (ngModelChange)="set('priority', $event)" />
            </div>
          </div>
          <p class="hint">Se duas campanhas valem ao mesmo tempo para a mesma TV, vence a de <b>maior prioridade</b>. Em empate, a que mira mais de perto (TV, depois grupo, depois local, depois todas) e, por fim, a mais nova.</p>

          <h3>Quando</h3>
          <div class="row">
            <div class="field grow">
              <label for="camp-start">De (data)</label>
              <input id="camp-start" name="startDate" type="date" [ngModel]="form().startDate" (ngModelChange)="set('startDate', $event)" />
            </div>
            <div class="field grow">
              <label for="camp-end">Até (data, inclusive)</label>
              <input id="camp-end" name="endDate" type="date" [ngModel]="form().endDate" (ngModelChange)="set('endDate', $event)" />
            </div>
          </div>
          <p class="hint">Deixe as datas vazias para não limitar o período.</p>

          <div class="field">
            <label>Dias da semana</label>
            <div class="chips">
              @for (day of allDays; track day) {
                <label class="check" [class.on]="form().days.has(day)">
                  <input type="checkbox" [checked]="form().days.has(day)" (change)="toggleDay(day)" /> {{ dayLabels[day] }}
                </label>
              }
            </div>
          </div>

          <div class="field">
            <label class="check"><input type="checkbox" [checked]="form().allDay" (change)="set('allDay', !form().allDay)" /> O dia todo</label>
            @if (!form().allDay) {
              <div class="row below">
                <div class="field grow">
                  <label for="camp-from">Das</label>
                  <input id="camp-from" name="startTime" type="time" [ngModel]="form().startTime" (ngModelChange)="set('startTime', $event)" />
                </div>
                <div class="field grow">
                  <label for="camp-to">Às</label>
                  <input id="camp-to" name="endTime" type="time" [ngModel]="form().endTime" (ngModelChange)="set('endTime', $event)" />
                </div>
              </div>
              <p class="hint">Se o fim for antes do início (ex.: das 22:00 às 06:00), a campanha atravessa a meia-noite e conta pelo dia em que começa.</p>
            }
          </div>

          <div class="field">
            <label for="camp-tz">Fuso horário das datas e horas</label>
            <select id="camp-tz" name="timezone" [ngModel]="form().timezone" (ngModelChange)="set('timezone', $event)">
              @for (zone of timezones; track zone.value) {
                <option [value]="zone.value">{{ zone.label }}</option>
              }
            </select>
          </div>

          <h3>Para quem</h3>
          <div class="field">
            <label class="check"><input type="checkbox" [checked]="form().allDevices" (change)="set('allDevices', !form().allDevices)" /> Todas as TVs</label>
          </div>
          @if (!form().allDevices) {
            <div class="targets">
              <div>
                <b>Locais</b>
                @for (location of locations(); track location.id) {
                  <label class="check"><input type="checkbox" [checked]="form().locationIds.has(location.id)" (change)="toggleId('locationIds', location.id)" /> {{ location.name }}</label>
                } @empty {
                  <small>Nenhum local criado.</small>
                }
              </div>
              <div>
                <b>Grupos</b>
                @for (group of groups(); track group.id) {
                  <label class="check"><input type="checkbox" [checked]="form().groupIds.has(group.id)" (change)="toggleId('groupIds', group.id)" /> {{ group.name }}</label>
                } @empty {
                  <small>Nenhum grupo criado.</small>
                }
              </div>
              <div>
                <b>TVs</b>
                @for (device of devices(); track device.id) {
                  <label class="check"><input type="checkbox" [checked]="form().deviceIds.has(device.id)" (change)="toggleId('deviceIds', device.id)" /> {{ device.name }}</label>
                } @empty {
                  <small>Nenhuma TV ainda.</small>
                }
              </div>
            </div>
          }

          <div class="field">
            <label class="check"><input type="checkbox" [checked]="form().enabled" (change)="set('enabled', !form().enabled)" /> Campanha ativada (desmarque para deixá-la pausada)</label>
          </div>

          @if (formError(); as error) {
            <div class="error" role="alert">{{ error }}</div>
          }
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">Cancelar</button>
            <button type="submit" class="primary" [disabled]="busy()">Salvar</button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .row-actions { white-space: nowrap; text-align: right; }
    .row-actions button { margin-left: 6px; }
    .simulator { margin-top: 18px; }
    .sim-row { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
    .sim-row select { width: auto; min-width: 220px; }
    .sim-row input { width: auto; }
    .sim-result { margin-top: 12px; background: #eef3ff; border: 1px solid #c9d8ff; border-radius: 10px; padding: 10px 14px; }
    .modal-box.wide { max-width: 720px; }
    .row { display: flex; gap: 12px; flex-wrap: wrap; }
    .row .grow { flex: 1 1 200px; }
    .small-field { flex: 0 0 130px; }
    .hint { color: var(--muted); font-size: 13px; margin: -6px 0 14px; }
    h3 { margin: 18px 0 8px; font-size: 15px; }
    .chips { display: flex; gap: 6px; flex-wrap: wrap; }
    .chips .check { border: 1px solid var(--line); border-radius: 999px; padding: 5px 12px; }
    .chips .check.on { background: #eef3ff; border-color: var(--blue); }
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
  readonly dayLabels = DAY_LABELS;
  readonly timezones = TIMEZONES;
  readonly schedule = describeSchedule;
  readonly targets = (campaign: Campaign) => describeTargets(campaign.targets);
  readonly badge = (campaign: Campaign) => stateBadge(campaign.state);

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
            ? `Campanha "${saved.name}" salva.`
            : `Campanha "${saved.name}" criada. As TVs alcançadas passam a segui-la sozinhas, no horário dela.`,
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
        this.notice.show(campaign.enabled ? `Campanha "${campaign.name}" pausada.` : `Campanha "${campaign.name}" reativada.`);
        this.load();
      },
      error: () => undefined,
    });
  }

  remove(campaign: Campaign): void {
    if (!confirm(`Excluir a campanha "${campaign.name}"? As TVs voltam a exibir a playlist delas (ou outra campanha que valha).`)) {
      return;
    }
    this.api.deleteCampaign(campaign.id).subscribe({
      next: () => {
        this.notice.show(`Campanha "${campaign.name}" excluída.`);
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
