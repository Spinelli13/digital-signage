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
import { IconComponent } from '../shared/icon';
import { EmptyStateComponent, PageHeaderComponent, PillComponent, SkeletonComponent } from '../shared/ui';

const REFRESH_MS = 15_000;

@Component({
  selector: 'app-alerts',
  imports: [FormsModule, RouterLink, TPipe, IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent],
  template: `
    <app-page-header [title]="'Alertas' | t" [subtitle]="'O painel avisa quando uma TV some, nunca se conecta ou o Player não para de dar erro. Os avisos também podem ir para o seu Slack, Discord ou outro sistema.' | t" icon="bell"></app-page-header>

    @if (notice.text(); as text) {
      <div class="notice" role="status"><app-icon name="circle-check" [size]="18" /><span>{{ text }}</span></div>
    }

    <div class="card">
      <div class="bar">
        <h3 class="sec"><app-icon name="bell" [size]="18" />{{ 'Alertas' | t }}</h3>
        <label class="check">
          <input type="checkbox" [ngModel]="onlyOpen()" (ngModelChange)="setOnlyOpen($event)" /> {{ 'Só os abertos' | t }}
        </label>
      </div>
      @if (alerts().length === 0) {
        @if (!loaded()) {
          <app-skeleton [rows]="3" />
        } @else if (onlyOpen()) {
          <app-empty-state icon="shield-check" [title]="'Nenhum alerta aberto' | t" [text]="'Tudo certo por aqui.' | t" />
        } @else {
          <app-empty-state icon="bell" [title]="'Nenhum alerta até agora.' | t" [text]="'Quando uma TV ficar sem contato, nunca se conectar ou o Player errar demais, o aviso aparece aqui.' | t" />
        }
      } @else {
        <table class="stack">
          <tr><th></th><th>{{ 'O que aconteceu' | t }}</th><th>{{ 'Desde' | t }}</th><th>{{ 'Situação' | t }}</th><th></th></tr>
          @for (alert of alerts(); track alert.id) {
            <tr [class.resolved]="!alert.open">
              <td class="dot-col"><span class="state-icon" [class.open]="alert.open"><app-icon [name]="alert.open ? 'triangle-alert' : 'circle-check'" [size]="18" /></span></td>
              <td class="first">
                <b>{{ label(alert) }}</b>
                <small class="msg">{{ messageOf(alert.message) }}</small>
              </td>
              <td [attr.data-label]="'Desde' | t" [title]="when(alert.openedAt)">{{ ago(alert.openedAt) }}</td>
              <td [attr.data-label]="'Situação' | t">
                <span class="situation">
                  @if (alert.open) {
                    <app-pill tone="err" icon="circle-alert">{{ 'Aberto' | t }}</app-pill>
                  } @else {
                    <app-pill tone="ok" icon="circle-check">{{ 'Resolvido' | t }}</app-pill>
                    <small>{{ ago(alert.resolvedAt) }}</small>
                  }
                  @if (settings()?.webhookConfigured) {
                    <small class="hook"><app-icon name="send" [size]="12" />{{ (alert.notified ? 'Webhook avisado' : 'Webhook: aguardando') | t }}</small>
                  }
                </span>
              </td>
              <td class="row-actions">
                <a class="secondary small btn" [routerLink]="['/dispositivos', alert.deviceId, 'historico']"><app-icon name="rotate-ccw-clock" [size]="15" />{{ 'Ver histórico' | t }}</a>
              </td>
            </tr>
          }
        </table>
      }
    </div>

    @if (auth.can('admin')) {
    <div class="card settings">
      <h3 class="sec"><app-icon name="settings" [size]="18" />{{ 'Quando avisar' | t }}</h3>
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
        <div class="field">
          <label for="playlog">{{ 'Guardar cada exibição (comprovação) por (dias)' | t }}</label>
          <input id="playlog" type="number" min="7" max="365" [ngModel]="playLogRetentionDays()" (ngModelChange)="playLogRetentionDays.set($event)" />
        </div>
      </div>

      <h3 class="sub sec"><app-icon name="mail" [size]="18" />{{ 'Aviso por e-mail' | t }}</h3>
      <p class="label">{{ 'Quando uma TV fica fora do ar, avisamos neste e-mail. Pode ser mais de um (até 3), separados por vírgula.' | t }}</p>
      @if (settings() && !settings()?.emailAvailable) {
        <div class="warn-box" role="status" data-testid="email-unavailable">
          <app-icon name="triangle-alert" [size]="18" />
          <span>{{ 'O envio de e-mail ainda não foi ativado neste servidor. Pode salvar o endereço: os avisos começam a sair assim que o envio for ativado.' | t }}</span>
        </div>
      }
      <div class="field">
        <label for="alert-email">{{ 'E-mail dos avisos' | t }}</label>
        <input id="alert-email" type="text" inputmode="email" autocomplete="off" spellcheck="false" [placeholder]="emailPlaceholder"
          [ngModel]="alertEmail()" (ngModelChange)="alertEmail.set($event)" />
      </div>
      @if (emailTestResult(); as result) {
        <div [class]="result.ok ? 'success' : 'error'" role="status" data-testid="email-test-result">
          <app-icon [name]="result.ok ? 'circle-check' : 'circle-alert'" [size]="18" />
          <span>{{ emailTestMessage(result) }}</span>
        </div>
      }
      @if (settings()?.alertEmail && settings()?.emailAvailable) {
        <div class="actions">
          <button class="secondary" (click)="testEmail()" [disabled]="saving() || testingEmail()"><app-icon name="send" [size]="16" />{{ (testingEmail() ? 'Enviando…' : 'Enviar e-mail de teste') | t }}</button>
        </div>
        <p class="label">{{ 'O teste usa o e-mail já salvo: salve antes de testar um endereço novo.' | t }}</p>
      }

      <h3 class="sub sec"><app-icon name="send" [size]="18" />{{ 'Aviso externo (webhook)' | t }}</h3>
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
          <app-icon [name]="result.ok ? 'circle-check' : 'circle-alert'" [size]="18" />
          <span>{{ testMessage(result) }}</span>
        </div>
      }

      <div class="actions">
        @if (settings()?.webhookConfigured) {
          <button class="danger" (click)="removeWebhook()" [disabled]="saving()"><app-icon name="trash" [size]="16" />{{ 'Remover webhook' | t }}</button>
          <button class="secondary" (click)="test()" [disabled]="saving() || testing()"><app-icon name="send" [size]="16" />{{ (testing() ? 'Enviando…' : 'Enviar teste') | t }}</button>
        }
        <button class="primary" (click)="save()" [disabled]="saving()"><app-icon name="check" [size]="16" />{{ (saving() ? 'Salvando…' : 'Salvar configurações') | t }}</button>
      </div>
    </div>
    }
  `,
  styles: `
    .bar { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 8px; }
    .bar h3 { margin: 0; }
    .btn { display: inline-flex; align-items: center; gap: 7px; text-decoration: none; }
    .sec { display: flex; align-items: center; gap: 8px; }
    .sec app-icon { color: var(--primary); }
    :host-context(:root[data-theme='dark']) .sec app-icon { color: var(--link); }
    .state-icon { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 11px; color: var(--ok-fg); background: var(--ok-bg); border: 1px solid var(--ok-line); }
    .state-icon.open { color: var(--err-fg); background: var(--err-bg); border-color: var(--err-line); }
    .msg { display: block; color: var(--muted); }
    .situation { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; }
    .hook { display: inline-flex; align-items: center; gap: 5px; color: var(--muted); }
    tr.resolved td.first b { color: var(--text-2); }
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
    .warn-box { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 14px; padding: 11px 14px; border-radius: var(--r); background: var(--warn-bg); color: var(--warn-fg); border: 1px solid var(--warn-line); }
    .warn-box app-icon { flex: none; margin-top: 1px; }
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
  readonly playLogRetentionDays = signal<number | null>(7);
  readonly notifyOnResolve = signal(true);
  readonly webhookUrl = signal('');
  readonly alertEmail = signal('');
  protected readonly emailPlaceholder = 'voce@empresa.com';

  readonly saving = signal(false);
  readonly testing = signal(false);
  readonly testingEmail = signal(false);
  readonly testResult = signal<WebhookTestResult | null>(null);
  readonly emailTestResult = signal<WebhookTestResult | null>(null);

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
  emailTestMessage(result: WebhookTestResult): string {
    return result.ok
      ? tr('E-mail de teste enviado. Confira a caixa de entrada (e o spam).')
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

  testEmail(): void {
    this.testingEmail.set(true);
    this.emailTestResult.set(null);
    this.api.testAlertEmail().subscribe({
      next: (result) => {
        this.testingEmail.set(false);
        this.emailTestResult.set(result);
      },
      error: () => this.testingEmail.set(false),
    });
  }

  private send(body: MonitoringSettingsRequest, message: string): void {
    this.saving.set(true);
    this.testResult.set(null);
    this.emailTestResult.set(null);
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

  /** Os limites precisam ser números inteiros; os intervalos exatos quem confere é o servidor. */
  private settingsBody(): MonitoringSettingsRequest | null {
    const numbers = [this.offlineAfterMinutes(), this.neverConnectedAfterHours(), this.errorBurstCount(), this.eventsRetentionDays(), this.playLogRetentionDays()];
    if (numbers.some((value) => value === null || !Number.isInteger(value))) {
      this.notice.show(tr('Preencha todos os limites com números inteiros.'));
      return null;
    }
    return {
      offlineAfterMinutes: this.offlineAfterMinutes() as number,
      neverConnectedAfterHours: this.neverConnectedAfterHours() as number,
      errorBurstCount: this.errorBurstCount() as number,
      eventsRetentionDays: this.eventsRetentionDays() as number,
      playLogRetentionDays: this.playLogRetentionDays() as number,
      notifyOnResolve: this.notifyOnResolve(),
      alertEmail: this.alertEmail().trim(),
    };
  }

  private applySettings(settings: MonitoringSettings): void {
    this.settings.set(settings);
    this.offlineAfterMinutes.set(settings.offlineAfterMinutes);
    this.neverConnectedAfterHours.set(settings.neverConnectedAfterHours);
    this.errorBurstCount.set(settings.errorBurstCount);
    this.eventsRetentionDays.set(settings.eventsRetentionDays);
    this.playLogRetentionDays.set(settings.playLogRetentionDays);
    this.notifyOnResolve.set(settings.notifyOnResolve);
    this.alertEmail.set(settings.alertEmail ?? '');
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
