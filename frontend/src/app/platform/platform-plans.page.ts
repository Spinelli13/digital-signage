import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { PlatformPlan, PlatformPlanRequest } from '../core/models';
import { Notice } from '../core/notice';
import { formatLimit, formatMoney, formatStorage, moneyInput, parseMoney } from '../core/saas';

@Component({
  selector: 'app-platform-plans',
  imports: [FormsModule],
  template: `
    <div class="head">
      <div>
        <h1>Planos</h1>
        <p>O que cada plano custa e o que inclui. Deixe um limite em branco para "ilimitado".</p>
      </div>
      <button class="primary" (click)="openNew()">+ Novo plano</button>
    </div>

    @if (notice.text(); as text) {
      <div class="notice" role="status">{{ text }}</div>
    }

    <div class="card">
      @if (items().length === 0) {
        <div class="empty">{{ loaded() ? 'Nenhum plano.' : 'Carregando…' }}</div>
      } @else {
        <table>
          <tr><th>Plano</th><th>Preço/mês</th><th>TVs</th><th>Espaço</th><th>Usuários</th><th>Clientes</th><th></th></tr>
          @for (plan of items(); track plan.id) {
            <tr>
              <td>
                <b>{{ plan.name }}</b>
                @if (!plan.active) {
                  <small> (desativado)</small>
                }
                <br /><small class="mono">{{ plan.code }}</small>
              </td>
              <td>{{ money(plan.priceCents) }}</td>
              <td>{{ limit(plan.maxDevices) }}</td>
              <td>{{ storage(plan.maxStorageMb) }}</td>
              <td>{{ limit(plan.maxUsers) }}</td>
              <td>{{ plan.tenantCount }}</td>
              <td class="row-actions">
                <button class="secondary small" (click)="openEdit(plan)">Editar</button>
                <button class="danger small" [disabled]="plan.tenantCount > 0" [title]="plan.tenantCount > 0 ? 'Há clientes neste plano' : ''" (click)="remove(plan)">Excluir</button>
              </td>
            </tr>
          }
        </table>
      }
    </div>

    @if (modal() !== null) {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="submit()">
          <h2>{{ modal() === 'new' ? 'Novo plano' : 'Editar plano' }}</h2>
          <div class="two">
            <div class="field">
              <label for="p-name">Nome</label>
              <input id="p-name" name="name" required [ngModel]="name()" (ngModelChange)="name.set($event)" />
            </div>
            <div class="field">
              <label for="p-code">Código</label>
              <input id="p-code" name="code" required placeholder="ex.: basico" [ngModel]="code()" (ngModelChange)="code.set($event)" />
            </div>
          </div>
          <div class="field">
            <label for="p-desc">Descrição (opcional)</label>
            <input id="p-desc" name="description" [ngModel]="description()" (ngModelChange)="description.set($event)" />
          </div>
          <div class="two">
            <div class="field">
              <label for="p-price">Preço por mês (R$)</label>
              <input id="p-price" name="price" inputmode="decimal" required [ngModel]="price()" (ngModelChange)="price.set($event)" />
            </div>
            <div class="field">
              <label for="p-order">Ordem na lista</label>
              <input id="p-order" name="order" type="number" [ngModel]="sortOrder()" (ngModelChange)="sortOrder.set(+$event)" />
            </div>
          </div>
          <div class="three">
            <div class="field">
              <label for="p-dev">TVs</label>
              <input id="p-dev" name="maxDevices" type="number" min="0" placeholder="ilimitado" [ngModel]="maxDevices()" (ngModelChange)="maxDevices.set($event)" />
            </div>
            <div class="field">
              <label for="p-sto">Espaço (MB)</label>
              <input id="p-sto" name="maxStorageMb" type="number" min="0" placeholder="ilimitado" [ngModel]="maxStorageMb()" (ngModelChange)="maxStorageMb.set($event)" />
            </div>
            <div class="field">
              <label for="p-usr">Usuários</label>
              <input id="p-usr" name="maxUsers" type="number" min="0" placeholder="ilimitado" [ngModel]="maxUsers()" (ngModelChange)="maxUsers.set($event)" />
            </div>
          </div>
          <div class="field">
            <label class="check">
              <input type="checkbox" name="active" [ngModel]="active()" (ngModelChange)="active.set($event)" />
              Disponível para novos clientes e para o cliente comparar
            </label>
          </div>
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">Cancelar</button>
            <button type="submit" class="primary" [disabled]="busy() || !canSubmit()">Salvar</button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .row-actions { white-space: nowrap; text-align: right; }
    .row-actions button { margin-left: 6px; }
    .two { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .three { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
  `,
})
export class PlatformPlansPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly items = signal<PlatformPlan[]>([]);
  readonly loaded = signal(false);
  readonly notice = new Notice();

  readonly modal = signal<'new' | 'edit' | null>(null);
  readonly busy = signal(false);
  readonly name = signal('');
  readonly code = signal('');
  readonly description = signal('');
  readonly price = signal('');
  readonly sortOrder = signal(0);
  readonly maxDevices = signal<number | string | null>(null);
  readonly maxStorageMb = signal<number | string | null>(null);
  readonly maxUsers = signal<number | string | null>(null);
  readonly active = signal(true);
  private editing: PlatformPlan | null = null;

  readonly canSubmit = computed(
    () => this.name().trim().length > 0 && /^[a-z0-9-]+$/.test(this.code().trim()) && parseMoney(this.price()) !== null,
  );

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.notice.dispose());
    this.load();
  }

  money(cents: number): string {
    return formatMoney(cents);
  }

  limit(value: number | null): string {
    return formatLimit(value);
  }

  storage(mb: number | null): string {
    return mb === null ? 'ilimitado' : formatStorage(mb * 1024 * 1024);
  }

  openNew(): void {
    this.editing = null;
    this.name.set('');
    this.code.set('');
    this.description.set('');
    this.price.set('');
    this.sortOrder.set((this.items().at(-1)?.sortOrder ?? 0) + 10);
    this.maxDevices.set(null);
    this.maxStorageMb.set(null);
    this.maxUsers.set(null);
    this.active.set(true);
    this.modal.set('new');
  }

  openEdit(plan: PlatformPlan): void {
    this.editing = plan;
    this.name.set(plan.name);
    this.code.set(plan.code);
    this.description.set(plan.description ?? '');
    this.price.set(moneyInput(plan.priceCents));
    this.sortOrder.set(plan.sortOrder);
    this.maxDevices.set(plan.maxDevices);
    this.maxStorageMb.set(plan.maxStorageMb);
    this.maxUsers.set(plan.maxUsers);
    this.active.set(plan.active);
    this.modal.set('edit');
  }

  closeModal(): void {
    this.modal.set(null);
  }

  submit(): void {
    const priceCents = parseMoney(this.price());
    if (priceCents === null || !this.canSubmit()) {
      return;
    }
    const body: PlatformPlanRequest = {
      code: this.code().trim(),
      name: this.name().trim(),
      description: this.description().trim() || null,
      priceCents,
      maxDevices: limitValue(this.maxDevices()),
      maxStorageMb: limitValue(this.maxStorageMb()),
      maxUsers: limitValue(this.maxUsers()),
      active: this.active(),
      sortOrder: this.sortOrder(),
    };
    this.busy.set(true);
    const editing = this.editing;
    const request = editing ? this.api.updatePlatformPlan(editing.id, body) : this.api.createPlatformPlan(body);
    request.subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.closeModal();
        this.notice.show(editing ? `Plano "${saved.name}" salvo.` : `Plano "${saved.name}" criado.`);
        this.load();
      },
      error: () => this.busy.set(false),
    });
  }

  remove(plan: PlatformPlan): void {
    if (!confirm(`Excluir o plano "${plan.name}"?`)) {
      return;
    }
    this.api.deletePlatformPlan(plan.id).subscribe({
      next: () => {
        this.notice.show(`Plano "${plan.name}" excluído.`);
        this.load();
      },
      error: () => undefined,
    });
  }

  private load(): void {
    this.api.platformPlans().subscribe({
      next: (list) => {
        this.items.set(list);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }
}

/** Campo vazio = ilimitado (null). */
function limitValue(value: number | string | null): number | null {
  if (value === null || value === '') {
    return null;
  }
  const number = Number(value);
  return Number.isFinite(number) && number >= 0 ? Math.floor(number) : null;
}
