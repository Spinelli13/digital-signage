import { Component, computed, inject, input } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

import { ICONS, IconName } from './icons';

/**
 * Ícone (Lucide, traço de 2 px, embutido no código: sem requisição e sem fonte de ícones).
 *   <app-icon name="monitor" />                      decorativo (some para o leitor de tela)
 *   <app-icon name="trash" label="Excluir" />        sozinho num botão: o `label` é lido
 * O tamanho vem de `size` (px) e a cor é a do texto ao redor (currentColor).
 */
@Component({
  selector: 'app-icon',
  host: {
    class: 'app-icon',
    '[style.width.px]': 'size()',
    '[style.height.px]': 'size()',
    '[attr.role]': 'label() ? "img" : null',
    '[attr.aria-label]': 'label() || null',
    '[attr.aria-hidden]': 'label() ? null : "true"',
  },
  template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" [attr.stroke-width]="stroke()" [innerHTML]="markup()"></svg>`,
  styles: `
    :host { display: inline-flex; flex: none; align-items: center; justify-content: center; line-height: 0; }
    svg { width: 100%; height: 100%; display: block; }
  `,
})
export class IconComponent {
  private readonly sanitizer = inject(DomSanitizer);

  readonly name = input.required<IconName>();
  readonly size = input(18);
  readonly stroke = input(2);
  readonly label = input('');

  /** O miolo do SVG vem só do registro embutido (icons.ts): confiável, nunca de dados do usuário. */
  protected readonly markup = computed<SafeHtml>(() => this.sanitizer.bypassSecurityTrustHtml(ICONS[this.name()] ?? ''));
}
