import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { PlatformOverview, TenantStatus } from '../core/models';
import { TPipe, tr } from '../core/i18n';
import { Notice } from '../core/notice';
import { formatMoney, tenantStatusLabel, TENANT_STATUS_TONES } from '../core/saas';
import { IconComponent } from '../shared/icon';
import { LEGACY_TONE, PageHeaderComponent, PillComponent, SkeletonComponent } from '../shared/ui';

const ORDER: TenantStatus[] = ['TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELED'];

@Component({
  selector: 'app-platform-overview',
  imports: [RouterLink, TPipe, IconComponent, PillComponent, PageHeaderComponent, SkeletonComponent],
  template: `
    <app-page-header [title]="'Plataforma' | t" [subtitle]="'Visão geral dos clientes, da receita recorrente e das cobranças.' | t" icon="server">
      <button class="secondary" [disabled]="running()" (click)="runBilling()">
        <app-icon name="refresh-cw" [size]="16" />{{ (running() ? 'Rodando…' : 'Rodar cobrança agora') | t }}
      </button>
    </app-page-header>

    @if (notice.text(); as text) {
      <div class="notice" role="status"><app-icon name="circle-check" [size]="18" /><span>{{ text }}</span></div>
    }

    @if (overview(); as data) {
      <div class="stats">
        <div class="stat"><span class="s-icon"><app-icon name="building" [size]="18" /></span><div class="label">{{ 'Clientes' | t }}</div><div class="n">{{ data.tenants }}</div></div>
        <div class="stat"><span class="s-icon"><app-icon name="monitor-play" [size]="18" /></span><div class="label">{{ 'TVs ativadas' | t }}</div><div class="n">{{ data.devices }}</div></div>
        <div class="stat">
          <span class="s-icon"><app-icon name="trending-up" [size]="18" /></span>
          <div class="label">{{ 'Receita mensal (planos ativos)' | t }}</div>
          <div class="n">{{ money(data.monthlyRecurringCents) }}</div>
        </div>
        <div class="stat">
          <span class="s-icon"><app-icon name="receipt-text" [size]="18" /></span>
          <div class="label">{{ 'Faturas em aberto' | t }}</div>
          <div class="n">{{ data.openInvoices }}</div>
          <small>{{ money(data.openInvoicesCents) }}</small>
        </div>
        <div class="stat" [class.alert]="data.overdueInvoices > 0">
          <span class="s-icon"><app-icon name="triangle-alert" [size]="18" /></span>
          <div class="label">{{ 'Vencidas' | t }}</div>
          <div class="n">{{ data.overdueInvoices }}</div>
          <small>{{ money(data.overdueInvoicesCents) }}</small>
        </div>
      </div>

      <div class="card">
        <h3 class="sec"><app-icon name="chart-column" [size]="18" />{{ 'Clientes por situação' | t }}</h3>
        <table>
          @for (status of order; track status) {
            <tr>
              <td><app-pill [tone]="look(status).tone" [icon]="look(status).icon">{{ label(status) }}</app-pill></td>
              <td class="qty"><b class="num">{{ data.tenantsByStatus[status] ?? 0 }}</b></td>
            </tr>
          }
        </table>
        <p class="actions-line">
          <a class="secondary small btn" routerLink="/plataforma/clientes"><app-icon name="building" [size]="15" />{{ 'Ver clientes' | t }}</a>
          <a class="secondary small btn" routerLink="/plataforma/faturas"><app-icon name="receipt-text" [size]="15" />{{ 'Ver faturas' | t }}</a>
        </p>
      </div>
    } @else {
      <div class="card"><app-skeleton [rows]="5" /></div>
    }
  `,
  styles: `
    .stat.alert .n { color: var(--err-fg); }
    .stat.alert .s-icon { color: var(--err-fg); background: var(--err-bg); border-color: var(--err-line); }
    .sec { display: flex; align-items: center; gap: 8px; }
    .sec app-icon { color: var(--primary); }
    :host-context(:root[data-theme='dark']) .sec app-icon { color: var(--link); }
    .qty { text-align: right; }
    .actions-line { margin-top: 16px; display: flex; gap: 10px; flex-wrap: wrap; }
    .btn { display: inline-flex; align-items: center; gap: 7px; text-decoration: none; color: var(--text); }
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
    return tenantStatusLabel(status);
  }

  look(status: TenantStatus) {
    return LEGACY_TONE[TENANT_STATUS_TONES[status]];
  }

  runBilling(): void {
    this.running.set(true);
    this.api.runBilling().subscribe({
      next: () => {
        this.running.set(false);
        this.notice.show(tr('Ciclo de cobrança executado: faturas geradas, atrasos e suspensões atualizados.'));
        this.load();
      },
      error: () => this.running.set(false),
    });
  }

  private load(): void {
    this.api.platformOverview().subscribe({ next: (data) => this.overview.set(data), error: () => undefined });
  }
}
