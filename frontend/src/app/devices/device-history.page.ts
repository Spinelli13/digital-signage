import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { deviceState, formatDateTime, timeAgo } from '../core/format';
import { TPipe, trServer } from '../core/i18n';
import { EVENT_LABELS, EVENT_TYPES, eventLabel, levelLabel } from '../core/monitoring-labels';
import { Device, DeviceEvent, EventLevel, EventType } from '../core/models';

const PAGE_SIZE = 50;
const REFRESH_MS = 15_000;

/** A história de uma TV: quando conectou, caiu, mudou de conteúdo, atualizou e o que o Player relatou. */
@Component({
  selector: 'app-device-history',
  imports: [FormsModule, RouterLink, TPipe],
  template: `
    <div class="head">
      <div>
        <p class="back"><a routerLink="/dispositivos">← {{ 'Dispositivos' | t }}</a></p>
        <h1>{{ 'Histórico' | t }}{{ device() ? ' — ' + device()!.name : '' }}</h1>
        @if (device(); as tv) {
          <p>
            <span class="dot" [class]="state(tv).tone"></span>{{ state(tv).label }}
            · {{ 'último contato' | t }} {{ ago(tv.lastSeenAt) }}{{ tv.playerVersion ? ' · Player v' + tv.playerVersion : '' }}
            @if (tv.currentMediaName) {
              · {{ 'na tela:' | t }} {{ tv.currentMediaName }}
            }
          </p>
        }
      </div>
      <button class="secondary" (click)="reload()">{{ '↻ Atualizar' | t }}</button>
    </div>

    <div class="card filters">
      <select [attr.aria-label]="'Tipo de evento' | t" [ngModel]="type()" (ngModelChange)="setType($event)">
        <option value="">{{ 'Todos os tipos' | t }}</option>
        @for (kind of types; track kind) {
          <option [value]="kind">{{ labels[kind].icon }} {{ eventText(kind) }}</option>
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
        <div class="empty">
          @if (!loaded()) {
            {{ 'Carregando…' | t }}
          } @else if (type() || level()) {
            {{ 'Nenhum evento com esses filtros.' | t }}
          } @else {
            {{ 'Ainda não há nada no histórico desta TV.' | t }}
          }
        </div>
      } @else {
        <ol class="timeline">
          @for (event of events(); track event.id) {
            <li [class]="'lv-' + event.level.toLowerCase()">
              <span class="icon" aria-hidden="true">{{ labels[event.type].icon }}</span>
              <div class="body">
                <div>
                  <b>{{ eventText(event.type) }}</b>
                  @if (event.level !== 'INFO') {
                    <span class="badge" [class]="'badge-' + event.level.toLowerCase()">{{ levelText(event.level) }}</span>
                  }
                </div>
                <div>{{ messageOf(event.message) }}</div>
              </div>
              <time class="when" [attr.datetime]="event.occurredAt" [title]="ago(event.occurredAt)">{{ when(event.occurredAt) }}</time>
            </li>
          }
        </ol>
        @if (hasMore()) {
          <div class="more"><button class="secondary" (click)="loadMore()" [disabled]="loadingMore()">{{ (loadingMore() ? 'Carregando…' : 'Carregar mais') | t }}</button></div>
        }
      }
    </div>
  `,
  styles: `
    .back { margin: 0 0 6px; }
    .filters { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 14px; padding: 14px; }
    .filters select { width: auto; min-width: 180px; }
    .timeline { list-style: none; margin: 0; padding: 0; }
    .timeline li { display: grid; grid-template-columns: 34px 1fr auto; gap: 12px; align-items: start; padding: 12px 4px; border-bottom: 1px solid var(--line); }
    .timeline li:last-child { border-bottom: 0; }
    .icon { font-size: 20px; text-align: center; }
    .when { color: var(--muted); font-size: 13px; white-space: nowrap; }
    .body { min-width: 0; word-break: break-word; }
    .lv-error .body > div:last-child { color: var(--err-fg); }
    .badge { display: inline-block; margin-left: 8px; font-size: 12px; border-radius: 999px; padding: 1px 9px; font-weight: 600; }
    .badge-warn { background: var(--warn-bg); color: var(--warn-fg); }
    .badge-error { background: var(--err-bg); color: var(--err-fg); }
    .more { text-align: center; padding-top: 14px; }
    @media (max-width: 720px) {
      .timeline li { grid-template-columns: 28px 1fr; }
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
