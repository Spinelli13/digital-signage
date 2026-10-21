import { Component, ElementRef, HostListener, computed, inject, input, signal } from '@angular/core';

import { TPipe } from '../core/i18n';
import { IconComponent } from './icon';
import { IconName } from './icons';

export type Tone = 'ok' | 'warn' | 'err' | 'info' | 'brand' | 'neutral';

/** Os tons antigos das tabelas (on/warn/off) com o tom e o ícone dos selos novos: a cor nunca vai sozinha. */
export const LEGACY_TONE: Record<'on' | 'warn' | 'off', { tone: Tone; icon: IconName }> = {
  on: { tone: 'ok', icon: 'circle-check' },
  warn: { tone: 'warn', icon: 'clock' },
  off: { tone: 'err', icon: 'circle-x' },
};

/** Estado em ícone + texto (nunca só cor): <app-pill tone="ok" icon="monitor-check">{{ 'Online' | t }}</app-pill>. */
@Component({
  selector: 'app-pill',
  imports: [IconComponent],
  host: { class: 'pill', '[class]': 'tone()' },
  template: `@if (icon(); as name) {<app-icon [name]="name" [size]="14" [stroke]="2.3" />}<span class="txt"><ng-content /></span>`,
  styles: `
    :host { display: inline-flex; align-items: center; gap: 6px; height: 26px; padding: 0 10px 0 8px; border-radius: 999px; white-space: nowrap;
      font-size: 12.5px; font-weight: 600; line-height: 1; border: 1px solid var(--line); background: var(--panel-2); color: var(--text-2); }
    .txt { min-width: 0; overflow: hidden; text-overflow: ellipsis; }
    :host(.ok) { background: var(--ok-bg); color: var(--ok-fg); border-color: var(--ok-line); }
    :host(.warn) { background: var(--warn-bg); color: var(--warn-fg); border-color: var(--warn-line); }
    :host(.err) { background: var(--err-bg); color: var(--err-fg); border-color: var(--err-line); }
    :host(.info) { background: var(--info-bg); color: var(--info-fg); border-color: var(--info-line); }
    :host(.brand) { background: var(--primary-soft); color: var(--primary); border-color: var(--primary-line); }
  `,
})
export class PillComponent {
  readonly tone = input<Tone>('neutral');
  readonly icon = input<IconName | null>(null);
}

/** Cabeçalho de página: ícone + título + explicação à esquerda, ações à direita (o conteúdo projetado). */
@Component({
  selector: 'app-page-header',
  imports: [IconComponent],
  template: `
    <header class="ph">
      <div class="ph-text">
        @if (icon(); as name) {
          <span class="ph-icon"><app-icon [name]="name" [size]="22" /></span>
        }
        <div class="ph-copy">
          <h1>{{ title() }}</h1>
          @if (subtitle()) {
            <p>{{ subtitle() }}</p>
          }
        </div>
      </div>
      <div class="ph-actions"><ng-content /></div>
    </header>
  `,
  styles: `
    .ph { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px 20px; margin-bottom: 24px; flex-wrap: wrap; }
    .ph-text { display: flex; align-items: flex-start; gap: 14px; flex: 1 1 320px; min-width: 0; }
    .ph-icon {
      flex: none; display: grid; place-items: center; width: 46px; height: 46px; border-radius: 14px; color: var(--primary);
      background: var(--primary-soft); border: 1px solid var(--primary-line);
    }
    :host-context(:root[data-theme='dark']) .ph-icon { color: var(--link); }
    .ph-copy { min-width: 0; }
    .ph-copy p { color: var(--muted); margin-top: 4px; max-width: 70ch; }
    .ph-actions { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
    .ph-actions:empty { display: none; }
    @media (max-width: 720px) {
      .ph { margin-bottom: 16px; }
      .ph-icon { width: 40px; height: 40px; border-radius: 12px; }
      .ph-actions { width: 100%; }
      .ph-actions > * { flex: 1 1 auto; }
    }
  `,
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly subtitle = input('');
  readonly icon = input<IconName | null>(null);
}

/** Tela vazia que ensina: o que aparecerá aqui, por que importa e o próximo passo (o botão projetado). */
@Component({
  selector: 'app-empty-state',
  imports: [IconComponent],
  template: `
    <div class="es" [class.compact]="compact()">
      <span class="es-icon"><app-icon [name]="icon()" [size]="compact() ? 22 : 28" [stroke]="1.8" /></span>
      <h3>{{ title() }}</h3>
      @if (text()) {
        <p>{{ text() }}</p>
      }
      <div class="es-actions"><ng-content /></div>
    </div>
  `,
  styles: `
    .es { display: flex; flex-direction: column; align-items: center; text-align: center; padding: 44px 20px; gap: 6px; }
    .es.compact { padding: 26px 16px; }
    .es-icon {
      display: grid; place-items: center; width: 64px; height: 64px; border-radius: 20px; margin-bottom: 8px; color: var(--primary);
      background: radial-gradient(circle at 30% 20%, var(--primary-soft), var(--panel-3)); border: 1px solid var(--primary-line);
    }
    .compact .es-icon { width: 48px; height: 48px; border-radius: 15px; }
    :host-context(:root[data-theme='dark']) .es-icon { color: var(--link); }
    h3 { margin: 0; font-size: 17px; }
    p { max-width: 48ch; color: var(--muted); margin: 0; }
    .es-actions { display: flex; gap: 10px; flex-wrap: wrap; justify-content: center; margin-top: 12px; }
    .es-actions:empty { display: none; }
  `,
})
export class EmptyStateComponent {
  readonly icon = input.required<IconName>();
  readonly title = input.required<string>();
  readonly text = input('');
  readonly compact = input(false);
}

/** Carregando: linhas cinzas que brilham no lugar do conteúdo (o texto fica só para o leitor de tela). */
@Component({
  selector: 'app-skeleton',
  imports: [TPipe],
  template: `
    <div class="sk" role="status" aria-busy="true">
      <span class="sr-only">{{ 'Carregando…' | t }}</span>
      @for (row of rowList(); track row) {
        <div class="skeleton" [style.height.px]="height()" [style.width.%]="row % 3 === 2 ? 72 : 100"></div>
      }
    </div>
  `,
  styles: `
    .sk { display: grid; gap: 12px; padding: 6px 0; }
  `,
})
export class SkeletonComponent {
  readonly rows = input(4);
  readonly height = input(44);
  protected readonly rowList = computed(() => Array.from({ length: this.rows() }, (_, i) => i));
}

/**
 * Menu "mais ações" (três pontinhos): guarda as ações menos usadas de uma linha para a tela ficar limpa.
 *   <app-menu [label]="'Mais ações' | t"><button class="menu-item" (click)="…">…</button></app-menu>
 * Fecha ao clicar fora, em qualquer item ou com Esc.
 */
@Component({
  selector: 'app-menu',
  imports: [IconComponent],
  template: `
    <div class="wrap">
      <button type="button" class="icon-btn bordered trigger" [class.open]="open()" aria-haspopup="menu" [attr.aria-expanded]="open()" [attr.aria-label]="label()" (click)="toggle($event)">
        <app-icon [name]="icon()" [size]="18" />
      </button>
      @if (open()) {
        <div class="panel" role="menu" [class.left]="align() === 'left'" (click)="close()">
          <ng-content />
        </div>
      }
    </div>
  `,
  styles: `
    .wrap { position: relative; display: inline-block; }
    .trigger.open { background: var(--panel-3); }
    .panel {
      position: absolute; z-index: 45; top: calc(100% + 6px); right: 0; min-width: 210px; padding: 6px; background: var(--panel);
      border: 1px solid var(--line-strong); border-radius: var(--r); box-shadow: var(--sh-3); animation: rise 0.16s var(--ease);
    }
    .panel.left { right: auto; left: 0; }
    @media (max-width: 720px) {
      .panel { min-width: 230px; }
    }
  `,
})
export class MenuComponent {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly label = input.required<string>();
  readonly icon = input<IconName>('ellipsis-vertical');
  readonly align = input<'left' | 'right'>('right');
  readonly open = signal(false);

  toggle(event: Event): void {
    event.stopPropagation();
    this.open.update((value) => !value);
  }

  close(): void {
    this.open.set(false);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.close();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.close();
  }
}

/** Atalho para quem só precisa dos nomes ao escrever o pipe de tradução em componentes de ação. */
export const UI_IMPORTS = [IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent, MenuComponent, TPipe];
