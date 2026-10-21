import { Component } from '@angular/core';

import { BRAND } from '../core/brand';

/**
 * O logotipo tipográfico do Vyzzu: o nome em Inter extra-negrito, com o "zz" (a marca registrada do nome) na cor de destaque.
 * Herda a cor do texto ao redor; o "zz" usa `--wm-accent` (quem fica sobre fundo escuro define `--wm-accent: var(--lavender)`).
 * O símbolo (o V com o pixel de luz) é o `favicon.svg`.
 */
@Component({
  selector: 'app-wordmark',
  template: `<span class="wm">{{ head }}<i>{{ accent }}</i>{{ tail }}</span>`,
  styles: `
    :host { display: inline-block; }
    .wm { font-weight: 800; letter-spacing: -0.035em; }
    .wm i { font-style: normal; color: var(--wm-accent, var(--link)); }
  `,
})
export class WordmarkComponent {
  /** "Vy" + "zz" + "u": o miolo (o "zz") leva a cor de destaque. */
  protected readonly head = BRAND.slice(0, 2);
  protected readonly accent = BRAND.slice(2, 4);
  protected readonly tail = BRAND.slice(4);
}
