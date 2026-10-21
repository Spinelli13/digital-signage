import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { Invoice, InvoiceStatus } from '../core/models';
import { FDatePipe, TPipe, tr } from '../core/i18n';
import { Notice } from '../core/notice';
import { IconComponent } from '../shared/icon';
import { IconName } from '../shared/icons';
import { EmptyStateComponent, LEGACY_TONE, MenuComponent, PageHeaderComponent, PillComponent, SkeletonComponent, Tone } from '../shared/ui';
import { formatMoney, INVOICE_STATUS_LABELS } from '../core/saas';

export const PAYMENT_METHODS = ['PIX', 'BOLETO', 'TRANSFERENCIA', 'CARTAO', 'DINHEIRO', 'OUTRO'];

/** Como cada forma de pagamento aparece (a chave em português; o código guardado no servidor não muda). */
export const METHOD_LABELS: Record<string, string> = {
  PIX: 'PIX',
  BOLETO: 'Boleto',
  TRANSFERENCIA: 'Transferência',
  CARTAO: 'Cartão',
  DINHEIRO: 'Dinheiro',
  OUTRO: 'Outro',
};

@Component({
  selector: 'app-platform-invoices',
  imports: [FDatePipe, FormsModule, TPipe, IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent, MenuComponent],
  template: `
    <app-page-header [title]="'Faturas' | t" [subtitle]="'Cobranças dos clientes. Ao receber um pagamento, registre-o aqui: a fatura fica paga e o cliente volta ao normal.' | t" icon="receipt-text">
      <button class="secondary" [disabled]="running()" (click)="runBilling()"><app-icon name="refresh-cw" [size]="16" />{{ (running() ? 'Rodando…' : 'Rodar cobrança agora') | t }}</button>
    </app-page-header>

    @if (notice.text(); as text) {
      <div class="notice" role="status"><app-icon name="circle-check" [size]="18" /><span>{{ text }}</span></div>
    }

    <div class="filters" role="radiogroup" [attr.aria-label]="'Situação' | t">
      <label class="chip" [class.on]="filter() === ''"><input type="radio" name="f" [checked]="filter() === ''" (change)="setFilter('')" />{{ 'Todas' | t }}</label>
      <label class="chip" [class.on]="filter() === 'OPEN'"><input type="radio" name="f" [checked]="filter() === 'OPEN'" (change)="setFilter('OPEN')" /><app-icon name="hourglass" [size]="15" />{{ 'A pagar' | t }}</label>
      <label class="chip" [class.on]="filter() === 'PAID'"><input type="radio" name="f" [checked]="filter() === 'PAID'" (change)="setFilter('PAID')" /><app-icon name="circle-check" [size]="15" />{{ 'Pagas' | t }}</label>
      <label class="chip" [class.on]="filter() === 'VOID'"><input type="radio" name="f" [checked]="filter() === 'VOID'" (change)="setFilter('VOID')" /><app-icon name="circle-x" [size]="15" />{{ 'Canceladas' | t }}</label>
    </div>

    <div class="card">
      @if (items().length === 0) {
        @if (loaded()) {
          <app-empty-state icon="receipt-text" [title]="'Nenhuma fatura neste filtro.' | t" />
        } @else {
          <app-skeleton [rows]="4" />
        }
      } @else {
        <table class="stack">
          <tr><th>{{ 'Número' | t }}</th><th>{{ 'Cliente' | t }}</th><th>{{ 'Descrição' | t }}</th><th>{{ 'Vencimento' | t }}</th><th>{{ 'Valor' | t }}</th><th>{{ 'Situação' | t }}</th><th></th></tr>
          @for (invoice of items(); track invoice.id) {
            <tr>
              <td class="mono first">{{ invoice.number }}</td>
              <td [attr.data-label]="'Cliente' | t"><b>{{ invoice.tenantName }}</b></td>
              <td [attr.data-label]="'Descrição' | t">{{ invoice.description }}</td>
              <td [attr.data-label]="'Vencimento' | t"><span class="num">{{ invoice.dueDate | fdate }}</span></td>
              <td [attr.data-label]="'Valor' | t"><b class="num">{{ money(invoice) }}</b></td>
              <td [attr.data-label]="'Situação' | t">
                <span class="col">
                  <app-pill [tone]="look(invoice).tone" [icon]="look(invoice).icon">{{ (invoice.overdue ? 'Vencida' : label(invoice.status)) | t }}</app-pill>
                  @if (invoice.paidAt) {
                    <small class="sub">{{ '{method} em {date}' | t: { method: methodText(invoice.paymentMethod), date: (invoice.paidAt | fdate) } }}</small>
                  }
                </span>
              </td>
              <td class="row-actions">
                @if (invoice.status === 'OPEN') {
                  <button class="primary small" (click)="openPay(invoice)"><app-icon name="check" [size]="15" />{{ 'Registrar pagamento' | t }}</button>
                  <app-menu [label]="'Mais ações' | t">
                    <button type="button" class="menu-item danger" (click)="voidInvoice(invoice)"><app-icon name="circle-x" />{{ 'Cancelar' | t }}</button>
                  </app-menu>
                }
              </td>
            </tr>
          }
        </table>
      }
    </div>

    @if (paying(); as invoice) {
      <div class="modal" (click)="closePay()">
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="submitPay()">
          <h2 class="mh"><app-icon name="receipt-text" [size]="22" />{{ 'Registrar pagamento' | t }}</h2>
          <p class="label">{{ invoice.number }} — {{ invoice.tenantName }} — {{ money(invoice) }}</p>
          <div class="field">
            <label for="pay-method">{{ 'Forma de pagamento' | t }}</label>
            <select id="pay-method" name="method" [ngModel]="method()" (ngModelChange)="method.set($event)">
              @for (option of methods; track option) {
                <option [value]="option">{{ methodText(option) }}</option>
              }
            </select>
          </div>
          <div class="field">
            <label for="pay-note">{{ 'Observação (opcional)' | t }}</label>
            <input id="pay-note" name="note" [placeholder]="'Ex.: comprovante recebido por e-mail' | t" [ngModel]="note()" (ngModelChange)="note.set($event)" />
          </div>
          <div class="actions">
            <button type="button" class="secondary" (click)="closePay()">{{ 'Cancelar' | t }}</button>
            <button type="submit" class="primary" [disabled]="busy()">{{ 'Confirmar pagamento' | t }}</button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .filters { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 14px; }
    .chip {
      position: relative; display: inline-flex; align-items: center; gap: 6px; height: 36px; padding: 0 14px; border-radius: 999px; cursor: pointer;
      border: 1px solid var(--line-strong); background: var(--panel); color: var(--text-2); font-size: 13.5px; font-weight: 600; transition: all 0.15s var(--ease);
    }
    .chip:hover { background: var(--panel-2); }
    .chip.on { background: var(--primary-soft); color: var(--primary); border-color: var(--primary); box-shadow: inset 0 0 0 1px var(--primary); }
    :host-context(:root[data-theme='dark']) .chip.on { color: var(--link); }
    .chip input { position: absolute; opacity: 0; inset: 0; width: 100%; height: 100%; margin: 0; cursor: pointer; }
    .chip:has(input:focus-visible) { outline: 2px solid var(--ring-color, var(--primary)); outline-offset: 2px; }
    .row-actions { white-space: nowrap; text-align: right; }
    .row-actions > * { margin-left: 6px; vertical-align: middle; }
    .col { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; }
    .sub { color: var(--muted); }
    .mh { display: flex; align-items: center; gap: 10px; }
    .mh app-icon { color: var(--primary); }
    :host-context(:root[data-theme='dark']) .mh app-icon { color: var(--link); }
  `,
})
export class PlatformInvoicesPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly methods = PAYMENT_METHODS;
  readonly items = signal<Invoice[]>([]);
  readonly loaded = signal(false);
  readonly filter = signal<'' | InvoiceStatus>('OPEN');
  readonly running = signal(false);
  readonly notice = new Notice();

  readonly paying = signal<Invoice | null>(null);
  readonly busy = signal(false);
  readonly method = signal('PIX');
  readonly note = signal('');

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.notice.dispose());
    this.load();
  }

  money(invoice: Invoice): string {
    return formatMoney(invoice.amountCents, invoice.currency);
  }

  /** A chave em português; a tela traduz com o pipe. */
  label(status: InvoiceStatus): string {
    return INVOICE_STATUS_LABELS[status];
  }

  methodText(code: string | null): string {
    return code ? tr(METHOD_LABELS[code] ?? code) : '';
  }

  look(invoice: Invoice): { tone: Tone; icon: IconName } {
    if (invoice.status === 'PAID') {
      return LEGACY_TONE.on;
    }
    if (invoice.status === 'OPEN') {
      return invoice.overdue ? LEGACY_TONE.off : { tone: 'warn', icon: 'hourglass' };
    }
    return { tone: 'neutral', icon: 'circle-x' };
  }

  setFilter(value: '' | InvoiceStatus): void {
    this.filter.set(value);
    this.load();
  }

  openPay(invoice: Invoice): void {
    this.method.set('PIX');
    this.note.set('');
    this.paying.set(invoice);
  }

  closePay(): void {
    this.paying.set(null);
  }

  submitPay(): void {
    const invoice = this.paying();
    if (!invoice) {
      return;
    }
    this.busy.set(true);
    this.api.payInvoice(invoice.id, this.method(), this.note().trim()).subscribe({
      next: () => {
        this.busy.set(false);
        this.closePay();
        this.notice.show(tr('Pagamento da fatura {number} registrado.', { number: invoice.number }));
        this.load();
      },
      error: () => this.busy.set(false),
    });
  }

  voidInvoice(invoice: Invoice): void {
    if (!confirm(tr('Cancelar a fatura {number} de "{tenant}"? O cliente deixa de dever este valor.', { number: invoice.number, tenant: invoice.tenantName }))) {
      return;
    }
    this.api.voidInvoice(invoice.id).subscribe({
      next: () => {
        this.notice.show(tr('Fatura {number} cancelada.', { number: invoice.number }));
        this.load();
      },
      error: () => undefined,
    });
  }

  runBilling(): void {
    this.running.set(true);
    this.api.runBilling().subscribe({
      next: () => {
        this.running.set(false);
        this.notice.show(tr('Ciclo de cobrança executado.'));
        this.load();
      },
      error: () => this.running.set(false),
    });
  }

  private load(): void {
    this.api.platformInvoices(this.filter() || undefined).subscribe({
      next: (list) => {
        this.items.set(list);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }
}
