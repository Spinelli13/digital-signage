import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { FileSaver } from '../core/download';
import { FDatePipe, TPipe, tr } from '../core/i18n';
import { PrivacyRequestEntry, PrivacySearch, PrivacySettings } from '../core/models';
import { Notice } from '../core/notice';
import { roleLabel } from '../core/saas';
import { IconComponent } from '../shared/icon';
import { EmptyStateComponent, PageHeaderComponent, PillComponent } from '../shared/ui';

/**
 * Privacidade e dados pessoais (LGPD) do cliente: o contato que aparece no aviso das páginas públicas, os prazos em que os nomes
 * são apagados e o atendimento dos pedidos de titulares (achar, baixar uma cópia e apagar os dados de uma pessoa). Só o Administrador.
 */
@Component({
  selector: 'app-privacy',
  imports: [FormsModule, RouterLink, TPipe, FDatePipe, IconComponent, PageHeaderComponent, PillComponent, EmptyStateComponent],
  template: `
    <app-page-header [title]="'Privacidade e dados' | t" [subtitle]="'Prazos de guarda, o contato do aviso de privacidade e o atendimento de pedidos de titulares (LGPD).' | t" icon="shield-check">
      <a class="secondary small" routerLink="/privacidade" target="_blank" rel="noopener"><app-icon name="external-link" [size]="15" />{{ 'Política de Privacidade' | t }}</a>
      <a class="secondary small" routerLink="/termos" target="_blank" rel="noopener"><app-icon name="external-link" [size]="15" />{{ 'Termos de Uso' | t }}</a>
    </app-page-header>

    @if (notice.text(); as text) {
      <div class="notice" role="status"><app-icon name="circle-check" [size]="18" /><span>{{ text }}</span></div>
    }

    <form class="card" (ngSubmit)="saveSettings()">
      <h3 class="sec"><app-icon name="mail" [size]="18" />{{ 'Contato do aviso de privacidade' | t }}</h3>
      <p class="label">{{ 'Aparece no aviso das páginas públicas da fila e do cupom, para a pessoa saber com quem falar sobre os dados dela.' | t }}</p>
      <div class="two">
        <div class="field">
          <label for="p-contact-name">{{ 'Nome do responsável' | t }}</label>
          <input id="p-contact-name" name="contactName" maxlength="120" [ngModel]="contactName()" (ngModelChange)="contactName.set($event)" />
        </div>
        <div class="field">
          <label for="p-contact-email">{{ 'E-mail de contato' | t }}</label>
          <input id="p-contact-email" name="contactEmail" type="email" maxlength="255" [ngModel]="contactEmail()" (ngModelChange)="contactEmail.set($event)" />
        </div>
      </div>

      <h3 class="sec sub"><app-icon name="clock" [size]="18" />{{ 'Por quanto tempo guardamos' | t }}</h3>
      <p class="label">{{ 'Passado o prazo, o que é pessoal é apagado sozinho (a limpeza roda de hora em hora). Prazos menores guardam menos dados.' | t }}</p>
      <div class="four">
        <div class="field">
          <label for="p-tickets">{{ 'Senhas da fila (dias)' | t }}</label>
          <input id="p-tickets" name="tickets" type="number" min="1" max="365" [ngModel]="ticketDays()" (ngModelChange)="ticketDays.set(+$event)" />
          <small>{{ 'A senha inteira, com o nome, é apagada.' | t }}</small>
        </div>
        <div class="field">
          <label for="p-coupons">{{ 'Nome nos cupons (dias)' | t }}</label>
          <input id="p-coupons" name="coupons" type="number" min="1" max="365" [ngModel]="couponDays()" (ngModelChange)="couponDays.set(+$event)" />
          <small>{{ 'O cupom fica, sem identificar a pessoa.' | t }}</small>
        </div>
        <div class="field">
          <label for="p-events">{{ 'Histórico das TVs (dias)' | t }}</label>
          <input id="p-events" name="events" type="number" min="7" max="730" [ngModel]="eventsDays()" (ngModelChange)="eventsDays.set(+$event)" />
          <small>{{ 'Eventos e alertas resolvidos.' | t }}</small>
        </div>
        <div class="field">
          <label for="p-plays">{{ 'Exibições detalhadas (dias)' | t }}</label>
          <input id="p-plays" name="plays" type="number" min="7" max="365" [ngModel]="playDays()" (ngModelChange)="playDays.set(+$event)" />
          <small>{{ 'A comprovação de exibição, uma a uma.' | t }}</small>
        </div>
      </div>
      <button type="submit" class="primary" [disabled]="busy()"><app-icon name="check" [size]="16" />{{ 'Salvar' | t }}</button>
    </form>

    <section class="card">
      <h3 class="sec"><app-icon name="search" [size]="18" />{{ 'Pedido de um titular' | t }}</h3>
      <p class="label">{{ 'Quando uma pessoa pedir cópia ou exclusão dos dados dela: busque pelo nome, pelo e-mail ou pelo código do cupom, confira o que foi encontrado e baixe a cópia ou apague. Cada pedido atendido fica registrado abaixo (sem o nome buscado).' | t }}</p>
      <form class="search" (ngSubmit)="search()">
        <div class="field grow">
          <label for="p-term">{{ 'Nome, e-mail ou código do cupom' | t }}</label>
          <input id="p-term" name="term" minlength="3" maxlength="120" autocomplete="off" [ngModel]="term()" (ngModelChange)="term.set($event)" />
        </div>
        <button type="submit" class="secondary" [disabled]="busy() || term().trim().length < 3"><app-icon name="search" [size]="16" />{{ 'Buscar' | t }}</button>
      </form>

      @if (result(); as found) {
        @if (total(found) === 0) {
          <app-empty-state icon="search" [title]="'Nada encontrado com esse texto.' | t" />
        } @else {
          @if (found.truncated) {
            <p class="warn-line" role="status"><app-icon name="triangle-alert" [size]="15" />{{ 'Há mais resultados do que os mostrados: use um texto mais específico.' | t }}</p>
          }

          @if (found.tickets.length > 0) {
            <h4>{{ 'Senhas da fila' | t }} <app-pill tone="brand">{{ found.tickets.length }}</app-pill></h4>
            <table class="stack">
              <tr><th>{{ 'Fila' | t }}</th><th>{{ 'Senha da fila' | t }}</th><th>{{ 'Dia' | t }}</th><th>{{ 'Nome' | t }}</th><th>{{ 'Concordou com o aviso em' | t }}</th></tr>
              @for (ticket of found.tickets; track ticket.id) {
                <tr>
                  <td class="first">{{ ticket.queue }}</td>
                  <td [attr.data-label]="'Senha da fila' | t">{{ ticket.label }}</td>
                  <td [attr.data-label]="'Dia' | t">{{ ticket.day | fdate }}</td>
                  <td [attr.data-label]="'Nome' | t"><b>{{ ticket.name }}</b></td>
                  <td [attr.data-label]="'Concordou com o aviso em' | t">{{ ticket.consentAt ? (ticket.consentAt | fdate: 'datetime') : ('Informado no balcão' | t) }}</td>
                </tr>
              }
            </table>
          }

          @if (found.coupons.length > 0) {
            <h4>{{ 'Cupons' | t }} <app-pill tone="brand">{{ found.coupons.length }}</app-pill></h4>
            <table class="stack">
              <tr><th>{{ 'Promoção' | t }}</th><th>{{ 'Código' | t }}</th><th>{{ 'Nome' | t }}</th><th>{{ 'Pego em' | t }}</th><th>{{ 'Concordou com o aviso em' | t }}</th></tr>
              @for (coupon of found.coupons; track coupon.id) {
                <tr>
                  <td class="first">{{ coupon.promotion }}</td>
                  <td [attr.data-label]="'Código' | t"><span class="code">{{ coupon.code }}</span></td>
                  <td [attr.data-label]="'Nome' | t">{{ coupon.name || ('(sem nome)' | t) }}</td>
                  <td [attr.data-label]="'Pego em' | t">{{ coupon.claimedAt | fdate: 'datetime' }}</td>
                  <td [attr.data-label]="'Concordou com o aviso em' | t">{{ coupon.consentAt ? (coupon.consentAt | fdate: 'datetime') : '-' }}</td>
                </tr>
              }
            </table>
          }

          @if (found.users.length > 0) {
            <h4>{{ 'Equipe do painel' | t }} <app-pill tone="brand">{{ found.users.length }}</app-pill></h4>
            <table class="stack">
              <tr><th>{{ 'Nome' | t }}</th><th>{{ 'E-mail' | t }}</th><th>{{ 'Papel' | t }}</th><th>{{ 'Criado em' | t }}</th></tr>
              @for (user of found.users; track user.id) {
                <tr>
                  <td class="first"><b>{{ user.name }}</b></td>
                  <td [attr.data-label]="'E-mail' | t">{{ user.email }}</td>
                  <td [attr.data-label]="'Papel' | t">{{ roleText(user.role) }}</td>
                  <td [attr.data-label]="'Criado em' | t">{{ user.createdAt | fdate }}</td>
                </tr>
              }
            </table>
            <p class="label">{{ 'Para excluir um usuário do painel, use a tela Equipe: aqui ele só entra na cópia dos dados.' | t }} <a routerLink="/equipe">{{ 'Abrir Equipe' | t }}</a></p>
          }

          <div class="field note">
            <label for="p-note">{{ 'Observação do pedido (opcional, ex.: o protocolo)' | t }}</label>
            <input id="p-note" name="note" maxlength="200" [ngModel]="note()" (ngModelChange)="note.set($event)" />
          </div>
          <div class="actions">
            <button type="button" class="secondary" [disabled]="busy()" (click)="exportData()"><app-icon name="download" [size]="16" />{{ 'Baixar cópia dos dados (JSON)' | t }}</button>
            <button type="button" class="danger" [disabled]="busy() || found.tickets.length + found.coupons.length === 0" (click)="erase()"><app-icon name="trash" [size]="16" />{{ 'Apagar dados pessoais encontrados' | t }}</button>
          </div>
          <p class="label">{{ 'Ao apagar, o nome, o aparelho e a concordância somem das senhas e dos cupons; eles continuam existindo, sem identificar ninguém, para os números do atendimento.' | t }}</p>
        }
      }
    </section>

    <section class="card">
      <h3 class="sec"><app-icon name="file-text" [size]="18" />{{ 'Pedidos atendidos' | t }}</h3>
      @if (requests().length === 0) {
        <p class="label">{{ 'Nenhum pedido registrado ainda.' | t }}</p>
      } @else {
        <table class="stack">
          <tr><th>{{ 'Quando' | t }}</th><th>{{ 'Tipo' | t }}</th><th>{{ 'Encontrado' | t }}</th><th>{{ 'Observação' | t }}</th><th>{{ 'Quem atendeu' | t }}</th></tr>
          @for (entry of requests(); track entry.id) {
            <tr>
              <td class="first">{{ entry.performedAt | fdate: 'datetime' }}</td>
              <td [attr.data-label]="'Tipo' | t"><app-pill [tone]="entry.kind === 'ERASE' ? 'warn' : 'neutral'" [icon]="entry.kind === 'ERASE' ? 'trash' : 'download'">{{ (entry.kind === 'ERASE' ? 'Exclusão' : 'Cópia') | t }}</app-pill></td>
              <td [attr.data-label]="'Encontrado' | t">{{ summary(entry) }}</td>
              <td [attr.data-label]="'Observação' | t">{{ entry.note || '-' }}</td>
              <td [attr.data-label]="'Quem atendeu' | t">{{ entry.performedBy }}</td>
            </tr>
          }
        </table>
      }
    </section>
  `,
  styles: `
    .card { margin-bottom: 18px; }
    .sec { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
    .sec.sub { margin-top: 22px; }
    .sec app-icon { color: var(--primary); }
    :host-context(:root[data-theme='dark']) .sec app-icon { color: var(--link); }
    .two { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
    .four { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 14px; }
    .search { display: flex; gap: 10px; align-items: flex-end; flex-wrap: wrap; margin-bottom: 14px; }
    .search .grow { flex: 1; min-width: 220px; margin: 0; }
    h4 { display: flex; align-items: center; gap: 8px; margin: 18px 0 8px; }
    .code { font-family: ui-monospace, Menlo, Consolas, monospace; font-weight: 700; letter-spacing: 0.02em; }
    .note { margin-top: 18px; max-width: 520px; }
    .actions { display: flex; flex-wrap: wrap; gap: 10px; margin: 6px 0 8px; }
    .actions button { display: inline-flex; align-items: center; gap: 6px; }
    .warn-line { display: flex; gap: 8px; align-items: flex-start; margin: 8px 0; font-size: 13.5px; color: var(--warn-fg); }
    @media (max-width: 720px) { .two { grid-template-columns: 1fr; } }
  `,
})
export class PrivacyPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly saver = inject(FileSaver);

  readonly notice = new Notice();
  readonly busy = signal(false);

  readonly contactName = signal('');
  readonly contactEmail = signal('');
  readonly ticketDays = signal(30);
  readonly couponDays = signal(90);
  readonly eventsDays = signal(90);
  readonly playDays = signal(14);

  readonly term = signal('');
  readonly note = signal('');
  readonly result = signal<PrivacySearch | null>(null);
  /** O texto que gerou o resultado em tela: a cópia e a exclusão usam ele, não o que foi digitado depois. */
  private searched = '';
  readonly requests = signal<PrivacyRequestEntry[]>([]);

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.notice.dispose());
    this.api.privacySettings().subscribe({ next: (settings) => this.apply(settings), error: () => undefined });
    this.loadRequests();
  }

  private apply(settings: PrivacySettings): void {
    this.contactName.set(settings.contactName ?? '');
    this.contactEmail.set(settings.contactEmail ?? '');
    this.ticketDays.set(settings.ticketRetentionDays);
    this.couponDays.set(settings.couponNameRetentionDays);
    this.eventsDays.set(settings.eventsRetentionDays);
    this.playDays.set(settings.playLogRetentionDays);
  }

  private loadRequests(): void {
    this.api.privacyRequests().subscribe({ next: (entries) => this.requests.set(entries), error: () => undefined });
  }

  saveSettings(): void {
    this.busy.set(true);
    this.api
      .updatePrivacySettings({
        contactName: this.contactName().trim(),
        contactEmail: this.contactEmail().trim(),
        ticketRetentionDays: this.ticketDays(),
        couponNameRetentionDays: this.couponDays(),
        eventsRetentionDays: this.eventsDays(),
        playLogRetentionDays: this.playDays(),
      })
      .subscribe({
        next: (settings) => {
          this.busy.set(false);
          this.apply(settings);
          this.notice.show(tr('Configurações de privacidade salvas.'));
        },
        error: () => this.busy.set(false),
      });
  }

  total(found: PrivacySearch): number {
    return found.tickets.length + found.coupons.length + found.users.length;
  }

  roleText(role: string): string {
    return roleLabel(role as Parameters<typeof roleLabel>[0]);
  }

  summary(entry: PrivacyRequestEntry): string {
    return tr('{tickets} senha(s), {coupons} cupom(ns), {users} usuário(s)', { tickets: entry.tickets, coupons: entry.coupons, users: entry.users });
  }

  search(): void {
    this.run(this.term().trim());
  }

  private run(term: string): void {
    if (term.length < 3) {
      return;
    }
    this.busy.set(true);
    this.api.privacySearch(term).subscribe({
      next: (found) => {
        this.busy.set(false);
        this.searched = term;
        this.result.set(found);
      },
      error: () => this.busy.set(false),
    });
  }

  exportData(): void {
    if (!this.searched) {
      return;
    }
    this.busy.set(true);
    this.api.privacyExport(this.searched, this.note().trim() || null).subscribe({
      next: (blob) => {
        this.busy.set(false);
        this.saver.save(blob, `dados-pessoais-${new Date().toISOString().slice(0, 10)}.json`);
        this.notice.show(tr('Cópia dos dados baixada. O pedido ficou registrado.'));
        this.loadRequests();
      },
      error: () => this.busy.set(false),
    });
  }

  erase(): void {
    const found = this.result();
    if (!this.searched || !found) {
      return;
    }
    const message = tr('Apagar os dados pessoais encontrados ({tickets} senha(s) e {coupons} cupom(ns))? Não dá para desfazer. Baixe a cópia antes se for preciso entregá-la.', {
      tickets: found.tickets.length,
      coupons: found.coupons.length,
    });
    if (!confirm(message)) {
      return;
    }
    this.busy.set(true);
    this.api.privacyErase(this.searched, this.note().trim() || null).subscribe({
      next: (done) => {
        this.busy.set(false);
        this.notice.show(tr('Dados apagados: {tickets} senha(s) e {coupons} cupom(ns). O pedido ficou registrado.', { tickets: done.tickets, coupons: done.coupons }));
        this.loadRequests();
        this.run(this.searched);
      },
      error: () => this.busy.set(false),
    });
  }
}
