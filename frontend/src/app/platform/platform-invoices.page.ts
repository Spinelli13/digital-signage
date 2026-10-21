import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { Invoice, InvoiceStatus } from '../core/models';
import { FDatePipe, TPipe, tr } from '../core/i18n';
import { Notice } from '../core/notice';
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
  imports: [FDatePipe, FormsModule, TPipe],
  template: `
    <div class="head">
      <div>
        <h1>{{ 'Faturas' | t }}</h1>
        <p>{{ 'Cobranças dos clientes. Ao receber um pagamento, registre-o aqui: a fatura fica paga e o cliente volta ao normal.' | t }}</p>
      </div>
      <button class="secondary" [disabled]="running()" (click)="runBilling()">{{ (running() ? 'Rodando…' : 'Rodar cobrança agora') | t }}</button>
    </div>

    @if (notice.text(); as text) {
      <div class="notice" role="status">{{ text }}</div>
    }

    <div class="filters">
      <label class="check"><input type="radio" name="f" [checked]="filter() === ''" (change)="setFilter('')" /> {{ 'Todas' | t }}</label>
      <label class="check"><input type="radio" name="f" [checked]="filter() === 'OPEN'" (change)="setFilter('OPEN')" /> {{ 'A pagar' | t }}</label>
      <label class="check"><input type="radio" name="f" [checked]="filter() === 'PAID'" (change)="setFilter('PAID')" /> {{ 'Pagas' | t }}</label>
      <label class="check"><input type="radio" name="f" [checked]="filter() === 'VOID'" (change)="setFilter('VOID')" /> {{ 'Canceladas' | t }}</label>
    </div>

    <div class="card">
      @if (items().length === 0) {
        <div class="empty">{{ (loaded() ? 'Nenhuma fatura neste filtro.' : 'Carregando…') | t }}</div>
      } @else {
        <table class="stack">
          <tr><th>{{ 'Número' | t }}</th><th>{{ 'Cliente' | t }}</th><th>{{ 'Descrição' | t }}</th><th>{{ 'Vencimento' | t }}</th><th>{{ 'Valor' | t }}</th><th>{{ 'Situação' | t }}</th><th></th></tr>
          @for (invoice of items(); track invoice.id) {
            <tr>
              <td class="mono first">{{ invoice.number }}</td>
              <td [attr.data-label]="'Cliente' | t">{{ invoice.tenantName }}</td>
              <td [attr.data-label]="'Descrição' | t">{{ invoice.description }}</td>
              <td [attr.data-label]="'Vencimento' | t">{{ invoice.dueDate | fdate }}</td>
              <td [attr.data-label]="'Valor' | t">{{ money(invoice) }}</td>
              <td [attr.data-label]="'Situação' | t">
                <span>
                  <span class="dot" [class]="tone(invoice)"></span>{{ (invoice.overdue ? 'Vencida' : label(invoice.status)) | t }}
                  @if (invoice.paidAt) {
                    <br /><small>{{ '{method} em {date}' | t: { method: methodText(invoice.paymentMethod), date: (invoice.paidAt | fdate) } }}</small>
                  }
                </span>
              </td>
              <td class="row-actions">
                @if (invoice.status === 'OPEN') {
                  <button class="primary small" (click)="openPay(invoice)">{{ 'Registrar pagamento' | t }}</button>
                  <button class="danger small" (click)="voidInvoice(invoice)">{{ 'Cancelar' | t }}</button>
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
          <h2>{{ 'Registrar pagamento' | t }}</h2>
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
    .filters { display: flex; gap: 18px; flex-wrap: wrap; margin-bottom: 14px; }
    .row-actions { white-space: nowrap; text-align: right; }
    .row-actions button { margin-left: 6px; }
    @media (max-width: 720px) { .filters { gap: 12px 18px; } }
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

  tone(invoice: Invoice): string {
    if (invoice.status === 'PAID') {
      return 'on';
    }
    return invoice.status === 'OPEN' ? (invoice.overdue ? 'off' : 'warn') : 'off';
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
