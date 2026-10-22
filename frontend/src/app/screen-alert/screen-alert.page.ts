import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { formatDateTime } from '../core/format';
import { TPipe, tr } from '../core/i18n';
import { Device, DeviceGroup, ScreenAlert, ScreenAlertTarget, ScreenAlertTone } from '../core/models';
import { Notice } from '../core/notice';
import { IconComponent } from '../shared/icon';
import { EmptyStateComponent, PageHeaderComponent, PillComponent, SkeletonComponent } from '../shared/ui';

const REFRESH_MS = 15_000;
const TICK_MS = 5_000;
export const MAX_MESSAGE = 300;

/** Quanto falta para o aviso sair do ar, em palavras ("faltam 12 min"). */
export function remainingLabel(expiresAt: string, now: number): string {
  const seconds = Math.max(0, Math.round((Date.parse(expiresAt) - now) / 1000));
  if (seconds < 60) {
    return tr('faltam {n} s', { n: seconds });
  }
  const minutes = Math.ceil(seconds / 60);
  if (minutes < 60) {
    return tr('faltam {n} min', { n: minutes });
  }
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest === 0 ? tr('faltam {h} h', { h: hours }) : tr('faltam {h} h {m} min', { h: hours, m: rest });
}

/**
 * "Alertar agora": escreve a mensagem, escolhe para quem vai (uma TV, um grupo ou todas) e por quanto tempo, e um clique manda o
 * aviso em tela cheia, por cima de tudo. Mostra o que está no ar (com o botão de encerrar) e o que já passou.
 */
@Component({
  selector: 'app-screen-alert',
  imports: [FormsModule, TPipe, IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent],
  template: `
    <app-page-header
      [title]="'Alertar agora' | t"
      [subtitle]="'Um aviso em tela cheia, por cima de tudo, para uma TV, um grupo ou todas as suas TVs. Some sozinho no fim do prazo e você pode encerrar quando quiser.' | t"
      icon="zap"></app-page-header>

    @if (notice.text(); as text) {
      <div class="notice" role="status"><app-icon name="circle-check" [size]="18" /><span>{{ text }}</span></div>
    }

    <div class="card compose">
      <div class="form">
        <div class="field">
          <label for="sa-title">{{ 'Título (opcional)' | t }}</label>
          <input id="sa-title" name="title" maxlength="60" autocomplete="off" [ngModel]="title()" (ngModelChange)="title.set($event)" />
        </div>
        <div class="field">
          <label for="sa-message">{{ 'Mensagem' | t }}</label>
          <textarea id="sa-message" name="message" rows="3" [attr.maxlength]="maxMessage" [ngModel]="message()" (ngModelChange)="message.set($event)"></textarea>
          <small class="count" [class.near]="message().length > maxMessage - 30">{{ message().length }}/{{ maxMessage }}</small>
        </div>

        <fieldset class="tones">
          <legend>{{ 'Urgência' | t }}</legend>
          <label class="tone urgent" [class.on]="tone() === 'URGENT'">
            <input type="radio" name="tone" value="URGENT" [ngModel]="tone()" (ngModelChange)="tone.set($event)" />
            <app-icon name="triangle-alert" [size]="18" />{{ 'Urgente' | t }}
          </label>
          <label class="tone warning" [class.on]="tone() === 'WARNING'">
            <input type="radio" name="tone" value="WARNING" [ngModel]="tone()" (ngModelChange)="tone.set($event)" />
            <app-icon name="circle-alert" [size]="18" />{{ 'Atenção' | t }}
          </label>
          <label class="tone info" [class.on]="tone() === 'INFO'">
            <input type="radio" name="tone" value="INFO" [ngModel]="tone()" (ngModelChange)="tone.set($event)" />
            <app-icon name="info" [size]="18" />{{ 'Informativo' | t }}
          </label>
        </fieldset>

        <div class="row">
          <div class="field">
            <label for="sa-target">{{ 'Para quem' | t }}</label>
            <select id="sa-target" name="target" [ngModel]="target()" (ngModelChange)="setTarget($event)">
              <option value="ALL">{{ 'Todas as TVs' | t }}</option>
              <option value="GROUP">{{ 'Um grupo' | t }}</option>
              <option value="DEVICE">{{ 'Uma TV' | t }}</option>
            </select>
          </div>
          @if (target() === 'GROUP') {
            <div class="field">
              <label for="sa-group">{{ 'Grupo' | t }}</label>
              <select id="sa-group" name="group" [ngModel]="targetId()" (ngModelChange)="targetId.set($event)">
                <option [ngValue]="null">{{ 'Escolha o grupo…' | t }}</option>
                @for (group of groups(); track group.id) {
                  <option [ngValue]="group.id">{{ group.name }}</option>
                }
              </select>
            </div>
          }
          @if (target() === 'DEVICE') {
            <div class="field">
              <label for="sa-device">{{ 'TV' | t }}</label>
              <select id="sa-device" name="device" [ngModel]="targetId()" (ngModelChange)="targetId.set($event)">
                <option [ngValue]="null">{{ 'Escolha a TV…' | t }}</option>
                @for (device of devices(); track device.id) {
                  <option [ngValue]="device.id">{{ device.name }}</option>
                }
              </select>
            </div>
          }
          <div class="field">
            <label for="sa-duration">{{ 'Fica na tela por' | t }}</label>
            <select id="sa-duration" name="duration" [ngModel]="duration()" (ngModelChange)="duration.set($event)">
              <option [ngValue]="5">{{ '5 minutos' | t }}</option>
              <option [ngValue]="15">{{ '15 minutos' | t }}</option>
              <option [ngValue]="30">{{ '30 minutos' | t }}</option>
              <option [ngValue]="60">{{ '1 hora' | t }}</option>
              <option [ngValue]="240">{{ '4 horas' | t }}</option>
              <option [ngValue]="1440">{{ 'Até eu encerrar (no máximo 24 horas)' | t }}</option>
            </select>
          </div>
        </div>

        <p class="reach" data-testid="reach">
          <app-icon name="monitor" [size]="15" />
          {{ reachText() }}
        </p>

        <div class="actions">
          <button type="button" class="primary send" data-testid="send" (click)="send()" [disabled]="!canSend() || busy()">
            <app-icon name="zap" [size]="18" />{{ (busy() ? 'Enviando…' : 'Alertar agora') | t }}
          </button>
        </div>
      </div>

      <div class="preview" aria-hidden="true">
        <small class="cap">{{ 'Assim aparece na TV' | t }}</small>
        <div [class]="'tv ' + toneClass()">
          @if (title().trim()) {
            <b class="tv-title">{{ title().trim() }}</b>
          }
          <span class="tv-message">{{ message().trim() || ('Sua mensagem aparece aqui' | t) }}</span>
        </div>
      </div>
    </div>

    <h3 class="sec"><app-icon name="zap" [size]="18" />{{ 'No ar agora' | t }}</h3>
    @if (!loaded()) {
      <div class="card"><app-skeleton [rows]="2" /></div>
    } @else if (active().length === 0) {
      <div class="card">
        <app-empty-state icon="shield-check" [compact]="true" [title]="'Nenhum aviso no ar' | t"
          [text]="'Quando você mandar um aviso, ele aparece aqui com o botão de encerrar.' | t" />
      </div>
    } @else {
      @for (item of active(); track item.id) {
        <div [class]="'card live ' + toneOf(item.tone)" data-testid="live">
          <div class="live-main">
            <app-pill [tone]="pillTone(item.tone)" icon="zap">{{ toneLabel(item.tone) }}</app-pill>
            <div class="live-text">
              @if (item.title) {
                <b>{{ item.title }}</b>
              }
              <span>{{ item.message }}</span>
              <small class="meta">{{ liveMeta(item) }}</small>
            </div>
          </div>
          <button type="button" class="danger" (click)="end(item)" [disabled]="ending() === item.id">
            <app-icon name="x" [size]="16" />{{ (ending() === item.id ? 'Encerrando…' : 'Encerrar agora') | t }}
          </button>
        </div>
      }
    }

    @if (past().length > 0) {
      <h3 class="sec"><app-icon name="clock" [size]="18" />{{ 'Avisos anteriores' | t }}</h3>
      <div class="card">
        <table class="stack">
          <tr><th>{{ 'Mensagem' | t }}</th><th>{{ 'Para quem' | t }}</th><th>{{ 'Enviado' | t }}</th><th>{{ 'Situação' | t }}</th></tr>
          @for (item of past(); track item.id) {
            <tr data-testid="past">
              <td class="first"><b>{{ item.title || item.message }}</b>@if (item.title) { <small class="meta">{{ item.message }}</small> }</td>
              <td>{{ targetLabel(item) }}</td>
              <td>{{ when(item.createdAt) }}@if (item.createdBy) { <small class="meta">{{ 'por {name}' | t: { name: item.createdBy } }}</small> }</td>
              <td>{{ outcome(item) }}</td>
            </tr>
          }
        </table>
      </div>
    }
  `,
  styles: `
    .sec { display: flex; align-items: center; gap: 8px; margin: 24px 0 10px; }
    .sec app-icon { color: var(--primary); }
    :host-context(:root[data-theme='dark']) .sec app-icon { color: var(--link); }
    .compose { display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr); gap: 24px; align-items: start; }
    .count { display: block; text-align: right; color: var(--muted); margin-top: 4px; }
    .count.near { color: var(--warn-fg); }
    textarea { width: 100%; resize: vertical; min-height: 84px; font: inherit; }
    .row { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 0 16px; }
    fieldset.tones { border: 0; margin: 0 0 14px; padding: 0; display: flex; gap: 10px; flex-wrap: wrap; }
    fieldset.tones legend { padding: 0 0 6px; font-weight: 600; font-size: 13.5px; color: var(--text-2); }
    .tone { display: inline-flex; align-items: center; gap: 8px; padding: 10px 14px; min-height: 44px; border-radius: var(--r); border: 1px solid var(--line); background: var(--panel); cursor: pointer; font-weight: 600; }
    .tone input { position: absolute; opacity: 0; pointer-events: none; }
    .tone:has(input:focus-visible) { outline: 2px solid var(--primary); outline-offset: 2px; }
    .tone.on.urgent { background: var(--err-bg); color: var(--err-fg); border-color: var(--err-line); }
    .tone.on.warning { background: var(--warn-bg); color: var(--warn-fg); border-color: var(--warn-line); }
    .tone.on.info { background: var(--primary-soft); color: var(--primary); border-color: var(--primary-line); }
    .reach { display: flex; align-items: center; gap: 8px; color: var(--muted); margin: 4px 0 14px; }
    .send { min-height: 52px; padding: 0 28px; font-size: 16px; font-weight: 700; }
    .preview .cap { display: block; color: var(--muted); margin-bottom: 8px; }
    .tv { aspect-ratio: 16 / 9; border-radius: var(--r); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; padding: 6%; text-align: center; color: #fff; box-shadow: var(--sh-2); overflow: hidden; }
    .tv.urgent { background: #b3261e; }
    .tv.warning { background: #b45309; }
    .tv.info { background: #5b21b6; }
    .tv-title { font-size: clamp(14px, 2.2vw, 22px); letter-spacing: 0.08em; text-transform: uppercase; opacity: 0.92; }
    .tv-message { font-size: clamp(16px, 2.8vw, 30px); font-weight: 800; line-height: 1.15; overflow-wrap: anywhere; }
    .live { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; margin-bottom: 10px; border-left: 4px solid var(--line); }
    .live.urgent { border-left-color: var(--err-fg); }
    .live.warning { border-left-color: var(--warn-fg); }
    .live.info { border-left-color: var(--primary); }
    .live-main { display: flex; align-items: flex-start; gap: 12px; flex: 1 1 320px; min-width: 0; }
    .live-text { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .live-text span { overflow-wrap: anywhere; }
    .meta { display: block; color: var(--muted); }
    @media (max-width: 900px) {
      .compose { grid-template-columns: 1fr; }
      .send { width: 100%; justify-content: center; }
    }
  `,
})
export class ScreenAlertPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly maxMessage = MAX_MESSAGE;
  readonly notice = new Notice();

  readonly title = signal('');
  readonly message = signal('');
  readonly tone = signal<ScreenAlertTone>('URGENT');
  readonly target = signal<ScreenAlertTarget>('ALL');
  readonly targetId = signal<number | null>(null);
  readonly duration = signal(15);

  readonly items = signal<ScreenAlert[]>([]);
  readonly groups = signal<DeviceGroup[]>([]);
  readonly devices = signal<Device[]>([]);
  readonly loaded = signal(false);
  readonly busy = signal(false);
  readonly ending = signal<number | null>(null);
  /** O relógio da tela, para a contagem regressiva andar sem pedir nada ao servidor. */
  readonly now = signal(Date.now());

  readonly active = computed(() => this.items().filter((item) => item.state === 'ACTIVE' && Date.parse(item.expiresAt) > this.now()));
  readonly past = computed(() => this.items().filter((item) => !this.active().includes(item)));

  readonly canSend = computed(() => {
    const text = this.message().trim();
    return text.length > 0 && text.length <= MAX_MESSAGE && (this.target() === 'ALL' || this.targetId() !== null);
  });

  /** Quantas TVs vão mostrar o aviso (para a pessoa saber o alcance antes de clicar). */
  readonly reachText = computed(() => {
    const active = this.devices();
    if (this.target() === 'ALL') {
      return tr('Vai para todas as TVs ativas ({n}).', { n: active.length });
    }
    if (this.target() === 'GROUP') {
      const group = this.groups().find((candidate) => candidate.id === this.targetId());
      return group ? tr('Vai para o grupo {name} ({n} TV(s)).', { name: group.name, n: group.deviceCount }) : tr('Escolha o grupo que vai receber o aviso.');
    }
    const device = active.find((candidate) => candidate.id === this.targetId());
    return device ? tr('Vai só para a TV {name}.', { name: device.name }) : tr('Escolha a TV que vai receber o aviso.');
  });

  ngOnInit(): void {
    this.load();
    this.api.groups().subscribe({ next: (groups) => this.groups.set(groups), error: () => undefined });
    this.api.devices().subscribe({
      next: (devices) => this.devices.set(devices.filter((device) => device.status === 'ACTIVE')),
      error: () => undefined,
    });
    const refresh = setInterval(() => this.load(), REFRESH_MS);
    const tick = setInterval(() => this.now.set(Date.now()), TICK_MS);
    this.destroyRef.onDestroy(() => {
      clearInterval(refresh);
      clearInterval(tick);
      this.notice.dispose();
    });
  }

  setTarget(target: ScreenAlertTarget): void {
    this.target.set(target);
    this.targetId.set(null);
  }

  send(): void {
    if (!this.canSend() || this.busy()) {
      return;
    }
    this.busy.set(true);
    const title = this.title().trim();
    this.api
      .createScreenAlert({
        ...(title ? { title } : {}),
        message: this.message().trim(),
        tone: this.tone(),
        targetType: this.target(),
        targetId: this.target() === 'ALL' ? null : this.targetId(),
        durationMinutes: this.duration(),
      })
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.title.set('');
          this.message.set('');
          this.notice.show(tr('Aviso enviado. As TVs mostram em até 30 segundos.'));
          this.load();
        },
        // O motivo (ex.: "Já há 5 avisos no ar") aparece no aviso de erro do painel; o que foi digitado fica.
        error: () => this.busy.set(false),
      });
  }

  end(item: ScreenAlert): void {
    this.ending.set(item.id);
    this.api.endScreenAlert(item.id).subscribe({
      next: () => {
        this.ending.set(null);
        this.notice.show(tr('Aviso encerrado. As TVs voltam ao normal em até 30 segundos.'));
        this.load();
      },
      error: () => this.ending.set(null),
    });
  }

  toneLabel(tone: ScreenAlertTone): string {
    return tone === 'URGENT' ? tr('Urgente') : tone === 'WARNING' ? tr('Atenção') : tr('Informativo');
  }

  toneClass(): string {
    return this.toneOf(this.tone());
  }

  toneOf(tone: ScreenAlertTone): string {
    return tone === 'URGENT' ? 'urgent' : tone === 'WARNING' ? 'warning' : 'info';
  }

  pillTone(tone: ScreenAlertTone): 'err' | 'warn' | 'brand' {
    return tone === 'URGENT' ? 'err' : tone === 'WARNING' ? 'warn' : 'brand';
  }

  targetLabel(item: ScreenAlert): string {
    if (item.targetType === 'ALL') {
      return tr('Todas as TVs');
    }
    const name = item.targetName ?? tr('(excluído)');
    return item.targetType === 'GROUP' ? tr('Grupo {name}', { name }) : tr('TV {name}', { name });
  }

  remaining(item: ScreenAlert): string {
    return remainingLabel(item.expiresAt, this.now());
  }

  /** "Todas as TVs · TVs: 3 · faltam 12 min · por Ana": o alcance, o que falta e quem mandou. */
  liveMeta(item: ScreenAlert): string {
    const parts = [this.targetLabel(item)];
    if (item.devices !== null) {
      parts.push(tr('TVs: {n}', { n: item.devices }));
    }
    parts.push(this.remaining(item));
    if (item.createdBy) {
      parts.push(tr('por {name}', { name: item.createdBy }));
    }
    return parts.join(' · ');
  }

  outcome(item: ScreenAlert): string {
    if (item.state === 'ENDED') {
      return item.endedBy ? tr('Encerrado por {name}', { name: item.endedBy }) : tr('Encerrado');
    }
    return item.state === 'EXPIRED' ? tr('O prazo acabou') : tr('No ar');
  }

  readonly when = (iso: string | null) => formatDateTime(iso);

  private load(): void {
    this.api.screenAlerts().subscribe({
      next: (list) => {
        this.items.set(list);
        this.now.set(Date.now());
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }
}
