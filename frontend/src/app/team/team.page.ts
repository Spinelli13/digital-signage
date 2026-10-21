import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { TPipe, tr } from '../core/i18n';
import { Role, TeamMember } from '../core/models';
import { Notice } from '../core/notice';
import { formatLimit, roleHelp, roleLabel, TEAM_ROLES } from '../core/saas';
import { IconComponent } from '../shared/icon';
import { IconName } from '../shared/icons';
import { EmptyStateComponent, MenuComponent, PageHeaderComponent, PillComponent, SkeletonComponent } from '../shared/ui';

const ROLE_ICONS: Record<Role, IconName> = { VIEWER: 'eye', EDITOR: 'pencil', ADMIN: 'shield-check', PLATFORM_ADMIN: 'key-round' };

@Component({
  selector: 'app-team',
  imports: [FormsModule, TPipe, IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent, MenuComponent],
  template: `
    <app-page-header [title]="'Equipe' | t" [subtitle]="'Quem acessa o painel deste cliente e o que cada pessoa pode fazer.' | t" icon="users">
      @if (auth.me(); as me) {
        <app-pill icon="users">{{ 'Usuários ativos:' | t }} <b class="num">{{ '{used} de {limit}' | t: { used: me.tenant.usage.users, limit: limit() } }}</b></app-pill>
      }
      <button class="primary" (click)="openNew()"><app-icon name="plus" />{{ 'Novo usuário' | t }}</button>
    </app-page-header>

    @if (notice.text(); as text) {
      <div class="notice" role="status"><app-icon name="circle-check" [size]="18" /><span>{{ text }}</span></div>
    }

    <div class="card">
      @if (items().length === 0) {
        @if (loaded()) {
          <app-empty-state icon="users" [title]="'Nenhum usuário.' | t" />
        } @else {
          <app-skeleton [rows]="3" />
        }
      } @else {
        <table class="stack">
          <tr><th>{{ 'Nome' | t }}</th><th>{{ 'Papel' | t }}</th><th>{{ 'Situação' | t }}</th><th></th></tr>
          @for (member of items(); track member.id) {
            <tr>
              <td class="first">
                <span class="who">
                  <span class="avatar" aria-hidden="true">{{ initials(member.name) }}</span>
                  <span class="who-text">
                    <b>{{ member.name }}@if (isMe(member)) {<small class="me"> {{ '(você)' | t }}</small>}</b>
                    <small>{{ member.email }}</small>
                  </span>
                </span>
              </td>
              <td [attr.data-label]="'Papel' | t"><app-pill tone="brand" [icon]="roleIcon(member.role)">{{ roleText(member.role) }}</app-pill></td>
              <td [attr.data-label]="'Situação' | t">
                <app-pill [tone]="member.active ? 'ok' : 'neutral'" [icon]="member.active ? 'circle-check' : 'circle-pause'">{{ (member.active ? 'Ativo' : 'Desativado') | t }}</app-pill>
              </td>
              <td class="row-actions">
                <button class="secondary small" (click)="openEdit(member)"><app-icon name="pencil" [size]="15" />{{ 'Editar' | t }}</button>
                <app-menu [label]="'Mais ações' | t">
                  <button type="button" class="menu-item danger" [disabled]="isMe(member)" (click)="remove(member)"><app-icon name="trash" />{{ 'Excluir' | t }}</button>
                </app-menu>
              </td>
            </tr>
          }
        </table>
      }
    </div>

    <div class="card roles">
      <h3 class="sec"><app-icon name="shield-check" [size]="18" />{{ 'O que cada papel pode' | t }}</h3>
      <div class="role-grid">
        @for (role of roles; track role) {
          <div class="role-card">
            <span class="role-icon"><app-icon [name]="roleIcon(role)" [size]="20" /></span>
            <b>{{ roleText(role) }}</b>
            <span class="muted">{{ helpText(role) }}</span>
          </div>
        }
      </div>
    </div>

    @if (modal() !== null) {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="submit()">
          <h2>{{ (modal() === 'new' ? 'Novo usuário' : 'Editar usuário') | t }}</h2>
          <div class="field">
            <label for="tm-name">{{ 'Nome' | t }}</label>
            <input id="tm-name" name="name" required [ngModel]="name()" (ngModelChange)="name.set($event)" />
          </div>
          <div class="field">
            <label for="tm-email">{{ 'E-mail (é o login)' | t }}</label>
            <input id="tm-email" name="email" type="email" required [disabled]="modal() === 'edit'"
              [ngModel]="email()" (ngModelChange)="email.set($event)" />
          </div>
          <div class="field">
            <label for="tm-role">{{ 'Papel' | t }}</label>
            <select id="tm-role" name="role" [ngModel]="role()" (ngModelChange)="role.set($event)">
              @for (option of roles; track option) {
                <option [value]="option">{{ roleText(option) }}</option>
              }
            </select>
            <small>{{ helpText(role()) }}</small>
          </div>
          <div class="field">
            <label for="tm-pass">{{ (modal() === 'new' ? 'Senha inicial' : 'Nova senha (deixe em branco para manter)') | t }}</label>
            <input id="tm-pass" name="password" type="password" autocomplete="new-password" minlength="8"
              [ngModel]="password()" (ngModelChange)="password.set($event)" />
            <small>{{ 'Pelo menos 8 caracteres. A pessoa pode trocá-la em "Minha conta".' | t }}</small>
          </div>
          @if (modal() === 'edit') {
            <div class="field">
              <label class="check">
                <input type="checkbox" name="active" [disabled]="editingSelf()"
                  [ngModel]="active()" (ngModelChange)="active.set($event)" />
                {{ 'Pode entrar no painel' | t }}
              </label>
            </div>
          }
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
    .roles { margin-top: 18px; }
    .sec { display: flex; align-items: center; gap: 8px; }
    .sec app-icon { color: var(--primary); }
    :host-context(:root[data-theme='dark']) .sec app-icon { color: var(--link); }
    .role-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 12px; margin-top: 12px; }
    .role-card { display: flex; flex-direction: column; gap: 6px; padding: 14px; border: 1px solid var(--line); border-radius: var(--r-lg); background: var(--panel-2); }
    .role-icon { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 12px; color: var(--primary); background: var(--primary-soft); border: 1px solid var(--primary-line); }
    :host-context(:root[data-theme='dark']) .role-icon { color: var(--link); }
  `,
})
export class TeamPage implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly roles = TEAM_ROLES;
  readonly items = signal<TeamMember[]>([]);
  readonly loaded = signal(false);
  readonly notice = new Notice();

  readonly modal = signal<'new' | 'edit' | null>(null);
  readonly busy = signal(false);
  readonly name = signal('');
  readonly email = signal('');
  readonly password = signal('');
  readonly role = signal<Role>('EDITOR');
  readonly active = signal(true);
  editing: TeamMember | null = null;

  /** Quem edita a si mesmo não pode se desativar (ficaria sem acesso). */
  editingSelf(): boolean {
    return this.editing !== null && this.isMe(this.editing);
  }

  readonly limit = computed(() => formatLimit(this.auth.me()?.tenant.effectivePlan?.maxUsers ?? this.auth.me()?.tenant.plan.maxUsers ?? null));

  readonly canSubmit = computed(() => {
    if (!this.name().trim()) {
      return false;
    }
    if (this.modal() === 'new') {
      return this.email().trim().length > 0 && this.password().length >= 8;
    }
    return this.password().length === 0 || this.password().length >= 8;
  });

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.notice.dispose());
    this.load();
  }

  initials(name: string): string {
    return name.trim().split(/\s+/).slice(0, 2).map((part) => part.charAt(0)).join('').toUpperCase() || '?';
  }

  roleIcon(role: Role): IconName {
    return ROLE_ICONS[role];
  }

  roleText(role: Role): string {
    return roleLabel(role);
  }

  helpText(role: Role): string {
    return roleHelp(role);
  }

  isMe(member: TeamMember): boolean {
    return this.auth.me()?.id === member.id;
  }

  openNew(): void {
    this.editing = null;
    this.name.set('');
    this.email.set('');
    this.password.set('');
    this.role.set('EDITOR');
    this.active.set(true);
    this.modal.set('new');
  }

  openEdit(member: TeamMember): void {
    this.editing = member;
    this.name.set(member.name);
    this.email.set(member.email);
    this.password.set('');
    this.role.set(member.role);
    this.active.set(member.active);
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
      ? this.api.updateTeamMember(editing.id, {
          name: this.name().trim(),
          role: this.role(),
          active: this.active(),
          ...(this.password() ? { password: this.password() } : {}),
        })
      : this.api.createTeamMember({
          name: this.name().trim(),
          email: this.email().trim(),
          password: this.password(),
          role: this.role(),
        });
    request.subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.closeModal();
        this.notice.show(editing ? tr('Usuário "{name}" salvo.', { name: saved.name }) : tr('Usuário "{name}" adicionado.', { name: saved.name }));
        this.load();
        if (editing && this.isMe(editing)) {
          void this.auth.loadMe();
        }
      },
      // O aviso (ex.: "limite de usuários do plano") aparece por cima da janela, que continua aberta.
      error: () => this.busy.set(false),
    });
  }

  remove(member: TeamMember): void {
    if (!confirm(tr('Excluir o usuário "{name}" ({email})? Ele perde o acesso ao painel na hora.', { name: member.name, email: member.email }))) {
      return;
    }
    this.api.deleteTeamMember(member.id).subscribe({
      next: () => {
        this.notice.show(tr('Usuário "{name}" excluído.', { name: member.name }));
        this.load();
        void this.auth.loadMe();
      },
      error: () => undefined,
    });
  }

  private load(): void {
    this.api.team().subscribe({
      next: (list) => {
        this.items.set(list);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }
}
