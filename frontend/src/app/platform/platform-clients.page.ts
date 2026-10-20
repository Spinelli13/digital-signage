import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { PlatformPlan, TenantStatus, TenantSummary } from '../core/models';
import { Notice } from '../core/notice';
import {
  dateInput,
  endOfDayIso,
  formatLimit,
  formatMoney,
  parseMoney,
  TENANT_STATUS_LABELS,
  TENANT_STATUS_TONES,
} from '../core/saas';

const STATUSES: TenantStatus[] = ['TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELED'];

@Component({
  selector: 'app-platform-clients',
  imports: [DatePipe, FormsModule],
  template: `
    <div class="head">
      <div>
        <h1>Clientes</h1>
        <p>As empresas que usam a plataforma. "Entrar" abre o painel do cliente para você dar suporte.</p>
      </div>
      <button class="primary" (click)="openNew()">+ Novo cliente</button>
    </div>

    @if (notice.text(); as text) {
      <div class="notice" role="status">{{ text }}</div>
    }

    <div class="card">
      @if (items().length === 0) {
        <div class="empty">{{ loaded() ? 'Nenhum cliente ainda. Cadastre o primeiro.' : 'Carregando…' }}</div>
      } @else {
        <table>
          <tr><th>Cliente</th><th>Plano</th><th>Situação</th><th>Uso</th><th>Próximo vencimento</th><th></th></tr>
          @for (tenant of items(); track tenant.id) {
            <tr>
              <td>
                <b>{{ tenant.name }}</b>
                @if (tenant.billingEmail) {
                  <br /><small>{{ tenant.billingEmail }}</small>
                }
              </td>
              <td>{{ tenant.plan.name }}<br /><small>{{ money(tenant.plan.priceCents) }}</small></td>
              <td><span class="dot" [class]="tone(tenant.status)"></span>{{ label(tenant.status) }}</td>
              <td>
                <small>
                  {{ tenant.usage.devices }}/{{ limit(tenant.plan.maxDevices) }} TVs<br />
                  {{ tenant.usage.users }}/{{ limit(tenant.plan.maxUsers) }} usuários
                </small>
              </td>
              <td>
                @if (tenant.status === 'TRIAL' && tenant.trialEndsAt) {
                  teste até {{ tenant.trialEndsAt | date: 'dd/MM/yyyy' }}
                } @else if (tenant.currentPeriodEnd) {
                  {{ tenant.currentPeriodEnd | date: 'dd/MM/yyyy' }}
                } @else {
                  <span class="muted">—</span>
                }
              </td>
              <td class="row-actions">
                <button class="secondary small" (click)="enter(tenant)">Entrar</button>
                <button class="secondary small" (click)="openInvoice(tenant)">Nova fatura</button>
                <button class="secondary small" (click)="openEdit(tenant)">Editar</button>
              </td>
            </tr>
          }
        </table>
      }
    </div>

    @if (modal() === 'new' || modal() === 'edit') {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="submit()">
          <h2>{{ modal() === 'new' ? 'Novo cliente' : 'Editar cliente' }}</h2>
          <div class="field">
            <label for="c-name">Nome da empresa</label>
            <input id="c-name" name="name" required [ngModel]="name()" (ngModelChange)="name.set($event)" />
          </div>
          <div class="field">
            <label for="c-plan">Plano</label>
            <select id="c-plan" name="plan" [ngModel]="planId()" (ngModelChange)="planId.set(+$event)">
              @for (plan of planOptions(); track plan.id) {
                <option [value]="plan.id">{{ plan.name }} — {{ money(plan.priceCents) }}</option>
              }
            </select>
          </div>
          @if (modal() === 'new') {
            <h3 class="sub">Administrador do cliente</h3>
            <div class="field">
              <label for="c-admin-name">Nome</label>
              <input id="c-admin-name" name="adminName" required [ngModel]="adminName()" (ngModelChange)="adminName.set($event)" />
            </div>
            <div class="field">
              <label for="c-admin-email">E-mail (é o login)</label>
              <input id="c-admin-email" name="adminEmail" type="email" required [ngModel]="adminEmail()" (ngModelChange)="adminEmail.set($event)" />
            </div>
            <div class="field">
              <label for="c-admin-pass">Senha inicial</label>
              <input id="c-admin-pass" name="adminPassword" type="password" autocomplete="new-password" minlength="8" required
                [ngModel]="adminPassword()" (ngModelChange)="adminPassword.set($event)" />
              <small>Pelo menos 8 caracteres. Envie ao cliente, que pode trocá-la em "Minha conta".</small>
            </div>
          } @else {
            <div class="field">
              <label for="c-status">Situação</label>
              <select id="c-status" name="status" [ngModel]="status()" (ngModelChange)="status.set($event)">
                @for (option of statuses; track option) {
                  <option [value]="option">{{ label(option) }}</option>
                }
              </select>
              <small>Suspenso bloqueia o painel (as TVs continuam); Encerrado também para as TVs.</small>
            </div>
            <div class="two">
              <div class="field">
                <label for="c-trial">Fim do teste</label>
                <input id="c-trial" name="trialEndsAt" type="date" [ngModel]="trialEndsAt()" (ngModelChange)="trialEndsAt.set($event)" />
              </div>
              <div class="field">
                <label for="c-period">Pago até</label>
                <input id="c-period" name="currentPeriodEnd" type="date" [ngModel]="periodEnd()" (ngModelChange)="periodEnd.set($event)" />
              </div>
            </div>
          }
          <div class="field">
            <label for="c-email">E-mail para cobrança (opcional)</label>
            <input id="c-email" name="billingEmail" type="email" [ngModel]="billingEmail()" (ngModelChange)="billingEmail.set($event)" />
          </div>
          <div class="field">
            <label for="c-doc">CPF/CNPJ (opcional)</label>
            <input id="c-doc" name="document" [ngModel]="document()" (ngModelChange)="document.set($event)" />
          </div>
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">Cancelar</button>
            <button type="submit" class="primary" [disabled]="busy() || !canSubmit()">Salvar</button>
          </div>
        </form>
      </div>
    }

    @if (modal() === 'invoice') {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="submitInvoice()">
          <h2>Nova fatura — {{ invoiceTenant?.name }}</h2>
          <p class="label">Para cobranças avulsas. As mensalidades são geradas sozinhas.</p>
          <div class="field">
            <label for="i-desc">Descrição</label>
            <input id="i-desc" name="description" required [ngModel]="invoiceDescription()" (ngModelChange)="invoiceDescription.set($event)" />
          </div>
          <div class="two">
            <div class="field">
              <label for="i-amount">Valor (R$)</label>
              <input id="i-amount" name="amount" inputmode="decimal" required placeholder="0,00" [ngModel]="invoiceAmount()" (ngModelChange)="invoiceAmount.set($event)" />
            </div>
            <div class="field">
              <label for="i-due">Vencimento</label>
              <input id="i-due" name="due" type="date" required [ngModel]="invoiceDue()" (ngModelChange)="invoiceDue.set($event)" />
            </div>
          </div>
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">Cancelar</button>
            <button type="submit" class="primary" [disabled]="busy() || !canSubmitInvoice()">Criar fatura</button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .row-actions { white-space: nowrap; text-align: right; }
    .row-actions button { margin-left: 6px; }
    .two { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .sub { margin: 14px 0 10px; font-size: 15px; }
  `,
})
export class PlatformClientsPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly statuses = STATUSES;
  readonly items = signal<TenantSummary[]>([]);
  readonly plans = signal<PlatformPlan[]>([]);
  readonly loaded = signal(false);
  readonly notice = new Notice();

  readonly modal = signal<'new' | 'edit' | 'invoice' | null>(null);
  readonly busy = signal(false);
  readonly name = signal('');
  readonly planId = signal(0);
  readonly status = signal<TenantStatus>('TRIAL');
  readonly trialEndsAt = signal('');
  readonly periodEnd = signal('');
  readonly billingEmail = signal('');
  readonly document = signal('');
  readonly adminName = signal('');
  readonly adminEmail = signal('');
  readonly adminPassword = signal('');
  private editing: TenantSummary | null = null;
  private readonly currentPlanId = signal<number | null>(null);

  invoiceTenant: TenantSummary | null = null;
  readonly invoiceDescription = signal('');
  readonly invoiceAmount = signal('');
  readonly invoiceDue = signal('');

  /** Planos que se pode escolher (os desativados só aparecem se o cliente já está neles). */
  readonly planOptions = computed(() => {
    const current = this.currentPlanId();
    return this.plans().filter((plan) => plan.active || plan.id === current);
  });

  readonly canSubmit = computed(() => {
    if (!this.name().trim() || !this.planId()) {
      return false;
    }
    if (this.modal() === 'new') {
      return this.adminName().trim().length > 0 && this.adminEmail().trim().length > 0 && this.adminPassword().length >= 8;
    }
    return true;
  });

  readonly canSubmitInvoice = computed(
    () => this.invoiceDescription().trim().length > 0 && parseMoney(this.invoiceAmount()) !== null && this.invoiceDue().length > 0,
  );

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.notice.dispose());
    this.api.platformPlans().subscribe({ next: (plans) => this.plans.set(plans), error: () => undefined });
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

  /** Limite do plano na tabela: o número, ou "∞" quando não há limite. */
  limit(value: number | null): string {
    return value === null ? '∞' : formatLimit(value);
  }

  async enter(tenant: TenantSummary): Promise<void> {
    await this.auth.enterTenant(tenant.id);
    await this.router.navigate(['/dashboard']);
  }

  openNew(): void {
    this.editing = null;
    this.currentPlanId.set(null);
    this.name.set('');
    this.planId.set(this.plans().find((plan) => plan.active && plan.code !== 'interno')?.id ?? 0);
    this.adminName.set('');
    this.adminEmail.set('');
    this.adminPassword.set('');
    this.billingEmail.set('');
    this.document.set('');
    this.modal.set('new');
  }

  openEdit(tenant: TenantSummary): void {
    this.editing = tenant;
    this.currentPlanId.set(tenant.plan.id);
    this.name.set(tenant.name);
    this.planId.set(tenant.plan.id);
    this.status.set(tenant.status);
    this.trialEndsAt.set(dateInput(tenant.trialEndsAt));
    this.periodEnd.set(dateInput(tenant.currentPeriodEnd));
    this.billingEmail.set(tenant.billingEmail ?? '');
    this.document.set('');
    this.modal.set('edit');
  }

  openInvoice(tenant: TenantSummary): void {
    this.invoiceTenant = tenant;
    this.invoiceDescription.set('');
    this.invoiceAmount.set('');
    this.invoiceDue.set('');
    this.modal.set('invoice');
  }

  closeModal(): void {
    this.modal.set(null);
  }

  submit(): void {
    if (!this.canSubmit()) {
      return;
    }
    this.busy.set(true);
    const editing = this.editing;
    const request = editing
      ? this.api.updatePlatformTenant(editing.id, {
          name: this.name().trim(),
          planId: this.planId(),
          status: this.status(),
          billingEmail: this.billingEmail().trim(),
          ...(this.document().trim() ? { document: this.document().trim() } : {}),
          ...(this.trialEndsAt() ? { trialEndsAt: endOfDayIso(this.trialEndsAt()) } : {}),
          ...(this.periodEnd() ? { currentPeriodEnd: endOfDayIso(this.periodEnd()) } : {}),
        })
      : this.api.createPlatformTenant({
          name: this.name().trim(),
          planId: this.planId(),
          adminName: this.adminName().trim(),
          adminEmail: this.adminEmail().trim(),
          adminPassword: this.adminPassword(),
          billingEmail: this.billingEmail().trim() || null,
          document: this.document().trim() || null,
        });
    request.subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.closeModal();
        this.notice.show(editing ? `Cliente "${saved.name}" salvo.` : `Cliente "${saved.name}" cadastrado.`);
        this.load();
      },
      // O aviso (ex.: "já existe um cliente com esse nome") aparece por cima da janela, que continua aberta.
      error: () => this.busy.set(false),
    });
  }

  submitInvoice(): void {
    const tenant = this.invoiceTenant;
    const amountCents = parseMoney(this.invoiceAmount());
    if (!tenant || amountCents === null || !this.canSubmitInvoice()) {
      return;
    }
    this.busy.set(true);
    this.api
      .createPlatformInvoice(tenant.id, { description: this.invoiceDescription().trim(), amountCents, dueDate: this.invoiceDue() })
      .subscribe({
        next: (invoice) => {
          this.busy.set(false);
          this.closeModal();
          this.notice.show(`Fatura ${invoice.number} criada para "${tenant.name}".`);
        },
        error: () => this.busy.set(false),
      });
  }

  private load(): void {
    this.api.platformTenants().subscribe({
      next: (list) => {
        this.items.set(list);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }
}
