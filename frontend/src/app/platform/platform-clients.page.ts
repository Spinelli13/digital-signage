import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { PlatformPlan, TenantStatus, TenantSummary } from '../core/models';
import { FDatePipe, TPipe, tr } from '../core/i18n';
import { Notice } from '../core/notice';
import { IconComponent } from '../shared/icon';
import { EmptyStateComponent, LEGACY_TONE, MenuComponent, PageHeaderComponent, PillComponent, SkeletonComponent } from '../shared/ui';
import {
  dateInput,
  endOfDayIso,
  formatLimit,
  formatMoney,
  parseMoney,
  planText,
  tenantStatusLabel,
  TENANT_STATUS_TONES,
} from '../core/saas';

const STATUSES: TenantStatus[] = ['TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELED'];

@Component({
  selector: 'app-platform-clients',
  imports: [FDatePipe, FormsModule, TPipe, IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent, MenuComponent],
  template: `
    <app-page-header [title]="'Clientes' | t" [subtitle]="'As empresas que usam a plataforma. &quot;Entrar&quot; abre o painel do cliente para você dar suporte.' | t" icon="building">
      <button class="primary" (click)="openNew()"><app-icon name="plus" />{{ 'Novo cliente' | t }}</button>
    </app-page-header>

    @if (notice.text(); as text) {
      <div class="notice" role="status"><app-icon name="circle-check" [size]="18" /><span>{{ text }}</span></div>
    }

    <div class="card">
      @if (items().length === 0) {
        @if (loaded()) {
          <app-empty-state icon="building" [title]="'Nenhum cliente ainda' | t" [text]="'Cadastre o primeiro.' | t">
            <button class="primary" (click)="openNew()"><app-icon name="plus" />{{ 'Novo cliente' | t }}</button>
          </app-empty-state>
        } @else {
          <app-skeleton [rows]="4" />
        }
      } @else {
        <table class="stack">
          <tr><th>{{ 'Cliente' | t }}</th><th>{{ 'Plano' | t }}</th><th>{{ 'Situação' | t }}</th><th>{{ 'Uso' | t }}</th><th>{{ 'Próximo vencimento' | t }}</th><th></th></tr>
          @for (tenant of items(); track tenant.id) {
            <tr>
              <td class="first">
                <span class="place">
                  <span class="place-icon"><app-icon name="building" [size]="18" /></span>
                  <span class="place-text">
                    <b>{{ tenant.name }}</b>
                    @if (tenant.billingEmail) {
                      <small>{{ tenant.billingEmail }}</small>
                    }
                  </span>
                </span>
              </td>
              <td [attr.data-label]="'Plano' | t"><span class="col">{{ planName(tenant.plan.name) }}<small class="sub num">{{ money(tenant.plan.priceCents) }}</small></span></td>
              <td [attr.data-label]="'Situação' | t"><app-pill [tone]="look(tenant.status).tone" [icon]="look(tenant.status).icon">{{ label(tenant.status) }}</app-pill></td>
              <td [attr.data-label]="'Uso' | t">
                <span class="col">
                  <small class="sub"><app-icon name="monitor" [size]="13" /><span class="num">{{ tenant.usage.devices }}/{{ limit(tenant.plan.maxDevices) }}</span> {{ 'TVs' | t }}</small>
                  <small class="sub"><app-icon name="users" [size]="13" /><span class="num">{{ tenant.usage.users }}/{{ limit(tenant.plan.maxUsers) }}</span> {{ 'usuários' | t }}</small>
                </span>
              </td>
              <td [attr.data-label]="'Próximo vencimento' | t">
                @if (tenant.status === 'TRIAL' && tenant.trialEndsAt) {
                  {{ 'teste até' | t }} {{ tenant.trialEndsAt | fdate }}
                } @else if (tenant.currentPeriodEnd) {
                  {{ tenant.currentPeriodEnd | fdate }}
                } @else {
                  <span class="muted">—</span>
                }
              </td>
              <td class="row-actions">
                <button class="secondary small" (click)="enter(tenant)"><app-icon name="arrow-right" [size]="15" />{{ 'Entrar' | t }}</button>
                <button class="secondary small" (click)="openEdit(tenant)"><app-icon name="pencil" [size]="15" />{{ 'Editar' | t }}</button>
                <app-menu [label]="'Mais ações' | t">
                  <button type="button" class="menu-item" (click)="openInvoice(tenant)"><app-icon name="receipt-text" />{{ 'Nova fatura' | t }}</button>
                </app-menu>
              </td>
            </tr>
          }
        </table>
      }
    </div>

    @if (modal() === 'new' || modal() === 'edit') {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="submit()">
          <h2>{{ (modal() === 'new' ? 'Novo cliente' : 'Editar cliente') | t }}</h2>
          <div class="field">
            <label for="c-name">{{ 'Nome da empresa' | t }}</label>
            <input id="c-name" name="name" required [ngModel]="name()" (ngModelChange)="name.set($event)" />
          </div>
          <div class="field">
            <label for="c-plan">{{ 'Plano' | t }}</label>
            <select id="c-plan" name="plan" [ngModel]="planId()" (ngModelChange)="planId.set(+$event)">
              @for (plan of planOptions(); track plan.id) {
                <option [value]="plan.id">{{ planName(plan.name) }} — {{ money(plan.priceCents) }}</option>
              }
            </select>
          </div>
          @if (modal() === 'new') {
            <h3 class="sub">{{ 'Administrador do cliente' | t }}</h3>
            <div class="field">
              <label for="c-admin-name">{{ 'Nome' | t }}</label>
              <input id="c-admin-name" name="adminName" required [ngModel]="adminName()" (ngModelChange)="adminName.set($event)" />
            </div>
            <div class="field">
              <label for="c-admin-email">{{ 'E-mail (é o login)' | t }}</label>
              <input id="c-admin-email" name="adminEmail" type="email" required [ngModel]="adminEmail()" (ngModelChange)="adminEmail.set($event)" />
            </div>
            <div class="field">
              <label for="c-admin-pass">{{ 'Senha inicial' | t }}</label>
              <input id="c-admin-pass" name="adminPassword" type="password" autocomplete="new-password" minlength="8" required
                [ngModel]="adminPassword()" (ngModelChange)="adminPassword.set($event)" />
              <small>{{ 'Pelo menos 8 caracteres. Envie ao cliente, que pode trocá-la em "Minha conta".' | t }}</small>
            </div>
          } @else {
            <div class="field">
              <label for="c-status">{{ 'Situação' | t }}</label>
              <select id="c-status" name="status" [ngModel]="status()" (ngModelChange)="status.set($event)">
                @for (option of statuses; track option) {
                  <option [value]="option">{{ label(option) }}</option>
                }
              </select>
              <small>{{ 'Suspenso bloqueia o painel (as TVs continuam); Encerrado também para as TVs.' | t }}</small>
            </div>
            <div class="two">
              <div class="field">
                <label for="c-trial">{{ 'Fim do teste' | t }}</label>
                <input id="c-trial" name="trialEndsAt" type="date" [ngModel]="trialEndsAt()" (ngModelChange)="trialEndsAt.set($event)" />
              </div>
              <div class="field">
                <label for="c-period">{{ 'Pago até' | t }}</label>
                <input id="c-period" name="currentPeriodEnd" type="date" [ngModel]="periodEnd()" (ngModelChange)="periodEnd.set($event)" />
              </div>
            </div>
          }
          <div class="field">
            <label for="c-email">{{ 'E-mail para cobrança (opcional)' | t }}</label>
            <input id="c-email" name="billingEmail" type="email" [ngModel]="billingEmail()" (ngModelChange)="billingEmail.set($event)" />
          </div>
          <div class="field">
            <label for="c-doc">{{ 'CPF/CNPJ (opcional)' | t }}</label>
            <input id="c-doc" name="document" [ngModel]="document()" (ngModelChange)="document.set($event)" />
          </div>
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">{{ 'Cancelar' | t }}</button>
            <button type="submit" class="primary" [disabled]="busy() || !canSubmit()">{{ 'Salvar' | t }}</button>
          </div>
        </form>
      </div>
    }

    @if (modal() === 'invoice') {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="submitInvoice()">
          <h2>{{ 'Nova fatura' | t }} — {{ invoiceTenant?.name }}</h2>
          <p class="label">{{ 'Para cobranças avulsas. As mensalidades são geradas sozinhas.' | t }}</p>
          <div class="field">
            <label for="i-desc">{{ 'Descrição' | t }}</label>
            <input id="i-desc" name="description" required [ngModel]="invoiceDescription()" (ngModelChange)="invoiceDescription.set($event)" />
          </div>
          <div class="two">
            <div class="field">
              <label for="i-amount">{{ 'Valor (R$)' | t }}</label>
              <input id="i-amount" name="amount" inputmode="decimal" required placeholder="0,00" [ngModel]="invoiceAmount()" (ngModelChange)="invoiceAmount.set($event)" />
            </div>
            <div class="field">
              <label for="i-due">{{ 'Vencimento' | t }}</label>
              <input id="i-due" name="due" type="date" required [ngModel]="invoiceDue()" (ngModelChange)="invoiceDue.set($event)" />
            </div>
          </div>
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">{{ 'Cancelar' | t }}</button>
            <button type="submit" class="primary" [disabled]="busy() || !canSubmitInvoice()">{{ 'Criar fatura' | t }}</button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .row-actions { white-space: nowrap; text-align: right; }
    .row-actions > * { margin-left: 6px; vertical-align: middle; }
    .place { display: flex; align-items: center; gap: 12px; min-width: 0; }
    .place-icon { flex: none; display: grid; place-items: center; width: 38px; height: 38px; border-radius: 12px; color: var(--primary); background: var(--primary-soft); border: 1px solid var(--primary-line); }
    :host-context(:root[data-theme='dark']) .place-icon { color: var(--link); }
    .place-text { display: flex; flex-direction: column; min-width: 0; }
    .place-text small { color: var(--muted); overflow: hidden; text-overflow: ellipsis; }
    .col { display: flex; flex-direction: column; align-items: flex-start; gap: 3px; }
    .sub { display: inline-flex; align-items: center; gap: 5px; color: var(--muted); }
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
  readonly look = (status: TenantStatus) => LEGACY_TONE[TENANT_STATUS_TONES[status]];
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
    return tenantStatusLabel(status);
  }

  readonly planName = planText;

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
        this.notice.show(editing ? tr('Cliente "{name}" salvo.', { name: saved.name }) : tr('Cliente "{name}" cadastrado.', { name: saved.name }));
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
          this.notice.show(tr('Fatura {number} criada para "{tenant}".', { number: invoice.number, tenant: tenant.name }));
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
