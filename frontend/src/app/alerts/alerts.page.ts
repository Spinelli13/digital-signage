import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { formatDateTime, timeAgo } from '../core/format';
import { TPipe, tr, trServer } from '../core/i18n';
import { alertLabel } from '../core/monitoring-labels';
import { Alert, MonitoringSettings, MonitoringSettingsRequest, WebhookTestResult } from '../core/models';
import { Notice } from '../core/notice';

const REFRESH_MS = 15_000;

@Component({
  selector: 'app-alerts',
  imports: [FormsModule, RouterLink, TPipe],
  template: `
    <div class="head">
      <div>
        <h1>{{ 'Alertas' | t }}</h1>
        <p>{{ 'O painel avisa quando uma TV some, nunca se conecta ou o Player não para de dar erro. Os avisos também podem ir para o seu Slack, Discord ou outro sistema.' | t }}</p>
      </div>
    </div>

    @if (notice.text(); as text) {
      <div class="notice" role="status">{{ text }}</div>
    }

    <div class="card">
      <div class="bar">
        <h3>{{ 'Alertas' | t }}</h3>
        <label class="check">
          <input type="checkbox" [ngModel]="onlyOpen()" (ngModelChange)="setOnlyOpen($event)" /> {{ 'Só os abertos' | t }}
        </label>
      </div>
      @if (alerts().length === 0) {
        <div class="empty">
          @if (!loaded()) {
            {{ 'Carregando…' | t }}
          } @else if (onlyOpen()) {
            {{ 'Nenhum alerta aberto. Tudo certo por aqui.' | t }} 🎉
          } @else {
            {{ 'Nenhum alerta até agora.' | t }}
          }
        </div>
      } @else {
        <table class="stack">
          <tr><th></th><th>{{ 'O que aconteceu' | t }}</th><th>{{ 'Desde' | t }}</th><th>{{ 'Situação' | t }}</th><th></th></tr>
          @for (alert of alerts(); track alert.id) {
            <tr [class.resolved]="!alert.open">
              <td class="dot-col"><span class="dot" [class]="alert.open ? 'off' : 'on'"></span></td>
              <td class="first">
                <b>{{ label(alert) }}</b><br />
                <small>{{ messageOf(alert.message) }}</small>
              </td>
              <td [attr.data-label]="'Desde' | t" [title]="when(alert.openedAt)">{{ ago(alert.openedAt) }}</td>
              <td [attr.data-label]="'Situação' | t">
                <span>
                  @if (alert.open) {
                    <b class="bad">{{ 'Aberto' | t }}</b>
                  } @else {
                    {{ 'Resolvido' | t }} {{ ago(alert.resolvedAt) }}
                  }
                  @if (settings()?.webhookConfigured) {
                    <br /><small>{{ (alert.notified ? 'Webhook avisado' : 'Webhook: aguardando') | t }}</small>
                  }
                </span>
              </td>
              <td class="row-actions">
                <a class="secondary small btn" [routerLink]="['/dispositivos', alert.deviceId, 'historico']">{{ 'Ver histórico' | t }}</a>
              </td>
            </tr>
          }
        </table>
      }
    </div>

    @if (auth.can('admin')) {
    <div class="card settings">
      <h3>{{ 'Quando avisar' | t }}</h3>
      <div class="fields">
        <div class="field">
          <label for="offline">{{ 'TV sem contato há mais de (minutos)' | t }}</label>
          <input id="offline" type="number" min="2" max="1440" [ngModel]="offlineAfterMinutes()" (ngModelChange)="offlineAfterMinutes.set($event)" />
        </div>
        <div class="field">
          <label for="never">{{ 'TV ativada que nunca se conectou, após (horas)' | t }}</label>
          <input id="never" type="number" min="1" max="720" [ngModel]="neverConnectedAfterHours()" (ngModelChange)="neverConnectedAfterHours.set($event)" />
        </div>
        <div class="field">
          <label for="errors">{{ 'Erros do Player em 10 minutos para avisar' | t }}</label>
          <input id="errors" type="number" min="1" max="100" [ngModel]="errorBurstCount()" (ngModelChange)="errorBurstCount.set($event)" />
        </div>
        <div class="field">
          <label for="retention">{{ 'Guardar o histórico por (dias)' | t }}</label>
          <input id="retention" type="number" min="7" max="730" [ngModel]="eventsRetentionDays()" (ngModelChange)="eventsRetentionDays.set($event)" />
        </div>
      </div>

      <h3 class="sub">{{ 'Aviso externo (webhook)' | t }}</h3>
      <p class="label">
        {{ 'Cole o endereço de um webhook do Slack, Discord, Teams ou Zapier: cada alerta vira uma mensagem por lá.' | t }}
        {{ 'Por segurança só aceitamos endereços' | t }} <b>https://</b> {{ 'públicos, e o endereço completo nunca é exibido de volta.' | t }}
      </p>
      <div class="field">
        <label for="webhook">{{ 'Endereço do webhook' | t }}</label>
        <input id="webhook" type="url" autocomplete="off" spellcheck="false"
          [placeholder]="webhookPlaceholder()"
          [ngModel]="webhookUrl()" (ngModelChange)="webhookUrl.set($event)" />
      </div>
      <label class="check">
        <input type="checkbox" [ngModel]="notifyOnResolve()" (ngModelChange)="notifyOnResolve.set($event)" /> {{ 'Avisar também quando o problema for resolvido' | t }}
      </label>

      @if (testResult(); as result) {
        <div [class]="result.ok ? 'success' : 'error'" role="status">
          {{ testMessage(result) }}
        </div>
      }

      <div class="actions">
        @if (settings()?.webhookConfigured) {
          <button class="danger" (click)="removeWebhook()" [disabled]="saving()">{{ 'Remover webhook' | t }}</button>
          <button class="secondary" (click)="test()" [disabled]="saving() || testing()">{{ (testing() ? 'Enviando…' : 'Enviar teste') | t }}</button>
        }
        <button class="primary" (click)="save()" [disabled]="saving()">{{ (saving() ? 'Salvando…' : 'Salvar configurações') | t }}</button>
      </div>
    </div>
    }
  `,
  styles: `
    .bar { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 8px; }
    .bar h3 { margin: 0; }
    .btn { display: inline-block; }
    .bad { color: var(--red); }
    tr.resolved td { color: var(--muted); }
    .row-actions { text-align: right; white-space: nowrap; }
    @media (max-width: 720px) {
      table.stack td.dot-col { position: absolute; top: 12px; right: 12px; border: 0; padding: 0; width: auto; }
      table.stack td.dot-col::before { content: none; }
      table.stack td.first { padding-right: 28px; }
    }
    .settings { margin-top: 18px; }
    /* rótulos de uma e de duas linhas: os campos alinham pela base, não pelo topo */
    .fields { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 0 18px; align-items: end; }
    .sub { margin-top: 18px; }
  `,
})
export class AlertsPage implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly alerts = signal<Alert[]>([]);
  readonly loaded = signal(false);
  readonly onlyOpen = signal(true);
  readonly settings = signal<MonitoringSettings | null>(null);
  readonly notice = new Notice();

  readonly offlineAfterMinutes = signal<number | null>(10);
  readonly neverConnectedAfterHours = signal<number | null>(24);
  readonly errorBurstCount = signal<number | null>(5);
  readonly eventsRetentionDays = signal<number | null>(90);
  readonly notifyOnResolve = signal(true);
  readonly webhookUrl = signal('');

  readonly saving = signal(false);
  readonly testing = signal(false);
  readonly testResult = signal<WebhookTestResult | null>(null);

  readonly label = (alert: Alert) => alertLabel(alert.type) + (alert.deviceName ? ` — ${alert.deviceName}` : '');
  /** Texto gerado pelo servidor (em português); as mensagens conhecidas aparecem traduzidas. */
  readonly messageOf = (message: string) => trServer(message);

  webhookPlaceholder(): string {
    const settings = this.settings();
    return settings?.webhookConfigured
      ? tr('Configurado: {hint} — digite um novo para trocar', { hint: settings.webhookHint })
      : 'https://hooks.slack.com/services/…';
  }

  testMessage(result: WebhookTestResult): string {
    return result.ok
      ? tr('Mensagem de teste enviada. Confira no seu canal.')
      : tr('O teste falhou: {reason}', { reason: result.error ? trServer(result.error) : tr('sem detalhes') });
  }
  readonly ago = (iso: string | null) => timeAgo(iso);
  readonly when = (iso: string | null) => formatDateTime(iso);

  ngOnInit(): void {
    this.loadSettings();
    this.loadAlerts();
    const timer = setInterval(() => this.loadAlerts(), REFRESH_MS);
    this.destroyRef.onDestroy(() => {
      clearInterval(timer);
      this.notice.dispose();
    });
  }

  setOnlyOpen(value: boolean): void {
    this.onlyOpen.set(value);
    this.loadAlerts();
  }

  save(): void {
    const body = this.settingsBody();
    if (!body) {
      return;
    }
    // O endereço só vai quando a pessoa digitou um: deixar em branco mantém o que já está configurado.
    const typed = this.webhookUrl().trim();
    if (typed) {
      body.webhookUrl = typed;
    }
    this.send(body, typed ? tr('Configurações salvas. O webhook foi atualizado.') : tr('Configurações salvas.'));
  }

  removeWebhook(): void {
    if (!confirm(tr('Remover o webhook? Os alertas continuam aparecendo aqui, mas deixam de ser enviados para fora.'))) {
      return;
    }
    this.send({ webhookUrl: '' }, tr('Webhook removido.'));
  }

  test(): void {
    this.testing.set(true);
    this.testResult.set(null);
    this.api.testWebhook().subscribe({
      next: (result) => {
        this.testing.set(false);
        this.testResult.set(result);
      },
      error: () => this.testing.set(false),
    });
  }

  private send(body: MonitoringSettingsRequest, message: string): void {
    this.saving.set(true);
    this.testResult.set(null);
    this.api.updateAlertSettings(body).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.applySettings(saved);
        this.webhookUrl.set('');
        this.notice.show(message);
      },
      // O motivo (ex.: "o endereço precisa começar com https://") aparece no aviso de erro do painel.
      error: () => this.saving.set(false),
    });
  }

  /** Os quatro limites precisam ser números inteiros; os intervalos exatos quem confere é o servidor. */
  private settingsBody(): MonitoringSettingsRequest | null {
    const numbers = [this.offlineAfterMinutes(), this.neverConnectedAfterHours(), this.errorBurstCount(), this.eventsRetentionDays()];
    if (numbers.some((value) => value === null || !Number.isInteger(value))) {
      this.notice.show(tr('Preencha os quatro limites com números inteiros.'));
      return null;
    }
    return {
      offlineAfterMinutes: this.offlineAfterMinutes() as number,
      neverConnectedAfterHours: this.neverConnectedAfterHours() as number,
      errorBurstCount: this.errorBurstCount() as number,
      eventsRetentionDays: this.eventsRetentionDays() as number,
      notifyOnResolve: this.notifyOnResolve(),
    };
  }

  private applySettings(settings: MonitoringSettings): void {
    this.settings.set(settings);
    this.offlineAfterMinutes.set(settings.offlineAfterMinutes);
    this.neverConnectedAfterHours.set(settings.neverConnectedAfterHours);
    this.errorBurstCount.set(settings.errorBurstCount);
    this.eventsRetentionDays.set(settings.eventsRetentionDays);
    this.notifyOnResolve.set(settings.notifyOnResolve);
  }

  private loadSettings(): void {
    this.api.alertSettings().subscribe({ next: (settings) => this.applySettings(settings), error: () => undefined });
  }

  private loadAlerts(): void {
    this.api.alerts(this.onlyOpen()).subscribe({
      next: (list) => {
        this.alerts.set(list);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }
}
