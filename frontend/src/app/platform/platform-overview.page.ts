import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { PlatformOverview, TenantStatus } from '../core/models';
import { Notice } from '../core/notice';
import { formatMoney, TENANT_STATUS_LABELS, TENANT_STATUS_TONES } from '../core/saas';

const ORDER: TenantStatus[] = ['TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELED'];

@Component({
  selector: 'app-platform-overview',
  imports: [RouterLink],
  template: `
    <div class="head">
      <div>
        <h1>Plataforma</h1>
        <p>Visão geral dos clientes, da receita recorrente e das cobranças.</p>
      </div>
      <button class="secondary" [disabled]="running()" (click)="runBilling()">
        {{ running() ? 'Rodando…' : 'Rodar cobrança agora' }}
      </button>
    </div>

    @if (notice.text(); as text) {
      <div class="notice" role="status">{{ text }}</div>
    }

    @if (overview(); as data) {
      <div class="stats">
        <div class="stat"><div class="label">Clientes</div><div class="n">{{ data.tenants }}</div></div>
        <div class="stat"><div class="label">TVs ativadas</div><div class="n">{{ data.devices }}</div></div>
        <div class="stat">
          <div class="label">Receita mensal (planos ativos)</div>
          <div class="n">{{ money(data.monthlyRecurringCents) }}</div>
        </div>
        <div class="stat">
          <div class="label">Faturas em aberto</div>
          <div class="n">{{ data.openInvoices }}</div>
          <small>{{ money(data.openInvoicesCents) }}</small>
        </div>
        <div class="stat" [class.alert]="data.overdueInvoices > 0">
          <div class="label">Vencidas</div>
          <div class="n">{{ data.overdueInvoices }}</div>
          <small>{{ money(data.overdueInvoicesCents) }}</small>
        </div>
      </div>

      <div class="card">
        <h3>Clientes por situação</h3>
        <table>
          @for (status of order; track status) {
            <tr>
              <td><span class="dot" [class]="tone(status)"></span>{{ label(status) }}</td>
              <td>{{ data.tenantsByStatus[status] ?? 0 }}</td>
            </tr>
          }
        </table>
        <p class="actions-line">
          <a routerLink="/plataforma/clientes">Ver clientes</a> · <a routerLink="/plataforma/faturas">Ver faturas</a>
        </p>
      </div>
    } @else {
      <div class="card"><div class="empty">Carregando…</div></div>
    }
  `,
  styles: `
    .stat.alert .n { color: var(--red); }
    .actions-line { margin-top: 14px; }
  `,
})
export class PlatformOverviewPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly order = ORDER;
  readonly overview = signal<PlatformOverview | null>(null);
  readonly running = signal(false);
  readonly notice = new Notice();

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.notice.dispose());
    this.load();
  }

  money(cents: number): string {
    return formatMoney(cents);
  }

  label(status: TenantStatus): string {
    return TENANT_STATUS_LABELS[status];
  }

  tone(status: TenantStatus): string {
    return TENANT_STATUS_TONES[status];
  }

  runBilling(): void {
    this.running.set(true);
    this.api.runBilling().subscribe({
      next: () => {
        this.running.set(false);
        this.notice.show('Ciclo de cobrança executado: faturas geradas, atrasos e suspensões atualizados.');
        this.load();
      },
      error: () => this.running.set(false),
    });
  }

  private load(): void {
    this.api.platformOverview().subscribe({ next: (data) => this.overview.set(data), error: () => undefined });
  }
}
