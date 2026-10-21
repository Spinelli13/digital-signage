import { Component, DestroyRef, HostListener, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { TPipe, tr } from '../core/i18n';
import { Notice } from '../core/notice';
import { daysUntil, roleLabel, tenantStatusLabel, TENANT_STATUS_TONES } from '../core/saas';
import { PreferencesBar } from '../shared/preferences';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule, TPipe, PreferencesBar],
  template: `
    <!-- celular: barra do topo e menu lateral que abre por cima -->
    <header class="appbar">
      <button type="button" class="icon-btn" data-testid="menu-button" (click)="toggleMenu()"
        [attr.aria-label]="'Menu' | t" [attr.aria-expanded]="menuOpen()">☰</button>
      <img class="logo" src="favicon.svg" alt="" width="30" height="30" />
      <span class="title">{{ brand }}</span>
      <span class="grow"></span>
      <app-preferences />
    </header>
    @if (menuOpen()) {
      <div class="backdrop" data-testid="backdrop" (click)="closeMenu()"></div>
    }

    <aside class="side" [class.open]="menuOpen()">
      <div class="brand"><img class="logo" src="favicon.svg" alt="" width="34" height="34" /> {{ brand }}</div>
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
          <a routerLink="/dashboard" routerLinkActive="active"><span class="ico">▦</span> {{ 'Dashboard' | t }}</a>
          <a routerLink="/dispositivos" routerLinkActive="active"><span class="ico">📺</span> {{ 'Dispositivos' | t }}</a>
          <a routerLink="/locais" routerLinkActive="active"><span class="ico">📍</span> {{ 'Pontos & Locais' | t }}</a>
          <a routerLink="/grupos" routerLinkActive="active"><span class="ico">🗂</span> {{ 'Grupos' | t }}</a>
          <a routerLink="/campanhas" routerLinkActive="active"><span class="ico">📣</span> {{ 'Campanhas' | t }}</a>
          <a routerLink="/layouts" routerLinkActive="active"><span class="ico">🧩</span> {{ 'Layouts' | t }}</a>
          <a routerLink="/alertas" routerLinkActive="active"><span class="ico">🔔</span> {{ 'Alertas' | t }}</a>
          <a routerLink="/relatorios" routerLinkActive="active"><span class="ico">📊</span> {{ 'Relatórios' | t }}</a>
          <a routerLink="/biblioteca" routerLinkActive="active"><span class="ico">🖼</span> {{ 'Biblioteca' | t }}</a>
          <a routerLink="/playlists" routerLinkActive="active"><span class="ico">☷</span> {{ 'Playlists' | t }}</a>
        }
        @if (auth.can('admin') && auth.me()) {
          <a routerLink="/equipe" routerLinkActive="active" [class.hidden]="blocked()"><span class="ico">👥</span> {{ 'Equipe' | t }}</a>
          <a routerLink="/plano" routerLinkActive="active"><span class="ico">💳</span> {{ 'Plano e cobrança' | t }}</a>
        }
        <a routerLink="/downloads" routerLinkActive="active"><span class="ico">⬇</span> {{ 'Downloads e manuais' | t }}</a>
        @if (auth.can('platform')) {
          <div class="section">{{ 'Plataforma' | t }}</div>
          <a routerLink="/plataforma" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }"><span class="ico">🏢</span> {{ 'Visão geral' | t }}</a>
          <a routerLink="/plataforma/clientes" routerLinkActive="active"><span class="ico">🧾</span> {{ 'Clientes' | t }}</a>
          <a routerLink="/plataforma/planos" routerLinkActive="active"><span class="ico">🏷</span> {{ 'Planos' | t }}</a>
          <a routerLink="/plataforma/faturas" routerLinkActive="active"><span class="ico">💰</span> {{ 'Faturas' | t }}</a>
          <a routerLink="/plataforma/configuracoes" routerLinkActive="active"><span class="ico">⚙</span> {{ 'Configurações' | t }}</a>
        }
      </nav>
      <div class="foot">
        @if (auth.me(); as me) {
          <div class="who">
            <b>{{ me.name }}</b>
            <small>{{ roleText() }}</small>
          </div>
        }
        <div class="side-prefs"><app-preferences /></div>
        <button class="logout" (click)="openAccount()">{{ 'Minha conta' | t }}</button>
        <button class="logout" (click)="logout()">{{ 'Sair' | t }}</button>
      </div>
    </aside>

    <main>
      @if (auth.isVisiting()) {
        <div class="banner visiting" role="status">
          <span>{{ 'Você está atuando no cliente' | t }} <b>{{ auth.me()?.tenant?.name }}</b>. {{ 'O que você fizer vale para ele.' | t }}</span>
          <button class="secondary small" (click)="leave()">{{ 'Voltar ao meu cliente' | t }}</button>
        </div>
      }
      @if (alert(); as banner) {
        <div class="banner" [class.danger]="banner.tone === 'danger'" role="status">{{ banner.text }}</div>
      }
      @if (blocked() && !auth.can('admin')) {
        <div class="card empty">
          <h2>{{ blockedTitle() }}</h2>
          <p>{{ 'O painel deste cliente está bloqueado. As TVs continuam exibindo o conteúdo. Fale com o administrador da sua conta para regularizar.' | t }}</p>
        </div>
      } @else if (showOutlet()) {
        <router-outlet />
      }
    </main>

    <!-- celular: atalhos fixos na base -->
    @if (auth.me() && !blocked()) {
      <nav class="bottombar" [attr.aria-label]="'Atalhos' | t">
        <a routerLink="/dashboard" routerLinkActive="active"><span class="ico">▦</span><span>{{ 'Dashboard' | t }}</span></a>
        <a routerLink="/dispositivos" routerLinkActive="active"><span class="ico">📺</span><span>{{ 'Dispositivos' | t }}</span></a>
        <a routerLink="/alertas" routerLinkActive="active"><span class="ico">🔔</span><span>{{ 'Alertas' | t }}</span></a>
        <a routerLink="/biblioteca" routerLinkActive="active"><span class="ico">🖼</span><span>{{ 'Biblioteca' | t }}</span></a>
        <button type="button" (click)="toggleMenu()"><span class="ico">☰</span><span>{{ 'Menu' | t }}</span></button>
      </nav>
    }

    @if (accountOpen()) {
      <div class="modal" (click)="closeAccount()">
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="changePassword()">
          <h2>{{ 'Minha conta' | t }}</h2>
          @if (auth.me(); as me) {
            <p class="label">{{ me.name }} · {{ me.email }} · {{ roleText() }}</p>
          }
          @if (notice.text(); as text) {
            <div class="notice" role="status">{{ text }}</div>
          }
          <h3 class="sub">{{ 'Preferências' | t }}</h3>
          <app-preferences [full]="true" />
          <h3 class="sub">{{ 'Trocar a senha' | t }}</h3>
          <div class="field">
            <label for="pw-current">{{ 'Senha atual' | t }}</label>
            <input id="pw-current" name="current" type="password" autocomplete="current-password" required
              [ngModel]="currentPassword()" (ngModelChange)="currentPassword.set($event)" />
          </div>
          <div class="field">
            <label for="pw-new">{{ 'Nova senha' | t }}</label>
            <input id="pw-new" name="new" type="password" autocomplete="new-password" required minlength="8"
              [ngModel]="newPassword()" (ngModelChange)="newPassword.set($event)" />
          </div>
          <div class="field">
            <label for="pw-confirm">{{ 'Repita a nova senha' | t }}</label>
            <input id="pw-confirm" name="confirm" type="password" autocomplete="new-password" required
              [ngModel]="confirmPassword()" (ngModelChange)="confirmPassword.set($event)" />
          </div>
          @if (passwordProblem(); as problem) {
            <small class="problem">{{ problem }}</small>
          }
          <div class="actions">
            <button type="button" class="secondary" (click)="closeAccount()">{{ 'Fechar' | t }}</button>
            <button type="submit" class="primary" [disabled]="busy() || passwordProblem() !== null || !currentPassword()">{{ 'Trocar senha' | t }}</button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    :host { display: grid; grid-template-columns: 250px minmax(0, 1fr); min-height: 100vh; }
    .appbar, .bottombar, .backdrop { display: none; }

    .side {
      background: var(--sidebar); color: #fff; padding: 22px 14px; display: flex; flex-direction: column; gap: 18px;
      position: sticky; top: 0; height: 100vh; overflow-y: auto; border-right: 1px solid var(--sidebar-line);
    }
    .brand { font-weight: 700; font-size: 17px; padding: 0 10px; display: flex; align-items: center; gap: 10px; }
    .logo { display: block; border-radius: 9px; }
    .tenant { padding: 10px 12px; border-radius: 10px; background: var(--sidebar-hover); border: 1px solid var(--sidebar-line); display: flex; flex-direction: column; gap: 3px; }
    .tenant small { color: var(--sidebar-text); }
    nav { display: flex; flex-direction: column; gap: 4px; }
    nav a { color: var(--sidebar-text); padding: 11px 12px; border-radius: 10px; display: flex; align-items: center; gap: 8px; }
    nav a.hidden { display: none; }
    nav a:hover { background: var(--sidebar-hover); }
    nav a.active { background: var(--primary); color: var(--on-primary); }
    .ico { display: inline-block; width: 1.4em; text-align: center; }
    .section { margin: 12px 12px 2px; color: var(--sidebar-muted); font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.6px; }
    .foot { margin-top: auto; display: flex; flex-direction: column; gap: 8px; }
    .who { padding: 0 4px; display: flex; flex-direction: column; }
    .who small { color: var(--sidebar-muted); }
    .side-prefs { padding: 0 2px; --prefs-fg: var(--sidebar-text); --prefs-bg: transparent; --prefs-line: var(--sidebar-line); }
    .logout { background: transparent; color: var(--sidebar-text); border: 1px solid var(--sidebar-line); border-radius: 10px; padding: 10px; }
    .logout:hover { background: var(--sidebar-hover); }
    main { padding: 28px; min-width: 0; }
    .banner { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; padding: 10px 14px; border-radius: 10px; background: var(--warn-bg); border: 1px solid var(--warn-line); color: var(--warn-fg); }
    .banner.visiting { background: var(--primary-soft); border-color: var(--primary-line); color: var(--text); }
    .banner.danger { background: var(--err-bg); border-color: var(--err-line); color: var(--err-fg); }
    .sub { margin: 14px 0 10px; font-size: 15px; }
    .problem { display: block; color: var(--red); margin: -6px 0 6px; }

    @media (max-width: 820px) {
      :host { display: block; min-height: 100vh; }

      .appbar {
        display: flex; align-items: center; gap: 10px; position: fixed; z-index: 30; top: 0; left: 0; right: 0;
        height: calc(var(--appbar-h) + env(safe-area-inset-top)); padding: env(safe-area-inset-top) 12px 0;
        background: var(--sidebar); color: #fff; border-bottom: 1px solid var(--sidebar-line);
      }
      .appbar .title { font-weight: 700; font-size: 16px; }
      .appbar .grow { flex: 1; }
      .appbar app-preferences { --prefs-fg: var(--sidebar-text); --prefs-bg: transparent; --prefs-line: var(--sidebar-line); }
      .icon-btn { background: transparent; color: #fff; border: 1px solid var(--sidebar-line); border-radius: 10px; width: 42px; height: 42px; font-size: 20px; line-height: 1; }

      .backdrop { display: block; position: fixed; inset: 0; z-index: 40; background: var(--overlay); }
      .side {
        position: fixed; z-index: 50; top: 0; left: 0; bottom: 0; height: auto; width: min(300px, 86vw);
        transform: translateX(-102%); transition: transform 0.2s ease; box-shadow: var(--shadow);
        padding-top: calc(22px + env(safe-area-inset-top)); padding-bottom: calc(22px + env(safe-area-inset-bottom));
      }
      .side.open { transform: none; }
      .side-prefs { display: none; }

      main {
        padding: 16px 14px;
        padding-top: calc(var(--appbar-h) + env(safe-area-inset-top) + 16px);
        padding-bottom: calc(var(--bottombar-h) + env(safe-area-inset-bottom) + 16px);
      }

      .bottombar {
        display: flex; flex-direction: row; gap: 0; position: fixed; z-index: 30; left: 0; right: 0; bottom: 0;
        height: calc(var(--bottombar-h) + env(safe-area-inset-bottom)); padding-bottom: env(safe-area-inset-bottom);
        background: var(--sidebar); border-top: 1px solid var(--sidebar-line);
      }
      .bottombar a, .bottombar button {
        flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px;
        color: var(--sidebar-muted); font-size: 11px; background: transparent; border: 0; padding: 4px 2px; min-width: 0;
      }
      .bottombar a span:last-child, .bottombar button span:last-child { max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
      .bottombar .ico { font-size: 20px; width: auto; line-height: 1.1; }
      .bottombar a { border-radius: 0; gap: 2px; }
      .bottombar a:hover, .bottombar a.active { background: transparent; }
      .bottombar a.active { color: #fff; }
      .bottombar a.active .ico { background: var(--primary); border-radius: 12px; padding: 2px 14px; }
    }
  `,
})
export class Shell {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  protected readonly brand = 'Digital Signage';

  readonly notice = new Notice();
  readonly accountOpen = signal(false);
  readonly menuOpen = signal(false);
  readonly busy = signal(false);
  readonly currentPassword = signal('');
  readonly newPassword = signal('');
  readonly confirmPassword = signal('');

  readonly roleText = computed(() => {
    const me = this.auth.me();
    return me ? roleLabel(me.role) : '';
  });

  readonly statusLabel = computed(() => {
    const me = this.auth.me();
    return me ? tenantStatusLabel(me.tenant.status) : '';
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

  /** A conta já chegou (ou falhou): só então as páginas abrem, para não pedirem dados que o servidor vai recusar. */
  readonly ready = signal(false);

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => this.router.url),
    ),
    { initialValue: this.router.url },
  );

  /** Com a assinatura suspensa o administrador só vê "Plano e cobrança" (e os downloads); o resto espera o redirecionamento. */
  readonly showOutlet = computed(() => {
    if (!this.ready()) {
      return false;
    }
    const url = this.url();
    return !this.blocked() || url.startsWith('/plano') || url.startsWith('/downloads');
  });

  /** "suspensa" / "encerrada", para frases sobre a assinatura. */
  readonly subscriptionState = computed(() => tr(this.auth.me()?.tenant.status === 'CANCELED' ? 'encerrada' : 'suspensa'));

  readonly blockedTitle = computed(() => tr(this.auth.me()?.tenant.status === 'CANCELED' ? 'Assinatura encerrada' : 'Assinatura suspensa'));

  /** Aviso do topo: teste acabando e atraso (só para quem cuida da cobrança). */
  readonly alert = computed<{ text: string; tone: 'warn' | 'danger' } | null>(() => {
    const me = this.auth.me();
    if (!me || !this.auth.can('admin') || me.platformAdmin) {
      return null;
    }
    if (me.tenant.status === 'SUSPENDED' || me.tenant.status === 'CANCELED') {
      return {
        text: tr('A assinatura está {state}. As TVs continuam exibindo, mas o painel só deixa ver "Plano e cobrança" até a regularização.', {
          state: this.subscriptionState(),
        }),
        tone: 'danger',
      };
    }
    if (me.tenant.status === 'PAST_DUE') {
      return { text: tr('Há uma fatura em atraso. Regularize em "Plano e cobrança" para o painel não ser suspenso.'), tone: 'warn' };
    }
    if (me.tenant.status === 'TRIAL' && me.tenant.trialEndsAt) {
      const days = daysUntil(me.tenant.trialEndsAt);
      if (days <= 7) {
        return {
          text:
            days <= 0
              ? tr('O período de teste terminou hoje.')
              : tr('O período de teste termina em {days} dia(s). Escolha um plano em "Plano e cobrança".', { days }),
          tone: 'warn',
        };
      }
    }
    return null;
  });

  readonly passwordProblem = computed(() => {
    const next = this.newPassword();
    if (next.length > 0 && next.length < 8) {
      return tr('A nova senha precisa ter pelo menos 8 caracteres.');
    }
    if (this.confirmPassword().length > 0 && this.confirmPassword() !== next) {
      return tr('As senhas não conferem.');
    }
    return next.length === 0 || this.confirmPassword().length === 0 ? tr('Preencha a nova senha duas vezes.') : null;
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.notice.dispose());
    void this.auth.ensureMe().finally(() => this.ready.set(true));
    // O administrador de um cliente suspenso cai direto em "Plano e cobrança" (o resto do painel responde 402).
    effect(() => {
      if (this.blocked() && this.auth.can('admin') && !this.router.url.startsWith('/plano') && !this.router.url.startsWith('/downloads')) {
        void this.router.navigate(['/plano']);
      }
    });
    // Celular: o menu lateral fecha ao navegar.
    effect(() => {
      this.url();
      this.menuOpen.set(false);
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.menuOpen.set(false);
  }

  toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  closeMenu(): void {
    this.menuOpen.set(false);
  }

  openAccount(): void {
    this.currentPassword.set('');
    this.newPassword.set('');
    this.confirmPassword.set('');
    this.menuOpen.set(false);
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
        this.notice.show(tr('Senha trocada.'));
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
