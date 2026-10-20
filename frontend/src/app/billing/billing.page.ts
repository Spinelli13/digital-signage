import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { BillingInfo, PlanOption } from '../core/models';
import { Notice } from '../core/notice';
import {
  daysUntil,
  formatLimit,
  formatMoney,
  formatStorage,
  INVOICE_STATUS_LABELS,
  TENANT_STATUS_LABELS,
  TENANT_STATUS_TONES,
  usageRows,
} from '../core/saas';

@Component({
  selector: 'app-billing',
  imports: [DatePipe, FormsModule],
  template: `
    <div class="head">
      <div>
        <h1>Plano e cobrança</h1>
        <p>O plano contratado, o quanto você já usa, as faturas e como pagar.</p>
      </div>
    </div>

    @if (notice.text(); as text) {
      <div class="notice" role="status">{{ text }}</div>
    }

    @if (info(); as billing) {
      @if (blockedText(); as text) {
        <div class="error" role="alert">{{ text }}</div>
      }

      <div class="card plan">
        <div class="plan-top">
          <div>
            <small>Seu plano</small>
            <h2>{{ billing.tenant.plan.name }}</h2>
            <span class="price">{{ price(billing.tenant.plan.priceCents, billing.tenant.plan.currency) }}</span>
          </div>
          <div class="status">
            <span class="dot" [class]="tone()"></span><b>{{ statusLabel() }}</b>
            @if (billing.tenant.status === 'TRIAL' && billing.tenant.trialEndsAt) {
              <br /><small>Teste até {{ billing.tenant.trialEndsAt | date: 'dd/MM/yyyy' }} ({{ trialDays() }} dia(s))</small>
            } @else if (billing.tenant.currentPeriodEnd) {
              <br /><small>Pago até {{ billing.tenant.currentPeriodEnd | date: 'dd/MM/yyyy' }}</small>
            }
          </div>
        </div>
        <div class="usage">
          @for (row of rows(); track row.label) {
            <div class="usage-row">
              <div class="usage-head"><span>{{ row.label }}</span><small>{{ row.text }}</small></div>
              <div class="bar" [class.full]="row.percent >= 100" [class.high]="row.percent >= 80 && row.percent < 100">
                <div class="fill" [style.width.%]="row.percent"></div>
              </div>
            </div>
          }
        </div>
      </div>

      <div class="card section">
        <h3>Faturas</h3>
        @if (billing.invoices.length === 0) {
          <div class="empty">Nenhuma fatura ainda. A primeira é gerada perto do fim do período de teste.</div>
        } @else {
          <table>
            <tr><th>Número</th><th>Descrição</th><th>Vencimento</th><th>Valor</th><th>Situação</th></tr>
            @for (invoice of billing.invoices; track invoice.id) {
              <tr>
                <td class="mono">{{ invoice.number }}</td>
                <td>{{ invoice.description }}</td>
                <td>{{ invoice.dueDate | date: 'dd/MM/yyyy' }}</td>
                <td>{{ price(invoice.amountCents, invoice.currency) }}</td>
                <td>
                  <span class="dot" [class]="invoiceTone(invoice.status, invoice.overdue)"></span>{{ invoice.overdue ? 'Vencida' : invoiceLabel(invoice.status) }}
                  @if (invoice.paidAt) {
                    <br /><small>em {{ invoice.paidAt | date: 'dd/MM/yyyy' }}</small>
                  }
                </td>
              </tr>
            }
          </table>
        }
      </div>

      <div class="card section">
        <h3>Como pagar</h3>
        @if (billing.paymentInstructions) {
          <p class="instructions">{{ billing.paymentInstructions }}</p>
        } @else {
          <p class="muted">A operação ainda não cadastrou as instruções de pagamento. Fale com o suporte.</p>
        }
        <p class="muted">
          Depois de pagar, o pagamento é registrado pela {{ billing.companyName }} e a fatura passa a "Paga" (o painel atualiza sozinho).
          @if (billing.supportEmail) {
            Dúvidas: <a [href]="'mailto:' + billing.supportEmail">{{ billing.supportEmail }}</a>.
          }
        </p>
      </div>

      <div class="card section">
        <h3>Planos disponíveis</h3>
        <div class="plans">
          @for (plan of billing.plans; track plan.id) {
            <div class="plan-card" [class.current]="plan.id === billing.tenant.plan.id">
              <b>{{ plan.name }}</b>
              <span class="price">{{ price(plan.priceCents, plan.currency) }}<small> /mês</small></span>
              @if (plan.description) {
                <small>{{ plan.description }}</small>
              }
              <ul>
                <li>TVs: {{ limitText(plan.maxDevices) }}</li>
                <li>Espaço de mídias: {{ storageText(plan) }}</li>
                <li>Usuários: {{ limitText(plan.maxUsers) }}</li>
              </ul>
              @if (plan.id === billing.tenant.plan.id) {
                <small class="current-tag">Seu plano</small>
              } @else if (billing.supportEmail) {
                <a class="secondary small" [href]="changeLink(plan, billing)">Pedir este plano</a>
              }
            </div>
          }
        </div>
      </div>

      <div class="card section">
        <h3>Dados da conta</h3>
        <form (ngSubmit)="saveProfile()">
          <div class="field">
            <label for="b-name">Nome do cliente</label>
            <input id="b-name" name="name" required [ngModel]="name()" (ngModelChange)="name.set($event)" />
          </div>
          <div class="field">
            <label for="b-email">E-mail para cobrança</label>
            <input id="b-email" name="billingEmail" type="email" [ngModel]="billingEmail()" (ngModelChange)="billingEmail.set($event)" />
          </div>
          <div class="field">
            <label for="b-doc">CPF/CNPJ (opcional)</label>
            <input id="b-doc" name="document" [ngModel]="document()" (ngModelChange)="document.set($event)" />
          </div>
          <button type="submit" class="primary" [disabled]="busy() || !name().trim()">Salvar</button>
        </form>
      </div>
    } @else {
      <div class="card"><div class="empty">Carregando…</div></div>
    }
  `,
  styles: `
    .section { margin-top: 18px; }
    .plan-top { display: flex; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
    .plan h2 { margin: 2px 0; }
    .price { font-size: 20px; font-weight: 700; }
    .price small { font-weight: 400; }
    .status { text-align: right; }
    .usage { margin-top: 18px; display: grid; gap: 14px; }
    .usage-head { display: flex; justify-content: space-between; margin-bottom: 6px; }
    .bar { height: 9px; border-radius: 6px; background: var(--line); overflow: hidden; }
    .fill { height: 100%; background: var(--green); }
    .bar.high .fill { background: var(--orange); }
    .bar.full .fill { background: var(--red); }
    .instructions { white-space: pre-line; }
    .plans { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 14px; }
    .plan-card { border: 1px solid var(--line); border-radius: 12px; padding: 16px; display: flex; flex-direction: column; gap: 6px; }
    .plan-card.current { border-color: var(--blue); background: var(--blue2); }
    .plan-card ul { margin: 6px 0; padding-left: 18px; }
    .plan-card a { text-align: center; }
    .secondary { display: inline-block; }
    .current-tag { color: var(--blue); font-weight: 600; }
  `,
})
export class BillingPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly info = signal<BillingInfo | null>(null);
  readonly notice = new Notice();
  readonly busy = signal(false);
  readonly name = signal('');
  readonly billingEmail = signal('');
  readonly document = signal('');

  readonly rows = computed(() => {
    const billing = this.info();
    return billing ? usageRows(billing.tenant.plan, billing.tenant.usage) : [];
  });

  readonly statusLabel = computed(() => {
    const billing = this.info();
    return billing ? TENANT_STATUS_LABELS[billing.tenant.status] : '';
  });

  readonly tone = computed(() => {
    const billing = this.info();
    return billing ? TENANT_STATUS_TONES[billing.tenant.status] : 'off';
  });

  readonly trialDays = computed(() => {
    const ends = this.info()?.tenant.trialEndsAt;
    return ends ? Math.max(0, daysUntil(ends)) : 0;
  });

  /** Por que o painel está bloqueado, quando está. */
  readonly blockedText = computed(() => {
    const status = this.info()?.tenant.status;
    if (status === 'SUSPENDED') {
      return 'A assinatura está suspensa por falta de pagamento: o painel fica bloqueado (menos esta página), mas as TVs continuam exibindo. Pague a fatura em aberto para liberar.';
    }
    if (status === 'CANCELED') {
      return 'A assinatura foi encerrada: o painel e as TVs deixaram de funcionar. Fale com o suporte para reativar.';
    }
    return null;
  });

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.notice.dispose());
    this.load();
  }

  price(cents: number, currency: string): string {
    return formatMoney(cents, currency);
  }

  invoiceLabel(status: 'OPEN' | 'PAID' | 'VOID'): string {
    return INVOICE_STATUS_LABELS[status];
  }

  invoiceTone(status: 'OPEN' | 'PAID' | 'VOID', overdue: boolean): string {
    if (status === 'PAID') {
      return 'on';
    }
    return status === 'OPEN' ? (overdue ? 'off' : 'warn') : 'off';
  }

  limitText(value: number | null): string {
    return formatLimit(value);
  }

  storageText(plan: PlanOption): string {
    return plan.maxStorageMb === null ? 'ilimitado' : formatStorage(plan.maxStorageMb * 1024 * 1024);
  }

  changeLink(plan: PlanOption, billing: BillingInfo): string {
    const subject = encodeURIComponent(`Troca de plano — ${billing.tenant.name}`);
    const body = encodeURIComponent(`Olá! Quero mudar o plano de ${billing.tenant.name} de "${billing.tenant.plan.name}" para "${plan.name}".`);
    return `mailto:${billing.supportEmail}?subject=${subject}&body=${body}`;
  }

  saveProfile(): void {
    if (!this.name().trim()) {
      return;
    }
    this.busy.set(true);
    this.api
      .updateBillingProfile({ name: this.name().trim(), billingEmail: this.billingEmail().trim(), document: this.document().trim() })
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.notice.show('Dados da conta salvos.');
          this.load();
          void this.auth.loadMe();
        },
        error: () => this.busy.set(false),
      });
  }

  private load(): void {
    this.api.billing().subscribe({
      next: (billing) => {
        this.info.set(billing);
        this.name.set(billing.tenant.name);
        this.billingEmail.set(billing.tenant.billingEmail ?? '');
        this.document.set(billing.document ?? '');
      },
      error: () => undefined,
    });
  }
}
