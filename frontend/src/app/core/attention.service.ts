import { Injectable, inject, signal } from '@angular/core';

import { ApiService } from './api.service';

const REFRESH_MS = 30_000;

/**
 * O que pede atenção agora (hoje: quantos alertas abertos), para o menu mostrar a bolinha vermelha no sino sem que a
 * pessoa precise abrir a tela de Alertas. Consulta o resumo a cada 30 segundos enquanto o menu está na tela.
 */
@Injectable({ providedIn: 'root' })
export class AttentionService {
  private readonly api = inject(ApiService);
  private timer: ReturnType<typeof setInterval> | undefined;

  readonly openAlerts = signal(0);

  start(): void {
    this.refresh();
    this.stop();
    this.timer = setInterval(() => this.refresh(), REFRESH_MS);
  }

  stop(): void {
    clearInterval(this.timer);
    this.timer = undefined;
  }

  refresh(): void {
    this.api.summary(true).subscribe({
      next: (summary) => this.openAlerts.set(summary.openAlerts),
      // Em silêncio: sem o contador o menu só não mostra o número.
      error: () => undefined,
    });
  }
}
