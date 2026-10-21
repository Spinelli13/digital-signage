import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { connectionOf, deviceState, formatDateTime, timeAgo } from '../core/format';
import { TPipe, trServer } from '../core/i18n';
import { EVENT_LABELS, EVENT_TYPES, eventLabel, eventTone, levelLabel } from '../core/monitoring-labels';
import { Device, DeviceEvent, EventLevel, EventType } from '../core/models';
import { SITUATION } from '../shared/connection';
import { IconComponent } from '../shared/icon';
import { EmptyStateComponent, PageHeaderComponent, PillComponent, SkeletonComponent } from '../shared/ui';

const PAGE_SIZE = 50;
const REFRESH_MS = 15_000;

/** A história de uma TV: quando conectou, caiu, mudou de conteúdo, atualizou e o que o Player relatou. */
@Component({
  selector: 'app-device-history',
  imports: [FormsModule, RouterLink, TPipe, IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent],
  template: `
    <p class="back"><a routerLink="/dispositivos"><app-icon name="arrow-left" [size]="15" />{{ 'Dispositivos' | t }}</a></p>
    <app-page-header [title]="('Histórico' | t) + (device() ? ' — ' + device()!.name : '')" icon="rotate-ccw-clock">
      <button class="secondary" (click)="reload()"><app-icon name="refresh-cw" [size]="16" />{{ 'Atualizar' | t }}</button>
    </app-page-header>

    @if (device(); as tv) {
      <div class="facts">
        <app-pill [tone]="sit(tv).tone" [icon]="sit(tv).icon">{{ state(tv).label }}</app-pill>
        <app-pill icon="clock">{{ 'último contato' | t }} {{ ago(tv.lastSeenAt) }}</app-pill>
        @if (tv.playerVersion) {
          <app-pill icon="package">{{ 'Player v{version}' | t: { version: tv.playerVersion } }}</app-pill>
        }
        @if (tv.currentMediaName) {
          <app-pill tone="brand" icon="play">{{ 'na tela:' | t }} {{ tv.currentMediaName }}</app-pill>
        }
      </div>
    }

    <div class="card filters">
      <select [attr.aria-label]="'Tipo de evento' | t" [ngModel]="type()" (ngModelChange)="setType($event)">
        <option value="">{{ 'Todos os tipos' | t }}</option>
        @for (kind of types; track kind) {
          <option [value]="kind">{{ eventText(kind) }}</option>
        }
      </select>
      <select [attr.aria-label]="'Nível' | t" [ngModel]="level()" (ngModelChange)="setLevel($event)">
        <option value="">{{ 'Todos os níveis' | t }}</option>
        <option value="ERROR">{{ 'Só erros' | t }}</option>
        <option value="WARN">{{ 'Só avisos' | t }}</option>
        <option value="INFO">{{ 'Só informações' | t }}</option>
      </select>
    </div>

    <div class="card">
      @if (events().length === 0) {
        @if (!loaded()) {
          <app-skeleton [rows]="4" />
        } @else if (type() || level()) {
          <app-empty-state icon="search" [title]="'Nenhum evento com esses filtros.' | t" />
        } @else {
          <app-empty-state icon="rotate-ccw-clock" [title]="'Ainda não há nada no histórico desta TV.' | t" [text]="'Conexões, quedas, trocas de conteúdo e atualizações vão aparecendo aqui.' | t" />
        }
      } @else {
        <ol class="timeline">
          @for (event of events(); track event.id) {
            <li [class]="'lv-' + event.level.toLowerCase()">
              <span class="ev" [class]="tone(event)" aria-hidden="true"><app-icon [name]="labels[event.type].icon" [size]="17" /></span>
              <div class="body">
                <div>
                  <b>{{ eventText(event.type) }}</b>
                  @if (event.level !== 'INFO') {
                    <app-pill [tone]="tone(event)" class="lvl">{{ levelText(event.level) }}</app-pill>
                  }
                </div>
                <div>{{ messageOf(event.message) }}</div>
              </div>
              <time class="when" [attr.datetime]="event.occurredAt" [title]="ago(event.occurredAt)">{{ when(event.occurredAt) }}</time>
            </li>
          }
        </ol>
        @if (hasMore()) {
          <div class="more"><button class="secondary" (click)="loadMore()" [disabled]="loadingMore()"><app-icon name="chevron-down" [size]="16" />{{ (loadingMore() ? 'Carregando…' : 'Carregar mais') | t }}</button></div>
        }
      }
    </div>
  `,
  styles: `
    .back { margin: 0 0 10px; }
    .back a { display: inline-flex; align-items: center; gap: 6px; font-weight: 600; text-decoration: none; }
    .facts { display: flex; gap: 8px; flex-wrap: wrap; margin: -8px 0 18px; }
    .filters { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 14px; padding: 14px; }
    .filters select { width: auto; min-width: 180px; }
    .timeline { list-style: none; margin: 0; padding: 0; }
    .timeline li { position: relative; display: grid; grid-template-columns: 36px 1fr auto; gap: 14px; align-items: start; padding: 12px 4px; }
    .timeline li:not(:last-child)::before { content: ''; position: absolute; left: 21px; top: 50px; bottom: -10px; width: 2px; background: var(--line); }
    .ev { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 50%; border: 1px solid var(--line); background: var(--panel-3); color: var(--text-2); }
    .ev.ok { background: var(--ok-bg); color: var(--ok-fg); border-color: var(--ok-line); }
    .ev.warn { background: var(--warn-bg); color: var(--warn-fg); border-color: var(--warn-line); }
    .ev.err { background: var(--err-bg); color: var(--err-fg); border-color: var(--err-line); }
    .ev.info { background: var(--info-bg); color: var(--info-fg); border-color: var(--info-line); }
    .ev.brand { background: var(--primary-soft); color: var(--primary); border-color: var(--primary-line); }
    :host-context(:root[data-theme='dark']) .ev.brand { color: var(--link); }
    .lvl { margin-left: 8px; height: 22px; font-size: 11.5px; }
    .when { color: var(--muted); font-size: 13px; white-space: nowrap; }
    .body { min-width: 0; word-break: break-word; }
    .lv-error .body > div:last-child { color: var(--err-fg); }
    .more { text-align: center; padding-top: 14px; }
    @media (max-width: 720px) {
      .timeline li { grid-template-columns: 36px 1fr; }
      .when { grid-column: 2; white-space: normal; }
      .filters { flex-direction: column; }
      .filters select { width: 100%; min-width: 0; }
    }
  `,
})
export class DeviceHistoryPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly device = signal<Device | null>(null);
  readonly events = signal<DeviceEvent[]>([]);
  readonly loaded = signal(false);
  readonly hasMore = signal(false);
  readonly loadingMore = signal(false);
  readonly type = signal<EventType | ''>('');
  readonly level = signal<EventLevel | ''>('');

  readonly types = EVENT_TYPES;
  readonly labels = EVENT_LABELS;
  readonly eventText = eventLabel;
  readonly levelText = levelLabel;
  /** Mensagens que o servidor registrou (em português); as conhecidas aparecem traduzidas. */
  readonly messageOf = (message: string) => trServer(message);
  readonly state = deviceState;
  readonly sit = (device: Device) => SITUATION[connectionOf(device)];
  readonly tone = (event: DeviceEvent) => eventTone(event.type, event.level);
  readonly ago = (iso: string | null) => timeAgo(iso);
  readonly when = (iso: string | null) => formatDateTime(iso);

  private deviceId = 0;

  ngOnInit(): void {
    this.deviceId = Number(this.route.snapshot.paramMap.get('id'));
    this.api.device(this.deviceId).subscribe({ next: (device) => this.device.set(device), error: () => undefined });
    this.reload();
    // Só a primeira página se atualiza sozinha; quem já abriu páginas antigas não perde o lugar.
    const timer = setInterval(() => {
      if (this.events().length <= PAGE_SIZE) {
        this.reload();
      }
    }, REFRESH_MS);
    this.destroyRef.onDestroy(() => clearInterval(timer));
  }

  setType(value: EventType | ''): void {
    this.type.set(value);
    this.reload();
  }

  setLevel(value: EventLevel | ''): void {
    this.level.set(value);
    this.reload();
  }

  reload(): void {
    this.api.deviceEvents(this.deviceId, { type: this.type(), level: this.level(), limit: PAGE_SIZE }).subscribe({
      next: (list) => {
        this.events.set(list);
        this.hasMore.set(list.length === PAGE_SIZE);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }

  loadMore(): void {
    const last = this.events()[this.events().length - 1];
    if (!last) {
      return;
    }
    this.loadingMore.set(true);
    this.api.deviceEvents(this.deviceId, { beforeId: last.id, type: this.type(), level: this.level(), limit: PAGE_SIZE }).subscribe({
      next: (list) => {
        this.events.update((current) => [...current, ...list]);
        this.hasMore.set(list.length === PAGE_SIZE);
        this.loadingMore.set(false);
      },
      error: () => this.loadingMore.set(false),
    });
  }
}
