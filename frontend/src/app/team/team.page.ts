import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Role, TeamMember } from '../core/models';
import { Notice } from '../core/notice';
import { formatLimit, ROLE_HELP, ROLE_LABELS, TEAM_ROLES } from '../core/saas';

@Component({
  selector: 'app-team',
  imports: [FormsModule],
  template: `
    <div class="head">
      <div>
        <h1>Equipe</h1>
        <p>
          Quem acessa o painel deste cliente e o que cada pessoa pode fazer.
          @if (auth.me(); as me) {
            Usuários ativos: <b>{{ me.tenant.usage.users }} de {{ limit() }}</b>.
          }
        </p>
      </div>
      <button class="primary" (click)="openNew()">+ Novo usuário</button>
    </div>

    @if (notice.text(); as text) {
      <div class="notice" role="status">{{ text }}</div>
    }

    <div class="card">
      @if (items().length === 0) {
        <div class="empty">{{ loaded() ? 'Nenhum usuário.' : 'Carregando…' }}</div>
      } @else {
        <table>
          <tr><th>Nome</th><th>Papel</th><th>Situação</th><th></th></tr>
          @for (member of items(); track member.id) {
            <tr>
              <td>
                <b>{{ member.name }}</b>
                @if (isMe(member)) {
                  <small> (você)</small>
                }
                <br /><small>{{ member.email }}</small>
              </td>
              <td>{{ roleLabel(member.role) }}</td>
              <td>
                <span class="dot" [class]="member.active ? 'on' : 'off'"></span>{{ member.active ? 'Ativo' : 'Desativado' }}
              </td>
              <td class="row-actions">
                <button class="secondary small" (click)="openEdit(member)">Editar</button>
                <button class="danger small" [disabled]="isMe(member)" (click)="remove(member)">Excluir</button>
              </td>
            </tr>
          }
        </table>
      }
    </div>

    <div class="card roles">
      <h3>O que cada papel pode</h3>
      @for (role of roles; track role) {
        <p><b>{{ roleLabel(role) }}</b> — {{ roleHelp(role) }}</p>
      }
    </div>

    @if (modal() !== null) {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="submit()">
          <h2>{{ modal() === 'new' ? 'Novo usuário' : 'Editar usuário' }}</h2>
          <div class="field">
            <label for="tm-name">Nome</label>
            <input id="tm-name" name="name" required [ngModel]="name()" (ngModelChange)="name.set($event)" />
          </div>
          <div class="field">
            <label for="tm-email">E-mail (é o login)</label>
            <input id="tm-email" name="email" type="email" required [disabled]="modal() === 'edit'"
              [ngModel]="email()" (ngModelChange)="email.set($event)" />
          </div>
          <div class="field">
            <label for="tm-role">Papel</label>
            <select id="tm-role" name="role" [ngModel]="role()" (ngModelChange)="role.set($event)">
              @for (option of roles; track option) {
                <option [value]="option">{{ roleLabel(option) }}</option>
              }
            </select>
            <small>{{ roleHelp(role()) }}</small>
          </div>
          <div class="field">
            <label for="tm-pass">{{ modal() === 'new' ? 'Senha inicial' : 'Nova senha (deixe em branco para manter)' }}</label>
            <input id="tm-pass" name="password" type="password" autocomplete="new-password" minlength="8"
              [ngModel]="password()" (ngModelChange)="password.set($event)" />
            <small>Pelo menos 8 caracteres. A pessoa pode trocá-la em "Minha conta".</small>
          </div>
          @if (modal() === 'edit') {
            <div class="field">
              <label class="check">
                <input type="checkbox" name="active" [disabled]="editingSelf()"
                  [ngModel]="active()" (ngModelChange)="active.set($event)" />
                Pode entrar no painel
              </label>
            </div>
          }
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">Cancelar</button>
            <button type="submit" class="primary" [disabled]="busy() || !canSubmit()">Salvar</button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .row-actions { white-space: nowrap; text-align: right; }
    .row-actions button { margin-left: 6px; }
    .roles { margin-top: 18px; }
    .roles p { margin: 6px 0; }
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

  readonly limit = computed(() => formatLimit(this.auth.me()?.tenant.plan.maxUsers ?? null));

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

  roleLabel(role: Role): string {
    return ROLE_LABELS[role];
  }

  roleHelp(role: Role): string {
    return ROLE_HELP[role];
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
        this.notice.show(editing ? `Usuário "${saved.name}" salvo.` : `Usuário "${saved.name}" adicionado.`);
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
    if (!confirm(`Excluir o usuário "${member.name}" (${member.email})? Ele perde o acesso ao painel na hora.`)) {
      return;
    }
    this.api.deleteTeamMember(member.id).subscribe({
      next: () => {
        this.notice.show(`Usuário "${member.name}" excluído.`);
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
