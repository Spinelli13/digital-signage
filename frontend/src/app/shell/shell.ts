import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { daysUntil, ROLE_LABELS, TENANT_STATUS_LABELS, TENANT_STATUS_TONES } from '../core/saas';
import { Notice } from '../core/notice';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule],
  template: `
    <aside class="side">
      <div class="brand"><img class="logo" src="favicon.svg" alt="" width="34" height="34" /> Digital Signage</div>
      @if (auth.me(); as me) {
        <div class="tenant" data-testid="tenant">
          <b>{{ me.tenant.name }}</b>
          <small>
            <span class="dot" [class]="tone()"></span>{{ me.tenant.plan.name }} · {{ statusLabel() }}
          </small>
        </div>
      }
      <nav>
        @if (!blocked()) {
          <a routerLink="/dashboard" routerLinkActive="active">▦ Dashboard</a>
          <a routerLink="/dispositivos" routerLinkActive="active">📺 Dispositivos</a>
          <a routerLink="/locais" routerLinkActive="active">📍 Pontos & Locais</a>
          <a routerLink="/grupos" routerLinkActive="active">🗂 Grupos</a>
          <a routerLink="/campanhas" routerLinkActive="active">📣 Campanhas</a>
          <a routerLink="/layouts" routerLinkActive="active">🧩 Layouts</a>
          <a routerLink="/alertas" routerLinkActive="active">🔔 Alertas</a>
          <a routerLink="/relatorios" routerLinkActive="active">📊 Relatórios</a>
          <a routerLink="/biblioteca" routerLinkActive="active">🖼 Biblioteca</a>
          <a routerLink="/playlists" routerLinkActive="active">☷ Playlists</a>
        }
        @if (auth.can('admin') && auth.me()) {
          <a routerLink="/equipe" routerLinkActive="active" [class.hidden]="blocked()">👥 Equipe</a>
          <a routerLink="/plano" routerLinkActive="active">💳 Plano e cobrança</a>
        }
        <a routerLink="/downloads" routerLinkActive="active">⬇ Downloads e manuais</a>
        @if (auth.can('platform')) {
          <div class="section">Plataforma</div>
          <a routerLink="/plataforma" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">🏢 Visão geral</a>
          <a routerLink="/plataforma/clientes" routerLinkActive="active">🧾 Clientes</a>
          <a routerLink="/plataforma/planos" routerLinkActive="active">🏷 Planos</a>
          <a routerLink="/plataforma/faturas" routerLinkActive="active">💰 Faturas</a>
          <a routerLink="/plataforma/configuracoes" routerLinkActive="active">⚙ Configurações</a>
        }
      </nav>
      <div class="foot">
        @if (auth.me(); as me) {
          <div class="who">
            <b>{{ me.name }}</b>
            <small>{{ roleLabel() }}</small>
          </div>
        }
        <button class="logout" (click)="openAccount()">Minha conta</button>
        <button class="logout" (click)="logout()">Sair</button>
      </div>
    </aside>
    <main>
      @if (auth.isVisiting()) {
        <div class="banner visiting" role="status">
          Você está atuando no cliente <b>{{ auth.me()?.tenant?.name }}</b>. O que você fizer vale para ele.
          <button class="secondary small" (click)="leave()">Voltar ao meu cliente</button>
        </div>
      }
      @if (alert(); as banner) {
        <div class="banner" [class.danger]="banner.tone === 'danger'" role="status">{{ banner.text }}</div>
      }
      @if (blocked() && !auth.can('admin')) {
        <div class="card empty">
          <h2>Assinatura {{ subscriptionState() }}</h2>
          <p>O painel deste cliente está bloqueado. As TVs continuam exibindo o conteúdo. Fale com o administrador da sua conta para regularizar.</p>
        </div>
      } @else {
        <router-outlet />
      }
    </main>

    @if (accountOpen()) {
      <div class="modal" (click)="closeAccount()">
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="changePassword()">
          <h2>Minha conta</h2>
          @if (auth.me(); as me) {
            <p class="label">{{ me.name }} · {{ me.email }} · {{ roleLabel() }}</p>
          }
          @if (notice.text(); as text) {
            <div class="notice" role="status">{{ text }}</div>
          }
          <h3 class="sub">Trocar a senha</h3>
          <div class="field">
            <label for="pw-current">Senha atual</label>
            <input id="pw-current" name="current" type="password" autocomplete="current-password" required
              [ngModel]="currentPassword()" (ngModelChange)="currentPassword.set($event)" />
          </div>
          <div class="field">
            <label for="pw-new">Nova senha</label>
            <input id="pw-new" name="new" type="password" autocomplete="new-password" required minlength="8"
              [ngModel]="newPassword()" (ngModelChange)="newPassword.set($event)" />
          </div>
          <div class="field">
            <label for="pw-confirm">Repita a nova senha</label>
            <input id="pw-confirm" name="confirm" type="password" autocomplete="new-password" required
              [ngModel]="confirmPassword()" (ngModelChange)="confirmPassword.set($event)" />
          </div>
          @if (passwordProblem(); as problem) {
            <small class="problem">{{ problem }}</small>
          }
          <div class="actions">
            <button type="button" class="secondary" (click)="closeAccount()">Fechar</button>
            <button type="submit" class="primary" [disabled]="busy() || passwordProblem() !== null || !currentPassword()">Trocar senha</button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    :host { display: grid; grid-template-columns: 240px 1fr; min-height: 100vh; }
    .side { background: var(--sidebar); color: #fff; padding: 22px 14px; display: flex; flex-direction: column; gap: 18px; }
    .brand { font-weight: 700; font-size: 17px; padding: 0 10px; display: flex; align-items: center; gap: 10px; }
    .logo { display: block; border-radius: 9px; }
    .tenant { padding: 10px 12px; border-radius: 10px; background: rgba(255, 255, 255, 0.07); display: flex; flex-direction: column; gap: 3px; }
    .tenant small { color: #c9d2e6; }
    nav { display: flex; flex-direction: column; gap: 4px; }
    nav a { color: #c9d2e6; padding: 11px 12px; border-radius: 10px; }
    nav a.hidden { display: none; }
    nav a:hover { background: rgba(255, 255, 255, 0.07); }
    nav a.active { background: var(--blue); color: #fff; }
    .section { margin: 12px 12px 2px; color: #8e9ab3; font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.6px; }
    .foot { margin-top: auto; display: flex; flex-direction: column; gap: 8px; }
    .who { padding: 0 4px; display: flex; flex-direction: column; }
    .who small { color: #8e9ab3; }
    .logout { background: transparent; color: #c9d2e6; border: 1px solid rgba(255, 255, 255, 0.2); border-radius: 10px; padding: 10px; }
    main { padding: 28px; min-width: 0; }
    .banner { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; padding: 10px 14px; border-radius: 10px; background: #fff7e8; border: 1px solid #f3d9a6; color: #7a5410; }
    .banner.visiting { background: var(--blue2); border-color: #c9d8ff; color: #23408f; }
    .banner.danger { background: #fdf0f2; border-color: #f2c8cf; color: #a8283a; }
    .sub { margin: 14px 0 10px; font-size: 15px; }
    .problem { display: block; color: var(--red); margin: -6px 0 6px; }
    @media (max-width: 820px) {
      :host { grid-template-columns: 1fr; }
      .side { flex-direction: row; align-items: center; flex-wrap: wrap; padding: 12px; gap: 10px; }
      nav { flex-direction: row; flex-wrap: wrap; }
      .foot { margin: 0 0 0 auto; flex-direction: row; align-items: center; }
      .who { display: none; }
      main { padding: 16px; }
    }
  `,
})
export class Shell {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly notice = new Notice();
  readonly accountOpen = signal(false);
  readonly busy = signal(false);
  readonly currentPassword = signal('');
  readonly newPassword = signal('');
  readonly confirmPassword = signal('');

  readonly roleLabel = computed(() => {
    const me = this.auth.me();
    return me ? ROLE_LABELS[me.role] : '';
  });

  readonly statusLabel = computed(() => {
    const me = this.auth.me();
    return me ? TENANT_STATUS_LABELS[me.tenant.status] : '';
  });

  readonly tone = computed(() => {
    const me = this.auth.me();
    return me ? TENANT_STATUS_TONES[me.tenant.status] : 'off';
  });

  /** Assinatura suspensa ou encerrada: o painel (menos "Plano e cobrança") fica bloqueado para o cliente. */
  readonly blocked = computed(() => {
    const me = this.auth.me();
    return me !== null && !me.platformAdmin && (me.tenant.status === 'SUSPENDED' || me.tenant.status === 'CANCELED');
  });

  /** "suspensa" / "encerrada", para frases sobre a assinatura. */
  readonly subscriptionState = computed(() => (this.auth.me()?.tenant.status === 'CANCELED' ? 'encerrada' : 'suspensa'));

  /** Aviso do topo: teste acabando e atraso (só para quem cuida da cobrança). */
  readonly alert = computed<{ text: string; tone: 'warn' | 'danger' } | null>(() => {
    const me = this.auth.me();
    if (!me || !this.auth.can('admin') || me.platformAdmin) {
      return null;
    }
    if (me.tenant.status === 'SUSPENDED' || me.tenant.status === 'CANCELED') {
      return {
        text: `A assinatura está ${this.subscriptionState()}. As TVs continuam exibindo, mas o painel só deixa ver "Plano e cobrança" até a regularização.`,
        tone: 'danger',
      };
    }
    if (me.tenant.status === 'PAST_DUE') {
      return { text: 'Há uma fatura em atraso. Regularize em "Plano e cobrança" para o painel não ser suspenso.', tone: 'warn' };
    }
    if (me.tenant.status === 'TRIAL' && me.tenant.trialEndsAt) {
      const days = daysUntil(me.tenant.trialEndsAt);
      if (days <= 7) {
        return {
          text: days <= 0 ? 'O período de teste terminou hoje.' : `O período de teste termina em ${days} dia(s). Escolha um plano em "Plano e cobrança".`,
          tone: 'warn',
        };
      }
    }
    return null;
  });

  readonly passwordProblem = computed(() => {
    const next = this.newPassword();
    if (next.length > 0 && next.length < 8) {
      return 'A nova senha precisa ter pelo menos 8 caracteres.';
    }
    if (this.confirmPassword().length > 0 && this.confirmPassword() !== next) {
      return 'As senhas não conferem.';
    }
    return next.length === 0 || this.confirmPassword().length === 0 ? 'Preencha a nova senha duas vezes.' : null;
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.notice.dispose());
    void this.auth.ensureMe();
    // O administrador de um cliente suspenso cai direto em "Plano e cobrança" (o resto do painel responde 402).
    effect(() => {
      if (this.blocked() && this.auth.can('admin') && !this.router.url.startsWith('/plano') && !this.router.url.startsWith('/downloads')) {
        void this.router.navigate(['/plano']);
      }
    });
  }

  openAccount(): void {
    this.currentPassword.set('');
    this.newPassword.set('');
    this.confirmPassword.set('');
    this.accountOpen.set(true);
  }

  closeAccount(): void {
    this.accountOpen.set(false);
  }

  changePassword(): void {
    if (this.passwordProblem() !== null || !this.currentPassword()) {
      return;
    }
    this.busy.set(true);
    this.api.changePassword(this.currentPassword(), this.newPassword()).subscribe({
      next: () => {
        this.busy.set(false);
        this.currentPassword.set('');
        this.newPassword.set('');
        this.confirmPassword.set('');
        this.notice.show('Senha trocada.');
      },
      // A mensagem (ex.: "senha atual incorreta") aparece por cima da janela, que continua aberta.
      error: () => this.busy.set(false),
    });
  }

  async leave(): Promise<void> {
    await this.auth.leaveTenant();
    await this.router.navigate(['/plataforma/clientes']);
  }

  logout(): void {
    this.auth.logout();
    void this.router.navigate(['/login']);
  }
}
