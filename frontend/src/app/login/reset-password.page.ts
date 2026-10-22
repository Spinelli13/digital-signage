import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { TPipe, tr } from '../core/i18n';
import { IconComponent } from '../shared/icon';
import { PreferencesBar } from '../shared/preferences';
import { WordmarkComponent } from '../shared/wordmark';

const MIN_PASSWORD = 8;

/**
 * A página do link que chega por e-mail (recuperação de senha e boas-vindas): escolhe a senha nova. O link é de uso único; se já
 * foi usado ou venceu, o servidor responde e a pessoa pede um novo.
 */
@Component({
  selector: 'app-reset-password',
  imports: [FormsModule, RouterLink, TPipe, PreferencesBar, IconComponent, WordmarkComponent],
  template: `
    <div class="prefs-corner"><app-preferences /></div>
    <main class="page">
      <div class="head"><img src="favicon.svg" alt="" width="44" height="44" /><app-wordmark /></div>

      @if (!done()) {
        <form class="card" (ngSubmit)="submit()">
          <h1>{{ 'Escolha a sua senha' | t }}</h1>
          <p class="lead">{{ 'Use pelo menos 8 caracteres. Depois é só entrar com ela.' | t }}</p>
          <div class="field">
            <label for="reset-new">{{ 'Nova senha' | t }}</label>
            <input id="reset-new" name="new" type="password" autocomplete="new-password" required [attr.minlength]="min"
              [ngModel]="password()" (ngModelChange)="password.set($event)" />
          </div>
          <div class="field">
            <label for="reset-confirm">{{ 'Repita a nova senha' | t }}</label>
            <input id="reset-confirm" name="confirm" type="password" autocomplete="new-password" required
              [ngModel]="confirm()" (ngModelChange)="confirm.set($event)" />
          </div>
          @if (problem(); as text) {
            <small class="problem">{{ text }}</small>
          }
          <button class="primary full" type="submit" [disabled]="busy() || !ready()">
            {{ (busy() ? 'Salvando…' : 'Salvar a senha') | t }}
          </button>
          <p class="again"><a routerLink="/esqueci-a-senha">{{ 'O link não funciona? Peça um novo' | t }}</a></p>
        </form>
      } @else {
        <section class="card finished" role="status" data-testid="reset-done">
          <app-icon name="circle-check" [size]="34" />
          <h1>{{ 'Senha salva' | t }}</h1>
          <p>{{ 'Pronto! Já pode entrar com a senha nova.' | t }}</p>
          <a class="primary btn-link" routerLink="/login">{{ 'Entrar' | t }}<app-icon name="arrow-right" [size]="18" /></a>
        </section>
      }
    </main>
  `,
  styles: `
    :host { display: block; min-height: 100vh; background: var(--bg); color: var(--text); position: relative; }
    .prefs-corner { position: absolute; top: calc(14px + env(safe-area-inset-top)); right: 14px; }
    .page { max-width: 440px; margin: 0 auto; padding: calc(72px + env(safe-area-inset-top)) 16px 40px; display: flex; flex-direction: column; gap: 20px; }
    .head { display: flex; align-items: center; gap: 12px; font-size: 28px; }
    .head img { border-radius: 12px; }
    h1 { margin: 0 0 6px; font-size: 26px; }
    .lead { color: var(--muted); margin: 0 0 22px; }
    .problem { display: block; margin-bottom: 12px; }
    .full { width: 100%; height: 46px; justify-content: center; }
    .again { margin: 18px 0 0; text-align: center; font-size: 14px; }
    .again a { color: var(--muted); text-decoration: underline; text-underline-offset: 3px; }
    .again a:hover { color: var(--link); }
    .finished { text-align: center; }
    .finished app-icon { color: var(--ok-fg); }
    .btn-link { display: inline-flex; align-items: center; gap: 8px; text-decoration: none; padding: 12px 22px; margin-top: 8px; }
  `,
})
export class ResetPasswordPage {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  protected readonly min = MIN_PASSWORD;
  readonly password = signal('');
  readonly confirm = signal('');
  readonly busy = signal(false);
  readonly done = signal(false);

  readonly problem = computed(() => {
    const next = this.password();
    if (next.length > 0 && next.length < MIN_PASSWORD) {
      return tr('A nova senha precisa ter pelo menos 8 caracteres.');
    }
    if (this.confirm().length > 0 && this.confirm() !== next) {
      return tr('As senhas não conferem.');
    }
    return null;
  });

  /** Senha com o tamanho mínimo e repetida igual: só então o botão libera. */
  readonly ready = computed(() => this.password().length >= MIN_PASSWORD && this.confirm() === this.password());

  submit(): void {
    const token = this.route.snapshot.paramMap.get('token') ?? '';
    if (!this.ready() || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.api.resetPassword(token, this.password()).subscribe({
      next: () => {
        this.busy.set(false);
        this.password.set('');
        this.confirm.set('');
        this.done.set(true);
      },
      // "Este link não vale mais…" e os outros motivos aparecem no aviso de erro do painel.
      error: () => this.busy.set(false),
    });
  }
}
