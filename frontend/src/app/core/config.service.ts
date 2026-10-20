import { Injectable } from '@angular/core';

const DEFAULT_API = 'http://localhost:8080';

/**
 * Configuração em tempo de execução, lida de /config.json. Assim a URL da API muda no deploy
 * (editando um arquivo estático) sem precisar recompilar o painel.
 */
@Injectable({ providedIn: 'root' })
export class ConfigService {
  apiBaseUrl = DEFAULT_API;

  async load(): Promise<void> {
    try {
      const response = await fetch('config.json', { cache: 'no-store' });
      if (response.ok) {
        const config = (await response.json()) as { apiBaseUrl?: string };
        if (config.apiBaseUrl) {
          this.apiBaseUrl = config.apiBaseUrl.replace(/\/+$/, '');
        }
      }
    } catch {
      // sem config.json: usa o padrão de desenvolvimento
    }
  }
}
