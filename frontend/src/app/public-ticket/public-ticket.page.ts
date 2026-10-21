import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { ApiService } from '../core/api.service';
import { BRAND } from '../core/brand';
import { TPipe, tr, trServer } from '../core/i18n';
import { PublicQueue, PublicTicket } from '../core/models';
import { IconComponent } from '../shared/icon';
import { PreferencesBar } from '../shared/preferences';

const STORAGE_PREFIX = 'smartplay.myticket.';
const POLL_MS = 5000;

/** A senha que esta pessoa já pegou nesta fila (guardada no próprio celular, para o QR Code não emitir uma segunda). */
export function rememberedTicket(queueToken: string): string | null {
  try {
    return localStorage.getItem(STORAGE_PREFIX + queueToken);
  } catch {
    return null;
  }
}

/** Um código aleatório deste celular (guardado no navegador): o servidor limita as senhas por aparelho com ele. */
export function deviceId(): string {
  const key = 'smartplay.client';
  try {
    const existing = localStorage.getItem(key);
    if (existing && /^[0-9a-zA-Z-]{8,64}$/.test(existing)) {
      return existing;
    }
    const created = typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
    localStorage.setItem(key, created);
    return created;
  } catch {
    return `anon-${Math.random().toString(36).slice(2, 12)}`;
  }
}

function remember(queueToken: string, ticketToken: string): void {
  try {
    localStorage.setItem(STORAGE_PREFIX + queueToken, ticketToken);
  } catch {
    // sem armazenamento (janela privada): a página da senha continua valendo, só não volta sozinha
  }
}

function forget(ticketToken: string): void {
  try {
    for (let i = localStorage.length - 1; i >= 0; i--) {
      const key = localStorage.key(i);
      if (key?.startsWith(STORAGE_PREFIX) && localStorage.getItem(key) === ticketToken) {
        localStorage.removeItem(key);
      }
    }
  } catch {
    // idem
  }
}

/** O motivo que o servidor deu (já traduzido) ou um texto geral quando nem chegou lá. */
export function reasonOf(error: unknown): string {
  const body = (error as { error?: { error?: unknown } } | null)?.error;
  if (body && typeof body.error === 'string' && body.error.trim()) {
    return trServer(body.error);
  }
  return tr('Não foi possível falar com o servidor agora. Confira a internet e tente de novo.');
}

const LAYOUT_CSS = `
  :host { display: block; min-height: 100vh; background: var(--bg); color: var(--text); }
  .prefs-corner { position: absolute; top: calc(12px + env(safe-area-inset-top)); right: 12px; }
  .wrap { max-width: 460px; margin: 0 auto; padding: calc(28px + env(safe-area-inset-top)) 16px 40px; display: flex; flex-direction: column; gap: 16px; }
  .brand { display: flex; align-items: center; gap: 10px; font-weight: 700; color: var(--muted); }
  .brand img { border-radius: 10px; }
  h1 { margin: 0; font-size: 22px; }
  .card { text-align: center; padding: 22px 18px; }
  .big { display: block; font-size: 64px; line-height: 1.05; font-weight: 800; letter-spacing: 0.02em; color: var(--primary); font-variant-numeric: tabular-nums; }
  :host-context(:root[data-theme='dark']) .big { color: var(--link); }
  .muted { color: var(--muted); }
  .state { margin-top: 10px; font-size: 18px; font-weight: 600; }
  .call { border: 2px solid var(--primary); background: var(--primary-soft); animation: pulse 1.4s ease-in-out infinite; }
  .actions { display: flex; flex-direction: column; gap: 10px; margin-top: 8px; }
  button.primary { width: 100%; justify-content: center; padding: 16px; font-size: 17px; }
  .problem { color: var(--danger, #b3261e); font-weight: 600; }
  @keyframes pulse { 0%, 100% { box-shadow: 0 0 0 0 transparent; } 50% { box-shadow: 0 0 0 8px var(--primary-soft); } }
  @media (prefers-reduced-motion: reduce) { .call { animation: none; } }
`;

/** A página aberta pelo QR Code da fila: o nome da fila, quantos esperam e o botão para pegar a senha. */
@Component({
  selector: 'app-queue-take',
  imports: [FormsModule, TPipe, IconComponent, PreferencesBar],
  template: `
    <div class="prefs-corner"><app-preferences /></div>
    <main class="wrap">
      <div class="brand"><img src="favicon.svg" alt="" width="32" height="32" /><span>{{ brand }}</span></div>
      @if (queue(); as info) {
        <section class="card">
          <h1>{{ info.name }}</h1>
          @if (info.open) {
            <p class="muted">{{ '{n} pessoa(s) aguardando agora.' | t: { n: info.waiting } }}</p>
            <div class="field">
              <label for="name">{{ 'Seu nome (opcional)' | t }}</label>
              <input id="name" name="name" maxlength="60" autocomplete="given-name" [ngModel]="customerName()" (ngModelChange)="customerName.set($event)" />
            </div>
            <div class="actions">
              <button class="primary" [disabled]="busy()" (click)="take()"><app-icon name="hourglass" />{{ 'Pegar minha senha' | t }}</button>
            </div>
          } @else {
            <p class="muted">{{ 'Esta fila não está recebendo senhas pelo celular agora. Procure o atendimento.' | t }}</p>
          }
        </section>
      } @else if (problem()) {
        <section class="card"><p class="problem">{{ problem() }}</p></section>
      } @else {
        <section class="card"><p class="muted">{{ 'Carregando…' | t }}</p></section>
      }
      @if (queue() && problem()) {
        <p class="problem" role="alert">{{ problem() }}</p>
      }
    </main>
  `,
  styles: LAYOUT_CSS,
})
export class QueueTakePage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly brand = BRAND;
  readonly queue = signal<PublicQueue | null>(null);
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
    // Quem já pegou uma senha nesta fila volta direto para ela (escanear de novo não emite outra).
    const existing = rememberedTicket(this.token);
    if (existing) {
      void this.router.navigate(['/senha', existing], { replaceUrl: true });
      return;
    }
    this.api.publicQueue(this.token).subscribe({
      next: (queue) => this.queue.set(queue),
      error: (error) => this.problem.set(reasonOf(error)),
    });
  }

  take(): void {
    this.busy.set(true);
    this.problem.set(null);
    this.api.takePublicTicket(this.token, this.customerName().trim() || null, deviceId()).subscribe({
      next: (ticket) => {
        this.busy.set(false);
        if (ticket.ticketToken) {
          remember(this.token, ticket.ticketToken);
          void this.router.navigate(['/senha', ticket.ticketToken]);
        }
      },
      error: (error) => {
        this.busy.set(false);
        this.problem.set(reasonOf(error));
      },
    });
  }
}

/** A página da senha: a posição na fila, o aviso de "é a sua vez" e a opção de sair da fila. Se atualiza sozinha. */
@Component({
  selector: 'app-ticket-status',
  imports: [TPipe, IconComponent, PreferencesBar],
  template: `
    <div class="prefs-corner"><app-preferences /></div>
    <main class="wrap">
      <div class="brand"><img src="favicon.svg" alt="" width="32" height="32" /><span>{{ brand }}</span></div>
      @if (ticket(); as t) {
        <section class="card" [class.call]="t.status === 'CALLING'" aria-live="polite">
          <span class="muted">{{ t.queueName }}</span>
          <b class="big">{{ t.label }}</b>
          @switch (t.status) {
            @case ('WAITING') {
              <p class="state">{{ aheadText() }}</p>
              <p class="muted">{{ 'A página se atualiza sozinha. Fique por perto: quando for a sua vez, ela avisa.' | t }}</p>
            }
            @case ('CALLING') {
              <p class="state">{{ 'É a sua vez! Vá ao {counter}.' | t: { counter: t.counter ?? ('guichê' | t) } }}</p>
            }
            @case ('SERVING') {
              <p class="state">{{ 'Você está sendo atendido.' | t }}</p>
            }
            @case ('DONE') {
              <p class="state">{{ 'Atendimento concluído. Obrigado!' | t }}</p>
            }
            @case ('NO_SHOW') {
              <p class="state">{{ 'Sua senha foi chamada e você não compareceu. Procure o atendimento.' | t }}</p>
            }
            @case ('CANCELED') {
              <p class="state">{{ 'Esta senha foi cancelada ou expirou.' | t }}</p>
            }
          }
        </section>
        @if (t.status === 'WAITING') {
          <div class="actions"><button class="secondary" [disabled]="busy()" (click)="leave()"><app-icon name="x" />{{ 'Sair da fila' | t }}</button></div>
        }
      } @else if (problem()) {
        <section class="card"><p class="problem">{{ problem() }}</p></section>
      } @else {
        <section class="card"><p class="muted">{{ 'Carregando…' | t }}</p></section>
      }
      @if (ticket() && problem()) {
        <p class="problem" role="alert">{{ problem() }}</p>
      }
    </main>
  `,
  styles: LAYOUT_CSS,
})
export class TicketStatusPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly brand = BRAND;
  readonly ticket = signal<PublicTicket | null>(null);
  readonly problem = signal<string | null>(null);
  readonly busy = signal(false);

  readonly aheadText = computed(() => {
    const ahead = this.ticket()?.ahead ?? 0;
    if (ahead === 0) {
      return tr('Você é o próximo da fila.');
    }
    return ahead === 1 ? tr('Há 1 pessoa na sua frente.') : tr('Há {n} pessoas na sua frente.', { n: ahead });
  });

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
    this.api.publicTicket(this.token).subscribe({
      next: (ticket) => this.show(ticket),
      // Um tropeço de rede não apaga a senha da tela: mostra o aviso e tenta de novo no próximo ciclo.
      error: (error) => {
        this.problem.set(reasonOf(error));
        if ((error as { status?: number })?.status === 404) {
          forget(this.token);
          clearInterval(this.timer);
        }
      },
    });
  }

  leave(): void {
    if (!confirm(tr('Sair da fila? Você perde o seu lugar.'))) {
      return;
    }
    this.busy.set(true);
    this.api.leavePublicQueue(this.token).subscribe({
      next: (ticket) => {
        this.busy.set(false);
        this.show(ticket);
      },
      error: (error) => {
        this.busy.set(false);
        this.problem.set(reasonOf(error));
      },
    });
  }

  private show(ticket: PublicTicket): void {
    const previous = this.ticket();
    this.ticket.set(ticket);
    this.problem.set(null);
    // É a vez da pessoa (ou chamaram de novo): o celular vibra, para quem está olhando outra coisa.
    if (ticket.status === 'CALLING' && (previous?.status !== 'CALLING' || previous.callCount !== ticket.callCount)) {
      try {
        navigator.vibrate?.([300, 150, 300]);
      } catch {
        // sem vibração neste aparelho
      }
    }
    // Terminou: a pessoa pode pegar outra senha escaneando de novo, e a página para de perguntar.
    if (ticket.status === 'DONE' || ticket.status === 'NO_SHOW' || ticket.status === 'CANCELED') {
      forget(this.token);
      clearInterval(this.timer);
    }
  }
}
