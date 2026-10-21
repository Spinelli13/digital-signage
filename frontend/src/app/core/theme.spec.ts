import { TestBed } from '@angular/core/testing';

import { THEME_KEY, ThemeService } from './theme';

type Listener = (event: { matches: boolean }) => void;

/** O jsdom não tem matchMedia: este é um "modo escuro do aparelho" de mentira que também avisa quando muda. */
function mockSystem(dark: boolean): { change(next: boolean): void } {
  const listeners: Listener[] = [];
  window.matchMedia = vi.fn().mockImplementation(() => ({
    matches: dark,
    addEventListener: (_: string, listener: Listener) => listeners.push(listener),
  })) as unknown as typeof window.matchMedia;
  return { change: (next) => listeners.forEach((listener) => listener({ matches: next })) };
}

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    const meta = document.createElement('meta');
    meta.name = 'theme-color';
    meta.content = '#000';
    document.head.appendChild(meta);
  });

  afterEach(() => {
    localStorage.clear();
    document.querySelectorAll('meta[name="theme-color"]').forEach((meta) => meta.remove());
  });

  const root = () => document.documentElement.getAttribute('data-theme');

  it('começa seguindo o aparelho (automático) quando nada foi escolhido', () => {
    mockSystem(false);

    const theme = TestBed.inject(ThemeService);

    expect(theme.mode()).toBe('system');
    expect(theme.applied()).toBe('light');
    expect(root()).toBe('light');
  });

  it('automático: o aparelho no modo escuro deixa o painel escuro', () => {
    mockSystem(true);

    const theme = TestBed.inject(ThemeService);

    expect(theme.applied()).toBe('dark');
    expect(root()).toBe('dark');
  });

  it('automático acompanha o aparelho quando ele muda com o painel aberto', () => {
    const system = mockSystem(false);
    const theme = TestBed.inject(ThemeService);

    system.change(true);

    expect(theme.applied()).toBe('dark');
    expect(root()).toBe('dark');
  });

  it('a escolha da pessoa vale mais que o aparelho e fica guardada', () => {
    mockSystem(true);
    const theme = TestBed.inject(ThemeService);

    theme.set('light');

    expect(theme.applied()).toBe('light');
    expect(root()).toBe('light');
    expect(localStorage.getItem(THEME_KEY)).toBe('light');
  });

  it('lembra a escolha na próxima abertura', () => {
    mockSystem(false);
    localStorage.setItem(THEME_KEY, 'dark');

    const theme = TestBed.inject(ThemeService);

    expect(theme.mode()).toBe('dark');
    expect(root()).toBe('dark');
  });

  it('ignora um valor guardado que não existe', () => {
    mockSystem(false);
    localStorage.setItem(THEME_KEY, 'azul');

    expect(TestBed.inject(ThemeService).mode()).toBe('system');
  });

  it('alterna entre claro e escuro a partir do que está na tela', () => {
    mockSystem(false);
    const theme = TestBed.inject(ThemeService);

    theme.toggle();
    expect(theme.applied()).toBe('dark');

    theme.toggle();
    expect(theme.applied()).toBe('light');
  });

  it('troca a cor da barra do navegador do celular junto com o tema', () => {
    mockSystem(false);
    const theme = TestBed.inject(ThemeService);
    const color = () => document.querySelector('meta[name="theme-color"]')?.getAttribute('content');
    expect(color()).toBe('#1a6a49');

    theme.set('dark');

    expect(color()).toBe('#050807');
  });
});
