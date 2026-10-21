import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { BillingInfo, PlanOption, RedeemedCoupon } from '../core/models';
import { TPipe, formatDate, tr } from '../core/i18n';
import { Notice } from '../core/notice';
import { IconComponent } from '../shared/icon';
import { IconName } from '../shared/icons';
import { EmptyStateComponent, LEGACY_TONE, PageHeaderComponent, PillComponent, SkeletonComponent, Tone } from '../shared/ui';
import {
  couponBenefit,
  daysUntil,
  FEATURES,
  formatLimit,
  formatMoney,
  formatStorage,
  INVOICE_STATUS_LABELS,
  planText,
  tenantStatusLabel,
  TENANT_STATUS_TONES,
  usageRows,
} from '../core/saas';

@Component({
  selector: 'app-billing',
  imports: [FormsModule, TPipe, IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent],
  template: `
    <app-page-header [title]="'Plano e cobrança' | t" [subtitle]="'O plano contratado, o quanto você já usa, as faturas e como pagar.' | t" icon="credit-card"></app-page-header>

    @if (notice.text(); as text) {
      <div class="notice" role="status"><app-icon name="circle-check" [size]="18" /><span>{{ text }}</span></div>
    }

    @if (info(); as billing) {
      @if (blockedText(); as text) {
        <div class="error" role="alert"><app-icon name="triangle-alert" [size]="18" /><span>{{ text }}</span></div>
      }

      <div class="card plan">
        <div class="plan-top">
          <div>
            <small class="eyebrow"><app-icon name="sparkles" [size]="14" />{{ 'Seu plano' | t }}</small>
            <h2>{{ planName(billing.tenant.plan.name) }}</h2>
            <span class="price num">{{ price(billing.tenant.plan.priceCents, billing.tenant.plan.currency) }}</span>
            @if (billing.tenant.bonusUntil) {
              <small class="bonus"><app-icon name="ticket" [size]="14" />{{ 'Plano {plan} liberado por cupom até {date}' | t: { plan: planName(billing.tenant.effectivePlan.name), date: date(billing.tenant.bonusUntil) } }}</small>
            }
          </div>
          <div class="status">
            <app-pill [tone]="look().tone" [icon]="look().icon">{{ statusLabel() }}</app-pill>
            @if (billing.tenant.status === 'TRIAL' && billing.tenant.trialEndsAt) {
              <small>{{ 'Teste até {date} ({n} dia(s))' | t: { date: date(billing.tenant.trialEndsAt), n: trialDays() } }}</small>
            } @else if (billing.tenant.currentPeriodEnd) {
              <small>{{ 'Pago até {date}' | t: { date: date(billing.tenant.currentPeriodEnd) } }}</small>
            }
          </div>
        </div>
        <div class="usage">
          @for (row of rows(); track row.label; let i = $index) {
            <div class="usage-row">
              <div class="usage-head"><span class="usage-label"><app-icon [name]="usageIcons[i]" [size]="16" />{{ row.label }}</span><small class="num">{{ row.text }}</small></div>
              <div class="bar" [class.full]="row.percent >= 100" [class.high]="row.percent >= 80 && row.percent < 100">
                <div class="fill" [style.width.%]="row.percent"></div>
              </div>
            </div>
          }
        </div>
      </div>

      <div class="card section">
        <h3 class="sec"><app-icon name="ticket" [size]="18" />{{ 'Cupom' | t }}</h3>
        <form class="redeem" (ngSubmit)="redeem()">
          <input name="coupon" autocomplete="off" autocapitalize="characters" [attr.aria-label]="'Código do cupom' | t" [placeholder]="'Digite o código do cupom' | t"
            [ngModel]="couponCode()" (ngModelChange)="couponCode.set($event)" />
          <button type="submit" class="primary" [disabled]="busy() || !couponCode().trim()"><app-icon name="check" [size]="16" />{{ 'Resgatar' | t }}</button>
        </form>
        @if (billing.coupons.length === 0) {
          <p class="muted">{{ 'Tem um cupom de desconto, dias grátis ou plano liberado? Digite o código acima.' | t }}</p>
        } @else {
          <ul class="redeemed">
            @for (item of billing.coupons; track item.code) {
              <li>
                <span class="mono">{{ item.code }}</span>
                <span class="grow">{{ benefit(item) }}</span>
                @if (item.active) {
                  <app-pill tone="ok" icon="circle-check">{{ detail(item) }}</app-pill>
                } @else {
                  <app-pill icon="circle-pause">{{ 'Já usado' | t }}</app-pill>
                }
              </li>
            }
          </ul>
        }
      </div>

      <div class="card section">
        <h3 class="sec"><app-icon name="receipt-text" [size]="18" />{{ 'Faturas' | t }}</h3>
        @if (billing.invoices.length === 0) {
          <app-empty-state [compact]="true" icon="receipt-text" [title]="'Nenhuma fatura ainda.' | t" [text]="'A primeira é gerada perto do fim do período de teste.' | t" />
        } @else {
          <table class="stack">
            <tr><th>{{ 'Número' | t }}</th><th>{{ 'Descrição' | t }}</th><th>{{ 'Vencimento' | t }}</th><th>{{ 'Valor' | t }}</th><th>{{ 'Situação' | t }}</th></tr>
            @for (invoice of billing.invoices; track invoice.id) {
              <tr>
                <td class="mono first">{{ invoice.number }}</td>
                <td [attr.data-label]="'Descrição' | t">{{ invoice.description }}</td>
                <td [attr.data-label]="'Vencimento' | t">{{ date(invoice.dueDate) }}</td>
                <td [attr.data-label]="'Valor' | t">{{ price(invoice.amountCents, invoice.currency) }}</td>
                <td [attr.data-label]="'Situação' | t">
                  <span class="inv">
                    <app-pill [tone]="invoiceLook(invoice.status, invoice.overdue).tone" [icon]="invoiceLook(invoice.status, invoice.overdue).icon">{{ (invoice.overdue ? 'Vencida' : invoiceLabel(invoice.status)) | t }}</app-pill>
                    @if (invoice.paidAt) {
                      <small>{{ 'em {date}' | t: { date: date(invoice.paidAt) } }}</small>
                    }
                  </span>
                </td>
              </tr>
            }
          </table>
        }
      </div>

      <div class="card section">
        <h3 class="sec"><app-icon name="credit-card" [size]="18" />{{ 'Como pagar' | t }}</h3>
        @if (billing.paymentInstructions) {
          <p class="instructions">{{ billing.paymentInstructions }}</p>
        } @else {
          <p class="muted">{{ 'A operação ainda não cadastrou as instruções de pagamento. Fale com o suporte.' | t }}</p>
        }
        <p class="muted">
          {{ 'Depois de pagar, o pagamento é registrado pela {company} e a fatura passa a "Paga" (o painel atualiza sozinho).' | t: { company: billing.companyName } }}
          @if (billing.supportEmail) {
            {{ 'Dúvidas:' | t }} <a [href]="'mailto:' + billing.supportEmail">{{ billing.supportEmail }}</a>.
          }
        </p>
      </div>

      <div class="card section">
        <h3 class="sec"><app-icon name="layers" [size]="18" />{{ 'Planos disponíveis' | t }}</h3>
        <div class="plans">
          @for (plan of billing.plans; track plan.id) {
            <div class="plan-card" [class.current]="plan.id === billing.tenant.plan.id">
              <b>{{ planName(plan.name) }}</b>
              <span class="price">{{ price(plan.priceCents, plan.currency) }}<small> {{ '/mês' | t }}</small></span>
              @if (plan.description) {
                <small>{{ planName(plan.description) }}</small>
              }
              <ul>
                <li><app-icon name="monitor" [size]="15" />{{ 'TVs' | t }}: <b class="num">{{ limitText(plan.maxDevices) }}</b></li>
                <li><app-icon name="hard-drive" [size]="15" />{{ 'Espaço de mídias' | t }}: <b class="num">{{ storageText(plan) }}</b></li>
                <li><app-icon name="users" [size]="15" />{{ 'Usuários' | t }}: <b class="num">{{ limitText(plan.maxUsers) }}</b></li>
                @if (plan.maxUploadMb !== null) {
                  <li><app-icon name="upload" [size]="15" />{{ 'Arquivo de até' | t }}: <b class="num">{{ uploadText(plan) }}</b></li>
                }
              </ul>
              <ul class="features">
                @for (feature of allFeatures; track feature.code) {
                  <li [class.off]="!plan.features.includes(feature.code)">
                    <app-icon [name]="plan.features.includes(feature.code) ? 'check' : 'circle-x'" [size]="15" />{{ feature.label | t }}
                  </li>
                }
              </ul>
              @if (plan.id === billing.tenant.plan.id) {
                <app-pill tone="brand" icon="badge-check" class="current-tag">{{ 'Seu plano' | t }}</app-pill>
              } @else if (billing.supportEmail) {
                <a class="secondary small ask" [href]="changeLink(plan, billing)"><app-icon name="mail" [size]="15" />{{ 'Pedir este plano' | t }}</a>
              }
            </div>
          }
        </div>
      </div>

      <div class="card section">
        <h3 class="sec"><app-icon name="building" [size]="18" />{{ 'Dados da conta' | t }}</h3>
        <form (ngSubmit)="saveProfile()">
          <div class="field">
            <label for="b-name">{{ 'Nome do cliente' | t }}</label>
            <input id="b-name" name="name" required [ngModel]="name()" (ngModelChange)="name.set($event)" />
          </div>
          <div class="field">
            <label for="b-email">{{ 'E-mail para cobrança' | t }}</label>
            <input id="b-email" name="billingEmail" type="email" [ngModel]="billingEmail()" (ngModelChange)="billingEmail.set($event)" />
          </div>
          <div class="field">
            <label for="b-doc">{{ 'CPF/CNPJ (opcional)' | t }}</label>
            <input id="b-doc" name="document" [ngModel]="document()" (ngModelChange)="document.set($event)" />
          </div>
          <button type="submit" class="primary" [disabled]="busy() || !name().trim()"><app-icon name="check" [size]="16" />{{ 'Salvar' | t }}</button>
        </form>
      </div>
    } @else {
      <div class="card"><app-skeleton [rows]="5" /></div>
    }
  `,
  styles: `
    .section { margin-top: 18px; }
    .sec { display: flex; align-items: center; gap: 8px; }
    .sec app-icon { color: var(--primary); }
    :host-context(:root[data-theme='dark']) .sec app-icon { color: var(--link); }
    .plan { position: relative; overflow: hidden; background: var(--grad-hero); color: #fff; border: 0; padding: 24px; }
    .plan::after { content: ''; position: absolute; width: 360px; height: 360px; right: -120px; top: -160px; border-radius: 50%; background: radial-gradient(circle, rgba(63, 224, 162, 0.28), transparent 65%); pointer-events: none; }
    .plan > * { position: relative; z-index: 1; }
    .plan-top { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; flex-wrap: wrap; }
    .eyebrow { display: inline-flex; align-items: center; gap: 6px; color: rgba(255, 255, 255, 0.78); text-transform: uppercase; letter-spacing: 0.06em; font-weight: 600; font-size: 11.5px; }
    .plan h2 { margin: 6px 0 2px; font-size: 28px; letter-spacing: -0.02em; color: #fff; }
    .price { font-size: 22px; font-weight: 700; color: var(--accent); }
    .status { display: flex; flex-direction: column; align-items: flex-end; gap: 6px; }
    .status small { color: rgba(255, 255, 255, 0.82); }
    .usage { margin-top: 22px; display: grid; gap: 16px; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); }
    .usage-head { display: flex; justify-content: space-between; align-items: baseline; gap: 8px; margin-bottom: 8px; }
    .usage-label { display: inline-flex; align-items: center; gap: 7px; font-weight: 600; }
    .usage-head small { color: rgba(255, 255, 255, 0.82); }
    .bar { height: 9px; border-radius: 999px; background: rgba(255, 255, 255, 0.18); overflow: hidden; }
    .fill { height: 100%; border-radius: 999px; background: var(--accent); }
    .bar.high .fill { background: #f5b342; }
    .bar.full .fill { background: #ff7a7a; }
    .bonus { display: flex; align-items: center; gap: 6px; margin-top: 6px; color: rgba(255, 255, 255, 0.9); }
    .redeem { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 12px; }
    .redeem input { flex: 1 1 220px; min-width: 0; text-transform: uppercase; }
    .redeemed { list-style: none; margin: 14px 0 0; padding: 0; display: grid; gap: 8px; }
    .redeemed li { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; padding: 10px 12px; border: 1px solid var(--line); border-radius: var(--r); background: var(--panel-2); }
    .redeemed .grow { flex: 1 1 200px; }
    .inv { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; }
    .instructions { white-space: pre-line; }
    .plans { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 14px; margin-top: 12px; }
    .plan-card { border: 1px solid var(--line); border-radius: var(--r-lg); padding: 18px; display: flex; flex-direction: column; gap: 6px; background: var(--panel); }
    .plan-card.current { border-color: var(--primary); background: var(--primary-soft); box-shadow: 0 0 0 1px var(--primary) inset; }
    .plan-card .price { color: var(--text); }
    .plan-card ul { margin: 8px 0; padding: 0; list-style: none; display: grid; gap: 8px; }
    .plan-card li { display: flex; align-items: center; gap: 8px; }
    .plan-card li app-icon { color: var(--primary); flex: none; }
    .plan-card ul.features { margin-top: 0; padding-top: 8px; border-top: 1px dashed var(--line); gap: 6px; }
    .plan-card li.off { color: var(--muted); }
    .plan-card li.off app-icon { color: var(--muted); }
    :host-context(:root[data-theme='dark']) .plan-card li app-icon { color: var(--link); }
    .ask { display: inline-flex; align-items: center; justify-content: center; gap: 7px; text-decoration: none; color: var(--text); margin-top: auto; }
    .current-tag { align-self: flex-start; margin-top: auto; }
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
  readonly couponCode = signal('');

  readonly rows = computed(() => {
    const billing = this.info();
    return billing ? usageRows(billing.tenant.effectivePlan ?? billing.tenant.plan, billing.tenant.usage) : [];
  });

  readonly statusLabel = computed(() => {
    const billing = this.info();
    return billing ? tenantStatusLabel(billing.tenant.status) : '';
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
      return tr('A assinatura está suspensa por falta de pagamento: o painel fica bloqueado (menos esta página), mas as TVs continuam exibindo. Pague a fatura em aberto para liberar.');
    }
    if (status === 'CANCELED') {
      return tr('A assinatura foi encerrada: o painel e as TVs deixaram de funcionar. Fale com o suporte para reativar.');
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

  /** A chave em português; a tela traduz com o pipe. */
  invoiceLabel(status: 'OPEN' | 'PAID' | 'VOID'): string {
    return INVOICE_STATUS_LABELS[status];
  }

  readonly usageIcons: IconName[] = ['monitor', 'hard-drive', 'users'];
  readonly look = computed(() => LEGACY_TONE[this.tone() as 'on' | 'warn' | 'off']);
  readonly planName = planText;
  readonly allFeatures = FEATURES;
  readonly date = (value: string | null) => formatDate(value);

  invoiceLook(status: 'OPEN' | 'PAID' | 'VOID', overdue: boolean): { tone: Tone; icon: IconName } {
    if (status === 'PAID') {
      return LEGACY_TONE.on;
    }
    if (status === 'OPEN') {
      return overdue ? LEGACY_TONE.off : { tone: 'warn', icon: 'hourglass' };
    }
    return { tone: 'neutral', icon: 'circle-x' };
  }

  limitText(value: number | null): string {
    return formatLimit(value);
  }

  uploadText(plan: PlanOption): string {
    return plan.maxUploadMb === null ? tr('ilimitado') : `${plan.maxUploadMb} MB`;
  }

  storageText(plan: PlanOption): string {
    return plan.maxStorageMb === null ? tr('ilimitado') : formatStorage(plan.maxStorageMb * 1024 * 1024);
  }

  changeLink(plan: PlanOption, billing: BillingInfo): string {
    const subject = encodeURIComponent(tr('Troca de plano — {tenant}', { tenant: billing.tenant.name }));
    const body = encodeURIComponent(
      tr('Olá! Quero mudar o plano de {tenant} de "{from}" para "{to}".', {
        tenant: billing.tenant.name,
        from: planText(billing.tenant.plan.name),
        to: planText(plan.name),
      }),
    );
    return `mailto:${billing.supportEmail}?subject=${subject}&body=${body}`;
  }

  benefit(item: RedeemedCoupon): string {
    return couponBenefit(item);
  }

  /** Para o cupom que ainda vale: quantas faturas faltam ou até quando o plano está liberado. */
  detail(item: RedeemedCoupon): string {
    if (item.kind === 'PLAN_UPGRADE') {
      return tr('até {date}', { date: formatDate(item.until) });
    }
    return tr('{n} fatura(s) restante(s)', { n: item.invoicesLeft ?? 0 });
  }

  redeem(): void {
    const code = this.couponCode().trim();
    if (!code || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.api.redeemCoupon(code).subscribe({
      next: (redeemed) => {
        this.busy.set(false);
        this.couponCode.set('');
        this.notice.show(tr('Cupom {code} resgatado: {benefit}.', { code: redeemed.code, benefit: couponBenefit(redeemed) }));
        this.load();
        void this.auth.loadMe();
      },
      // "Cupom inválido ou expirado." e os demais motivos aparecem no aviso de erro
      error: () => this.busy.set(false),
    });
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
          this.notice.show(tr('Dados da conta salvos.'));
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
