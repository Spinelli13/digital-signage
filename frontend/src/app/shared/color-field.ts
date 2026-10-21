import { Component, input, output, signal } from '@angular/core';

import { normalizeHex } from '../core/color';
import { TPipe } from '../core/i18n';
import { IconComponent } from './icon';

/**
 * Um campo de cor: o seletor do navegador e o código #rrggbb lado a lado. Em branco = "a cor do tema" (o `fallback` só é
 * mostrado no seletor). Emite sempre uma cor válida em minúsculas, ou null para voltar à do tema.
 */
@Component({
  selector: 'app-color-field',
  imports: [TPipe, IconComponent],
  template: `
    <div class="cf">
      <div class="head">
        <label [for]="fieldId()">{{ label() }}</label>
        @if (value()) {
          <button type="button" class="reset" [title]="'Usar a cor do tema' | t" [attr.aria-label]="'Usar a cor do tema' | t" (click)="valueChange.emit(null)">
            <app-icon name="rotate-ccw" [size]="14" />
          </button>
        }
      </div>
      <div class="row">
        <input class="swatch" type="color" [id]="fieldId()" [value]="value() ?? fallback()" (input)="pick($event)" />
        <input
          class="hex"
          type="text"
          [id]="fieldId() + '-hex'"
          [value]="draft() ?? value() ?? ''"
          [placeholder]="fallback()"
          maxlength="7"
          spellcheck="false"
          autocomplete="off"
          [attr.aria-label]="label()"
          [attr.aria-invalid]="invalid()"
          (input)="type($event)"
          (blur)="draft.set(null)"
        />
      </div>
      @if (invalid()) {
        <p class="bad" role="alert">{{ 'Use o formato #RRGGBB' | t }}</p>
      }
    </div>
  `,
  styles: `
    :host { display: block; min-width: 0; }
    .head { display: flex; align-items: center; justify-content: space-between; gap: 6px; min-height: 26px; margin-bottom: 4px; }
    .head label { margin: 0; flex: 1 1 auto; width: auto; min-width: 0; }
    .row { display: flex; align-items: center; gap: 8px; }
    .swatch { flex: none; width: 42px; height: 42px; padding: 3px; border-radius: var(--r-sm); border: 1px solid var(--line-strong); background: var(--panel); cursor: pointer; }
    .hex { flex: 1; min-width: 0; padding-inline: 10px; font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 13.5px; text-transform: lowercase; }
    .reset { flex: none; width: 26px; height: 26px; padding: 0; display: inline-flex; align-items: center; justify-content: center; border-radius: var(--r-sm);
      border: 1px solid var(--line); background: var(--panel-2); color: var(--text-2); }
    .reset:hover { background: var(--panel-3); }
    .bad { margin: 6px 0 0; font-size: 13px; color: var(--err-fg); }
  `,
})
export class ColorFieldComponent {
  /** O nome do campo, já traduzido (quem usa o componente passa `'Fundo' | t`). */
  readonly label = input.required<string>();
  readonly fieldId = input.required<string>();
  /** A cor escolhida (#rrggbb) ou null = a do tema. */
  readonly value = input<string | null>(null);
  /** A cor que o tema usa (só para mostrar no seletor e como dica). */
  readonly fallback = input('#000000');
  readonly valueChange = output<string | null>();

  /** O que está sendo digitado, enquanto ainda não é uma cor válida. */
  readonly draft = signal<string | null>(null);
  readonly invalid = signal(false);

  pick(event: Event): void {
    this.draft.set(null);
    this.invalid.set(false);
    this.valueChange.emit(normalizeHex((event.target as HTMLInputElement).value));
  }

  type(event: Event): void {
    const text = (event.target as HTMLInputElement).value;
    if (!text.trim()) {
      this.draft.set(null);
      this.invalid.set(false);
      this.valueChange.emit(null);
      return;
    }
    const color = normalizeHex(text);
    if (color) {
      this.draft.set(null);
      this.invalid.set(false);
      this.valueChange.emit(color);
    } else {
      this.draft.set(text);
      this.invalid.set(text.replace('#', '').length >= 6);
    }
  }
}
