import { Component, computed, inject, input } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { I18nService, Lang, TPipe } from '../core/i18n';
import { ThemeMode, ThemeService } from '../core/theme';
import { IconComponent } from './icon';
import { IconName } from './icons';

const THEME_ICONS: Record<ThemeMode, IconName> = { system: 'monitor', light: 'sun', dark: 'moon' };
export const THEME_LABELS: Record<ThemeMode, string> = { system: 'Automático', light: 'Claro', dark: 'Escuro' };
const NEXT: Record<ThemeMode, ThemeMode> = { system: 'light', light: 'dark', dark: 'system' };

/**
 * Tema (claro, escuro ou automático) e idioma. Compacto (botão + seletor) na barra do topo, no menu e no login;
 * completo (com rótulos) em "Minha conta".
 */
@Component({
  selector: 'app-preferences',
  imports: [FormsModule, TPipe, IconComponent],
  template: `
    @if (full()) {
      <div class="field">
        <label for="pref-theme">{{ 'Tema' | t }}</label>
        <select id="pref-theme" name="theme" [ngModel]="theme.mode()" (ngModelChange)="setTheme($event)">
          <option value="system">{{ 'Automático (segue o aparelho)' | t }}</option>
          <option value="light">{{ 'Claro' | t }}</option>
          <option value="dark">{{ 'Escuro' | t }}</option>
        </select>
      </div>
      <div class="field">
        <label for="pref-lang">{{ 'Idioma' | t }}</label>
        <select id="pref-lang" name="lang" [ngModel]="i18n.lang()" (ngModelChange)="setLang($event)">
          @for (language of i18n.languages; track language.code) {
            <option [value]="language.code">{{ language.label }}</option>
          }
        </select>
      </div>
    } @else {
      <div class="prefs">
        <button
          type="button"
          class="pref"
          data-testid="theme-button"
          [title]="('Tema' | t) + ': ' + (themeLabel() | t)"
          [attr.aria-label]="('Tema' | t) + ': ' + (themeLabel() | t)"
          (click)="cycle()"
        ><app-icon [name]="icon()" [size]="18" /></button>
        <select
          class="pref lang"
          data-testid="lang-select"
          [attr.aria-label]="'Idioma' | t"
          [title]="'Idioma' | t"
          [ngModel]="i18n.lang()"
          (ngModelChange)="setLang($event)"
        >
          @for (language of i18n.languages; track language.code) {
            <option [value]="language.code">{{ language.short }}</option>
          }
        </select>
      </div>
    }
  `,
  styles: `
    :host { display: inline-block; }
    .prefs { display: flex; gap: 6px; align-items: center; }
    .pref {
      width: auto; min-height: 40px; padding: 6px 10px; border-radius: var(--r); font-size: 15px; line-height: 1;
      color: var(--prefs-fg, var(--text)); background: var(--prefs-bg, var(--panel));
      border: 1px solid var(--prefs-line, var(--line));
    }
    button.pref { cursor: pointer; display: inline-grid; place-items: center; min-width: 40px; }
    select.pref { padding-right: 6px; font-size: 13px; font-weight: 600; }
    select.pref option { color: var(--text); background: var(--panel); }
  `,
})
export class PreferencesBar {
  protected readonly theme = inject(ThemeService);
  protected readonly i18n = inject(I18nService);

  /** Versão com rótulos (para "Minha conta"). */
  readonly full = input(false);

  readonly icon = computed(() => THEME_ICONS[this.theme.mode()]);
  readonly themeLabel = computed(() => THEME_LABELS[this.theme.mode()]);

  cycle(): void {
    this.theme.set(NEXT[this.theme.mode()]);
  }

  setTheme(mode: ThemeMode): void {
    this.theme.set(mode);
  }

  setLang(lang: Lang): void {
    this.i18n.set(lang);
  }
}
