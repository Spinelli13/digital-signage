import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { FileSaver } from '../core/download';
import { daysAgo, formatDateTime, formatPercent, humanSeconds, stateOfConnection } from '../core/format';
import { FDatePipe, TPipe, tr, trServer } from '../core/i18n';
import { EVENT_LABELS, EVENT_TYPES, eventLabel, eventTone, levelLabel } from '../core/monitoring-labels';
import {
  Connection,
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
import { SITUATION } from '../shared/connection';
import { IconComponent } from '../shared/icon';
import { IconName } from '../shared/icons';
import { EmptyStateComponent, PageHeaderComponent, PillComponent, SkeletonComponent } from '../shared/ui';
import { ProofReportComponent } from './proof-report';

export type ReportTab = 'uptime' | 'plays' | 'proof' | 'inventory' | 'events';

export const TABS: { id: ReportTab; label: string; icon: IconName }[] = [
  { id: 'uptime', label: 'Disponibilidade', icon: 'activity' },
  { id: 'plays', label: 'Exibições', icon: 'monitor-play' },
  { id: 'proof', label: 'Comprovação', icon: 'shield-check' },
  { id: 'inventory', label: 'Inventário', icon: 'package' },
  { id: 'events', label: 'Eventos', icon: 'list' },
];

@Component({
  selector: 'app-reports',
  imports: [FormsModule, RouterLink, TPipe, FDatePipe, IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent, ProofReportComponent],
  template: `
    <app-page-header [title]="'Relatórios' | t" [subtitle]="'Quanto tempo as TVs ficaram no ar, o que foi exibido e o que aconteceu. Cada relatório pode ser baixado em CSV para abrir no Excel.' | t" icon="chart-column">
      @if (tab() !== 'proof') {
        <button class="secondary" (click)="downloadCsv()" [disabled]="downloading()"><app-icon name="download" [size]="17" />{{ (downloading() ? 'Gerando…' : 'Baixar CSV') | t }}</button>
      }
    </app-page-header>

    <div class="tabs" role="tablist">
      @for (item of tabs; track item.id) {
        <button role="tab" [attr.aria-selected]="tab() === item.id" [class.on]="tab() === item.id" (click)="select(item.id)"><app-icon [name]="item.icon" [size]="17" />{{ item.label | t }}</button>
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
              <div class="sum"><span class="sum-icon"><app-icon name="activity" [size]="20" /></span><div><span class="label">{{ 'Disponibilidade da frota' | t }}</span><div class="big num">{{ percent(report.fleetUptimePercent) }}</div></div></div>
              <div class="sum"><span class="sum-icon"><app-icon name="calendar" [size]="20" /></span><div><span class="label">{{ 'Período' | t }}</span><div class="mid">{{ report.from | fdate }} {{ 'a' | t }} {{ report.to | fdate }}</div><small>{{ 'fuso' | t }} {{ report.timezone }}</small></div></div>
            </div>
            @if (report.devices.length === 0) {
              <app-empty-state [compact]="true" icon="monitor-off" [title]="'Nenhuma TV neste filtro.' | t" />
            } @else {
              <table class="stack">
                <tr><th>{{ 'TV' | t }}</th><th>{{ 'Local' | t }}</th><th>{{ 'Disponibilidade' | t }}</th><th>{{ 'Online' | t }}</th><th>{{ 'Offline' | t }}</th><th>{{ 'Quedas' | t }}</th><th></th></tr>
                @for (row of report.devices; track row.deviceId) {
                  <tr>
                    <td class="first"><b>{{ row.name }}</b></td>
                    <td [attr.data-label]="'Local' | t">{{ row.location || '—' }}</td>
                    <td [attr.data-label]="'Disponibilidade' | t">
                      <span class="up">
                        @if (row.uptimePercent === null) {
                          <span class="muted" [title]="'A TV ainda não tinha se conectado neste período.' | t">{{ 'sem dados' | t }}</span>
                        } @else {
                          <span class="meter" [title]="percent(row.uptimePercent)"><span [style.width.%]="row.uptimePercent" [class.low]="row.uptimePercent < 95"></span></span>
                          <span class="num">{{ percent(row.uptimePercent) }}</span>
                        }
                      </span>
                    </td>
                    <td [attr.data-label]="'Online' | t">{{ human(row.onlineSeconds) }}</td>
                    <td [attr.data-label]="'Offline' | t">{{ human(row.offlineSeconds) }}</td>
                    <td [attr.data-label]="'Quedas' | t">{{ row.incidents }}</td>
                    <td class="row-actions"><a class="secondary small btn" [routerLink]="['/dispositivos', row.deviceId, 'historico']"><app-icon name="rotate-ccw-clock" [size]="15" />{{ 'Histórico' | t }}</a></td>
                  </tr>
                }
              </table>
            }
          } @else {
            <app-skeleton [rows]="4" />
          }
        }
        @case ('plays') {
          @if (plays(); as report) {
            <div class="summary">
              <div class="sum"><span class="sum-icon"><app-icon name="monitor-play" [size]="20" /></span><div><span class="label">{{ 'Exibições no período' | t }}</span><div class="big num">{{ report.totalPlays }}</div></div></div>
              <div class="sum"><span class="sum-icon"><app-icon name="timer" [size]="20" /></span><div><span class="label">{{ 'Tempo total na tela' | t }}</span><div class="big num">{{ human(report.totalSeconds) }}</div></div></div>
            </div>
            @if (report.rows.length === 0) {
              <app-empty-state [compact]="true" icon="monitor-play" [title]="'Nenhuma exibição registrada neste período.' | t"
                [text]="'As contagens vêm do Player (versão 0.5 ou mais nova) a cada contato com o servidor.' | t" />
            } @else {
              <table class="stack">
                <tr><th>{{ playsColumn() }}</th><th>{{ 'Exibições' | t }}</th><th>{{ 'Tempo na tela' | t }}</th><th>{{ 'TVs' | t }}</th></tr>
                @for (row of report.rows; track $index) {
                  <tr>
                    <td class="first"><b>{{ row.day ?? row.label }}</b></td>
                    <td [attr.data-label]="'Exibições' | t"><span class="num">{{ row.plays }}</span></td>
                    <td [attr.data-label]="'Tempo na tela' | t"><span class="num">{{ human(row.seconds) }}</span></td>
                    <td [attr.data-label]="'TVs' | t"><span class="num">{{ row.devices }}</span></td>
                  </tr>
                }
              </table>
            }
          } @else {
            <app-skeleton [rows]="4" />
          }
        }
        @case ('proof') {
          <app-proof-report [from]="from()" [to]="to()" [devices]="devices()" />
        }
        @case ('inventory') {
          @if (inventory(); as rows) {
            @if (rows.length === 0) {
              <app-empty-state icon="monitor-play" [title]="'Nenhuma TV cadastrada ainda.' | t" [text]="'Quando você ativar as TVs, o inventário completo aparece aqui.' | t" />
            } @else {
              <table class="stack">
                <tr><th>{{ 'TV' | t }}</th><th>{{ 'Local / grupos' | t }}</th><th>{{ 'Situação' | t }}</th><th>{{ 'Player' | t }}</th><th>{{ 'Exibindo' | t }}</th></tr>
                @for (row of rows; track row.id) {
                  <tr>
                    <td class="first"><b>{{ row.name }}</b><small class="sub">{{ 'ativada' | t }} {{ when(row.activatedAt) }}</small></td>
                    <td [attr.data-label]="'Local / grupos' | t"><span class="col">{{ row.location || ('Sem local' | t) }}@if (row.groups.length > 0) {<small class="sub">{{ row.groups.join(', ') }}</small>}</span></td>
                    <td [attr.data-label]="'Situação' | t"><span class="col"><app-pill [tone]="sit(row.connection).tone" [icon]="sit(row.connection).icon">{{ stateOf(row.connection).label }}</app-pill><small class="sub">{{ 'contato' | t }} {{ when(row.lastSeenAt) }}</small></span></td>
                    <td [attr.data-label]="'Player' | t"><span class="num">{{ row.playerVersion ? 'v' + row.playerVersion : '—' }}</span></td>
                    <td [attr.data-label]="'Exibindo' | t">
                      <span class="col">
                        {{ row.playlist || ('Sem playlist' | t) }}
                        @if (row.campaign) {
                          <small class="sub"><app-icon name="megaphone" [size]="12" />{{ 'campanha {name}' | t: { name: row.campaign } }}</small>
                        }
                        @if (row.currentMedia) {
                          <small class="sub"><app-icon name="play" [size]="12" />{{ row.currentMedia }}</small>
                        }
                      </span>
                    </td>
                  </tr>
                }
              </table>
            }
          } @else {
            <app-skeleton [rows]="4" />
          }
        }
        @case ('events') {
          @if (events(); as rows) {
            @if (rows.length === 0) {
              <app-empty-state [compact]="true" icon="list" [title]="'Nenhum evento neste período e filtro.' | t" />
            } @else {
              <table class="stack">
                <tr><th>{{ 'Quando' | t }}</th><th>{{ 'TV' | t }}</th><th>{{ 'Tipo' | t }}</th><th>{{ 'O que aconteceu' | t }}</th></tr>
                @for (row of rows; track row.id) {
                  <tr>
                    <td class="nowrap first">{{ when(row.occurredAt) }}</td>
                    <td [attr.data-label]="'TV' | t">{{ row.deviceName || '—' }}</td>
                    <td class="nowrap" [attr.data-label]="'Tipo' | t"><span class="col"><app-pill [tone]="tone(row)" [icon]="eventLabels[row.type].icon">{{ eventText(row.type) }}</app-pill>@if (row.level !== 'INFO') { <small class="sub">{{ levelText(row.level) }}</small> }</span></td>
                    <td [class.bad]="row.level === 'ERROR'" [attr.data-label]="'O que aconteceu' | t">{{ messageOf(row.message) }}</td>
                  </tr>
                }
              </table>
              @if (rows.length >= 500) {
                <p class="label">{{ 'Mostrando os 500 mais recentes. Baixe o CSV para ver até 20.000 linhas ou reduza o período.' | t }}</p>
              }
            }
          } @else {
            <app-skeleton [rows]="4" />
          }
        }
      }
    </div>
  `,
  styles: `
    .tabs { display: flex; gap: 4px; padding: 4px; margin-bottom: 16px; width: fit-content; max-width: 100%; background: var(--panel-3); border: 1px solid var(--line); border-radius: var(--r-lg); }
    .tabs button { display: inline-flex; align-items: center; gap: 8px; min-height: 40px; padding: 0 16px; border: 0; border-radius: var(--r); background: transparent; font-weight: 600; color: var(--muted); }
    .tabs button:hover { color: var(--text); }
    .tabs button.on { background: var(--panel); color: var(--primary); box-shadow: var(--sh-1); }
    :host-context(:root[data-theme='dark']) .tabs button.on { color: var(--link); }
    .filters { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; margin-bottom: 14px; padding: 14px; }
    .filters label { display: inline-flex; align-items: center; gap: 6px; color: var(--muted); font-size: 14px; }
    .filters input[type='date'], .filters select { width: auto; min-width: 150px; }
    .presets { display: flex; gap: 6px; }
    .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 12px; margin-bottom: 18px; }
    .sum { display: flex; align-items: center; gap: 14px; padding: 14px 16px; background: var(--panel-2); border: 1px solid var(--line); border-radius: var(--r-lg); }
    .sum-icon { flex: none; display: grid; place-items: center; width: 44px; height: 44px; border-radius: 13px; color: var(--primary); background: var(--primary-soft); border: 1px solid var(--primary-line); }
    :host-context(:root[data-theme='dark']) .sum-icon { color: var(--link); }
    .big { font-size: 28px; font-weight: 700; margin-top: 2px; letter-spacing: -0.02em; line-height: 1.15; }
    .mid { font-weight: 600; margin-top: 2px; }
    .up { display: inline-flex; align-items: center; gap: 8px; }
    .meter { display: inline-block; width: 96px; height: 8px; background: var(--line); border-radius: 999px; overflow: hidden; }
    .meter span { display: block; height: 100%; border-radius: 999px; background: var(--green); }
    .meter span.low { background: var(--orange); }
    .col { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; }
    .sub { display: inline-flex; align-items: center; gap: 5px; color: var(--muted); }
    td.first .sub { display: flex; }
    .btn { display: inline-flex; align-items: center; gap: 7px; text-decoration: none; }
    .row-actions { text-align: right; }
    .nowrap { white-space: nowrap; }
    .bad { color: var(--err-fg); }
    @media (max-width: 720px) {
      .tabs { width: 100%; flex-wrap: nowrap; overflow-x: auto; }
      .tabs button { flex: 1 0 auto; justify-content: center; min-height: 44px; }
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
  readonly sit = (connection: Connection) => SITUATION[connection];
  readonly tone = (row: EventRow) => eventTone(row.type, row.level);
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
      case 'proof':
        break; // a aba carrega sozinha (período vem daqui, os demais filtros são dela)
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
      proof: {},
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
    const names: Record<ReportTab, string> = { uptime: 'disponibilidade', plays: 'exibicoes', proof: 'comprovacao', inventory: 'inventario', events: 'eventos' };
    return tab === 'inventory' ? `inventario-${daysAgo(0)}.csv` : `${names[tab]}-${range.from}-a-${range.to}.csv`;
  }

  private range(): ReportRange {
    return { from: this.from() || undefined, to: this.to() || undefined };
  }
}
