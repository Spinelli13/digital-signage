import { TestBed } from '@angular/core/testing';

import { currentLang, resetLangForTests } from '../core/i18n';
import { THEME_KEY, ThemeService } from '../core/theme';
import { PreferencesBar } from './preferences';

describe('PreferencesBar', () => {
  beforeEach(() => {
    localStorage.clear();
    window.matchMedia = vi.fn().mockImplementation(() => ({ matches: false, addEventListener: () => undefined })) as unknown as typeof window.matchMedia;
    resetLangForTests('pt');
  });

  afterEach(() => {
    localStorage.clear();
    resetLangForTests('pt');
  });

  function render(full = false) {
    const fixture = TestBed.createComponent(PreferencesBar);
    fixture.componentRef.setInput('full', full);
    fixture.detectChanges();
    return fixture;
  }

  it('o botão do tema gira entre automático, claro e escuro', () => {
    const fixture = render();
    const theme = TestBed.inject(ThemeService);
    const button = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="theme-button"]') as HTMLButtonElement;
    expect(theme.mode()).toBe('system');

    button.click();
    expect(theme.mode()).toBe('light');
    button.click();
    expect(theme.mode()).toBe('dark');
    button.click();
    expect(theme.mode()).toBe('system');
    expect(localStorage.getItem(THEME_KEY)).toBe('system');
  });

  it('diz o tema atual no botão (leitor de tela) e no idioma escolhido', () => {
    const fixture = render();
    const button = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="theme-button"]') as HTMLButtonElement;
    expect(button.getAttribute('aria-label')).toBe('Tema: Automático');

    TestBed.inject(ThemeService).set('dark');
    fixture.detectChanges();
    expect(button.getAttribute('aria-label')).toBe('Tema: Escuro');

    fixture.componentInstance.setLang('en');
    fixture.detectChanges();
    expect(button.getAttribute('aria-label')).toBe('Theme: Dark');
  });

  it('o seletor troca o idioma na hora', () => {
    const fixture = render();

    fixture.componentInstance.setLang('en');

    expect(currentLang()).toBe('en');
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="lang-select"]')?.getAttribute('aria-label')).toBe('Language');
  });

  it('a versão completa mostra os rótulos e escolhe tema e idioma', () => {
    const fixture = render(true);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('Tema');
    expect(text).toContain('Idioma');
    expect(text).toContain('Português');
    expect(text).toContain('English');

    fixture.componentInstance.setTheme('dark');
    expect(TestBed.inject(ThemeService).mode()).toBe('dark');
  });
});
