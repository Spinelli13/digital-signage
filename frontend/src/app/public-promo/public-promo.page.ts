import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { ApiService } from '../core/api.service';
import { FDatePipe, TPipe } from '../core/i18n';
import { PublicPromoCoupon, PublicPromotion } from '../core/models';
import { IconComponent } from '../shared/icon';
import { PreferencesBar } from '../shared/preferences';
import { WordmarkComponent } from '../shared/wordmark';
import { deviceId, reasonOf } from '../public-ticket/public-ticket.page';

const STORAGE_PREFIX = 'smartplay.mycoupon.';
const POLL_MS = 10_000;

/** O cupom que esta pessoa já pegou nesta promoção (guardado no próprio celular, para escanear de novo voltar a ele). */
export function rememberedCoupon(promotionToken: string): string | null {
  try {
    return localStorage.getItem(STORAGE_PREFIX + promotionToken);
  } catch {
    return null;
  }
}

function remember(promotionToken: string, couponToken: string): void {
  try {
    localStorage.setItem(STORAGE_PREFIX + promotionToken, couponToken);
  } catch {
    // sem armazenamento (janela privada): a página do cupom continua valendo, só não volta sozinha
  }
}

/**
 * O estilo comum das duas páginas. Cada componente acrescenta o seu próprio trecho: dois componentes do mesmo arquivo com o MESMO
 * estilo, palavra por palavra, fazem o `ng test` não terminar (o processo fica pendurado depois de passar tudo).
 */
const PROMO_CSS = `
  :host { display: block; min-height: 100vh; background: var(--bg); color: var(--text); }
  .prefs-corner { position: absolute; top: calc(12px + env(safe-area-inset-top)); right: 12px; }
  .wrap { max-width: 460px; margin: 0 auto; padding: calc(28px + env(safe-area-inset-top)) 16px 40px; display: flex; flex-direction: column; gap: 16px; }
  .brand { display: flex; align-items: center; gap: 10px; font-size: 20px; color: var(--text-2); }
  .brand img { border-radius: 10px; }
  h1 { margin: 0; font-size: 22px; }
  .card { text-align: center; padding: 24px 18px; }
  .muted { color: var(--muted); }
  .terms { white-space: pre-line; margin: 10px 0; }
  .actions { display: flex; flex-direction: column; gap: 10px; margin-top: 8px; }
  button.primary { width: 100%; justify-content: center; padding: 16px; font-size: 17px; }
  .problem { color: var(--danger, #b3261e); font-weight: 600; }
`;

/** A página aberta pelo QR Code da TV: a promoção e o botão para pegar o cupom. */
@Component({
  selector: 'app-promo-claim',
  imports: [FormsModule, TPipe, FDatePipe, IconComponent, PreferencesBar, WordmarkComponent],
  template: `
    <div class="prefs-corner"><app-preferences /></div>
    <main class="wrap">
      <div class="brand"><img src="favicon.svg" alt="" width="32" height="32" /><app-wordmark /></div>
      @if (promotion(); as info) {
        <section class="card">
          <app-icon name="ticket" [size]="34" />
          <h1>{{ info.name }}</h1>
          @if (info.description) {
            <p class="terms">{{ info.description }}</p>
          }
          @if (info.open) {
            @if (info.claimDays) {
              <p class="muted">{{ 'O cupom vale por {n} dia(s) depois de pego.' | t: { n: info.claimDays } }}</p>
            } @else if (info.validUntil) {
              <p class="muted">{{ 'Vale até {date}.' | t: { date: (info.validUntil | fdate) } }}</p>
            }
            <div class="field">
              <label for="name">{{ 'Seu nome (opcional)' | t }}</label>
              <input id="name" name="name" maxlength="60" autocomplete="given-name" [ngModel]="customerName()" (ngModelChange)="customerName.set($event)" />
            </div>
            <div class="actions">
              <button class="primary" [disabled]="busy()" (click)="claim()"><app-icon name="ticket" />{{ 'Pegar meu cupom' | t }}</button>
            </div>
          } @else {
            <p class="muted">{{ 'Esta promoção não está disponível agora. Pode ter acabado. Procure o atendimento da loja.' | t }}</p>
          }
        </section>
      } @else if (problem()) {
        <section class="card"><p class="problem">{{ problem() }}</p></section>
      } @else {
        <section class="card"><p class="muted">{{ 'Carregando…' | t }}</p></section>
      }
      @if (promotion() && problem()) {
        <p class="problem" role="alert">{{ problem() }}</p>
      }
    </main>
  `,
  styles: PROMO_CSS + '.claim { display: block; }',
})
export class PromoClaimPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly promotion = signal<PublicPromotion | null>(null);
  readonly problem = signal<string | null>(null);
  readonly busy = signal(false);
  readonly customerName = signal('');

  private get token(): string {
    return this.route.snapshot.paramMap.get('token') ?? '';
  }

  private started = false;

  ngOnInit(): void {
    if (this.started) {
      return;
    }
    this.started = true;
    // Quem já pegou o cupom desta promoção volta direto para ele (escanear de novo não gera outro).
    const existing = rememberedCoupon(this.token);
    if (existing) {
      void this.router.navigate(['/meu-cupom', existing], { replaceUrl: true });
      return;
    }
    this.api.publicPromotion(this.token).subscribe({
      next: (promotion) => this.promotion.set(promotion),
      error: (error) => this.problem.set(reasonOf(error)),
    });
  }

  claim(): void {
    this.busy.set(true);
    this.problem.set(null);
    this.api.claimPublicCoupon(this.token, this.customerName().trim() || null, deviceId()).subscribe({
      next: (coupon) => {
        this.busy.set(false);
        remember(this.token, coupon.token);
        void this.router.navigate(['/meu-cupom', coupon.token]);
      },
      error: (error) => {
        this.busy.set(false);
        this.problem.set(reasonOf(error));
      },
    });
  }
}

/** A página do cupom: o código para mostrar no caixa e se ele ainda vale. Se atualiza sozinha enquanto vale. */
@Component({
  selector: 'app-my-coupon',
  imports: [TPipe, FDatePipe, IconComponent, PreferencesBar, WordmarkComponent],
  template: `
    <div class="prefs-corner"><app-preferences /></div>
    <main class="wrap">
      <div class="brand"><img src="favicon.svg" alt="" width="32" height="32" /><app-wordmark /></div>
      @if (coupon(); as c) {
        <section class="card" [class.done]="c.status !== 'ISSUED'" aria-live="polite">
          <span class="muted">{{ c.promotionName }}</span>
          <b class="code">{{ c.code }}</b>
          @switch (c.status) {
            @case ('ISSUED') {
              <p class="state"><app-icon name="circle-check" [size]="20" />{{ 'Mostre este código no caixa.' | t }}</p>
              @if (c.expiresAt) {
                <p class="muted">{{ 'Vale até {date}.' | t: { date: (c.expiresAt | fdate: 'datetime') } }}</p>
              }
            }
            @case ('REDEEMED') {
              <p class="state"><app-icon name="badge-check" [size]="20" />{{ 'Cupom já usado.' | t }}</p>
              @if (c.redeemedAt) {
                <p class="muted">{{ 'Usado em {date}.' | t: { date: (c.redeemedAt | fdate: 'datetime') } }}</p>
              }
            }
            @default {
              <p class="state"><app-icon name="clock" [size]="20" />{{ 'Este cupom venceu.' | t }}</p>
            }
          }
          @if (c.promotionDescription) {
            <p class="terms muted">{{ c.promotionDescription }}</p>
          }
        </section>
      } @else if (problem()) {
        <section class="card"><p class="problem">{{ problem() }}</p></section>
      } @else {
        <section class="card"><p class="muted">{{ 'Carregando…' | t }}</p></section>
      }
      @if (coupon() && problem()) {
        <p class="problem" role="alert">{{ problem() }}</p>
      }
    </main>
  `,
  styles: PROMO_CSS + '.code { display: block; margin: 8px 0; font-size: 44px; line-height: 1.1; font-weight: 800; letter-spacing: 0.06em; font-variant-numeric: tabular-nums; color: var(--primary); } .state { display: flex; align-items: center; justify-content: center; gap: 8px; font-size: 18px; font-weight: 600; } .done .code { color: var(--muted); text-decoration: line-through; }',
})
export class MyCouponPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly coupon = signal<PublicPromoCoupon | null>(null);
  readonly problem = signal<string | null>(null);

  private started = false;
  private timer: ReturnType<typeof setInterval> | undefined;

  private get token(): string {
    return this.route.snapshot.paramMap.get('token') ?? '';
  }

  ngOnInit(): void {
    if (this.started) {
      return;
    }
    this.started = true;
    this.refresh();
    this.timer = setInterval(() => this.refresh(), POLL_MS);
    this.destroyRef.onDestroy(() => clearInterval(this.timer));
  }

  refresh(): void {
    this.api.publicPromoCoupon(this.token).subscribe({
      next: (coupon) => {
        this.coupon.set(coupon);
        this.problem.set(null);
        // Usado ou vencido: nada mais vai mudar, a página para de perguntar.
        if (coupon.status !== 'ISSUED') {
          clearInterval(this.timer);
        }
      },
      // Um tropeço de rede não apaga o cupom da tela: mostra o aviso e tenta de novo no próximo ciclo.
      error: (error) => {
        this.problem.set(reasonOf(error));
        if ((error as { status?: number })?.status === 404) {
          clearInterval(this.timer);
        }
      },
    });
  }
}
