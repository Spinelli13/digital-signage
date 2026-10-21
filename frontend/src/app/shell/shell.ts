import { Component, DestroyRef, HostListener, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { ApiService } from '../core/api.service';
import { AttentionService } from '../core/attention.service';
import { BRAND } from '../core/brand';
import { AuthService } from '../core/auth.service';
import { TPipe, tr } from '../core/i18n';
import { Notice } from '../core/notice';
import { daysUntil, planText, roleLabel, tenantStatusLabel, TENANT_STATUS_TONES } from '../core/saas';
import { IconComponent } from '../shared/icon';
import { PreferencesBar } from '../shared/preferences';
import { PillComponent } from '../shared/ui';
import { UserMenuComponent } from '../shared/user-menu';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule, TPipe, PreferencesBar, IconComponent, PillComponent, UserMenuComponent],
  template: `
    <!-- celular: barra do topo e menu lateral que abre por cima -->
    <header class="appbar">
      <button type="button" class="icon-btn menu-btn" data-testid="menu-button" (click)="toggleMenu()"
        [attr.aria-label]="'Menu' | t" [attr.aria-expanded]="menuOpen()"><app-icon name="menu" [size]="22" /></button>
      <img class="logo" src="favicon.svg" alt="" width="30" height="30" />
      <span class="title">{{ brand }}</span>
      <span class="grow"></span>
      <app-user-menu variant="bar" (account)="openAccount()" (logout)="logout()" />
    </header>
    @if (menuOpen()) {
      <div class="backdrop" data-testid="backdrop" (click)="closeMenu()"></div>
    }

    <aside class="side" [class.open]="menuOpen()">
      <div class="brand"><img class="logo" src="favicon.svg" alt="" width="34" height="34" /><span>{{ brand }}</span></div>
      @if (auth.me(); as me) {
        <div class="tenant" data-testid="tenant">
          <span class="tenant-icon"><app-icon name="building" [size]="18" /></span>
          <div class="tenant-text">
            <b>{{ me.tenant.name }}</b>
            <span class="tenant-pills">
              <app-pill tone="brand">{{ planName(me.tenant.plan.name) }}</app-pill>
              <app-pill [tone]="statusTone()">{{ statusLabel() }}</app-pill>
            </span>
          </div>
        </div>
      }

      <nav>
        @if (!blocked()) {
          <a routerLink="/dashboard" routerLinkActive="active"><app-icon name="layout-dashboard" /><span>{{ 'Dashboard' | t }}</span></a>

          <div class="section">{{ 'Operação' | t }}</div>
          <a routerLink="/dispositivos" routerLinkActive="active"><app-icon name="monitor" /><span>{{ 'Dispositivos' | t }}</span></a>
          <a routerLink="/locais" routerLinkActive="active"><app-icon name="map-pin" /><span>{{ 'Pontos & Locais' | t }}</span></a>
          <a routerLink="/grupos" routerLinkActive="active"><app-icon name="layers" /><span>{{ 'Grupos' | t }}</span></a>

          <div class="section">{{ 'Conteúdo' | t }}</div>
          <a routerLink="/biblioteca" routerLinkActive="active"><app-icon name="image" /><span>{{ 'Biblioteca' | t }}</span></a>
          <a routerLink="/playlists" routerLinkActive="active"><app-icon name="list-video" /><span>{{ 'Playlists' | t }}</span></a>
          <a routerLink="/campanhas" routerLinkActive="active"><app-icon name="megaphone" /><span>{{ 'Campanhas' | t }}</span></a>
          <a routerLink="/layouts" routerLinkActive="active"><app-icon name="layout-template" /><span>{{ 'Layouts' | t }}</span></a>
          <a routerLink="/precos" routerLinkActive="active"><app-icon name="badge-dollar-sign" /><span>{{ 'Preços' | t }}</span></a>

          <div class="section">{{ 'Monitoramento' | t }}</div>
          <a routerLink="/alertas" routerLinkActive="active">
            <app-icon name="bell" /><span>{{ 'Alertas' | t }}</span>
            @if (attention.openAlerts() > 0) {
              <span class="badge" data-testid="alerts-badge">{{ attention.openAlerts() }}</span>
            }
          </a>
          <a routerLink="/relatorios" routerLinkActive="active"><app-icon name="chart-column" /><span>{{ 'Relatórios' | t }}</span></a>
        }

        <div class="section">{{ 'Administração' | t }}</div>
        @if (auth.can('admin') && auth.me()) {
          <a routerLink="/equipe" routerLinkActive="active" [class.hidden]="blocked()"><app-icon name="users" /><span>{{ 'Equipe' | t }}</span></a>
          <a routerLink="/plano" routerLinkActive="active"><app-icon name="credit-card" /><span>{{ 'Plano e cobrança' | t }}</span></a>
        }
        <a routerLink="/downloads" routerLinkActive="active"><app-icon name="download" /><span>{{ 'Downloads e manuais' | t }}</span></a>

        @if (auth.can('platform')) {
          <div class="section">{{ 'Plataforma' | t }}</div>
          <a routerLink="/plataforma" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }"><app-icon name="gauge" /><span>{{ 'Visão geral' | t }}</span></a>
          <a routerLink="/plataforma/clientes" routerLinkActive="active"><app-icon name="building" /><span>{{ 'Clientes' | t }}</span></a>
          <a routerLink="/plataforma/planos" routerLinkActive="active"><app-icon name="tag" /><span>{{ 'Planos' | t }}</span></a>
          <a routerLink="/plataforma/faturas" routerLinkActive="active"><app-icon name="receipt-text" /><span>{{ 'Faturas' | t }}</span></a>
          <a routerLink="/plataforma/configuracoes" routerLinkActive="active"><app-icon name="settings" /><span>{{ 'Configurações' | t }}</span></a>
        }
      </nav>

      <div class="foot">
        <app-user-menu variant="side" (account)="openAccount()" (logout)="logout()" />
      </div>
    </aside>

    <main>
      @if (auth.isVisiting()) {
        <div class="banner visiting" role="status">
          <span class="banner-text"><app-icon name="eye" [size]="18" /><span>{{ 'Você está atuando no cliente' | t }} <b>{{ auth.me()?.tenant?.name }}</b>. {{ 'O que você fizer vale para ele.' | t }}</span></span>
          <button class="secondary small" (click)="leave()"><app-icon name="arrow-left" [size]="15" />{{ 'Voltar ao meu cliente' | t }}</button>
        </div>
      }
      @if (alert(); as banner) {
        <div class="banner" [class.danger]="banner.tone === 'danger'" role="status">
          <span class="banner-text"><app-icon [name]="banner.tone === 'danger' ? 'circle-alert' : 'triangle-alert'" [size]="18" /><span>{{ banner.text }}</span></span>
          @if (auth.can('admin')) {
            <a class="secondary small" routerLink="/plano">{{ 'Plano e cobrança' | t }}<app-icon name="arrow-right" [size]="15" /></a>
          }
        </div>
      }
      @if (blocked() && !auth.can('admin')) {
        <div class="card blocked">
          <span class="blocked-icon"><app-icon name="lock" [size]="28" [stroke]="1.8" /></span>
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
        <a routerLink="/dashboard" routerLinkActive="active"><span class="ico"><app-icon name="layout-dashboard" [size]="21" /></span><span>{{ 'Dashboard' | t }}</span></a>
        <a routerLink="/dispositivos" routerLinkActive="active"><span class="ico"><app-icon name="monitor" [size]="21" /></span><span>{{ 'Dispositivos' | t }}</span></a>
        <a routerLink="/alertas" routerLinkActive="active">
          <span class="ico"><app-icon name="bell" [size]="21" />@if (attention.openAlerts() > 0) {<span class="dotbadge"></span>}</span><span>{{ 'Alertas' | t }}</span>
        </a>
        <a routerLink="/biblioteca" routerLinkActive="active"><span class="ico"><app-icon name="image" [size]="21" /></span><span>{{ 'Biblioteca' | t }}</span></a>
        <button type="button" (click)="toggleMenu()"><span class="ico"><app-icon name="menu" [size]="21" /></span><span>{{ 'Menu' | t }}</span></button>
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
    :host { display: grid; grid-template-columns: 268px minmax(0, 1fr); min-height: 100vh; }
    .appbar, .bottombar, .backdrop { display: none; }

    .side {
      background: linear-gradient(180deg, var(--sidebar) 0%, var(--sidebar-2) 100%); color: #fff; padding: 20px 14px 16px; display: flex; flex-direction: column; gap: 16px;
      position: sticky; top: 0; height: 100vh; overflow-y: auto; border-right: 1px solid var(--sidebar-line); scrollbar-width: thin;
    }
    .brand { display: flex; align-items: center; gap: 11px; padding: 0 8px; font-weight: 700; font-size: 17px; letter-spacing: -0.01em; }
    .logo { display: block; border-radius: 9px; }

    .tenant { display: flex; align-items: center; gap: 11px; padding: 11px 12px; border-radius: var(--r); background: var(--sidebar-hover); border: 1px solid var(--sidebar-line); }
    .tenant-icon { flex: none; display: grid; place-items: center; width: 36px; height: 36px; border-radius: 10px; color: var(--accent); background: rgba(47, 207, 146, 0.12); }
    .tenant-text { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
    .tenant-text b { font-size: 14px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .tenant-pills { display: flex; gap: 6px; flex-wrap: wrap; }
    .tenant-pills app-pill { height: 22px; font-size: 11.5px; }

    nav { display: flex; flex-direction: column; gap: 2px; }
    nav a {
      position: relative; display: flex; align-items: center; gap: 11px; padding: 10px 12px; border-radius: var(--r); color: var(--sidebar-text);
      font-size: 14.5px; font-weight: 500; text-decoration: none; transition: background 0.15s var(--ease), color 0.15s var(--ease);
    }
    nav a app-icon { color: var(--sidebar-muted); transition: color 0.15s var(--ease); }
    nav a.hidden { display: none; }
    nav a:hover { background: var(--sidebar-hover); color: #fff; text-decoration: none; }
    nav a:hover app-icon { color: #fff; }
    nav a.active { background: linear-gradient(90deg, rgba(47, 207, 146, 0.2), rgba(47, 207, 146, 0.06)); color: #fff; font-weight: 600; }
    nav a.active app-icon { color: var(--accent); }
    nav a.active::before { content: ''; position: absolute; left: -14px; top: 9px; bottom: 9px; width: 4px; border-radius: 0 4px 4px 0; background: var(--accent); }
    .badge { margin-left: auto; min-width: 22px; padding: 1px 7px; border-radius: 999px; background: var(--red); color: #fff; font-size: 12px; font-weight: 700; text-align: center; line-height: 1.5; }
    .section { margin: 14px 12px 4px; color: var(--sidebar-muted); font-size: 11.5px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.08em; }
    .foot { margin-top: auto; padding-top: 8px; }

    main { padding: 32px 36px 48px; min-width: 0; max-width: 1480px; width: 100%; margin: 0 auto; animation: fade 0.2s var(--ease); }

    .banner { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 18px; padding: 12px 16px; border-radius: var(--r); background: var(--warn-bg); border: 1px solid var(--warn-line); color: var(--warn-fg); }
    .banner-text { display: flex; align-items: flex-start; gap: 10px; }
    .banner-text app-icon { margin-top: 2px; }
    .banner.visiting { background: var(--primary-soft); border-color: var(--primary-line); color: var(--text); }
    .banner.danger { background: var(--err-bg); border-color: var(--err-line); color: var(--err-fg); }
    .blocked { display: flex; flex-direction: column; align-items: center; text-align: center; padding: 48px 20px; gap: 6px; }
    .blocked-icon { display: grid; place-items: center; width: 64px; height: 64px; border-radius: 20px; color: var(--red); background: var(--err-bg); border: 1px solid var(--err-line); margin-bottom: 8px; }
    .blocked p { max-width: 52ch; color: var(--muted); }
    .sub { margin: 16px 0 10px; font-size: 15px; }
    .problem { display: block; color: var(--red); margin: -6px 0 6px; }

    @media (max-width: 1400px) {
      :host { grid-template-columns: 240px minmax(0, 1fr); }
      main { padding: 28px 26px 44px; }
    }

    @media (max-width: 1100px) {
      main { padding: 26px 22px 40px; }
    }

    @media (max-width: 820px) {
      :host { display: block; min-height: 100vh; }

      .appbar {
        display: flex; align-items: center; gap: 10px; position: fixed; z-index: 30; top: 0; left: 0; right: 0;
        height: calc(var(--appbar-h) + env(safe-area-inset-top)); padding: env(safe-area-inset-top) 12px 0;
        background: var(--sidebar); color: #fff; border-bottom: 1px solid var(--sidebar-line);
      }
      .appbar .title { font-weight: 700; font-size: 16px; }
      .appbar .grow { flex: 1; }
      .menu-btn { color: #fff; border: 1px solid var(--sidebar-line); }
      .menu-btn:hover:not(:disabled) { background: var(--sidebar-hover); color: #fff; }

      .backdrop { display: block; position: fixed; inset: 0; z-index: 40; background: var(--overlay); backdrop-filter: blur(2px); animation: fade 0.15s var(--ease); }
      .side {
        position: fixed; z-index: 50; top: 0; left: 0; bottom: 0; height: auto; width: min(310px, 88vw);
        transform: translateX(-102%); transition: transform 0.22s var(--ease); box-shadow: var(--sh-3);
        padding-top: calc(20px + env(safe-area-inset-top)); padding-bottom: calc(16px + env(safe-area-inset-bottom));
      }
      .side.open { transform: none; }
      .side .foot { display: none; }
      nav a { padding: 12px; }
      nav a.active::before { left: -14px; }

      main {
        padding: 16px 14px;
        padding-top: calc(var(--appbar-h) + env(safe-area-inset-top) + 16px);
        padding-bottom: calc(var(--bottombar-h) + env(safe-area-inset-bottom) + 18px);
      }

      .bottombar {
        display: flex; flex-direction: row; gap: 0; position: fixed; z-index: 30; left: 0; right: 0; bottom: 0;
        height: calc(var(--bottombar-h) + env(safe-area-inset-bottom)); padding-bottom: env(safe-area-inset-bottom);
        background: color-mix(in srgb, var(--sidebar) 94%, transparent); backdrop-filter: blur(10px); border-top: 1px solid var(--sidebar-line);
      }
      .bottombar a, .bottombar button {
        flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 3px; border-radius: 0;
        color: var(--sidebar-muted); font-size: 11px; font-weight: 500; background: transparent; border: 0; padding: 4px 2px; min-width: 0;
      }
      .bottombar a:hover, .bottombar a.active { background: transparent; }
      .bottombar a.active { color: #fff; font-weight: 600; }
      .bottombar a.active::before { display: none; }
      .bottombar .ico { position: relative; display: grid; place-items: center; width: 52px; height: 30px; border-radius: 999px; transition: background 0.18s var(--ease); }
      .bottombar a.active .ico { background: rgba(47, 207, 146, 0.22); color: var(--accent); }
      .bottombar a > span:last-child, .bottombar button > span:last-child { max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
      .dotbadge { position: absolute; top: 3px; right: 14px; width: 9px; height: 9px; border-radius: 50%; background: var(--red); box-shadow: 0 0 0 2px var(--sidebar); }
    }
  `,
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly attention = inject(AttentionService);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  protected readonly brand = BRAND;
  protected readonly planName = planText;

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

  /** O tom da situação do cliente no selo do menu (verde, âmbar ou vermelho). */
  readonly statusTone = computed(() => ({ on: 'ok', warn: 'warn', off: 'err' })[this.tone()] as 'ok' | 'warn' | 'err');

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
    inject(DestroyRef).onDestroy(() => {
      this.notice.dispose();
      this.attention.stop();
    });
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
    // A bolinha dos alertas só faz sentido (e só é permitida) com o painel liberado.
    effect(() => {
      if (this.auth.me() && !this.blocked()) {
        this.attention.start();
      } else {
        this.attention.stop();
      }
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
