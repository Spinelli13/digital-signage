import { Component, ElementRef, HostListener, computed, inject, input, output, signal } from '@angular/core';

import { AuthService } from '../core/auth.service';
import { I18nService, Lang, TPipe } from '../core/i18n';
import { roleLabel } from '../core/saas';
import { ThemeMode, ThemeService } from '../core/theme';
import { IconComponent } from './icon';
import { IconName } from './icons';

const THEMES: { mode: ThemeMode; icon: IconName; label: string }[] = [
  { mode: 'system', icon: 'monitor', label: 'Automático' },
  { mode: 'light', icon: 'sun', label: 'Claro' },
  { mode: 'dark', icon: 'moon', label: 'Escuro' },
];

/**
 * Menu da pessoa logada: quem é, o tema, o idioma, "Minha conta" e "Sair". Fica no pé do menu lateral (computador) e no
 * canto da barra do topo (celular): tudo o que é "de quem usa" num só lugar, e fora do caminho de quem trabalha.
 */
@Component({
  selector: 'app-user-menu',
  imports: [IconComponent, TPipe],
  template: `
    <div class="wrap" [class.bar]="variant() === 'bar'">
      <button type="button" class="trigger" [class.open]="open()" aria-haspopup="menu" [attr.aria-expanded]="open()"
        [attr.aria-label]="'Menu da conta' | t" data-testid="user-button" (click)="toggle($event)">
        <span class="avatar" aria-hidden="true">{{ initials() }}</span>
        @if (variant() === 'side') {
          <span class="who">
            <b>{{ name() }}</b>
            <small>{{ role() }}</small>
          </span>
          <app-icon name="chevron-up" [size]="16" />
        }
      </button>
      @if (open()) {
        <div class="panel" role="menu" [class.up]="variant() === 'side'" (click)="$event.stopPropagation()">
          <div class="head">
            <span class="avatar big" aria-hidden="true">{{ initials() }}</span>
            <div class="head-text">
              <b>{{ name() }}</b>
              <small>{{ email() }}</small>
              <span class="role">{{ role() }}</span>
            </div>
          </div>

          <div class="group">
            <span class="group-label"><app-icon name="palette" [size]="14" /> {{ 'Tema' | t }}</span>
            <div class="seg" role="group" [attr.aria-label]="'Tema' | t">
              @for (option of themes; track option.mode) {
                <button type="button" [class.on]="theme.mode() === option.mode" [attr.aria-pressed]="theme.mode() === option.mode" (click)="theme.set(option.mode)">
                  <app-icon [name]="option.icon" [size]="15" /><span>{{ option.label | t }}</span>
                </button>
              }
            </div>
          </div>

          <div class="group">
            <span class="group-label"><app-icon name="languages" [size]="14" /> {{ 'Idioma' | t }}</span>
            <div class="seg" role="group" [attr.aria-label]="'Idioma' | t">
              @for (language of i18n.languages; track language.code) {
                <button type="button" [class.on]="i18n.lang() === language.code" [attr.aria-pressed]="i18n.lang() === language.code" (click)="i18n.set(language.code)">
                  <span>{{ language.label }}</span>
                </button>
              }
            </div>
          </div>

          <button type="button" class="menu-item" data-testid="account-item" (click)="choose('account')"><app-icon name="user" /> {{ 'Minha conta' | t }}</button>
          <button type="button" class="menu-item danger" data-testid="logout-item" (click)="choose('logout')"><app-icon name="log-out" /> {{ 'Sair' | t }}</button>
        </div>
      }
    </div>
  `,
  styles: `
    :host { display: block; }
    .wrap { position: relative; }
    .trigger {
      display: flex; align-items: center; gap: 10px; width: 100%; padding: 8px 10px; border-radius: var(--r); background: transparent;
      border: 1px solid var(--sidebar-line); color: var(--sidebar-text); text-align: left; transition: background 0.15s var(--ease);
    }
    .trigger:hover, .trigger.open { background: var(--sidebar-hover); }
    .bar .trigger { width: auto; padding: 3px; border-radius: 999px; border-color: var(--sidebar-line); }
    .avatar {
      flex: none; display: grid; place-items: center; width: 34px; height: 34px; border-radius: 50%; color: #fff; font-size: 13px; font-weight: 700;
      letter-spacing: 0.02em; background: var(--grad-primary); box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.14);
    }
    .avatar.big { width: 44px; height: 44px; font-size: 15px; }
    .who { display: flex; flex-direction: column; min-width: 0; flex: 1; line-height: 1.25; }
    .who b { color: #fff; font-size: 13.5px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .who small { color: var(--sidebar-muted); font-size: 12px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

    .panel {
      position: absolute; z-index: 60; width: 292px; padding: 10px; background: var(--panel); color: var(--text); border: 1px solid var(--line-strong);
      border-radius: var(--r-lg); box-shadow: var(--sh-3); animation: rise 0.16s var(--ease);
    }
    /* fixo: o menu lateral rola e cortaria o painel, que é mais largo que a coluna */
    .panel.up { position: fixed; bottom: 78px; left: 14px; }
    .bar .panel { top: calc(100% + 8px); right: 0; }
    .head { display: flex; align-items: center; gap: 12px; padding: 6px 6px 12px; border-bottom: 1px solid var(--line); margin-bottom: 8px; }
    .head-text { display: flex; flex-direction: column; min-width: 0; }
    .head-text b { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .head-text small { overflow: hidden; text-overflow: ellipsis; }
    .role { margin-top: 4px; align-self: flex-start; padding: 2px 8px; border-radius: 999px; font-size: 11.5px; font-weight: 600; color: var(--primary); background: var(--primary-soft); border: 1px solid var(--primary-line); }
    :host-context(:root[data-theme='dark']) .role { color: var(--link); }
    .group { padding: 6px 6px 8px; }
    .group-label { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; color: var(--muted); font-size: 12px; font-weight: 600; letter-spacing: 0.04em; text-transform: uppercase; }
    .seg { display: grid; grid-auto-flow: column; grid-auto-columns: 1fr; gap: 4px; padding: 3px; border-radius: var(--r); background: var(--panel-3); }
    .seg button {
      display: flex; align-items: center; justify-content: center; gap: 6px; padding: 7px 6px; border: 0; border-radius: var(--r-sm); background: transparent;
      color: var(--text-2); font-size: 12.5px; font-weight: 600; min-height: 34px;
    }
    .seg button.on { background: var(--panel); color: var(--text); box-shadow: var(--sh-2); }
    @media (max-width: 720px) {
      .seg button { min-height: 42px; }
      .panel { position: fixed; top: auto; right: 8px; bottom: calc(8px + env(safe-area-inset-bottom)); left: 8px; width: auto; }
      .bar .panel { top: auto; }
    }
  `,
})
export class UserMenuComponent {
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  protected readonly i18n = inject(I18nService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  /** side: linha com nome no pé do menu lateral; bar: só a bolinha, na barra do topo do celular. */
  readonly variant = input<'side' | 'bar'>('side');
  readonly account = output<void>();
  readonly logout = output<void>();

  readonly open = signal(false);
  readonly themes = THEMES;

  readonly name = computed(() => this.auth.me()?.name ?? '');
  readonly email = computed(() => this.auth.me()?.email ?? '');
  readonly role = computed(() => {
    const me = this.auth.me();
    return me ? roleLabel(me.role) : '';
  });
  readonly initials = computed(() => {
    const words = this.name().trim().split(/\s+/).filter(Boolean);
    if (words.length === 0) {
      return '?';
    }
    return (words[0][0] + (words.length > 1 ? words[words.length - 1][0] : '')).toUpperCase();
  });

  toggle(event: Event): void {
    event.stopPropagation();
    this.open.update((value) => !value);
  }

  choose(action: 'account' | 'logout'): void {
    this.open.set(false);
    if (action === 'account') {
      this.account.emit();
    } else {
      this.logout.emit();
    }
  }

  setLang(lang: Lang): void {
    this.i18n.set(lang);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.open.set(false);
  }
}
