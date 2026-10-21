import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { BRAND } from '../core/brand';
import { TPipe } from '../core/i18n';
import { IconComponent } from '../shared/icon';
import { PreferencesBar } from '../shared/preferences';

@Component({
  selector: 'app-login',
  imports: [FormsModule, TPipe, PreferencesBar, IconComponent],
  template: `
    <div class="prefs-corner"><app-preferences /></div>

    <section class="hero" aria-hidden="false">
      <div class="hero-in">
        <div class="hero-brand"><img class="logo" src="favicon.svg" alt="" width="44" height="44" /><span>{{ brand }}</span></div>
        <h2>{{ 'Todas as suas telas, em um só lugar.' | t }}</h2>
        <ul>
          <li><span class="chip"><app-icon name="monitor-check" [size]="20" /></span><span>{{ 'Veja quais TVs estão no ar, em tempo real.' | t }}</span></li>
          <li><span class="chip"><app-icon name="calendar-clock" [size]="20" /></span><span>{{ 'Monte playlists e campanhas com agenda.' | t }}</span></li>
          <li><span class="chip"><app-icon name="bell" [size]="20" /></span><span>{{ 'Receba alertas e acompanhe relatórios.' | t }}</span></li>
        </ul>
      </div>
    </section>

    <main class="pane">
      <form class="box" (ngSubmit)="submit()">
        <div class="mobile-brand"><img class="logo" src="favicon.svg" [alt]="brand" width="52" height="52" /></div>
        <h1>{{ 'Entrar' | t }}</h1>
        <p class="lead">{{ 'Entre para gerenciar suas telas.' | t }}</p>

        <div class="field">
          <label for="email">{{ 'E-mail' | t }}</label>
          <div class="input-icon">
            <app-icon name="mail" [size]="18" />
            <input id="email" name="email" type="email" autocomplete="username" required
              [ngModel]="email()" (ngModelChange)="email.set($event)" />
          </div>
        </div>
        <div class="field">
          <label for="password">{{ 'Senha' | t }}</label>
          <div class="input-icon">
            <app-icon name="lock" [size]="18" />
            <input id="password" name="password" [type]="visible() ? 'text' : 'password'" autocomplete="current-password" required
              [ngModel]="password()" (ngModelChange)="password.set($event)" />
            <button type="button" class="reveal" [attr.aria-label]="(visible() ? 'Ocultar senha' : 'Mostrar senha') | t" [attr.aria-pressed]="visible()" (click)="visible.set(!visible())">
              <app-icon [name]="visible() ? 'eye-off' : 'eye'" [size]="18" />
            </button>
          </div>
        </div>
        <button class="primary submit" type="submit" [disabled]="busy()">
          {{ (busy() ? 'Entrando…' : 'Entrar') | t }}
          @if (!busy()) {
            <app-icon name="arrow-right" [size]="18" />
          }
        </button>
      </form>
    </main>
  `,
  styles: `
    :host { display: grid; grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr); min-height: 100vh; position: relative; }
    .prefs-corner { position: absolute; z-index: 5; top: calc(14px + env(safe-area-inset-top)); right: 14px; }

    .hero { position: relative; display: flex; align-items: center; padding: 56px; overflow: hidden; background: var(--grad-hero); color: #fff; }
    .hero::before, .hero::after { content: ''; position: absolute; border-radius: 50%; background: radial-gradient(circle, rgba(63, 224, 162, 0.28), transparent 65%); }
    .hero::before { width: 560px; height: 560px; right: -180px; top: -160px; }
    .hero::after { width: 420px; height: 420px; left: -120px; bottom: -160px; opacity: 0.7; }
    .hero-in { position: relative; max-width: 520px; display: flex; flex-direction: column; gap: 28px; }
    .hero-brand { display: flex; align-items: center; gap: 14px; font-size: 22px; font-weight: 700; letter-spacing: -0.01em; }
    .logo { display: block; border-radius: 12px; box-shadow: 0 6px 20px rgba(0, 0, 0, 0.35); }
    .hero h2 { margin: 0; font-size: 38px; line-height: 1.12; letter-spacing: -0.03em; font-weight: 750; }
    .hero ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 14px; }
    .hero li { display: flex; align-items: center; gap: 14px; font-size: 16px; color: rgba(255, 255, 255, 0.9); }
    .chip { flex: none; display: grid; place-items: center; width: 40px; height: 40px; border-radius: 12px; background: rgba(255, 255, 255, 0.12); border: 1px solid rgba(255, 255, 255, 0.18); color: var(--accent); }

    .pane { display: grid; place-items: center; padding: 32px 20px; }
    .box { width: 100%; max-width: 400px; }
    .mobile-brand { display: none; }
    h1 { font-size: 30px; }
    .lead { color: var(--muted); margin: 6px 0 26px; }
    .input-icon { position: relative; }
    .input-icon > app-icon { position: absolute; left: 13px; top: 50%; transform: translateY(-50%); color: var(--muted); pointer-events: none; }
    .input-icon input { padding-left: 42px; height: 48px; }
    .reveal { position: absolute; right: 6px; top: 50%; transform: translateY(-50%); display: grid; place-items: center; width: 36px; height: 36px; border: 0; border-radius: var(--r-sm); background: transparent; color: var(--muted); }
    .reveal:hover { background: var(--panel-3); color: var(--text); }
    .reveal[aria-pressed='true'] { color: var(--primary); }
    .submit { width: 100%; height: 48px; margin-top: 8px; font-size: 15.5px; }

    @media (max-width: 900px) {
      :host { grid-template-columns: 1fr; }
      .hero { display: none; }
      .pane { align-content: center; min-height: 100vh; padding-top: 72px; }
      .mobile-brand { display: block; margin-bottom: 18px; }
      .mobile-brand .logo { box-shadow: var(--sh-2); }
      h1 { font-size: 28px; }
    }
  `,
})
export class LoginPage {
  protected readonly brand = BRAND;
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly email = signal('');
  readonly password = signal('');
  readonly busy = signal(false);
  readonly visible = signal(false);

  async submit(): Promise<void> {
    this.busy.set(true);
    try {
      await this.auth.login(this.email().trim(), this.password());
      await this.router.navigate(['/dashboard']);
    } catch {
      // A mensagem (ex.: e-mail ou senha inválidos) é exibida pelo interceptor de erros.
    } finally {
      this.busy.set(false);
    }
  }
}
