import { Injectable, computed, signal } from '@angular/core';

/** O que a pessoa escolhe: claro, escuro ou "seguir o aparelho". */
export type ThemeMode = 'light' | 'dark' | 'system';
/** O que de fato é aplicado. */
export type AppliedTheme = 'light' | 'dark';

export const THEME_KEY = 'signage.theme';
const THEME_COLORS: Record<AppliedTheme, string> = { light: '#1a6a49', dark: '#050807' };

/**
 * Tema claro/escuro. A escolha fica no aparelho (localStorage); "sistema" acompanha o modo do celular ou do
 * computador, inclusive quando ele muda com o painel aberto. O index.html aplica o tema antes de o Angular
 * carregar (sem piscar branco no modo escuro); este serviço assume em seguida.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly mode = signal<ThemeMode>(readMode());
  private readonly systemDark = signal(systemPrefersDark());

  readonly applied = computed<AppliedTheme>(() => {
    const mode = this.mode();
    return mode === 'system' ? (this.systemDark() ? 'dark' : 'light') : mode;
  });

  constructor() {
    this.apply();
    try {
      window
        .matchMedia('(prefers-color-scheme: dark)')
        .addEventListener('change', (event) => {
          this.systemDark.set(event.matches);
          this.apply();
        });
    } catch {
      // navegadores sem matchMedia: fica no que foi lido na abertura
    }
  }

  set(mode: ThemeMode): void {
    this.mode.set(mode);
    try {
      localStorage.setItem(THEME_KEY, mode);
    } catch {
      // sem armazenamento (navegação privada): vale só até fechar a aba
    }
    this.apply();
  }

  /** Alterna claro/escuro (a partir do que está sendo mostrado agora). */
  toggle(): void {
    this.set(this.applied() === 'dark' ? 'light' : 'dark');
  }

  private apply(): void {
    const theme = this.applied();
    document.documentElement.setAttribute('data-theme', theme);
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', THEME_COLORS[theme]);
  }
}

function readMode(): ThemeMode {
  try {
    const stored = localStorage.getItem(THEME_KEY);
    return stored === 'light' || stored === 'dark' || stored === 'system' ? stored : 'system';
  } catch {
    return 'system';
  }
}

function systemPrefersDark(): boolean {
  try {
    return window.matchMedia('(prefers-color-scheme: dark)').matches;
  } catch {
    return false;
  }
}
