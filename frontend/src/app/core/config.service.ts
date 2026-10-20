import { Injectable } from '@angular/core';

const DEFAULT_API = 'http://localhost:8080';
const DEFAULT_DOWNLOADS = 'https://signage-downloads.pages.dev';

/**
 * Configuração em tempo de execução, lida de /config.json. Assim a URL da API muda no deploy
 * (editando um arquivo estático) sem precisar recompilar o painel.
 */
@Injectable({ providedIn: 'root' })
export class ConfigService {
  apiBaseUrl = DEFAULT_API;
  /** Site público com o APK da TV, os manuais e o latest.json. */
  downloadsUrl = DEFAULT_DOWNLOADS;

  async load(): Promise<void> {
    try {
      const response = await fetch('config.json', { cache: 'no-store' });
      if (response.ok) {
        const config = (await response.json()) as { apiBaseUrl?: string; downloadsUrl?: string };
        if (config.apiBaseUrl) {
          this.apiBaseUrl = config.apiBaseUrl.replace(/\/+$/, '');
        }
        if (config.downloadsUrl) {
          this.downloadsUrl = config.downloadsUrl.replace(/\/+$/, '');
        }
      }
    } catch {
      // sem config.json: usa o padrão de desenvolvimento
    }
  }
}
