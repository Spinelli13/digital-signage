import { Component, ElementRef, effect, inject, input, viewChild } from '@angular/core';

import { WidgetKind, WidgetSettings } from '../core/models';
import { WidgetTemplates } from '../core/widgets';

/**
 * A pré-visualização de um widget: a mesma página que a TV desenha, dentro de um quadro isolado (sem acesso ao painel).
 * O quadro tem a proporção da tela (horizontal ou em pé) e o widget se adapta a ele, como se adapta a uma zona da TV.
 */
@Component({
  selector: 'app-widget-preview',
  template: `<div class="frame" [class.portrait]="portrait()"><iframe #frame sandbox="allow-scripts" [attr.title]="title()"></iframe></div>`,
  styles: `
    :host { display: block; }
    .frame { position: relative; width: 100%; aspect-ratio: 16 / 9; border-radius: var(--r); overflow: hidden; border: 1px solid var(--line); background: #071a15; box-shadow: var(--sh-1); }
    .frame.portrait { aspect-ratio: 9 / 16; max-width: 260px; margin: 0 auto; }
    iframe { position: absolute; inset: 0; width: 100%; height: 100%; border: 0; background: transparent; }
  `,
})
export class WidgetPreviewComponent {
  private readonly templates = inject(WidgetTemplates);
  private readonly frame = viewChild.required<ElementRef<HTMLIFrameElement>>('frame');
  private version = 0;

  readonly kind = input.required<WidgetKind>();
  readonly settings = input.required<WidgetSettings>();
  /** Os dados que o servidor entregaria (clima, notícias, cotações); nulos nos demais ou se não deu para buscar. */
  readonly data = input<Record<string, unknown> | null>(null);
  readonly portrait = input(false);
  /** Um instante para simular (ex.: o horário do happy hour); vazio = agora. */
  readonly nowMs = input<number | null>(null);
  readonly title = input('Pré-visualização do widget');

  constructor() {
    effect(() => {
      const init = { kind: this.kind(), settings: this.settings(), data: this.data(), nowMs: this.nowMs() ?? Date.now() };
      const frame = this.frame().nativeElement;
      const mine = ++this.version;
      this.templates.page(init).then(
        (html) => {
          // Só a última versão vale: uma resposta atrasada não pode desfazer uma mais nova.
          if (mine === this.version) {
            frame.srcdoc = html;
          }
        },
        () => undefined,
      );
    });
  }
}
