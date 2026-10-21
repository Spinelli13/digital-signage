import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';

import { ConfigService } from './config.service';
import { SILENT_ERRORS } from './http-errors';

/**
 * unknown: ainda não perguntamos · ok: respondeu rápido · waking: não respondeu (o servidor gratuito dorme sem uso e
 * leva até 1 minuto para acordar) · ready: acabou de acordar (aviso curto) · down: passou muito tempo sem resposta.
 */
export type ServerState = 'unknown' | 'ok' | 'waking' | 'ready' | 'down';

/** Mais que isso sem resposta e já avisamos que o servidor está acordando. */
export const SLOW_MS = 3_500;
export const RETRY_MS = 4_000;
export const READY_MS = 5_000;
/** Depois de quantas tentativas (RETRY_MS cada) desistimos de dizer "acordando" e dizemos "sem resposta". */
export const MAX_ATTEMPTS = 45;

/**
 * Descobre se o servidor está dormindo (plano gratuito) e avisa com um alerta na tela, em vez de deixar a pessoa
 * olhando um botão que não responde. Pergunta ao /health ao abrir o painel e de novo sempre que alguma chamada
 * falha por falta de conexão; enquanto não houver resposta, repete a pergunta a cada poucos segundos.
 */
@Injectable({ providedIn: 'root' })
export class ServerStatusService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ConfigService);

  readonly state = signal<ServerState>('unknown');
  /** Quantas vezes já perguntamos desde que o servidor parou de responder. */
  readonly attempts = signal(0);

  private slowTimer: ReturnType<typeof setTimeout> | undefined;
  private retryTimer: ReturnType<typeof setTimeout> | undefined;
  private readyTimer: ReturnType<typeof setTimeout> | undefined;
  private inFlight = false;
  private wasDown = false;

  /** Pergunta se o servidor está de pé. Várias chamadas seguidas viram uma só. */
  probe(): void {
    if (this.inFlight) {
      return;
    }
    this.inFlight = true;
    clearTimeout(this.retryTimer);
    // Se a resposta demorar, avisa já: não espera o pedido terminar (pode levar um minuto).
    this.slowTimer = setTimeout(() => this.markDown(), SLOW_MS);

    const context = new HttpContext().set(SILENT_ERRORS, true);
    this.http.get(`${this.config.apiBaseUrl}/api/v1/health`, { context }).subscribe({
      next: () => this.onUp(),
      error: () => this.onFailure(),
    });
  }

  /** Uma chamada qualquer falhou por falta de conexão (status 0, 502, 503 ou 504): vale conferir o servidor. */
  suspect(): void {
    if (this.state() !== 'waking' && this.state() !== 'down') {
      this.probe();
    }
  }

  private onUp(): void {
    this.settle();
    this.attempts.set(0);
    if (this.wasDown) {
      this.wasDown = false;
      this.state.set('ready');
      clearTimeout(this.readyTimer);
      this.readyTimer = setTimeout(() => {
        if (this.state() === 'ready') {
          this.state.set('ok');
        }
      }, READY_MS);
    } else {
      this.state.set('ok');
    }
  }

  private onFailure(): void {
    this.settle();
    this.markDown();
    this.attempts.update((value) => value + 1);
    if (this.attempts() >= MAX_ATTEMPTS) {
      this.state.set('down');
    }
    // Continua perguntando: um servidor que acorda volta sozinho, e o alerta some na hora.
    this.retryTimer = setTimeout(() => this.probe(), RETRY_MS);
  }

  private markDown(): void {
    this.wasDown = true;
    if (this.state() !== 'down') {
      this.state.set('waking');
    }
  }

  private settle(): void {
    clearTimeout(this.slowTimer);
    this.inFlight = false;
  }

  /** Para os relógios (testes e fechamento do app). */
  stop(): void {
    clearTimeout(this.slowTimer);
    clearTimeout(this.retryTimer);
    clearTimeout(this.readyTimer);
    this.inFlight = false;
  }
}
