import { signal } from '@angular/core';

/** Mensagem de confirmação (não é erro) que some sozinha, para ações como "pedido enviado às TVs". */
export class Notice {
  readonly text = signal<string | null>(null);
  private timer: ReturnType<typeof setTimeout> | undefined;

  show(message: string, durationMs = 12_000): void {
    this.text.set(message);
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.text.set(null), durationMs);
  }

  dispose(): void {
    clearTimeout(this.timer);
  }
}
