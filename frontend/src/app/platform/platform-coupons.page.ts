import { NgTemplateOutlet } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { FDatePipe, TPipe, tr } from '../core/i18n';
import { Coupon, CouponBatch, CouponKind, CouponRedemption, CouponRequest, PlatformPlan } from '../core/models';
import { Notice } from '../core/notice';
import {
  COUPON_KINDS,
  COUPON_STATE_TONES,
  couponBenefit,
  couponKindLabel,
  couponStateLabel,
  moneyInput,
  parseMoney,
  planText,
} from '../core/saas';
import { IconComponent } from '../shared/icon';
import { EmptyStateComponent, MenuComponent, PageHeaderComponent, PillComponent, SkeletonComponent } from '../shared/ui';

/** Sem 0/O e 1/I/L: o código é digitado à mão. */
const CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';

/** Um código novo no formato dos de lote (ABCD-EFGH). */
export function randomCouponCode(random: (max: number) => number = (max) => crypto.getRandomValues(new Uint32Array(1))[0] % max): string {
  const pick = () => CODE_ALPHABET.charAt(random(CODE_ALPHABET.length));
  return Array.from({ length: 4 }, pick).join('') + '-' + Array.from({ length: 4 }, pick).join('');
}

/** "2026-12-31" (campo de data) para o instante de início ou de fim desse dia, no fuso do navegador. */
export function dateInputToIso(value: string, endOfDay: boolean): string | null {
  if (!value) {
    return null;
  }
  const date = new Date(`${value}T${endOfDay ? '23:59:59' : '00:00:00'}`);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

/** O instante guardado no servidor de volta para o campo de data. */
export function isoToDateInput(iso: string | null): string {
  if (!iso) {
    return '';
  }
  const date = new Date(iso);
  const two = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())}`;
}

/** Cupons de assinatura: o gestor cria (um a um ou em lote), vê quem usou e desliga. */
@Component({
  selector: 'app-platform-coupons',
  imports: [FormsModule, NgTemplateOutlet, TPipe, FDatePipe, IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent, MenuComponent],
  template: `
    <app-page-header [title]="'Cupons' | t" [subtitle]="'Cupons de assinatura: desconto, dias grátis ou um plano maior por um tempo. O cliente resgata em &quot;Plano e cobrança&quot;.' | t" icon="ticket">
      <button class="secondary" (click)="openBatch()"><app-icon name="copy" />{{ 'Gerar em lote' | t }}</button>
      <button class="primary" (click)="openNew()"><app-icon name="plus" />{{ 'Novo cupom' | t }}</button>
    </app-page-header>

    @if (notice.text(); as text) {
      <div class="notice" role="status"><app-icon name="circle-check" [size]="18" /><span>{{ text }}</span></div>
    }

    <div class="card">
      @if (items().length === 0) {
        @if (loaded()) {
          <app-empty-state icon="ticket" [title]="'Nenhum cupom ainda.' | t" [text]="'Crie um cupom para dar desconto, dias grátis ou liberar um plano maior.' | t">
            <button class="primary" (click)="openNew()"><app-icon name="plus" />{{ 'Novo cupom' | t }}</button>
          </app-empty-state>
        } @else {
          <app-skeleton [rows]="3" />
        }
      } @else {
        <table class="stack">
          <tr><th>{{ 'Código' | t }}</th><th>{{ 'O que dá' | t }}</th><th>{{ 'Validade' | t }}</th><th>{{ 'Usos' | t }}</th><th>{{ 'Situação' | t }}</th><th></th></tr>
          @for (coupon of items(); track coupon.id) {
            <tr>
              <td class="first">
                <span class="code">
                  <b class="mono">{{ coupon.code }}</b>
                  @if (coupon.batch) {
                    <small>{{ coupon.batch }}</small>
                  } @else if (coupon.description) {
                    <small>{{ coupon.description }}</small>
                  }
                </span>
              </td>
              <td [attr.data-label]="'O que dá' | t">{{ benefit(coupon) }}</td>
              <td [attr.data-label]="'Validade' | t">{{ validity(coupon) }}</td>
              <td [attr.data-label]="'Usos' | t"><span class="num">{{ coupon.redeemedCount }}{{ coupon.maxRedemptions === null ? '' : ' / ' + coupon.maxRedemptions }}</span></td>
              <td [attr.data-label]="'Situação' | t"><app-pill [tone]="stateTone(coupon)">{{ stateLabel(coupon) }}</app-pill></td>
              <td class="row-actions">
                <button class="secondary small" (click)="copy(coupon)" [attr.aria-label]="'Copiar o código' | t"><app-icon name="copy" [size]="15" /></button>
                <button class="secondary small" (click)="openEdit(coupon)"><app-icon name="pencil" [size]="15" />{{ 'Editar' | t }}</button>
                <app-menu [label]="'Mais ações' | t">
                  <button type="button" class="menu-item" (click)="showUses(coupon)"><app-icon name="users" />{{ 'Ver quem usou' | t }}</button>
                  <button type="button" class="menu-item danger" [disabled]="coupon.redeemedCount > 0" [title]="coupon.redeemedCount > 0 ? ('Já foi usado: desative em vez de excluir' | t) : ''" (click)="remove(coupon)"><app-icon name="trash" />{{ 'Excluir' | t }}</button>
                </app-menu>
              </td>
            </tr>
          }
        </table>
      }
    </div>

    @if (modal() === 'edit') {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="submit()">
          <h2>{{ (editing ? 'Editar cupom' : 'Novo cupom') | t }}</h2>
          <div class="field">
            <label for="cp-code">{{ 'Código' | t }}</label>
            <span class="with-button">
              <input id="cp-code" name="code" required class="mono" [disabled]="editing !== null" [placeholder]="'ex.: BEMVINDO20' | t" [ngModel]="code()" (ngModelChange)="code.set($event)" />
              @if (!editing) {
                <button type="button" class="secondary" (click)="code.set(newCode())"><app-icon name="refresh-cw" [size]="16" />{{ 'Gerar' | t }}</button>
              }
            </span>
            <small>{{ 'Letras, números, hífen e sublinhado (de 3 a 40). Maiúsculas e minúsculas valem o mesmo.' | t }}</small>
          </div>
          <div class="field">
            <label for="cp-desc">{{ 'Descrição (opcional, só você vê)' | t }}</label>
            <input id="cp-desc" name="description" [ngModel]="description()" (ngModelChange)="description.set($event)" />
          </div>
          @if (locked()) {
            <p class="muted">{{ 'Este cupom já foi usado: o que ele dá não muda mais. Dá para ajustar a validade, o limite de usos e a situação.' | t }}</p>
          }
          <ng-container [ngTemplateOutlet]="benefitFields" />
          <div class="two">
            <div class="field">
              <label for="cp-from">{{ 'Vale a partir de (opcional)' | t }}</label>
              <input id="cp-from" name="validFrom" type="date" [ngModel]="validFrom()" (ngModelChange)="validFrom.set($event)" />
            </div>
            <div class="field">
              <label for="cp-until">{{ 'Vale até (opcional)' | t }}</label>
              <input id="cp-until" name="validUntil" type="date" [ngModel]="validUntil()" (ngModelChange)="validUntil.set($event)" />
            </div>
          </div>
          <div class="field">
            <label for="cp-max">{{ 'Quantos clientes podem usar' | t }}</label>
            <input id="cp-max" name="maxRedemptions" type="number" min="1" [placeholder]="'ilimitado' | t" [ngModel]="maxRedemptions()" (ngModelChange)="maxRedemptions.set($event)" />
          </div>
          <div class="field">
            <label class="check">
              <input type="checkbox" name="active" [ngModel]="active()" (ngModelChange)="active.set($event)" />
              {{ 'Ligado (pode ser resgatado)' | t }}
            </label>
          </div>
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">{{ 'Cancelar' | t }}</button>
            <button type="submit" class="primary" [disabled]="busy() || !canSubmit()">{{ 'Salvar' | t }}</button>
          </div>
        </form>
      </div>
    }

    @if (modal() === 'batch') {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="submitBatch()">
          <h2>{{ 'Gerar cupons em lote' | t }}</h2>
          @if (batchResult(); as result) {
            <p>{{ 'Foram gerados {n} cupons de uso único no lote "{batch}".' | t: { n: result.coupons.length, batch: result.batch } }}</p>
            <textarea class="mono" readonly rows="8" [value]="batchCodes()"></textarea>
            <div class="actions">
              <button type="button" class="secondary" (click)="copyBatch()"><app-icon name="copy" [size]="16" />{{ 'Copiar todos' | t }}</button>
              <button type="button" class="primary" (click)="closeModal()">{{ 'Fechar' | t }}</button>
            </div>
          } @else {
            <p class="muted">{{ 'Cada código é novo, aleatório e vale para um cliente só: dá para entregar um a cada pessoa.' | t }}</p>
            <div class="two">
              <div class="field">
                <label for="cb-qty">{{ 'Quantos códigos (1 a 500)' | t }}</label>
                <input id="cb-qty" name="quantity" type="number" min="1" max="500" required [ngModel]="quantity()" (ngModelChange)="quantity.set($event)" />
              </div>
              <div class="field">
                <label for="cb-prefix">{{ 'Começo do código (opcional)' | t }}</label>
                <input id="cb-prefix" name="prefix" class="mono" maxlength="12" [placeholder]="'ex.: FEIRA' | t" [ngModel]="prefix()" (ngModelChange)="prefix.set($event)" />
              </div>
            </div>
            <div class="field">
              <label for="cb-name">{{ 'Nome do lote (opcional)' | t }}</label>
              <input id="cb-name" name="batch" maxlength="60" [ngModel]="batchName()" (ngModelChange)="batchName.set($event)" />
            </div>
            <ng-container [ngTemplateOutlet]="benefitFields" />
            <div class="field">
              <label for="cb-until">{{ 'Vale até (opcional)' | t }}</label>
              <input id="cb-until" name="validUntil" type="date" [ngModel]="validUntil()" (ngModelChange)="validUntil.set($event)" />
            </div>
            <div class="actions">
              <button type="button" class="secondary" (click)="closeModal()">{{ 'Cancelar' | t }}</button>
              <button type="submit" class="primary" [disabled]="busy() || !canSubmitBatch()">{{ 'Gerar' | t }}</button>
            </div>
          }
        </form>
      </div>
    }

    @if (modal() === 'uses') {
      <div class="modal" (click)="closeModal()">
        <div class="modal-box" (click)="$event.stopPropagation()">
          <h2>{{ 'Quem usou' | t }} <span class="mono">{{ viewing?.code }}</span></h2>
          @if (uses().length === 0) {
            <p class="muted">{{ 'Ninguém usou este cupom ainda.' | t }}</p>
          } @else {
            <ul class="uses">
              @for (use of uses(); track $index) {
                <li><b>{{ use.tenantName }}</b><small>{{ use.redeemedBy }} · {{ use.redeemedAt | fdate }}</small></li>
              }
            </ul>
          }
          <div class="actions"><button type="button" class="primary" (click)="closeModal()">{{ 'Fechar' | t }}</button></div>
        </div>
      </div>
    }

    <!-- o que o cupom dá: igual para o cupom avulso e para o lote -->
    <ng-template #benefitFields>
      <div class="field">
        <label for="cp-kind">{{ 'O que o cupom dá' | t }}</label>
        <select id="cp-kind" name="kind" [disabled]="locked()" [ngModel]="kind()" (ngModelChange)="kind.set($event)">
          @for (option of kinds; track option.code) {
            <option [value]="option.code">{{ option.label | t }}</option>
          }
        </select>
        <small>{{ kindHelp() | t }}</small>
      </div>
      <div class="two">
        <div class="field">
          <label for="cp-value">{{ valueLabel() }}</label>
          <input id="cp-value" name="value" required [attr.inputmode]="kind() === 'AMOUNT_OFF' ? 'decimal' : 'numeric'" [disabled]="locked()" [ngModel]="valueText()" (ngModelChange)="valueText.set($event)" />
        </div>
        @if (isDiscount()) {
          <div class="field">
            <label for="cp-months">{{ 'Vale para quantas faturas' | t }}</label>
            <input id="cp-months" name="durationMonths" type="number" min="1" max="60" [disabled]="locked()" [ngModel]="durationMonths()" (ngModelChange)="durationMonths.set($event)" />
          </div>
        }
        @if (kind() === 'PLAN_UPGRADE') {
          <div class="field">
            <label for="cp-plan">{{ 'Plano liberado' | t }}</label>
            <select id="cp-plan" name="upgradePlan" [disabled]="locked()" [ngModel]="upgradePlanId()" (ngModelChange)="upgradePlanId.set($event)">
              <option [ngValue]="null">{{ 'Escolha um plano' | t }}</option>
              @for (plan of plans(); track plan.id) {
                <option [ngValue]="plan.id">{{ planName(plan.name) }}</option>
              }
            </select>
          </div>
        }
      </div>
    </ng-template>
  `,
  styles: `
    .row-actions { white-space: nowrap; text-align: right; }
    .row-actions > * { margin-left: 6px; vertical-align: middle; }
    .code { display: flex; flex-direction: column; min-width: 0; }
    .code small { color: var(--muted); }
    .two { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .with-button { display: flex; gap: 8px; }
    .with-button input { flex: 1; min-width: 0; }
    textarea { width: 100%; resize: vertical; }
    .uses { list-style: none; margin: 8px 0 0; padding: 0; display: grid; gap: 8px; }
    .uses li { display: flex; flex-direction: column; padding: 10px 12px; border: 1px solid var(--line); border-radius: var(--r); background: var(--panel-2); }
    .uses small { color: var(--muted); }
  `,
})
export class PlatformCouponsPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly kinds = COUPON_KINDS;
  readonly planName = planText;
  readonly items = signal<Coupon[]>([]);
  readonly plans = signal<PlatformPlan[]>([]);
  readonly loaded = signal(false);
  readonly notice = new Notice();

  readonly modal = signal<'edit' | 'batch' | 'uses' | null>(null);
  readonly busy = signal(false);
  editing: Coupon | null = null;
  viewing: Coupon | null = null;

  readonly code = signal('');
  readonly description = signal('');
  readonly kind = signal<CouponKind>('PERCENT_OFF');
  readonly valueText = signal('');
  readonly durationMonths = signal<number | string | null>(1);
  readonly upgradePlanId = signal<number | null>(null);
  readonly validFrom = signal('');
  readonly validUntil = signal('');
  readonly maxRedemptions = signal<number | string | null>(null);
  readonly active = signal(true);

  readonly quantity = signal<number | string>(10);
  readonly prefix = signal('');
  readonly batchName = signal('');
  readonly batchResult = signal<CouponBatch | null>(null);
  readonly uses = signal<CouponRedemption[]>([]);

  readonly isDiscount = computed(() => this.kind() === 'PERCENT_OFF' || this.kind() === 'AMOUNT_OFF');
  readonly batchCodes = computed(() => (this.batchResult()?.coupons ?? []).map((coupon) => coupon.code).join('\n'));

  /** Depois do primeiro uso, o que o cupom dá não muda (quem já resgatou contou com isso). */
  locked(): boolean {
    return this.editing !== null && this.editing.redeemedCount > 0;
  }

  readonly kindHelp = computed(() => COUPON_KINDS.find((option) => option.code === this.kind())?.help ?? '');

  readonly valueLabel = computed(() => {
    switch (this.kind()) {
      case 'PERCENT_OFF':
        return tr('Porcentagem de desconto (1 a 100)');
      case 'AMOUNT_OFF':
        return tr('Valor do desconto (R$)');
      case 'FREE_DAYS':
        return tr('Quantos dias grátis');
      default:
        return tr('Por quantos dias o plano fica liberado');
    }
  });

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.notice.dispose());
    this.load();
    this.api.platformPlans().subscribe({ next: (list) => this.plans.set(list.filter((plan) => plan.active)), error: () => undefined });
  }

  benefit(coupon: Coupon): string {
    return couponBenefit({ ...coupon, planName: coupon.upgradePlanName });
  }

  stateLabel(coupon: Coupon): string {
    return couponStateLabel(coupon.state);
  }

  stateTone(coupon: Coupon): 'ok' | 'warn' | 'neutral' | 'err' {
    return COUPON_STATE_TONES[coupon.state];
  }

  kindLabel(coupon: Coupon): string {
    return couponKindLabel(coupon.kind);
  }

  validity(coupon: Coupon): string {
    if (!coupon.validFrom && !coupon.validUntil) {
      return tr('Sem prazo');
    }
    const from = coupon.validFrom ? isoToDateInput(coupon.validFrom).split('-').reverse().join('/') : null;
    const until = coupon.validUntil ? isoToDateInput(coupon.validUntil).split('-').reverse().join('/') : null;
    if (from && until) {
      return tr('{from} a {until}', { from, until });
    }
    return from ? tr('a partir de {date}', { date: from }) : tr('até {date}', { date: until ?? '' });
  }

  newCode(): string {
    return randomCouponCode();
  }

  // ---------- cupom avulso ----------

  readonly canSubmit = computed(() => this.code().trim().length >= 3 && this.benefitValid());

  private benefitValid(): boolean {
    if (this.value() === null) {
      return false;
    }
    if (this.kind() === 'PERCENT_OFF' && (this.value() ?? 0) > 100) {
      return false;
    }
    return this.kind() !== 'PLAN_UPGRADE' || this.upgradePlanId() !== null;
  }

  /** O valor digitado em número inteiro (porcentagem, centavos ou dias) ou nulo se não vale. */
  private value(): number | null {
    const text = this.valueText().trim();
    if (!text) {
      return null;
    }
    const number = this.kind() === 'AMOUNT_OFF' ? parseMoney(text) : Number(text);
    return number !== null && Number.isInteger(number) && number >= 1 ? number : null;
  }

  openNew(): void {
    this.editing = null;
    this.resetForm();
    this.code.set(randomCouponCode());
    this.modal.set('edit');
  }

  openEdit(coupon: Coupon): void {
    this.editing = coupon;
    this.code.set(coupon.code);
    this.description.set(coupon.description ?? '');
    this.kind.set(coupon.kind);
    this.valueText.set(coupon.kind === 'AMOUNT_OFF' ? moneyInput(coupon.value) : String(coupon.value));
    this.durationMonths.set(coupon.durationMonths ?? 1);
    this.upgradePlanId.set(coupon.upgradePlanId);
    this.validFrom.set(isoToDateInput(coupon.validFrom));
    this.validUntil.set(isoToDateInput(coupon.validUntil));
    this.maxRedemptions.set(coupon.maxRedemptions);
    this.active.set(coupon.active);
    this.modal.set('edit');
  }

  closeModal(): void {
    this.modal.set(null);
    this.batchResult.set(null);
  }

  submit(): void {
    const value = this.value();
    if (!this.canSubmit() || value === null) {
      return;
    }
    const body: CouponRequest = {
      code: this.code().trim(),
      description: this.description().trim() || null,
      kind: this.kind(),
      value,
      durationMonths: this.isDiscount() ? this.number(this.durationMonths()) ?? 1 : null,
      upgradePlanId: this.kind() === 'PLAN_UPGRADE' ? this.upgradePlanId() : null,
      validFrom: dateInputToIso(this.validFrom(), false),
      validUntil: dateInputToIso(this.validUntil(), true),
      maxRedemptions: this.number(this.maxRedemptions()),
      active: this.active(),
    };
    this.busy.set(true);
    const editing = this.editing;
    const request = editing ? this.api.updateCoupon(editing.id, body) : this.api.createCoupon(body);
    request.subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.closeModal();
        this.notice.show(editing ? tr('Cupom {code} salvo.', { code: saved.code }) : tr('Cupom {code} criado.', { code: saved.code }));
        this.load();
      },
      error: () => this.busy.set(false),
    });
  }

  remove(coupon: Coupon): void {
    if (!confirm(tr('Excluir o cupom {code}?', { code: coupon.code }))) {
      return;
    }
    this.api.deleteCoupon(coupon.id).subscribe({
      next: () => {
        this.notice.show(tr('Cupom {code} excluído.', { code: coupon.code }));
        this.load();
      },
      error: () => undefined,
    });
  }

  copy(coupon: Coupon): void {
    this.copyText(coupon.code, tr('Código {code} copiado.', { code: coupon.code }));
  }

  showUses(coupon: Coupon): void {
    this.viewing = coupon;
    this.uses.set([]);
    this.modal.set('uses');
    this.api.couponRedemptions(coupon.id).subscribe({ next: (list) => this.uses.set(list), error: () => undefined });
  }

  // ---------- lote ----------

  readonly canSubmitBatch = computed(() => {
    const quantity = this.number(this.quantity());
    return quantity !== null && quantity >= 1 && quantity <= 500 && this.benefitValid() && /^[A-Za-z0-9]*$/.test(this.prefix());
  });

  openBatch(): void {
    this.editing = null;
    this.resetForm();
    this.batchResult.set(null);
    this.modal.set('batch');
  }

  submitBatch(): void {
    const value = this.value();
    const quantity = this.number(this.quantity());
    if (!this.canSubmitBatch() || value === null || quantity === null) {
      return;
    }
    this.busy.set(true);
    this.api
      .createCouponBatch({
        prefix: this.prefix().trim(),
        quantity,
        batch: this.batchName().trim(),
        description: this.description().trim() || null,
        kind: this.kind(),
        value,
        durationMonths: this.isDiscount() ? this.number(this.durationMonths()) ?? 1 : null,
        upgradePlanId: this.kind() === 'PLAN_UPGRADE' ? this.upgradePlanId() : null,
        validFrom: null,
        validUntil: dateInputToIso(this.validUntil(), true),
      })
      .subscribe({
        next: (result) => {
          this.busy.set(false);
          this.batchResult.set(result);
          this.load();
        },
        error: () => this.busy.set(false),
      });
  }

  copyBatch(): void {
    this.copyText(this.batchCodes(), tr('Códigos copiados.'));
  }

  // ---------- apoio ----------

  private resetForm(): void {
    this.code.set('');
    this.description.set('');
    this.kind.set('PERCENT_OFF');
    this.valueText.set('');
    this.durationMonths.set(1);
    this.upgradePlanId.set(null);
    this.validFrom.set('');
    this.validUntil.set('');
    this.maxRedemptions.set(null);
    this.active.set(true);
    this.quantity.set(10);
    this.prefix.set('');
    this.batchName.set('');
  }

  /** Campo vazio = nulo; senão, o número inteiro. */
  private number(value: number | string | null): number | null {
    if (value === null || value === '') {
      return null;
    }
    const number = Number(value);
    return Number.isFinite(number) ? Math.floor(number) : null;
  }

  private copyText(text: string, message: string): void {
    navigator.clipboard?.writeText(text).then(
      () => this.notice.show(message),
      () => undefined,
    );
  }

  private load(): void {
    this.api.platformCoupons().subscribe({
      next: (list) => {
        this.items.set(list);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }
}
