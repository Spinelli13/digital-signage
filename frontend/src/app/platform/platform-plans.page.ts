import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { FeatureCode, PlatformPlan, PlatformPlanRequest } from '../core/models';
import { TPipe, tr } from '../core/i18n';
import { Notice } from '../core/notice';
import { IconComponent } from '../shared/icon';
import { EmptyStateComponent, MenuComponent, PageHeaderComponent, PillComponent, SkeletonComponent } from '../shared/ui';
import { FEATURES, featureLabel, formatLimit, formatMoney, formatStorage, moneyInput, parseMoney, planText } from '../core/saas';

@Component({
  selector: 'app-platform-plans',
  imports: [FormsModule, TPipe, IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent, MenuComponent],
  template: `
    <app-page-header [title]="'Planos' | t" [subtitle]="'O que cada plano custa e o que inclui. Deixe um limite em branco para &quot;ilimitado&quot;.' | t" icon="layers">
      <button class="primary" (click)="openNew()"><app-icon name="plus" />{{ 'Novo plano' | t }}</button>
    </app-page-header>

    @if (notice.text(); as text) {
      <div class="notice" role="status"><app-icon name="circle-check" [size]="18" /><span>{{ text }}</span></div>
    }

    <div class="card">
      @if (items().length === 0) {
        @if (loaded()) {
          <app-empty-state icon="layers" [title]="'Nenhum plano.' | t">
            <button class="primary" (click)="openNew()"><app-icon name="plus" />{{ 'Novo plano' | t }}</button>
          </app-empty-state>
        } @else {
          <app-skeleton [rows]="3" />
        }
      } @else {
        <table class="stack">
          <tr><th>{{ 'Plano' | t }}</th><th>{{ 'Preço/mês' | t }}</th><th>{{ 'TVs' | t }}</th><th>{{ 'Espaço' | t }}</th><th>{{ 'Usuários' | t }}</th><th>{{ 'Recursos' | t }}</th><th>{{ 'Clientes' | t }}</th><th></th></tr>
          @for (plan of items(); track plan.id) {
            <tr>
              <td class="first">
                <span class="place">
                  <span class="place-icon"><app-icon name="tag" [size]="18" /></span>
                  <span class="place-text">
                    <b>{{ planName(plan.name) }}@if (!plan.active) {<small class="off"> {{ '(desativado)' | t }}</small>}</b>
                    <small class="mono">{{ plan.code }}</small>
                  </span>
                </span>
              </td>
              <td [attr.data-label]="'Preço/mês' | t"><b class="num">{{ money(plan.priceCents) }}</b></td>
              <td [attr.data-label]="'TVs' | t"><span class="num">{{ limit(plan.maxDevices) }}</span></td>
              <td [attr.data-label]="'Espaço' | t"><span class="num">{{ storage(plan.maxStorageMb) }}</span></td>
              <td [attr.data-label]="'Usuários' | t"><span class="num">{{ limit(plan.maxUsers) }}</span></td>
              <td [attr.data-label]="'Recursos' | t"><span class="num" [title]="featureNames(plan)">{{ plan.features.length }}/{{ allFeatures.length }}</span></td>
              <td [attr.data-label]="'Clientes' | t"><app-pill icon="building"><span class="num">{{ plan.tenantCount }}</span></app-pill></td>
              <td class="row-actions">
                <button class="secondary small" (click)="openEdit(plan)"><app-icon name="pencil" [size]="15" />{{ 'Editar' | t }}</button>
                <app-menu [label]="'Mais ações' | t">
                  <button type="button" class="menu-item danger" [disabled]="plan.tenantCount > 0" [title]="plan.tenantCount > 0 ? ('Há clientes neste plano' | t) : ''" (click)="remove(plan)"><app-icon name="trash" />{{ 'Excluir' | t }}</button>
                </app-menu>
              </td>
            </tr>
          }
        </table>
      }
    </div>

    @if (modal() !== null) {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="submit()">
          <h2>{{ (modal() === 'new' ? 'Novo plano' : 'Editar plano') | t }}</h2>
          <div class="two">
            <div class="field">
              <label for="p-name">{{ 'Nome' | t }}</label>
              <input id="p-name" name="name" required [ngModel]="name()" (ngModelChange)="name.set($event)" />
            </div>
            <div class="field">
              <label for="p-code">{{ 'Código' | t }}</label>
              <input id="p-code" name="code" required [placeholder]="'ex.: basico' | t" [ngModel]="code()" (ngModelChange)="code.set($event)" />
            </div>
          </div>
          <div class="field">
            <label for="p-desc">{{ 'Descrição (opcional)' | t }}</label>
            <input id="p-desc" name="description" [ngModel]="description()" (ngModelChange)="description.set($event)" />
          </div>
          <div class="two">
            <div class="field">
              <label for="p-price">{{ 'Preço por mês (R$)' | t }}</label>
              <input id="p-price" name="price" inputmode="decimal" required [ngModel]="price()" (ngModelChange)="price.set($event)" />
            </div>
            <div class="field">
              <label for="p-order">{{ 'Ordem na lista' | t }}</label>
              <input id="p-order" name="order" type="number" [ngModel]="sortOrder()" (ngModelChange)="sortOrder.set(+$event)" />
            </div>
          </div>
          <div class="three">
            <div class="field">
              <label for="p-dev">{{ 'TVs' | t }}</label>
              <input id="p-dev" name="maxDevices" type="number" min="0" [placeholder]="'ilimitado' | t" [ngModel]="maxDevices()" (ngModelChange)="maxDevices.set($event)" />
            </div>
            <div class="field">
              <label for="p-sto">{{ 'Espaço (MB)' | t }}</label>
              <input id="p-sto" name="maxStorageMb" type="number" min="0" [placeholder]="'ilimitado' | t" [ngModel]="maxStorageMb()" (ngModelChange)="maxStorageMb.set($event)" />
            </div>
            <div class="field">
              <label for="p-usr">{{ 'Usuários' | t }}</label>
              <input id="p-usr" name="maxUsers" type="number" min="0" [placeholder]="'ilimitado' | t" [ngModel]="maxUsers()" (ngModelChange)="maxUsers.set($event)" />
            </div>
          </div>
          <div class="field">
            <label for="p-upl">{{ 'Tamanho máximo de cada arquivo (MB)' | t }}</label>
            <input id="p-upl" name="maxUploadMb" type="number" min="1" [placeholder]="'ilimitado' | t" [ngModel]="maxUploadMb()" (ngModelChange)="maxUploadMb.set($event)" />
          </div>
          <fieldset class="features">
            <legend>{{ 'O que o plano inclui' | t }}</legend>
            @for (feature of allFeatures; track feature.code) {
              <label class="check feature">
                <input type="checkbox" [name]="'f-' + feature.code" [ngModel]="hasFeature(feature.code)" (ngModelChange)="toggleFeature(feature.code, $event)" />
                <span><b>{{ feature.label | t }}</b><small>{{ feature.help | t }}</small></span>
              </label>
            }
          </fieldset>
          <div class="field">
            <label class="check">
              <input type="checkbox" name="active" [ngModel]="active()" (ngModelChange)="active.set($event)" />
              {{ 'Disponível para novos clientes e para o cliente comparar' | t }}
            </label>
          </div>
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">{{ 'Cancelar' | t }}</button>
            <button type="submit" class="primary" [disabled]="busy() || !canSubmit()">{{ 'Salvar' | t }}</button>
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
    .place-text small { color: var(--muted); }
    .off { color: var(--warn-fg); font-weight: 600; }
    .two { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .three { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
    .features { border: 1px solid var(--line); border-radius: var(--r); padding: 10px 14px 6px; margin: 0 0 14px; }
    .features legend { padding: 0 6px; font-weight: 600; }
    .feature { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 8px; }
    .feature span { display: flex; flex-direction: column; }
    .feature small { color: var(--muted); }
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
  readonly maxUploadMb = signal<number | string | null>(null);
  readonly features = signal<FeatureCode[]>(FEATURES.map((feature) => feature.code));
  readonly allFeatures = FEATURES;
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
    return mb === null ? tr('ilimitado') : formatStorage(mb * 1024 * 1024);
  }

  readonly planName = planText;

  hasFeature(code: FeatureCode): boolean {
    return this.features().includes(code);
  }

  toggleFeature(code: FeatureCode, on: boolean): void {
    this.features.update((list) => (on ? [...list.filter((item) => item !== code), code] : list.filter((item) => item !== code)));
  }

  featureNames(plan: PlatformPlan): string {
    return plan.features.length === 0 ? tr('Nenhum recurso opcional') : plan.features.map(featureLabel).join(', ');
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
    this.maxUploadMb.set(null);
    this.features.set(FEATURES.map((feature) => feature.code));
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
    this.maxUploadMb.set(plan.maxUploadMb);
    this.features.set([...plan.features]);
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
      maxUploadMb: limitValue(this.maxUploadMb()),
      features: this.features(),
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
        this.notice.show(editing ? tr('Plano "{name}" salvo.', { name: saved.name }) : tr('Plano "{name}" criado.', { name: saved.name }));
        this.load();
      },
      error: () => this.busy.set(false),
    });
  }

  remove(plan: PlatformPlan): void {
    if (!confirm(tr('Excluir o plano "{name}"?', { name: plan.name }))) {
      return;
    }
    this.api.deletePlatformPlan(plan.id).subscribe({
      next: () => {
        this.notice.show(tr('Plano "{name}" excluído.', { name: plan.name }));
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
