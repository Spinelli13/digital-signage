import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { Invoice, InvoiceStatus } from '../core/models';
import { Notice } from '../core/notice';
import { formatMoney, INVOICE_STATUS_LABELS } from '../core/saas';

export const PAYMENT_METHODS = ['PIX', 'BOLETO', 'TRANSFERENCIA', 'CARTAO', 'DINHEIRO', 'OUTRO'];

@Component({
  selector: 'app-platform-invoices',
  imports: [DatePipe, FormsModule],
  template: `
    <div class="head">
      <div>
        <h1>Faturas</h1>
        <p>Cobranças dos clientes. Ao receber um pagamento, registre-o aqui: a fatura fica paga e o cliente volta ao normal.</p>
      </div>
      <button class="secondary" [disabled]="running()" (click)="runBilling()">{{ running() ? 'Rodando…' : 'Rodar cobrança agora' }}</button>
    </div>

    @if (notice.text(); as text) {
      <div class="notice" role="status">{{ text }}</div>
    }

    <div class="filters">
      <label class="check"><input type="radio" name="f" [checked]="filter() === ''" (change)="setFilter('')" /> Todas</label>
      <label class="check"><input type="radio" name="f" [checked]="filter() === 'OPEN'" (change)="setFilter('OPEN')" /> A pagar</label>
      <label class="check"><input type="radio" name="f" [checked]="filter() === 'PAID'" (change)="setFilter('PAID')" /> Pagas</label>
      <label class="check"><input type="radio" name="f" [checked]="filter() === 'VOID'" (change)="setFilter('VOID')" /> Canceladas</label>
    </div>

    <div class="card">
      @if (items().length === 0) {
        <div class="empty">{{ loaded() ? 'Nenhuma fatura neste filtro.' : 'Carregando…' }}</div>
      } @else {
        <table>
          <tr><th>Número</th><th>Cliente</th><th>Descrição</th><th>Vencimento</th><th>Valor</th><th>Situação</th><th></th></tr>
          @for (invoice of items(); track invoice.id) {
            <tr>
              <td class="mono">{{ invoice.number }}</td>
              <td>{{ invoice.tenantName }}</td>
              <td>{{ invoice.description }}</td>
              <td>{{ invoice.dueDate | date: 'dd/MM/yyyy' }}</td>
              <td>{{ money(invoice) }}</td>
              <td>
                <span class="dot" [class]="tone(invoice)"></span>{{ invoice.overdue ? 'Vencida' : label(invoice.status) }}
                @if (invoice.paidAt) {
                  <br /><small>{{ invoice.paymentMethod }} em {{ invoice.paidAt | date: 'dd/MM/yyyy' }}</small>
                }
              </td>
              <td class="row-actions">
                @if (invoice.status === 'OPEN') {
                  <button class="primary small" (click)="openPay(invoice)">Registrar pagamento</button>
                  <button class="danger small" (click)="voidInvoice(invoice)">Cancelar</button>
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
          <h2>Registrar pagamento</h2>
          <p class="label">{{ invoice.number }} — {{ invoice.tenantName }} — {{ money(invoice) }}</p>
          <div class="field">
            <label for="pay-method">Forma de pagamento</label>
            <select id="pay-method" name="method" [ngModel]="method()" (ngModelChange)="method.set($event)">
              @for (option of methods; track option) {
                <option [value]="option">{{ option }}</option>
              }
            </select>
          </div>
          <div class="field">
            <label for="pay-note">Observação (opcional)</label>
            <input id="pay-note" name="note" placeholder="Ex.: comprovante recebido por e-mail" [ngModel]="note()" (ngModelChange)="note.set($event)" />
          </div>
          <div class="actions">
            <button type="button" class="secondary" (click)="closePay()">Cancelar</button>
            <button type="submit" class="primary" [disabled]="busy()">Confirmar pagamento</button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .filters { display: flex; gap: 18px; flex-wrap: wrap; margin-bottom: 14px; }
    .row-actions { white-space: nowrap; text-align: right; }
    .row-actions button { margin-left: 6px; }
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

  label(status: InvoiceStatus): string {
    return INVOICE_STATUS_LABELS[status];
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
        this.notice.show(`Pagamento da fatura ${invoice.number} registrado.`);
        this.load();
      },
      error: () => this.busy.set(false),
    });
  }

  voidInvoice(invoice: Invoice): void {
    if (!confirm(`Cancelar a fatura ${invoice.number} de "${invoice.tenantName}"? O cliente deixa de dever este valor.`)) {
      return;
    }
    this.api.voidInvoice(invoice.id).subscribe({
      next: () => {
        this.notice.show(`Fatura ${invoice.number} cancelada.`);
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
        this.notice.show('Ciclo de cobrança executado.');
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
