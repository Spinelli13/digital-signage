import { Component, DestroyRef, ElementRef, OnInit, computed, effect, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { dateInputToIso, isoToDateInput } from '../core/date-input';
import { FDatePipe, TPipe, tr } from '../core/i18n';
import { Device, Promotion, PromotionCoupon, PromotionLookup, PromotionRequest } from '../core/models';
import { Notice } from '../core/notice';
import { PROMOTION_STATE_TONES, promotionCouponLabel, promotionStateLabel } from '../core/saas';
import { QR_IMAGE_SIZE, drawQr } from '../media/qr';
import { IconComponent } from '../shared/icon';
import { EmptyStateComponent, MenuComponent, PageHeaderComponent, PillComponent, SkeletonComponent } from '../shared/ui';
import { cleanOrigin, linkWithOrigin, tvOrigin } from './qr-origin';

/** Cupons para os clientes da loja: montar as promoções, o QR Code da TV e dar baixa no caixa. */
@Component({
  selector: 'app-promotions',
  imports: [FormsModule, TPipe, FDatePipe, IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent, MenuComponent],
  template: `
    <app-page-header [title]="'Cupons' | t" [subtitle]="'Promoções para os clientes da loja: o cliente escaneia o QR Code na TV, recebe um código único e o caixa dá baixa aqui.' | t" icon="ticket">
      @if (auth.can('content')) {
        <button class="primary" (click)="openNew()"><app-icon name="plus" />{{ 'Nova promoção' | t }}</button>
      }
    </app-page-header>

    @if (notice.text(); as text) {
      <div class="notice" role="status"><app-icon name="circle-check" [size]="18" /><span>{{ text }}</span></div>
    }

    @if (auth.can('content')) {
      <div class="card cashier">
        <h3 class="sec"><app-icon name="badge-check" [size]="18" />{{ 'Dar baixa no caixa' | t }}</h3>
        <form class="lookup" (ngSubmit)="check()">
          <input name="code" class="mono" autocomplete="off" autocapitalize="characters" maxlength="12" [attr.aria-label]="'Código do cupom' | t" [placeholder]="'Código do cupom (ex.: ABCD-2345)' | t"
            [ngModel]="code()" (ngModelChange)="code.set($event)" />
          <button type="submit" class="secondary" [disabled]="busy() || !code().trim()"><app-icon name="search" [size]="16" />{{ 'Conferir' | t }}</button>
        </form>
        @if (found(); as coupon) {
          <div class="found" [class.ok]="coupon.status === 'ISSUED'">
            <div class="found-top">
              <b class="mono">{{ coupon.code }}</b>
              <app-pill [tone]="couponTone(coupon.status)">{{ couponStatusLabel(coupon.status) }}</app-pill>
            </div>
            <span>{{ coupon.promotionName }}</span>
            @if (coupon.promotionDescription) {
              <small class="muted">{{ coupon.promotionDescription }}</small>
            }
            @if (coupon.claimantName) {
              <small class="muted">{{ 'Cliente: {name}' | t: { name: coupon.claimantName } }}</small>
            }
            @if (coupon.expiresAt) {
              <small class="muted">{{ 'Vale até {date}' | t: { date: (coupon.expiresAt | fdate: 'datetime') } }}</small>
            }
            @if (coupon.redeemedAt) {
              <small class="muted">{{ 'Usado em {date}' | t: { date: (coupon.redeemedAt | fdate: 'datetime') } }}</small>
            }
            @if (coupon.status === 'ISSUED') {
              <div class="actions"><button type="button" class="primary" [disabled]="busy()" (click)="redeem()"><app-icon name="check" [size]="16" />{{ 'Dar baixa neste cupom' | t }}</button></div>
            }
          </div>
        }
      </div>
    }

    <div class="card list">
      @if (items().length === 0) {
        @if (loaded()) {
          <app-empty-state icon="ticket" [title]="'Nenhuma promoção ainda.' | t" [text]="'Crie uma promoção, imprima o QR Code ou mostre-o na TV e deixe os clientes pegarem o cupom.' | t">
            @if (auth.can('content')) {
              <button class="primary" (click)="openNew()"><app-icon name="plus" />{{ 'Nova promoção' | t }}</button>
            }
          </app-empty-state>
        } @else {
          <app-skeleton [rows]="3" />
        }
      } @else {
        <table class="stack">
          <tr><th>{{ 'Promoção' | t }}</th><th>{{ 'Cupons pegos' | t }}</th><th>{{ 'Usados' | t }}</th><th>{{ 'Validade' | t }}</th><th>{{ 'Situação' | t }}</th><th></th></tr>
          @for (promotion of items(); track promotion.id) {
            <tr>
              <td class="first"><b>{{ promotion.name }}</b>@if (promotion.description) {<small class="muted block">{{ promotion.description }}</small>}</td>
              <td [attr.data-label]="'Cupons pegos' | t"><span class="num">{{ promotion.claimedCount }}{{ promotion.maxClaims === null ? '' : ' / ' + promotion.maxClaims }}</span></td>
              <td [attr.data-label]="'Usados' | t"><span class="num">{{ promotion.redeemedCount }}</span></td>
              <td [attr.data-label]="'Validade' | t">{{ validity(promotion) }}</td>
              <td [attr.data-label]="'Situação' | t"><app-pill [tone]="stateTone(promotion)">{{ stateLabel(promotion) }}</app-pill></td>
              <td class="row-actions">
                @if (auth.can('content')) {
                  <button class="secondary small" (click)="openEdit(promotion)"><app-icon name="pencil" [size]="15" />{{ 'Editar' | t }}</button>
                }
                <app-menu [label]="'Mais ações' | t">
                  <button type="button" class="menu-item" (click)="showCoupons(promotion)"><app-icon name="list" />{{ 'Ver os cupons' | t }}</button>
                  @if (auth.can('content')) {
                    <button type="button" class="menu-item danger" [disabled]="promotion.claimedCount > 0" [title]="promotion.claimedCount > 0 ? ('Já teve cupons pegos: desligue em vez de excluir' | t) : ''" (click)="remove(promotion)"><app-icon name="trash" />{{ 'Excluir' | t }}</button>
                  }
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
          <h2>{{ (editing ? 'Editar promoção' : 'Nova promoção') | t }}</h2>
          <div class="field">
            <label for="pr-name">{{ 'Nome da promoção' | t }}</label>
            <input id="pr-name" name="name" required maxlength="120" [placeholder]="'ex.: 10% na primeira compra' | t" [ngModel]="name()" (ngModelChange)="name.set($event)" />
          </div>
          <div class="field">
            <label for="pr-desc">{{ 'Regras que o cliente lê (opcional)' | t }}</label>
            <textarea id="pr-desc" name="description" rows="3" maxlength="500" [placeholder]="'ex.: válido para compras acima de R$ 50, uma vez por cliente' | t" [ngModel]="description()" (ngModelChange)="description.set($event)"></textarea>
          </div>
          <div class="two">
            <div class="field">
              <label for="pr-from">{{ 'Começa em (opcional)' | t }}</label>
              <input id="pr-from" name="validFrom" type="date" [ngModel]="validFrom()" (ngModelChange)="validFrom.set($event)" />
            </div>
            <div class="field">
              <label for="pr-until">{{ 'Termina em (opcional)' | t }}</label>
              <input id="pr-until" name="validUntil" type="date" [ngModel]="validUntil()" (ngModelChange)="validUntil.set($event)" />
            </div>
          </div>
          <div class="two">
            <div class="field">
              <label for="pr-days">{{ 'O cupom vale quantos dias depois de pego' | t }}</label>
              <input id="pr-days" name="claimDays" type="number" min="1" [placeholder]="'até o fim da promoção' | t" [ngModel]="claimDays()" (ngModelChange)="claimDays.set($event)" />
            </div>
            <div class="field">
              <label for="pr-max">{{ 'Quantos cupons no total' | t }}</label>
              <input id="pr-max" name="maxClaims" type="number" min="1" [placeholder]="'ilimitado' | t" [ngModel]="maxClaims()" (ngModelChange)="maxClaims.set($event)" />
            </div>
          </div>
          <div class="field">
            <label class="check">
              <input type="checkbox" name="active" [ngModel]="active()" (ngModelChange)="active.set($event)" />
              {{ 'No ar (os clientes podem pegar o cupom)' | t }}
            </label>
          </div>
          @if (editing && editing.publicToken) {
            <div class="qr-box">
              <canvas #qrCanvas [width]="imageSize" [height]="imageSize" [attr.aria-label]="'QR Code da promoção' | t"></canvas>
              <div class="qr-side">
                <p class="muted">{{ 'Mostre este QR Code na TV (envie a imagem para a Biblioteca) ou imprima. O endereço abaixo abre a mesma página.' | t }}</p>
                <div class="field origin">
                  <label for="qr-origin">{{ 'De onde é este QR Code?' | t }}</label>
                  <select id="qr-origin" name="qrOrigin" [ngModel]="originChoice()" (ngModelChange)="originChoice.set($event)">
                    <option value="">{{ 'QR Code geral (sem origem)' | t }}</option>
                    @for (device of devices(); track device.id) {
                      <option [value]="tvOrigin(device.id)">{{ 'TV: {name}' | t: { name: device.name } }}</option>
                    }
                    <option value="custom">{{ 'Outro lugar (balcão, panfleto…)' | t }}</option>
                  </select>
                  @if (originChoice() === 'custom') {
                    <input name="qrOriginName" maxlength="40" [attr.aria-label]="'Nome do lugar' | t" [placeholder]="'Ex.: balcão' | t" [ngModel]="customOrigin()" (ngModelChange)="customOrigin.set($event)" />
                  }
                  <small class="muted">{{ 'Faça um QR Code para cada TV: assim os Resultados mostram quantos escaneamentos e cupons cada TV rendeu.' | t }}</small>
                </div>
                <input readonly [value]="publicLink()" [attr.aria-label]="'Endereço da promoção' | t" />
                <div class="actions">
                  <button type="button" class="secondary small" (click)="copyLink()"><app-icon name="copy" [size]="15" />{{ 'Copiar o endereço' | t }}</button>
                  <button type="button" class="secondary small" (click)="downloadQr()"><app-icon name="download" [size]="15" />{{ 'Baixar o QR Code' | t }}</button>
                </div>
              </div>
            </div>
          }
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">{{ 'Cancelar' | t }}</button>
            <button type="submit" class="primary" [disabled]="busy() || !canSubmit()">{{ 'Salvar' | t }}</button>
          </div>
        </form>
      </div>
    }

    @if (modal() === 'coupons') {
      <div class="modal" (click)="closeModal()">
        <div class="modal-box" (click)="$event.stopPropagation()">
          <h2>{{ 'Cupons de' | t }} {{ viewing?.name }}</h2>
          @if (coupons().length === 0) {
            <p class="muted">{{ 'Ninguém pegou um cupom desta promoção ainda.' | t }}</p>
          } @else {
            <ul class="coupons">
              @for (coupon of coupons(); track coupon.id) {
                <li>
                  <b class="mono">{{ coupon.code }}</b>
                  <app-pill [tone]="couponTone(coupon.status)">{{ couponStatusLabel(coupon.status) }}</app-pill>
                  <small class="muted">{{ coupon.claimantName ? coupon.claimantName + ' · ' : '' }}{{ coupon.claimedAt | fdate: 'datetime' }}</small>
                </li>
              }
            </ul>
          }
          <div class="actions"><button type="button" class="primary" (click)="closeModal()">{{ 'Fechar' | t }}</button></div>
        </div>
      </div>
    }
  `,
  styles: `
    .cashier { margin-bottom: 18px; }
    .list { margin-top: 0; }
    .sec { display: flex; align-items: center; gap: 8px; }
    .sec app-icon { color: var(--primary); }
    :host-context(:root[data-theme='dark']) .sec app-icon { color: var(--link); }
    .lookup { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 12px; }
    .lookup input { flex: 1 1 220px; min-width: 0; text-transform: uppercase; }
    .found { display: flex; flex-direction: column; gap: 6px; margin-top: 14px; padding: 14px; border: 1px solid var(--line); border-radius: var(--r-lg); background: var(--panel-2); }
    .found.ok { border-color: var(--primary); background: var(--primary-soft); }
    .found-top { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
    .found-top b { font-size: 20px; letter-spacing: 0.05em; }
    .block { display: block; }
    .row-actions { white-space: nowrap; text-align: right; }
    .row-actions > * { margin-left: 6px; vertical-align: middle; }
    .two { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    textarea { width: 100%; resize: vertical; }
    .qr-box { display: flex; gap: 16px; align-items: flex-start; margin: 4px 0 14px; }
    .qr-box canvas { width: 180px; height: 180px; flex: none; border: 1px solid var(--line); border-radius: 10px; }
    .qr-side { display: flex; flex-direction: column; gap: 8px; min-width: 0; flex: 1; }
    .coupons { list-style: none; margin: 8px 0 0; padding: 0; display: grid; gap: 8px; max-height: 50vh; overflow-y: auto; }
    .coupons li { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; padding: 10px 12px; border: 1px solid var(--line); border-radius: var(--r); background: var(--panel-2); }
    @media (max-width: 560px) { .qr-box { flex-direction: column; } .two { grid-template-columns: 1fr; } }
  `,
})
export class PromotionsPage implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly items = signal<Promotion[]>([]);
  readonly loaded = signal(false);
  readonly notice = new Notice();
  readonly busy = signal(false);

  // o caixa
  readonly code = signal('');
  readonly found = signal<PromotionLookup | null>(null);

  // a promoção aberta na janela
  readonly modal = signal<'edit' | 'coupons' | null>(null);
  editing: Promotion | null = null;
  viewing: Promotion | null = null;
  readonly coupons = signal<PromotionCoupon[]>([]);
  readonly name = signal('');
  readonly description = signal('');
  readonly validFrom = signal('');
  readonly validUntil = signal('');
  readonly claimDays = signal<number | string | null>(7);
  readonly maxClaims = signal<number | string | null>(null);
  readonly active = signal(true);

  readonly imageSize = QR_IMAGE_SIZE;
  readonly tvOrigin = tvOrigin;
  /** As TVs, para fazer um QR Code por TV (carregadas na primeira vez que uma promoção é aberta). */
  readonly devices = signal<Device[]>([]);
  private devicesLoaded = false;
  /** "" = QR geral; "tv-12" = a TV; "custom" = um lugar que a pessoa nomeia (balcão, panfleto). */
  readonly originChoice = signal('');
  readonly customOrigin = signal('');
  /** A origem que vai no endereço do QR Code. */
  readonly origin = computed(() => (this.originChoice() === 'custom' ? cleanOrigin(this.customOrigin()) : this.originChoice()));
  private readonly qrCanvas = viewChild<ElementRef<HTMLCanvasElement>>('qrCanvas');
  private started = false;

  readonly canSubmit = computed(() => this.name().trim().length > 0);

  constructor() {
    // O QR Code da promoção se desenha sozinho quando a janela abre.
    effect(() => {
      const canvas = this.qrCanvas()?.nativeElement;
      const link = this.publicLink();
      const ctx = canvas?.getContext('2d');
      if (canvas && ctx && link) {
        try {
          drawQr(ctx, { text: link, title: this.editing?.name ?? '', caption: tr('Escaneie para pegar o seu cupom'), theme: 'claro' });
        } catch {
          ctx.clearRect(0, 0, canvas.width, canvas.height);
        }
      }
    });
  }

  ngOnInit(): void {
    // Uma vez só: a detecção automática de mudanças pode chamar de novo depois de uma chamada manual (testes).
    if (this.started) {
      return;
    }
    this.started = true;
    this.destroyRef.onDestroy(() => this.notice.dispose());
    this.load();
  }

  stateLabel(promotion: Promotion): string {
    return promotionStateLabel(promotion.state);
  }

  stateTone(promotion: Promotion): 'ok' | 'warn' | 'neutral' {
    return PROMOTION_STATE_TONES[promotion.state];
  }

  couponStatusLabel(status: PromotionCoupon['status']): string {
    return promotionCouponLabel(status);
  }

  couponTone(status: PromotionCoupon['status']): 'ok' | 'neutral' | 'warn' {
    return status === 'ISSUED' ? 'ok' : status === 'REDEEMED' ? 'neutral' : 'warn';
  }

  validity(promotion: Promotion): string {
    if (!promotion.validFrom && !promotion.validUntil) {
      return tr('Sem prazo');
    }
    const day = (iso: string) => isoToDateInput(iso).split('-').reverse().join('/');
    if (promotion.validFrom && promotion.validUntil) {
      return tr('{from} a {until}', { from: day(promotion.validFrom), until: day(promotion.validUntil) });
    }
    return promotion.validFrom ? tr('a partir de {date}', { date: day(promotion.validFrom) }) : tr('até {date}', { date: day(promotion.validUntil ?? '') });
  }

  // ---------- o caixa ----------

  check(): void {
    const code = this.code().trim();
    if (!code || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.found.set(null);
    this.api.lookupPromotionCoupon(code).subscribe({
      next: (coupon) => {
        this.busy.set(false);
        this.found.set(coupon);
      },
      // "Cupom não encontrado" e "código inválido" aparecem no aviso de erro
      error: () => this.busy.set(false),
    });
  }

  redeem(): void {
    const coupon = this.found();
    if (!coupon || coupon.status !== 'ISSUED' || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.api.redeemPromotionCoupon(coupon.code).subscribe({
      next: (done) => {
        this.busy.set(false);
        this.found.set(done);
        this.code.set('');
        this.notice.show(tr('Cupom {code} usado. Pode aplicar o benefício.', { code: done.code }));
        this.load();
      },
      error: () => {
        this.busy.set(false);
        // alguém pode ter usado antes: mostra a situação de agora
        this.check();
      },
    });
  }

  // ---------- a promoção ----------

  openNew(): void {
    this.editing = null;
    this.name.set('');
    this.description.set('');
    this.validFrom.set('');
    this.validUntil.set('');
    this.claimDays.set(7);
    this.maxClaims.set(null);
    this.active.set(true);
    this.modal.set('edit');
  }

  /** Carrega as TVs uma vez, para o seletor de "de onde é este QR Code". Falhar não atrapalha: só fica o QR geral. */
  private loadDevices(): void {
    if (this.devicesLoaded) {
      return;
    }
    this.devicesLoaded = true;
    this.api.devices().subscribe({ next: (devices) => this.devices.set(devices.filter((device) => device.status === 'ACTIVE')), error: () => undefined });
  }

  openEdit(promotion: Promotion): void {
    this.originChoice.set('');
    this.customOrigin.set('');
    this.loadDevices();
    this.editing = promotion;
    this.name.set(promotion.name);
    this.description.set(promotion.description ?? '');
    this.validFrom.set(isoToDateInput(promotion.validFrom));
    this.validUntil.set(isoToDateInput(promotion.validUntil));
    this.claimDays.set(promotion.claimDays);
    this.maxClaims.set(promotion.maxClaims);
    this.active.set(promotion.active);
    this.modal.set('edit');
  }

  closeModal(): void {
    this.modal.set(null);
  }

  submit(): void {
    if (!this.canSubmit()) {
      return;
    }
    const body: PromotionRequest = {
      name: this.name().trim(),
      description: this.description().trim() || null,
      validFrom: dateInputToIso(this.validFrom(), false),
      validUntil: dateInputToIso(this.validUntil(), true),
      claimDays: this.number(this.claimDays()),
      maxClaims: this.number(this.maxClaims()),
      active: this.active(),
    };
    this.busy.set(true);
    const editing = this.editing;
    const request = editing ? this.api.updatePromotion(editing.id, body) : this.api.createPromotion(body);
    request.subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.closeModal();
        this.notice.show(editing ? tr('Promoção "{name}" salva.', { name: saved.name }) : tr('Promoção "{name}" criada. Abra-a de novo para pegar o QR Code.', { name: saved.name }));
        this.load();
      },
      error: () => this.busy.set(false),
    });
  }

  remove(promotion: Promotion): void {
    if (!confirm(tr('Excluir a promoção "{name}"?', { name: promotion.name }))) {
      return;
    }
    this.api.deletePromotion(promotion.id).subscribe({
      next: () => {
        this.notice.show(tr('Promoção "{name}" excluída.', { name: promotion.name }));
        this.load();
      },
      error: () => undefined,
    });
  }

  showCoupons(promotion: Promotion): void {
    this.viewing = promotion;
    this.coupons.set([]);
    this.modal.set('coupons');
    this.api.promotionCoupons(promotion.id).subscribe({ next: (list) => this.coupons.set(list), error: () => undefined });
  }

  // ---------- QR Code ----------

  /** O endereço público da promoção (o do QR Code): o painel e a página do cupom ficam no mesmo site. */
  publicLink(): string {
    const token = this.editing?.publicToken;
    return token ? linkWithOrigin(`${location.origin}/cupom/${token}`, this.origin()) : '';
  }

  copyLink(): void {
    const link = this.publicLink();
    if (!link) {
      return;
    }
    navigator.clipboard?.writeText(link).then(
      () => this.notice.show(tr('Endereço copiado.')),
      () => this.notice.show(tr('Não foi possível copiar. Selecione o endereço e copie à mão.')),
    );
  }

  downloadQr(): void {
    const canvas = this.qrCanvas()?.nativeElement;
    if (!canvas) {
      return;
    }
    canvas.toBlob((blob) => {
      if (!blob) {
        return;
      }
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = this.origin() ? `qr-cupom-${this.origin()}.png` : 'qr-cupom.png';
      link.click();
      URL.revokeObjectURL(url);
    }, 'image/png');
  }

  // ---------- apoio ----------

  /** Campo vazio = nulo; senão, o número inteiro. */
  private number(value: number | string | null): number | null {
    if (value === null || value === '') {
      return null;
    }
    const number = Number(value);
    return Number.isFinite(number) && number >= 1 ? Math.floor(number) : null;
  }

  private load(): void {
    this.api.promotions().subscribe({
      next: (list) => {
        this.items.set(list);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }
}
