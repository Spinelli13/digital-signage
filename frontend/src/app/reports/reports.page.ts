import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { FileSaver } from '../core/download';
import { daysAgo, formatDateTime, formatPercent, humanSeconds, stateOfConnection } from '../core/format';
import { EVENT_LABELS, EVENT_TYPES, LEVEL_LABELS } from '../core/monitoring-labels';
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

const TABS: { id: ReportTab; label: string }[] = [
  { id: 'uptime', label: 'Disponibilidade' },
  { id: 'plays', label: 'Exibições' },
  { id: 'inventory', label: 'Inventário' },
  { id: 'events', label: 'Eventos' },
];

@Component({
  selector: 'app-reports',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="head">
      <div>
        <h1>Relatórios</h1>
        <p>Quanto tempo as TVs ficaram no ar, o que foi exibido e o que aconteceu. Cada relatório pode ser baixado em CSV para abrir no Excel.</p>
      </div>
      <button class="secondary" (click)="downloadCsv()" [disabled]="downloading()">{{ downloading() ? 'Gerando…' : '⬇ Baixar CSV' }}</button>
    </div>

    <div class="tabs" role="tablist">
      @for (item of tabs; track item.id) {
        <button role="tab" [attr.aria-selected]="tab() === item.id" [class.on]="tab() === item.id" (click)="select(item.id)">{{ item.label }}</button>
      }
    </div>

    @if (tab() !== 'inventory') {
      <div class="card filters">
        <div class="presets">
          <button class="secondary small" (click)="preset(0)">Hoje</button>
          <button class="secondary small" (click)="preset(6)">7 dias</button>
          <button class="secondary small" (click)="preset(29)">30 dias</button>
        </div>
        <label>De <input type="date" aria-label="Data inicial" [ngModel]="from()" (ngModelChange)="setFrom($event)" /></label>
        <label>até <input type="date" aria-label="Data final" [ngModel]="to()" (ngModelChange)="setTo($event)" /></label>

        @if (tab() === 'uptime' || tab() === 'plays') {
          <select aria-label="Local" [ngModel]="locationId()" (ngModelChange)="setLocation($event)">
            <option [ngValue]="null">Todos os locais</option>
            @for (location of locations(); track location.id) {
              <option [ngValue]="location.id">{{ location.name }}</option>
            }
          </select>
        }
        @if (tab() === 'uptime') {
          <select aria-label="Grupo" [ngModel]="groupId()" (ngModelChange)="setGroup($event)">
            <option [ngValue]="null">Todos os grupos</option>
            @for (group of groups(); track group.id) {
              <option [ngValue]="group.id">{{ group.name }}</option>
            }
          </select>
        }
        @if (tab() === 'plays') {
          <select aria-label="Agrupar por" [ngModel]="groupBy()" (ngModelChange)="setGroupBy($event)">
            <option value="media">Por mídia</option>
            <option value="device">Por TV</option>
            <option value="day">Por dia</option>
          </select>
        }
        @if (tab() === 'events') {
          <select aria-label="TV" [ngModel]="eventDevice()" (ngModelChange)="setEventDevice($event)">
            <option [ngValue]="null">Todas as TVs</option>
            @for (device of devices(); track device.id) {
              <option [ngValue]="device.id">{{ device.name }}</option>
            }
          </select>
          <select aria-label="Tipo" [ngModel]="eventType()" (ngModelChange)="setEventType($event)">
            <option value="">Todos os tipos</option>
            @for (kind of eventTypes; track kind) {
              <option [value]="kind">{{ eventLabels[kind].label }}</option>
            }
          </select>
          <select aria-label="Nível" [ngModel]="eventLevel()" (ngModelChange)="setEventLevel($event)">
            <option value="">Todos os níveis</option>
            <option value="ERROR">Só erros</option>
            <option value="WARN">Só avisos</option>
            <option value="INFO">Só informações</option>
          </select>
        }
      </div>
    }

    <div class="card">
      @switch (tab()) {
        @case ('uptime') {
          @if (uptime(); as report) {
            <div class="summary">
              <div><span class="label">Disponibilidade da frota</span><div class="big">{{ percent(report.fleetUptimePercent) }}</div></div>
              <div><span class="label">Período</span><div>{{ report.from }} a {{ report.to }}</div><small>fuso {{ report.timezone }}</small></div>
            </div>
            @if (report.devices.length === 0) {
              <div class="empty">Nenhuma TV neste filtro.</div>
            } @else {
              <table>
                <tr><th>TV</th><th>Local</th><th>Disponibilidade</th><th>Online</th><th>Offline</th><th>Quedas</th><th></th></tr>
                @for (row of report.devices; track row.deviceId) {
                  <tr>
                    <td><b>{{ row.name }}</b></td>
                    <td>{{ row.location || '—' }}</td>
                    <td>
                      @if (row.uptimePercent === null) {
                        <span class="muted" title="A TV ainda não tinha se conectado neste período.">sem dados</span>
                      } @else {
                        <div class="meter" [title]="percent(row.uptimePercent)"><span [style.width.%]="row.uptimePercent" [class.low]="row.uptimePercent < 95"></span></div>
                        {{ percent(row.uptimePercent) }}
                      }
                    </td>
                    <td>{{ human(row.onlineSeconds) }}</td>
                    <td>{{ human(row.offlineSeconds) }}</td>
                    <td>{{ row.incidents }}</td>
                    <td class="row-actions"><a [routerLink]="['/dispositivos', row.deviceId, 'historico']">Histórico</a></td>
                  </tr>
                }
              </table>
            }
          } @else {
            <div class="empty">Carregando…</div>
          }
        }
        @case ('plays') {
          @if (plays(); as report) {
            <div class="summary">
              <div><span class="label">Exibições no período</span><div class="big">{{ report.totalPlays }}</div></div>
              <div><span class="label">Tempo total na tela</span><div class="big">{{ human(report.totalSeconds) }}</div></div>
            </div>
            @if (report.rows.length === 0) {
              <div class="empty">Nenhuma exibição registrada neste período. As contagens vêm do Player (versão 0.5 ou mais nova) a cada contato com o servidor.</div>
            } @else {
              <table>
                <tr><th>{{ playsColumn() }}</th><th>Exibições</th><th>Tempo na tela</th><th>TVs</th></tr>
                @for (row of report.rows; track $index) {
                  <tr>
                    <td><b>{{ row.day ?? row.label }}</b></td>
                    <td>{{ row.plays }}</td>
                    <td>{{ human(row.seconds) }}</td>
                    <td>{{ row.devices }}</td>
                  </tr>
                }
              </table>
            }
          } @else {
            <div class="empty">Carregando…</div>
          }
        }
        @case ('inventory') {
          @if (inventory(); as rows) {
            @if (rows.length === 0) {
              <div class="empty">Nenhuma TV cadastrada ainda.</div>
            } @else {
              <table>
                <tr><th>TV</th><th>Local / grupos</th><th>Situação</th><th>Player</th><th>Exibindo</th></tr>
                @for (row of rows; track row.id) {
                  <tr>
                    <td><b>{{ row.name }}</b><br /><small>ativada {{ when(row.activatedAt) }}</small></td>
                    <td>{{ row.location || 'Sem local' }}@if (row.groups.length > 0) {<br /><small>{{ row.groups.join(', ') }}</small>}</td>
                    <td><span class="dot" [class]="stateOf(row.connection).tone"></span>{{ stateOf(row.connection).label }}<br /><small>contato {{ when(row.lastSeenAt) }}</small></td>
                    <td>{{ row.playerVersion ? 'v' + row.playerVersion : '—' }}</td>
                    <td>
                      {{ row.playlist || 'Sem playlist' }}
                      @if (row.campaign) {
                        <br /><small>📣 campanha {{ row.campaign }}</small>
                      }
                      @if (row.currentMedia) {
                        <br /><small>▶ {{ row.currentMedia }}</small>
                      }
                    </td>
                  </tr>
                }
              </table>
            }
          } @else {
            <div class="empty">Carregando…</div>
          }
        }
        @case ('events') {
          @if (events(); as rows) {
            @if (rows.length === 0) {
              <div class="empty">Nenhum evento neste período e filtro.</div>
            } @else {
              <table>
                <tr><th>Quando</th><th>TV</th><th>Tipo</th><th>O que aconteceu</th></tr>
                @for (row of rows; track row.id) {
                  <tr>
                    <td class="nowrap">{{ when(row.occurredAt) }}</td>
                    <td>{{ row.deviceName || '—' }}</td>
                    <td class="nowrap">{{ eventLabels[row.type].icon }} {{ eventLabels[row.type].label }}@if (row.level !== 'INFO') { <small>({{ levels[row.level] }})</small> }</td>
                    <td [class.bad]="row.level === 'ERROR'">{{ row.message }}</td>
                  </tr>
                }
              </table>
              @if (rows.length >= 500) {
                <p class="label">Mostrando os 500 mais recentes. Baixe o CSV para ver até 20.000 linhas ou reduza o período.</p>
              }
            }
          } @else {
            <div class="empty">Carregando…</div>
          }
        }
      }
    </div>
  `,
  styles: `
    .tabs { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 14px; }
    .tabs button { background: var(--panel); border: 1px solid var(--line); border-radius: 10px; padding: 9px 16px; font-weight: 600; color: var(--muted); }
    .tabs button.on { background: var(--blue); border-color: var(--blue); color: #fff; }
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
    .bad { color: #a8283a; }
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

  readonly playsColumn = computed(() => ({ media: 'Mídia', device: 'TV', day: 'Dia' })[this.groupBy()]);

  readonly eventTypes = EVENT_TYPES;
  readonly eventLabels = EVENT_LABELS;
  readonly levels = LEVEL_LABELS;
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
