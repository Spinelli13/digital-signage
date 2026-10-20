import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ConfigService } from './config.service';

const TOKEN_KEY = 'signage.token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ConfigService);

  private readonly tokenSignal = signal<string | null>(readToken());
  readonly isLoggedIn = computed(() => this.tokenSignal() !== null);

  get token(): string | null {
    return this.tokenSignal();
  }

  async login(email: string, password: string): Promise<void> {
    const response = await firstValueFrom(
      this.http.post<{ token: string }>(`${this.config.apiBaseUrl}/api/v1/auth/login`, { email, password }),
    );
    writeToken(response.token);
    this.tokenSignal.set(response.token);
  }

  logout(): void {
    writeToken(null);
    this.tokenSignal.set(null);
  }
}

// localStorage pode lançar exceção (navegação privada, dados bloqueados): o painel segue funcionando sem persistir.
function readToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

function writeToken(token: string | null): void {
  try {
    if (token === null) {
      localStorage.removeItem(TOKEN_KEY);
    } else {
      localStorage.setItem(TOKEN_KEY, token);
    }
  } catch {
    // ignora
  }
}
