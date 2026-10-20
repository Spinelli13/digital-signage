import { Injectable, signal } from '@angular/core';

export interface AppError {
  id: number;
  message: string;
}

const MAX_VISIBLE = 5;
/** Depois que o usuário fecha uma mensagem, a mesma não reaparece por este tempo (ex.: atualização automática). */
const COOLDOWN_AFTER_DISMISS_MS = 60_000;

/**
 * Lista central de erros exibidos ao usuário. Cada erro fica na tela até ele clicar em Fechar.
 * Mensagens idênticas não se empilham (um servidor fora do ar não pode encher a tela de avisos).
 */
@Injectable({ providedIn: 'root' })
export class ErrorService {
  readonly errors = signal<AppError[]>([]);

  private nextId = 1;
  private readonly dismissedAt = new Map<string, number>();

  show(message: string): void {
    if (this.errors().some((e) => e.message === message)) {
      return;
    }
    const dismissed = this.dismissedAt.get(message);
    if (dismissed !== undefined && this.now() - dismissed < COOLDOWN_AFTER_DISMISS_MS) {
      return;
    }
    this.errors.update((list) => [...list, { id: this.nextId++, message }].slice(-MAX_VISIBLE));
  }

  dismiss(id: number): void {
    const error = this.errors().find((e) => e.id === id);
    if (error) {
      this.dismissedAt.set(error.message, this.now());
    }
    this.errors.update((list) => list.filter((e) => e.id !== id));
  }

  clear(): void {
    this.errors.set([]);
  }

  // Separado para os testes controlarem o relógio.
  protected now(): number {
    return Date.now();
  }
}
