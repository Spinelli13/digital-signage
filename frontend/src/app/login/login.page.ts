import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  template: `
    <form class="card box" (ngSubmit)="submit()">
      <img class="logo" src="favicon.svg" alt="Digital Signage" width="56" height="56" />
      <h2>Digital Signage</h2>
      <p class="label">Entre para gerenciar suas telas.</p>

      <div class="field">
        <label for="email">E-mail</label>
        <input id="email" name="email" type="email" autocomplete="username" required
          [ngModel]="email()" (ngModelChange)="email.set($event)" />
      </div>
      <div class="field">
        <label for="password">Senha</label>
        <input id="password" name="password" type="password" autocomplete="current-password" required
          [ngModel]="password()" (ngModelChange)="password.set($event)" />
      </div>
      <button class="primary" type="submit" [disabled]="busy()">{{ busy() ? 'Entrando…' : 'Entrar' }}</button>
    </form>
  `,
  styles: `
    :host { display: flex; min-height: 100vh; align-items: center; justify-content: center; padding: 16px; }
    .box { width: 100%; max-width: 380px; padding: 28px; }
    .box button { width: 100%; margin-top: 6px; }
    .label { margin-bottom: 18px; }
    .logo { display: block; margin-bottom: 14px; border-radius: 13px; }
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly email = signal('');
  readonly password = signal('');
  readonly busy = signal(false);

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
