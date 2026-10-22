import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { TPipe } from '../core/i18n';
import { IconComponent } from '../shared/icon';
import { PreferencesBar } from '../shared/preferences';
import { WordmarkComponent } from '../shared/wordmark';

/**
 * "Esqueci a senha": a pessoa digita o e-mail e a resposta é sempre a mesma, tenha ou não conta (o servidor não revela quem é
 * cliente). O link para escolher a senha nova chega por e-mail e vale 1 hora.
 */
@Component({
  selector: 'app-forgot-password',
  imports: [FormsModule, RouterLink, TPipe, PreferencesBar, IconComponent, WordmarkComponent],
  template: `
    <div class="prefs-corner"><app-preferences /></div>
    <main class="wrap">
      <div class="brand"><img src="favicon.svg" alt="" width="44" height="44" /><app-wordmark /></div>

      @if (!sent()) {
        <form class="card" (ngSubmit)="submit()">
          <h1>{{ 'Esqueci a senha' | t }}</h1>
          <p class="lead">{{ 'Digite o e-mail da sua conta e enviaremos um link para escolher uma senha nova.' | t }}</p>
          <div class="field">
            <label for="forgot-email">{{ 'E-mail' | t }}</label>
            <input id="forgot-email" name="email" type="email" autocomplete="username" required
              [ngModel]="email()" (ngModelChange)="email.set($event)" />
          </div>
          <button class="primary wide" type="submit" [disabled]="busy() || !email().trim()">
            {{ (busy() ? 'Enviando…' : 'Enviar o link') | t }}
          </button>
          <p class="back"><a routerLink="/login"><app-icon name="arrow-left" [size]="15" />{{ 'Voltar para entrar' | t }}</a></p>
        </form>
      } @else {
        <section class="card done" role="status" data-testid="forgot-sent">
          <app-icon name="mail" [size]="34" />
          <h1>{{ 'Confira o seu e-mail' | t }}</h1>
          <p>{{ 'Se existir uma conta com este e-mail, enviamos um link para escolher a senha nova. O link vale por 1 hora; olhe também a caixa de spam.' | t }}</p>
          <p class="back"><a routerLink="/login"><app-icon name="arrow-left" [size]="15" />{{ 'Voltar para entrar' | t }}</a></p>
        </section>
      }
    </main>
  `,
  styles: `
    :host { display: block; min-height: 100vh; background: var(--bg); color: var(--text); position: relative; }
    .prefs-corner { position: absolute; top: calc(14px + env(safe-area-inset-top)); right: 14px; }
    .wrap { max-width: 440px; margin: 0 auto; padding: calc(72px + env(safe-area-inset-top)) 16px 40px; display: flex; flex-direction: column; gap: 20px; }
    .brand { display: flex; align-items: center; gap: 12px; font-size: 28px; }
    .brand img { border-radius: 12px; }
    .card h1 { margin: 0 0 6px; font-size: 26px; }
    .lead { color: var(--muted); margin: 0 0 22px; }
    .wide { width: 100%; height: 46px; justify-content: center; }
    .back { margin: 18px 0 0; text-align: center; font-size: 14px; }
    .back a { display: inline-flex; align-items: center; gap: 6px; color: var(--muted); text-decoration: underline; text-underline-offset: 3px; }
    .back a:hover { color: var(--link); }
    .done { text-align: center; }
    .done app-icon { color: var(--primary); }
  `,
})
export class ForgotPasswordPage {
  private readonly api = inject(ApiService);

  readonly email = signal('');
  readonly busy = signal(false);
  readonly sent = signal(false);

  submit(): void {
    const address = this.email().trim();
    if (!address || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.api.forgotPassword(address).subscribe({
      next: () => {
        this.busy.set(false);
        this.sent.set(true);
      },
      // O motivo (ex.: servidor fora do ar) aparece no aviso de erro do painel; a pessoa pode tentar de novo.
      error: () => this.busy.set(false),
    });
  }
}
