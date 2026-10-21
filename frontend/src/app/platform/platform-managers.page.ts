import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { TPipe, tr } from '../core/i18n';
import { Manager } from '../core/models';
import { Notice } from '../core/notice';
import { IconComponent } from '../shared/icon';
import { EmptyStateComponent, MenuComponent, PageHeaderComponent, PillComponent, SkeletonComponent } from '../shared/ui';

/** A senha que um gestor define para outra pessoa é provisória e pode ser curta: a pessoa é obrigada a trocá-la ao entrar. */
const TEMPORARY_MIN = 6;

/** Os gestores da plataforma: quem opera clientes, planos, cupons e faturas. Cada um entra com o próprio e-mail e senha. */
@Component({
  selector: 'app-platform-managers',
  imports: [FormsModule, TPipe, IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent, MenuComponent],
  template: `
    <app-page-header [title]="'Gestores' | t" [subtitle]="'Quem opera a plataforma: clientes, planos, cupons e faturas. Cada gestor entra com o próprio e-mail e senha.' | t" icon="key-round">
      <button class="primary" (click)="openNew()"><app-icon name="plus" />{{ 'Novo gestor' | t }}</button>
    </app-page-header>

    @if (notice.text(); as text) {
      <div class="notice" role="status"><app-icon name="circle-check" [size]="18" /><span>{{ text }}</span></div>
    }

    <div class="card">
      @if (items().length === 0) {
        @if (loaded()) {
          <app-empty-state icon="key-round" [title]="'Nenhum gestor.' | t" />
        } @else {
          <app-skeleton [rows]="3" />
        }
      } @else {
        <table class="stack">
          <tr><th>{{ 'Nome' | t }}</th><th>{{ 'Situação' | t }}</th><th></th></tr>
          @for (manager of items(); track manager.id) {
            <tr>
              <td class="first">
                <span class="who">
                  <span class="avatar" aria-hidden="true">{{ initials(manager.name) }}</span>
                  <span class="who-text">
                    <b>{{ manager.name }}@if (isMe(manager)) {<small class="me"> {{ '(você)' | t }}</small>}</b>
                    <small>{{ manager.email }}</small>
                  </span>
                </span>
              </td>
              <td [attr.data-label]="'Situação' | t">
                <app-pill [tone]="manager.active ? 'ok' : 'neutral'" [icon]="manager.active ? 'circle-check' : 'circle-pause'">{{ (manager.active ? 'Ativo' : 'Desativado') | t }}</app-pill>
                @if (manager.mustChangePassword) {
                  <app-pill tone="warn" icon="key-round">{{ 'Vai trocar a senha ao entrar' | t }}</app-pill>
                }
              </td>
              <td class="row-actions">
                <button class="secondary small" (click)="openEdit(manager)"><app-icon name="pencil" [size]="15" />{{ 'Editar' | t }}</button>
                <app-menu [label]="'Mais ações' | t">
                  <button type="button" class="menu-item danger" [disabled]="isMe(manager)" (click)="remove(manager)"><app-icon name="trash" />{{ 'Excluir' | t }}</button>
                </app-menu>
              </td>
            </tr>
          }
        </table>
      }
    </div>

    @if (modal() !== null) {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="submit()">
          <h2>{{ (modal() === 'new' ? 'Novo gestor' : 'Editar gestor') | t }}</h2>
          <div class="field">
            <label for="mg-name">{{ 'Nome' | t }}</label>
            <input id="mg-name" name="name" required [ngModel]="name()" (ngModelChange)="name.set($event)" />
          </div>
          <div class="field">
            <label for="mg-email">{{ 'E-mail (é o login)' | t }}</label>
            <input id="mg-email" name="email" type="email" required [disabled]="modal() === 'edit'"
              [ngModel]="email()" (ngModelChange)="email.set($event)" />
          </div>
          <div class="field">
            <label for="mg-pass">{{ (modal() === 'new' ? 'Senha inicial' : 'Nova senha (deixe em branco para manter)') | t }}</label>
            <input id="mg-pass" name="password" type="password" autocomplete="new-password" minlength="6"
              [ngModel]="password()" (ngModelChange)="password.set($event)" />
            <small>{{ (editingSelf() ? 'Pelo menos 8 caracteres.' : 'Senha provisória, com pelo menos 6 caracteres: a pessoa será obrigada a trocá-la ao entrar.') | t }}</small>
          </div>
          @if (modal() === 'edit') {
            <div class="field">
              <label class="check">
                <input type="checkbox" name="active" [disabled]="editingSelf()" [ngModel]="active()" (ngModelChange)="active.set($event)" />
                {{ 'Pode entrar no painel' | t }}
              </label>
            </div>
          }
          <p class="muted">{{ 'O gestor tem acesso a tudo da plataforma: clientes, planos, cupons, faturas e configurações.' | t }}</p>
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">{{ 'Cancelar' | t }}</button>
            <button type="submit" class="primary" [disabled]="busy() || !canSubmit()">{{ 'Salvar' | t }}</button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .row-actions { white-space: nowrap; text-align: right; }
    .row-actions > * { margin-left: 6px; vertical-align: middle; }
    .who { display: flex; align-items: center; gap: 12px; min-width: 0; }
    .avatar { flex: none; display: grid; place-items: center; width: 38px; height: 38px; border-radius: 50%; color: #fff; font-weight: 700; font-size: 13.5px; background: var(--grad-primary); }
    .who-text { display: flex; flex-direction: column; min-width: 0; }
    .who-text small { color: var(--muted); overflow: hidden; text-overflow: ellipsis; }
    .me { color: var(--muted); font-weight: 400; }
  `,
})
export class PlatformManagersPage implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly items = signal<Manager[]>([]);
  readonly loaded = signal(false);
  readonly notice = new Notice();

  readonly modal = signal<'new' | 'edit' | null>(null);
  readonly busy = signal(false);
  readonly name = signal('');
  readonly email = signal('');
  readonly password = signal('');
  readonly active = signal(true);
  editing: Manager | null = null;

  readonly canSubmit = computed(() => {
    if (!this.name().trim()) {
      return false;
    }
    if (this.modal() === 'new') {
      return this.email().trim().length > 0 && this.password().length >= TEMPORARY_MIN;
    }
    return this.password().length === 0 || this.password().length >= (this.editingSelf() ? 8 : TEMPORARY_MIN);
  });

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.notice.dispose());
    this.load();
  }

  initials(name: string): string {
    return name.trim().split(/\s+/).slice(0, 2).map((part) => part.charAt(0)).join('').toUpperCase() || '?';
  }

  isMe(manager: Manager): boolean {
    return this.auth.me()?.id === manager.id;
  }

  /** Quem edita a si mesmo não pode se desativar (ficaria sem acesso). */
  editingSelf(): boolean {
    return this.editing !== null && this.isMe(this.editing);
  }

  openNew(): void {
    this.editing = null;
    this.name.set('');
    this.email.set('');
    this.password.set('');
    this.active.set(true);
    this.modal.set('new');
  }

  openEdit(manager: Manager): void {
    this.editing = manager;
    this.name.set(manager.name);
    this.email.set(manager.email);
    this.password.set('');
    this.active.set(manager.active);
    this.modal.set('edit');
  }

  closeModal(): void {
    this.modal.set(null);
  }

  submit(): void {
    if (!this.canSubmit()) {
      return;
    }
    this.busy.set(true);
    const editing = this.editing;
    const request = editing
      ? this.api.updateManager(editing.id, {
          name: this.name().trim(),
          active: this.active(),
          ...(this.password() ? { password: this.password() } : {}),
        })
      : this.api.createManager({ name: this.name().trim(), email: this.email().trim(), password: this.password() });
    request.subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.closeModal();
        this.notice.show(editing ? tr('Gestor "{name}" salvo.', { name: saved.name }) : tr('Gestor "{name}" adicionado.', { name: saved.name }));
        this.load();
      },
      error: () => this.busy.set(false),
    });
  }

  remove(manager: Manager): void {
    if (!confirm(tr('Excluir o gestor "{name}" ({email})? Ele perde o acesso ao painel na hora.', { name: manager.name, email: manager.email }))) {
      return;
    }
    this.api.deleteManager(manager.id).subscribe({
      next: () => {
        this.notice.show(tr('Gestor "{name}" excluído.', { name: manager.name }));
        this.load();
      },
      error: () => undefined,
    });
  }

  private load(): void {
    this.api.managers().subscribe({
      next: (list) => {
        this.items.set(list);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }
}
