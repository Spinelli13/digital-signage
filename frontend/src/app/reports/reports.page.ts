import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { FileSaver } from '../core/download';
import { daysAgo, formatDateTime, formatPercent, humanSeconds, stateOfConnection } from '../core/format';
import { FDatePipe, TPipe, tr, trServer } from '../core/i18n';
import { EVENT_LABELS, EVENT_TYPES, eventLabel, levelLabel } from '../core/monitoring-labels';
import {
  Device,
  DeviceGroup,
  EventLevel,
  EventRow,
  EventType,
  InventoryRow,
  Location,
  PlaysGroupBy,
  PlaysReport,
  ReportRange,
  UptimeReport,
} from '../core/models';

export type ReportTab = 'uptime' | 'plays' | 'inventory' | 'events';

export const TABS: { id: ReportTab; label: string }[] = [
  { id: 'uptime', label: 'Disponibilidade' },
  { id: 'plays', label: 'Exibições' },
  { id: 'inventory', label: 'Inventário' },
  { id: 'events', label: 'Eventos' },
];

@Component({
  selector: 'app-reports',
  imports: [FormsModule, RouterLink, TPipe, FDatePipe],
  template: `
    <div class="head">
      <div>
        <h1>{{ 'Relatórios' | t }}</h1>
        <p>{{ 'Quanto tempo as TVs ficaram no ar, o que foi exibido e o que aconteceu. Cada relatório pode ser baixado em CSV para abrir no Excel.' | t }}</p>
      </div>
      <button class="secondary" (click)="downloadCsv()" [disabled]="downloading()">{{ (downloading() ? 'Gerando…' : '⬇ Baixar CSV') | t }}</button>
    </div>

    <div class="tabs" role="tablist">
      @for (item of tabs; track item.id) {
        <button role="tab" [attr.aria-selected]="tab() === item.id" [class.on]="tab() === item.id" (click)="select(item.id)">{{ item.label | t }}</button>
      }
    </div>

    @if (tab() !== 'inventory') {
      <div class="card filters">
        <div class="presets">
          <button class="secondary small" (click)="preset(0)">{{ 'Hoje' | t }}</button>
          <button class="secondary small" (click)="preset(6)">{{ '7 dias' | t }}</button>
          <button class="secondary small" (click)="preset(29)">{{ '30 dias' | t }}</button>
        </div>
        <label>{{ 'De' | t }} <input type="date" [attr.aria-label]="'Data inicial' | t" [ngModel]="from()" (ngModelChange)="setFrom($event)" /></label>
        <label>{{ 'até' | t }} <input type="date" [attr.aria-label]="'Data final' | t" [ngModel]="to()" (ngModelChange)="setTo($event)" /></label>

        @if (tab() === 'uptime' || tab() === 'plays') {
          <select [attr.aria-label]="'Local' | t" [ngModel]="locationId()" (ngModelChange)="setLocation($event)">
            <option [ngValue]="null">{{ 'Todos os locais' | t }}</option>
            @for (location of locations(); track location.id) {
              <option [ngValue]="location.id">{{ location.name }}</option>
            }
          </select>
        }
        @if (tab() === 'uptime') {
          <select [attr.aria-label]="'Grupo' | t" [ngModel]="groupId()" (ngModelChange)="setGroup($event)">
            <option [ngValue]="null">{{ 'Todos os grupos' | t }}</option>
            @for (group of groups(); track group.id) {
              <option [ngValue]="group.id">{{ group.name }}</option>
            }
          </select>
        }
        @if (tab() === 'plays') {
          <select [attr.aria-label]="'Agrupar por' | t" [ngModel]="groupBy()" (ngModelChange)="setGroupBy($event)">
            <option value="media">{{ 'Por mídia' | t }}</option>
            <option value="device">{{ 'Por TV' | t }}</option>
            <option value="day">{{ 'Por dia' | t }}</option>
          </select>
        }
        @if (tab() === 'events') {
          <select [attr.aria-label]="'TV' | t" [ngModel]="eventDevice()" (ngModelChange)="setEventDevice($event)">
            <option [ngValue]="null">{{ 'Todas as TVs' | t }}</option>
            @for (device of devices(); track device.id) {
              <option [ngValue]="device.id">{{ device.name }}</option>
            }
          </select>
          <select [attr.aria-label]="'Tipo' | t" [ngModel]="eventType()" (ngModelChange)="setEventType($event)">
            <option value="">{{ 'Todos os tipos' | t }}</option>
            @for (kind of eventTypes; track kind) {
              <option [value]="kind">{{ eventText(kind) }}</option>
            }
          </select>
          <select [attr.aria-label]="'Nível' | t" [ngModel]="eventLevel()" (ngModelChange)="setEventLevel($event)">
            <option value="">{{ 'Todos os níveis' | t }}</option>
            <option value="ERROR">{{ 'Só erros' | t }}</option>
            <option value="WARN">{{ 'Só avisos' | t }}</option>
            <option value="INFO">{{ 'Só informações' | t }}</option>
          </select>
        }
      </div>
    }

    <div class="card">
      @switch (tab()) {
        @case ('uptime') {
          @if (uptime(); as report) {
            <div class="summary">
              <div><span class="label">{{ 'Disponibilidade da frota' | t }}</span><div class="big">{{ percent(report.fleetUptimePercent) }}</div></div>
              <div><span class="label">{{ 'Período' | t }}</span><div>{{ report.from | fdate }} {{ 'a' | t }} {{ report.to | fdate }}</div><small>{{ 'fuso' | t }} {{ report.timezone }}</small></div>
            </div>
            @if (report.devices.length === 0) {
              <div class="empty">{{ 'Nenhuma TV neste filtro.' | t }}</div>
            } @else {
              <table class="stack">
                <tr><th>{{ 'TV' | t }}</th><th>{{ 'Local' | t }}</th><th>{{ 'Disponibilidade' | t }}</th><th>{{ 'Online' | t }}</th><th>{{ 'Offline' | t }}</th><th>{{ 'Quedas' | t }}</th><th></th></tr>
                @for (row of report.devices; track row.deviceId) {
                  <tr>
                    <td class="first"><b>{{ row.name }}</b></td>
                    <td [attr.data-label]="'Local' | t">{{ row.location || '—' }}</td>
                    <td [attr.data-label]="'Disponibilidade' | t">
                      <span>
                        @if (row.uptimePercent === null) {
                          <span class="muted" [title]="'A TV ainda não tinha se conectado neste período.' | t">{{ 'sem dados' | t }}</span>
                        } @else {
                          <span class="meter" [title]="percent(row.uptimePercent)"><span [style.width.%]="row.uptimePercent" [class.low]="row.uptimePercent < 95"></span></span>
                          {{ percent(row.uptimePercent) }}
                        }
                      </span>
                    </td>
                    <td [attr.data-label]="'Online' | t">{{ human(row.onlineSeconds) }}</td>
                    <td [attr.data-label]="'Offline' | t">{{ human(row.offlineSeconds) }}</td>
                    <td [attr.data-label]="'Quedas' | t">{{ row.incidents }}</td>
                    <td class="row-actions"><a [routerLink]="['/dispositivos', row.deviceId, 'historico']">{{ 'Histórico' | t }}</a></td>
                  </tr>
                }
              </table>
            }
          } @else {
            <div class="empty">{{ 'Carregando…' | t }}</div>
          }
        }
        @case ('plays') {
          @if (plays(); as report) {
            <div class="summary">
              <div><span class="label">{{ 'Exibições no período' | t }}</span><div class="big">{{ report.totalPlays }}</div></div>
              <div><span class="label">{{ 'Tempo total na tela' | t }}</span><div class="big">{{ human(report.totalSeconds) }}</div></div>
            </div>
            @if (report.rows.length === 0) {
              <div class="empty">{{ 'Nenhuma exibição registrada neste período. As contagens vêm do Player (versão 0.5 ou mais nova) a cada contato com o servidor.' | t }}</div>
            } @else {
              <table class="stack">
                <tr><th>{{ playsColumn() }}</th><th>{{ 'Exibições' | t }}</th><th>{{ 'Tempo na tela' | t }}</th><th>{{ 'TVs' | t }}</th></tr>
                @for (row of report.rows; track $index) {
                  <tr>
                    <td class="first"><b>{{ row.day ?? row.label }}</b></td>
                    <td [attr.data-label]="'Exibições' | t">{{ row.plays }}</td>
                    <td [attr.data-label]="'Tempo na tela' | t">{{ human(row.seconds) }}</td>
                    <td [attr.data-label]="'TVs' | t">{{ row.devices }}</td>
                  </tr>
                }
              </table>
            }
          } @else {
            <div class="empty">{{ 'Carregando…' | t }}</div>
          }
        }
        @case ('inventory') {
          @if (inventory(); as rows) {
            @if (rows.length === 0) {
              <div class="empty">{{ 'Nenhuma TV cadastrada ainda.' | t }}</div>
            } @else {
              <table class="stack">
                <tr><th>{{ 'TV' | t }}</th><th>{{ 'Local / grupos' | t }}</th><th>{{ 'Situação' | t }}</th><th>{{ 'Player' | t }}</th><th>{{ 'Exibindo' | t }}</th></tr>
                @for (row of rows; track row.id) {
                  <tr>
                    <td class="first"><b>{{ row.name }}</b><br /><small>{{ 'ativada' | t }} {{ when(row.activatedAt) }}</small></td>
                    <td [attr.data-label]="'Local / grupos' | t"><span>{{ row.location || ('Sem local' | t) }}@if (row.groups.length > 0) {<br /><small>{{ row.groups.join(', ') }}</small>}</span></td>
                    <td [attr.data-label]="'Situação' | t"><span class="st"><span class="dot" [class]="stateOf(row.connection).tone"></span>{{ stateOf(row.connection).label }}<br /><small>{{ 'contato' | t }} {{ when(row.lastSeenAt) }}</small></span></td>
                    <td [attr.data-label]="'Player' | t">{{ row.playerVersion ? 'v' + row.playerVersion : '—' }}</td>
                    <td [attr.data-label]="'Exibindo' | t">
                      <span>
                        {{ row.playlist || ('Sem playlist' | t) }}
                        @if (row.campaign) {
                          <br /><small>{{ '📣 campanha {name}' | t: { name: row.campaign } }}</small>
                        }
                        @if (row.currentMedia) {
                          <br /><small>▶ {{ row.currentMedia }}</small>
                        }
                      </span>
                    </td>
                  </tr>
                }
              </table>
            }
          } @else {
            <div class="empty">{{ 'Carregando…' | t }}</div>
          }
        }
        @case ('events') {
          @if (events(); as rows) {
            @if (rows.length === 0) {
              <div class="empty">{{ 'Nenhum evento neste período e filtro.' | t }}</div>
            } @else {
              <table class="stack">
                <tr><th>{{ 'Quando' | t }}</th><th>{{ 'TV' | t }}</th><th>{{ 'Tipo' | t }}</th><th>{{ 'O que aconteceu' | t }}</th></tr>
                @for (row of rows; track row.id) {
                  <tr>
                    <td class="nowrap first">{{ when(row.occurredAt) }}</td>
                    <td [attr.data-label]="'TV' | t">{{ row.deviceName || '—' }}</td>
                    <td class="nowrap" [attr.data-label]="'Tipo' | t"><span>{{ eventLabels[row.type].icon }} {{ eventText(row.type) }}@if (row.level !== 'INFO') { <small>({{ levelText(row.level) }})</small> }</span></td>
                    <td [class.bad]="row.level === 'ERROR'" [attr.data-label]="'O que aconteceu' | t">{{ messageOf(row.message) }}</td>
                  </tr>
                }
              </table>
              @if (rows.length >= 500) {
                <p class="label">{{ 'Mostrando os 500 mais recentes. Baixe o CSV para ver até 20.000 linhas ou reduza o período.' | t }}</p>
              }
            }
          } @else {
            <div class="empty">{{ 'Carregando…' | t }}</div>
          }
        }
      }
    </div>
  `,
  styles: `
    .tabs { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 14px; }
    .tabs button { background: var(--panel); border: 1px solid var(--line); border-radius: 10px; padding: 9px 16px; font-weight: 600; color: var(--muted); }
    .tabs button.on { background: var(--primary); border-color: var(--primary); color: var(--on-primary); }
    .filters { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; margin-bottom: 14px; padding: 14px; }
    .filters label { display: inline-flex; align-items: center; gap: 6px; color: var(--muted); font-size: 14px; }
    .filters input[type='date'], .filters select { width: auto; min-width: 150px; }
    .presets { display: flex; gap: 6px; }
    .summary { display: flex; gap: 40px; flex-wrap: wrap; margin-bottom: 14px; }
    .big { font-size: 28px; font-weight: 700; margin-top: 4px; }
    .meter { display: inline-block; width: 90px; height: 8px; background: var(--line); border-radius: 999px; overflow: hidden; vertical-align: middle; margin-right: 8px; }
    .meter span { display: block; height: 100%; background: var(--green); }
    .meter span.low { background: var(--orange); }
    .row-actions { text-align: right; }
    .nowrap { white-space: nowrap; }
    .bad { color: var(--err-fg); }
    @media (max-width: 720px) {
      .tabs { flex-wrap: nowrap; overflow-x: auto; padding-bottom: 4px; }
      .tabs button { flex: none; min-height: 44px; }
      .filters { flex-direction: column; align-items: stretch; }
      .filters label { display: flex; }
      .filters input[type='date'], .filters select { width: 100%; min-width: 0; }
      .presets { justify-content: space-between; }
      .presets button { flex: 1; }
      .summary { gap: 18px; }
    }
  `,
})
export class ReportsPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly files = inject(FileSaver);

  readonly tabs = TABS;
  readonly tab = signal<ReportTab>('uptime');
  readonly from = signal(daysAgo(6));
  readonly to = signal(daysAgo(0));
  readonly locationId = signal<number | null>(null);
  readonly groupId = signal<number | null>(null);
  readonly groupBy = signal<PlaysGroupBy>('media');
  readonly eventDevice = signal<number | null>(null);
  readonly eventType = signal<EventType | ''>('');
  readonly eventLevel = signal<EventLevel | ''>('');

  readonly locations = signal<Location[]>([]);
  readonly groups = signal<DeviceGroup[]>([]);
  readonly devices = signal<Device[]>([]);

  readonly uptime = signal<UptimeReport | null>(null);
  readonly plays = signal<PlaysReport | null>(null);
  readonly inventory = signal<InventoryRow[] | null>(null);
  readonly events = signal<EventRow[] | null>(null);
  readonly downloading = signal(false);

  readonly playsColumn = computed(() => tr({ media: 'Mídia', device: 'TV', day: 'Dia' }[this.groupBy()]));

  readonly eventTypes = EVENT_TYPES;
  readonly eventLabels = EVENT_LABELS;
  readonly eventText = eventLabel;
  readonly levelText = levelLabel;
  /** Mensagens que o servidor registrou (em português); as conhecidas aparecem traduzidas. */
  readonly messageOf = (message: string) => trServer(message);
  readonly stateOf = stateOfConnection;
  readonly human = humanSeconds;
  readonly percent = formatPercent;
  readonly when = (iso: string | null) => formatDateTime(iso);

  ngOnInit(): void {
    this.api.locations().subscribe({ next: (list) => this.locations.set(list), error: () => undefined });
    this.api.groups().subscribe({ next: (list) => this.groups.set(list), error: () => undefined });
    this.api.devices().subscribe({ next: (list) => this.devices.set(list), error: () => undefined });
    this.load();
  }

  select(tab: ReportTab): void {
    this.tab.set(tab);
    this.load();
  }

  preset(daysBack: number): void {
    this.from.set(daysAgo(daysBack));
    this.to.set(daysAgo(0));
    this.load();
  }

  setFrom(value: string): void {
    this.from.set(value);
    this.load();
  }

  setTo(value: string): void {
    this.to.set(value);
    this.load();
  }

  setLocation(value: number | null): void {
    this.locationId.set(value);
    this.load();
  }

  setGroup(value: number | null): void {
    this.groupId.set(value);
    this.load();
  }

  setGroupBy(value: PlaysGroupBy): void {
    this.groupBy.set(value);
    this.load();
  }

  setEventDevice(value: number | null): void {
    this.eventDevice.set(value);
    this.load();
  }

  setEventType(value: EventType | ''): void {
    this.eventType.set(value);
    this.load();
  }

  setEventLevel(value: EventLevel | ''): void {
    this.eventLevel.set(value);
    this.load();
  }

  load(): void {
    const range = this.range();
    switch (this.tab()) {
      case 'uptime':
        this.api.uptimeReport(range, this.locationId(), this.groupId()).subscribe({ next: (r) => this.uptime.set(r), error: () => undefined });
        break;
      case 'plays':
        this.api.playsReport(range, this.groupBy(), this.locationId()).subscribe({ next: (r) => this.plays.set(r), error: () => undefined });
        break;
      case 'inventory':
        this.api.inventoryReport().subscribe({ next: (r) => this.inventory.set(r), error: () => undefined });
        break;
      case 'events':
        this.api
          .eventsReport(range, this.eventDevice(), this.eventType(), this.eventLevel())
          .subscribe({ next: (r) => this.events.set(r), error: () => undefined });
        break;
    }
  }

  /** Baixa o relatório que está na tela, com os mesmos filtros, como CSV. */
  downloadCsv(): void {
    const tab = this.tab();
    const range = this.range();
    const filters: Record<ReportTab, Record<string, string | number | null | undefined>> = {
      uptime: { from: range.from, to: range.to, locationId: this.locationId(), groupId: this.groupId() },
      plays: { from: range.from, to: range.to, groupBy: this.groupBy(), locationId: this.locationId() },
      inventory: {},
      events: { from: range.from, to: range.to, deviceId: this.eventDevice(), type: this.eventType(), level: this.eventLevel() },
    };
    this.downloading.set(true);
    this.api.reportCsv(tab, filters[tab]).subscribe({
      next: (blob) => {
        this.downloading.set(false);
        this.files.save(blob, this.filename(tab, range));
      },
      error: () => this.downloading.set(false),
    });
  }

  private filename(tab: ReportTab, range: ReportRange): string {
    const names: Record<ReportTab, string> = { uptime: 'disponibilidade', plays: 'exibicoes', inventory: 'inventario', events: 'eventos' };
    return tab === 'inventory' ? `inventario-${daysAgo(0)}.csv` : `${names[tab]}-${range.from}-a-${range.to}.csv`;
  }

  private range(): ReportRange {
    return { from: this.from() || undefined, to: this.to() || undefined };
  }
}
