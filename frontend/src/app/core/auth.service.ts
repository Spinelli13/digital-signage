import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ConfigService } from './config.service';
import { FeatureCode, Me, Role } from './models';

const TOKEN_KEY = 'signage.token';
const ACTING_KEY = 'signage.acting-tenant';

/** O que o usuário quer fazer: ver (qualquer papel), mexer no conteúdo (Editor+), administrar o cliente (Admin+) ou operar a plataforma. */
export type Capability = 'content' | 'admin' | 'platform';

const RANK: Record<Role, number> = { VIEWER: 0, EDITOR: 1, ADMIN: 2, PLATFORM_ADMIN: 3 };
const NEEDS: Record<Capability, Role> = { content: 'EDITOR', admin: 'ADMIN', platform: 'PLATFORM_ADMIN' };

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ConfigService);

  private readonly tokenSignal = signal<string | null>(readToken());
  readonly isLoggedIn = computed(() => this.tokenSignal() !== null);

  /** Quem está logado (carregado depois do login). Nulo enquanto não chegou. */
  readonly me = signal<Me | null>(null);

  /** Só o operador da plataforma: o cliente em que ele escolheu atuar (nulo = o dele mesmo). */
  readonly actingTenantId = signal<number | null>(readActing());

  /** Atuando num cliente que não é o do próprio usuário? */
  readonly isVisiting = computed(() => {
    const me = this.me();
    return me !== null && me.platformAdmin && this.actingTenantId() !== null;
  });

  get token(): string | null {
    return this.tokenSignal();
  }

  async login(email: string, password: string): Promise<void> {
    const response = await firstValueFrom(
      this.http.post<{ token: string }>(`${this.config.apiBaseUrl}/api/v1/auth/login`, { email, password }),
    );
    writeToken(response.token);
    this.tokenSignal.set(response.token);
    this.me.set(null);
    // Entrou: a conta carrega em seguida; se falhar, o painel tenta de novo (o login já valeu).
    await this.loadMe().catch(() => null);
  }

  /** Quem sou eu, o plano e o uso. O interceptor manda o cliente escolhido pelo operador junto. */
  async loadMe(): Promise<Me | null> {
    if (!this.isLoggedIn()) {
      return null;
    }
    const me = await firstValueFrom(this.http.get<Me>(`${this.config.apiBaseUrl}/api/v1/auth/me`));
    this.me.set(me);
    return me;
  }

  /** Para os guardas de rota: carrega uma vez e reaproveita. */
  async ensureMe(): Promise<Me | null> {
    return this.me() ?? this.loadMe().catch(() => null);
  }

  /**
   * Pode fazer isto? Enquanto a conta não chegou, a resposta é "sim" para tudo menos a plataforma: a tela não
   * esconde nada por engano durante o carregamento (e o servidor é quem realmente decide).
   */
  can(capability: Capability): boolean {
    const me = this.me();
    if (!me) {
      return capability !== 'platform';
    }
    return RANK[me.role] >= RANK[NEEDS[capability]];
  }

  /**
   * O plano do cliente inclui este recurso? O operador da plataforma vê tudo, e enquanto a conta não chegou a resposta é "sim"
   * (nada some por engano; o servidor é quem realmente barra).
   */
  has(feature: FeatureCode): boolean {
    const me = this.me();
    return !me || me.platformAdmin || (me.tenant.plan.features?.includes(feature) ?? true);
  }

  /** O operador entra no cliente indicado (todas as chamadas passam a atuar nele). */
  async enterTenant(tenantId: number): Promise<void> {
    writeActing(tenantId);
    this.actingTenantId.set(tenantId);
    await this.loadMe();
  }

  /** Volta ao cliente do próprio operador. */
  async leaveTenant(): Promise<void> {
    writeActing(null);
    this.actingTenantId.set(null);
    await this.loadMe();
  }

  logout(): void {
    writeToken(null);
    writeActing(null);
    this.tokenSignal.set(null);
    this.me.set(null);
    this.actingTenantId.set(null);
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

/** O cliente em que o operador está atuando só vale nesta aba (sessionStorage): outra aba abre de volta no cliente dele. */
function readActing(): number | null {
  try {
    const value = sessionStorage.getItem(ACTING_KEY);
    return value ? Number(value) : null;
  } catch {
    return null;
  }
}

function writeActing(tenantId: number | null): void {
  try {
    if (tenantId === null) {
      sessionStorage.removeItem(ACTING_KEY);
    } else {
      sessionStorage.setItem(ACTING_KEY, String(tenantId));
    }
  } catch {
    // ignora
  }
}
