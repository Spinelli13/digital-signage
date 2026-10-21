import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { resetLangForTests, setLang } from '../core/i18n';
import { ICONS } from './icons';
import { IconComponent } from './icon';
import { EmptyStateComponent, LEGACY_TONE, MenuComponent, PageHeaderComponent, PillComponent, SkeletonComponent } from './ui';

@Component({
  imports: [PillComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent, MenuComponent, IconComponent],
  template: `
    <app-page-header title="Equipe" subtitle="Quem acessa" icon="users"><button class="act">Novo</button></app-page-header>
    <app-pill tone="ok" icon="circle-check">Online</app-pill>
    <app-pill>Neutro</app-pill>
    <app-empty-state icon="search" title="Nada aqui" text="Tente outra busca" [compact]="compact"><button class="cta">Limpar</button></app-empty-state>
    <app-skeleton [rows]="3" [height]="20" />
    <app-menu label="Mais ações"><button class="menu-item first" (click)="clicked = clicked + 1">Um</button><button class="menu-item">Dois</button></app-menu>
    <p id="outside">fora</p>
    <app-icon name="trash" label="Excluir" />
    <app-icon name="trash" />
  `,
})
class Host {
  compact = false;
  clicked = 0;
}

describe('componentes de interface compartilhados', () => {
  let root: HTMLElement;
  let current: ReturnType<typeof fixtureOf>;

  beforeEach(() => {
    resetLangForTests('pt');
    current = TestBed.createComponent(Host);
    current.detectChanges();
    root = current.nativeElement as HTMLElement;
  });

  afterEach(() => resetLangForTests('pt'));

  const fixtureOf = () => TestBed.createComponent(Host);

  describe('app-page-header', () => {
    it('mostra o título, a explicação, o ícone e as ações projetadas', () => {
      const header = root.querySelector('app-page-header') as HTMLElement;
      expect(header.querySelector('h1')?.textContent).toBe('Equipe');
      expect(header.querySelector('p')?.textContent).toBe('Quem acessa');
      expect(header.querySelector('.ph-icon svg')).not.toBeNull();
      expect(header.querySelector('.ph-actions .act')?.textContent).toBe('Novo');
    });
  });

  describe('app-pill', () => {
    it('leva o tom como classe e o ícone antes do texto', () => {
      const [ok, neutral] = Array.from(root.querySelectorAll('app-pill')) as HTMLElement[];
      expect(ok.classList.contains('pill')).toBe(true);
      expect(ok.classList.contains('ok')).toBe(true);
      expect(ok.querySelector('svg')).not.toBeNull();
      expect(ok.textContent?.trim()).toBe('Online');
      expect(neutral.querySelector('svg')).toBeNull();
    });
  });

  describe('app-empty-state', () => {
    it('ensina: ícone, título, explicação e o próximo passo', () => {
      const empty = root.querySelector('app-empty-state') as HTMLElement;
      expect(empty.querySelector('h3')?.textContent).toBe('Nada aqui');
      expect(empty.querySelector('p')?.textContent).toBe('Tente outra busca');
      expect(empty.querySelector('.es-icon svg')).not.toBeNull();
      expect(empty.querySelector('.es-actions .cta')).not.toBeNull();
    });

    it('a versão compacta usa menos espaço', () => {
      const fixture = fixtureOf();
      fixture.componentInstance.compact = true;
      fixture.detectChanges();
      expect((fixture.nativeElement as HTMLElement).querySelector('.es.compact')).not.toBeNull();
    });
  });

  describe('app-skeleton', () => {
    it('desenha as linhas pedidas e avisa o leitor de tela em vez de mostrar texto', () => {
      const skeleton = root.querySelector('app-skeleton') as HTMLElement;
      expect(skeleton.querySelectorAll('.skeleton')).toHaveLength(3);
      expect(skeleton.querySelector('[role="status"]')?.getAttribute('aria-busy')).toBe('true');
      expect(skeleton.querySelector('.sr-only')?.textContent).toBe('Carregando…');
    });

    it('o aviso para o leitor de tela acompanha o idioma', () => {
      setLang('en');
      const fixture = fixtureOf();
      fixture.detectChanges();
      expect((fixture.nativeElement as HTMLElement).querySelector('app-skeleton .sr-only')?.textContent).toBe('Loading…');
    });
  });

  describe('app-menu', () => {
    const trigger = () => root.querySelector('app-menu button[aria-haspopup="menu"]') as HTMLButtonElement;
    const items = () => root.querySelectorAll('app-menu .menu-item');

    it('começa fechado e abre pelo botão, que informa o estado ao leitor de tela', () => {
      expect(items()).toHaveLength(0);
      expect(trigger().getAttribute('aria-label')).toBe('Mais ações');
      expect(trigger().getAttribute('aria-expanded')).toBe('false');

      trigger().click();
      current.detectChanges();
      expect(trigger().getAttribute('aria-expanded')).toBe('true');
    });

    it('abre, mostra as ações e fecha ao escolher uma', () => {
      const fixture = fixtureOf();
      fixture.detectChanges();
      const element = fixture.nativeElement as HTMLElement;
      (element.querySelector('app-menu button[aria-haspopup="menu"]') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(element.querySelectorAll('app-menu .menu-item')).toHaveLength(2);

      (element.querySelector('app-menu .menu-item.first') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(fixture.componentInstance.clicked).toBe(1);
      expect(element.querySelectorAll('app-menu .menu-item')).toHaveLength(0);
    });

    it('fecha ao clicar fora e com a tecla Esc', () => {
      const fixture = fixtureOf();
      fixture.detectChanges();
      const element = fixture.nativeElement as HTMLElement;
      document.body.appendChild(element);
      const open = () => (element.querySelector('app-menu button[aria-haspopup="menu"]') as HTMLButtonElement).click();

      open();
      fixture.detectChanges();
      expect(element.querySelectorAll('app-menu .menu-item')).toHaveLength(2);
      (element.querySelector('#outside') as HTMLElement).click();
      fixture.detectChanges();
      expect(element.querySelectorAll('app-menu .menu-item')).toHaveLength(0);

      open();
      fixture.detectChanges();
      expect(element.querySelectorAll('app-menu .menu-item')).toHaveLength(2);
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      fixture.detectChanges();
      expect(element.querySelectorAll('app-menu .menu-item')).toHaveLength(0);
      element.remove();
    });
  });

  describe('app-icon', () => {
    it('é decorativo por padrão (some para o leitor de tela)', () => {
      const [labelled, plain] = Array.from(root.querySelectorAll('app-icon')) as HTMLElement[];
      expect(plain.getAttribute('aria-hidden')).toBe('true');
      expect(plain.getAttribute('role')).toBeNull();
      expect(plain.querySelector('svg path, svg line, svg polyline, svg circle')).not.toBeNull();
      expect(labelled).not.toBeUndefined();
    });

    it('sozinho num botão usa o rótulo como nome acessível', () => {
      const labelled = Array.from(root.querySelectorAll('app-icon')).find((icon) => icon.getAttribute('aria-label') === 'Excluir') as HTMLElement;
      expect(labelled.getAttribute('role')).toBe('img');
      expect(labelled.getAttribute('aria-hidden')).toBeNull();
    });

    it('todo ícone registrado é um miolo de SVG', () => {
      for (const [name, markup] of Object.entries(ICONS)) {
        expect(markup, name).toMatch(/^<(path|circle|rect|line|polyline|polygon|ellipse|g)\b/);
        expect(markup, name).not.toMatch(/<script|onerror|javascript:/i);
      }
    });
  });

  describe('LEGACY_TONE', () => {
    it('traduz os tons antigos para tom + ícone (a cor nunca vai sozinha)', () => {
      expect(LEGACY_TONE.on).toEqual({ tone: 'ok', icon: 'circle-check' });
      expect(LEGACY_TONE.warn.tone).toBe('warn');
      expect(LEGACY_TONE.off.tone).toBe('err');
      for (const { icon } of Object.values(LEGACY_TONE)) {
        expect(ICONS[icon]).toBeDefined();
      }
    });
  });
});
