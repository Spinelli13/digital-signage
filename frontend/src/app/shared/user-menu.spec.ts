import { TestBed } from '@angular/core/testing';

import { AuthService } from '../core/auth.service';
import { currentLang, resetLangForTests } from '../core/i18n';
import { THEME_KEY, ThemeService } from '../core/theme';
import { meFixture } from '../testing/fixtures';
import { UserMenuComponent } from './user-menu';

describe('UserMenuComponent', () => {
  beforeEach(() => {
    localStorage.clear();
    window.matchMedia = vi.fn().mockImplementation(() => ({ matches: false, addEventListener: () => undefined })) as unknown as typeof window.matchMedia;
    resetLangForTests('pt');
  });

  afterEach(() => {
    localStorage.clear();
    resetLangForTests('pt');
  });

  function render(variant: 'side' | 'bar' = 'side') {
    const fixture = TestBed.createComponent(UserMenuComponent);
    TestBed.inject(AuthService).me.set({ ...meFixture('ADMIN'), name: 'Ana Maria Souza' });
    fixture.componentRef.setInput('variant', variant);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const open = () => {
      (element.querySelector('[data-testid="user-button"]') as HTMLButtonElement).click();
      fixture.detectChanges();
    };
    return { fixture, element, open };
  }

  it('mostra as iniciais, o nome e o papel no menu lateral', () => {
    const { element } = render();

    expect(element.querySelector('.avatar')?.textContent?.trim()).toBe('AS');
    expect(element.querySelector('.who b')?.textContent).toBe('Ana Maria Souza');
    expect(element.querySelector('.who small')?.textContent).toContain('Administrador');
    element.remove();
  });

  it('na barra do celular mostra só a bolinha com as iniciais', () => {
    const { element } = render('bar');

    expect(element.querySelector('.avatar')?.textContent?.trim()).toBe('AS');
    expect(element.querySelector('.who')).toBeNull();
    element.remove();
  });

  it('abre o painel com o e-mail, o tema, o idioma, Minha conta e Sair', () => {
    const { element, open } = render();
    expect(element.querySelector('[role="menu"]')).toBeNull();

    open();

    const panel = element.querySelector('[role="menu"]') as HTMLElement;
    expect(panel.textContent).toContain('Tema');
    expect(panel.textContent).toContain('Idioma');
    expect(panel.querySelector('[data-testid="account-item"]')).not.toBeNull();
    expect(panel.querySelector('[data-testid="logout-item"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="user-button"]')?.getAttribute('aria-expanded')).toBe('true');
    element.remove();
  });

  it('troca o tema e o idioma sem fechar o painel', () => {
    const { fixture, element, open } = render();
    open();

    const themeButtons = element.querySelectorAll('.group')[0].querySelectorAll('.seg button');
    expect(themeButtons).toHaveLength(3);
    (themeButtons[2] as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(TestBed.inject(ThemeService).mode()).toBe('dark');
    expect(localStorage.getItem(THEME_KEY)).toBe('dark');
    expect(themeButtons[2].getAttribute('aria-pressed')).toBe('true');

    const languageButtons = element.querySelectorAll('.group')[1].querySelectorAll('.seg button');
    (languageButtons[1] as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(currentLang()).toBe('en');
    expect(element.querySelector('[role="menu"]')).not.toBeNull();
    element.remove();
  });

  it('"Minha conta" e "Sair" avisam quem usa o menu e fecham o painel', () => {
    const { fixture, element, open } = render();
    const account = vi.fn();
    const logout = vi.fn();
    fixture.componentInstance.account.subscribe(account);
    fixture.componentInstance.logout.subscribe(logout);

    open();
    (element.querySelector('[data-testid="account-item"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(account).toHaveBeenCalledTimes(1);
    expect(element.querySelector('[role="menu"]')).toBeNull();

    open();
    (element.querySelector('[data-testid="logout-item"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(logout).toHaveBeenCalledTimes(1);
    element.remove();
  });

  it('fecha ao clicar fora e com Esc', () => {
    const { fixture, element, open } = render();

    open();
    document.body.click();
    fixture.detectChanges();
    expect(element.querySelector('[role="menu"]')).toBeNull();

    open();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(element.querySelector('[role="menu"]')).toBeNull();
    element.remove();
  });
});
