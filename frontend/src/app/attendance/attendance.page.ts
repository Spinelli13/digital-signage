import { Component, DestroyRef, ElementRef, OnInit, computed, effect, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Observable } from 'rxjs';

import { AttendanceToday, CounterInfo, QueueInfo, Ticket, TicketAction, WaitingTicket } from '../core/models';
import { TPipe, tr } from '../core/i18n';
import { Notice } from '../core/notice';
import { IconComponent } from '../shared/icon';
import { QR_IMAGE_SIZE, drawQr } from '../media/qr';
import { EmptyStateComponent, PageHeaderComponent, PillComponent, SkeletonComponent } from '../shared/ui';

const POLL_MS = 4000;
const COUNTER_KEY = 'smartplay.counter';

/** Segundos como a pessoa lê: "45 s", "3 min", "1 h 05". */
export function formatWait(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined) {
    return '-';
  }
  const total = Math.max(0, Math.round(seconds));
  if (total < 60) {
    return `${total} s`;
  }
  const minutes = Math.floor(total / 60);
  if (minutes < 60) {
    return `${minutes} min`;
  }
  return `${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, '0')}`;
}

/**
 * Atendimento: o atendente escolhe o guichê, chama a próxima senha e conclui; quem recepciona emite senhas; o dono
 * acompanha a fila e os números do dia. As filas e os guichês são montados aqui mesmo (Administrador).
 */
@Component({
  selector: 'app-attendance',
  imports: [FormsModule, TPipe, IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent],
  template: `
    <app-page-header [title]="'Atendimento' | t" [subtitle]="'Emita senhas, chame a próxima e acompanhe a fila. O painel de chamadas nas TVs é um widget da Biblioteca (Painel de chamadas).' | t" icon="hourglass">
      @if (auth.can('admin') && queues().length > 0) {
        <button class="secondary" (click)="config.set(!config())"><app-icon name="settings" />{{ (config() ? 'Fechar a configuração' : 'Filas e guichês') | t }}</button>
      }
    </app-page-header>

    @if (notice.text(); as text) {
      <div class="notice" role="status"><app-icon name="circle-check" [size]="18" /><span>{{ text }}</span></div>
    }

    @if (!loaded()) {
      <div class="card"><app-skeleton [rows]="4" [height]="56" /></div>
    } @else if (queues().length === 0) {
      <div class="card">
        <app-empty-state icon="hourglass" [title]="'Nenhuma fila de atendimento ainda' | t"
          [text]="'Crie as filas (por exemplo, Atendimento geral e Prioritário) e os guichês. Depois é só emitir senhas e chamar pela TV.' | t">
          @if (auth.can('admin')) {
            <button class="primary" (click)="openQueue(null)"><app-icon name="plus" />{{ 'Criar a primeira fila' | t }}</button>
          } @else {
            <p class="label">{{ 'Peça ao Administrador para criar as filas.' | t }}</p>
          }
        </app-empty-state>
      </div>
    } @else {
      <div class="today">
        <app-pill icon="list">{{ '{n} emitidas hoje' | t: { n: today()?.issued ?? 0 } }}</app-pill>
        <app-pill tone="ok" icon="circle-check">{{ '{n} atendidas' | t: { n: today()?.served ?? 0 } }}</app-pill>
        <app-pill icon="hourglass">{{ '{n} aguardando' | t: { n: today()?.waiting ?? 0 } }}</app-pill>
        @if ((today()?.noShow ?? 0) > 0) {
          <app-pill tone="warn" icon="circle-alert">{{ '{n} não compareceram' | t: { n: today()?.noShow } }}</app-pill>
        }
        <app-pill icon="timer">{{ 'espera média {time}' | t: { time: wait(today()?.averageWaitSeconds) } }}</app-pill>
        <app-pill icon="clock">{{ 'atendimento médio {time}' | t: { time: wait(today()?.averageServiceSeconds) } }}</app-pill>
      </div>

      <div class="cols">
        <section class="card">
          <h2>{{ 'Meu guichê' | t }}</h2>
          @if (counters().length === 0) {
            <p class="label">{{ 'Ainda não há guichês. O Administrador cadastra em "Filas e guichês".' | t }}</p>
          } @else {
            <div class="field">
              <label for="counter">{{ 'Atendo no guichê' | t }}</label>
              <select id="counter" name="counter" [ngModel]="selectedCounterId()" (ngModelChange)="chooseCounter($event)">
                <option [ngValue]="null">{{ 'Escolha o seu guichê…' | t }}</option>
                @for (counter of counters(); track counter.id) {
                  <option [ngValue]="counter.id" [disabled]="!counter.active">{{ counter.name }}</option>
                }
              </select>
            </div>
            @if (selectedCounter(); as counter) {
              @if (counter.current; as ticket) {
                <div class="current" [class.calling]="ticket.status === 'CALLING'">
                  <small>{{ (ticket.status === 'CALLING' ? 'Chamando agora' : 'Em atendimento') | t }}</small>
                  <b class="big">{{ ticket.label }}</b>
                  <span>{{ ticket.queueName }}</span>
                  @if (ticket.customerName) {
                    <span class="who">{{ ticket.customerName }}</span>
                  }
                  @if (ticket.callCount > 1) {
                    <small>{{ 'chamada {n}' | t: { n: ticket.callCount } }}</small>
                  }
                </div>
                @if (auth.can('content')) {
                  <div class="actions">
                    @if (ticket.status === 'CALLING') {
                      <button class="secondary" [disabled]="busy()" (click)="act(ticket, 'recall')"><app-icon name="megaphone" [size]="16" />{{ 'Chamar de novo' | t }}</button>
                      <button class="primary" [disabled]="busy()" (click)="act(ticket, 'start')"><app-icon name="play" [size]="16" />{{ 'Iniciar atendimento' | t }}</button>
                      <button class="secondary" [disabled]="busy()" (click)="act(ticket, 'no-show')"><app-icon name="circle-x" [size]="16" />{{ 'Não compareceu' | t }}</button>
                    }
                    <button [class]="ticket.status === 'SERVING' ? 'primary' : 'secondary'" [disabled]="busy()" (click)="act(ticket, 'finish')"><app-icon name="check" [size]="16" />{{ 'Concluir' | t }}</button>
                  </div>
                }
              } @else {
                <p class="label">{{ 'Nenhuma senha em atendimento neste guichê.' | t }}</p>
                @if (auth.can('content')) {
                  <button class="primary big-button" [disabled]="busy() || !counter.active" (click)="callNext(counter)"><app-icon name="megaphone" />{{ 'Chamar a próxima senha' | t }}</button>
                }
              }
            }
          }
        </section>

        <section class="card">
          <h2>{{ 'Emitir senha' | t }}</h2>
          @if (auth.can('content')) {
            <div class="field">
              <label for="customer">{{ 'Nome da pessoa (opcional)' | t }}</label>
              <input id="customer" name="customer" maxlength="60" [placeholder]="'Aparece só para os atendentes' | t" [ngModel]="customerName()" (ngModelChange)="customerName.set($event)" />
            </div>
            <div class="issue">
              @for (queue of activeQueues(); track queue.id) {
                <button class="secondary issue-button" [disabled]="busy()" (click)="issue(queue)">
                  <b>{{ queue.prefix }}</b>
                  <span>{{ queue.name }}@if (queue.priority) { <app-icon name="star" [size]="13" /> }</span>
                  <small>{{ '{n} aguardando' | t: { n: queue.waiting } }}</small>
                </button>
              }
            </div>
          } @else {
            <p class="label">{{ 'Você pode acompanhar a fila, mas só o Editor e o Administrador emitem e chamam senhas.' | t }}</p>
          }
        </section>
      </div>

      <section class="card">
        <h2>{{ 'Fila de espera' | t }}</h2>
        @if (waiting().length === 0) {
          <p class="label">{{ 'Ninguém aguardando.' | t }}</p>
        } @else {
          <table class="stack">
            <tr><th>{{ 'Nº da senha' | t }}</th><th>{{ 'Fila' | t }}</th><th>{{ 'Nome' | t }}</th><th>{{ 'Espera' | t }}</th><th></th></tr>
            @for (ticket of waiting(); track ticket.id) {
              <tr>
                <td [attr.data-label]="'Nº da senha' | t"><b>{{ ticket.label }}</b></td>
                <td [attr.data-label]="'Fila' | t">{{ ticket.queueName }}@if (ticket.priority) { <app-pill tone="brand" icon="star">{{ 'prioritária' | t }}</app-pill> }</td>
                <td [attr.data-label]="'Nome' | t">{{ ticket.customerName || '-' }}</td>
                <td [attr.data-label]="'Espera' | t">{{ wait(ticket.waitingSeconds) }}</td>
                <td class="row-actions">
                  @if (auth.can('content')) {
                    <button class="danger small" [disabled]="busy()" (click)="cancelTicket(ticket)" [attr.aria-label]="'Cancelar a senha {label}' | t: { label: ticket.label }"><app-icon name="x" [size]="15" /></button>
                  }
                </td>
              </tr>
            }
          </table>
        }
      </section>
    }

    @if (auth.can('admin') && (config() || (loaded() && queues().length === 0))) {
      <section class="card">
        <div class="section-head">
          <h2>{{ 'Filas' | t }}</h2>
          <button class="secondary small" (click)="openQueue(null)"><app-icon name="plus" [size]="15" />{{ 'Nova fila' | t }}</button>
        </div>
        @if (queues().length === 0) {
          <p class="label">{{ 'Nenhuma fila.' | t }}</p>
        } @else {
          <table class="stack">
            <tr><th>{{ 'Letra' | t }}</th><th>{{ 'Nome' | t }}</th><th>{{ 'Situação' | t }}</th><th></th></tr>
            @for (queue of queues(); track queue.id) {
              <tr>
                <td [attr.data-label]="'Letra' | t"><b>{{ queue.prefix }}</b></td>
                <td [attr.data-label]="'Nome' | t">{{ queue.name }}@if (queue.priority) { <app-pill tone="brand" icon="star">{{ 'prioritária' | t }}</app-pill> }</td>
                <td [attr.data-label]="'Situação' | t"><app-pill [tone]="queue.active ? 'ok' : 'neutral'" [icon]="queue.active ? 'circle-check' : 'circle-pause'">{{ (queue.active ? 'Ativa' : 'Desativada') | t }}</app-pill></td>
                <td class="row-actions">
                  <button class="secondary small" (click)="openQueue(queue)" [attr.aria-label]="'Editar a fila {name}' | t: { name: queue.name }"><app-icon name="pencil" [size]="15" /></button>
                  <button class="danger small" (click)="removeQueue(queue)" [attr.aria-label]="'Excluir a fila {name}' | t: { name: queue.name }"><app-icon name="trash" [size]="15" /></button>
                </td>
              </tr>
            }
          </table>
        }
      </section>

      <section class="card">
        <div class="section-head">
          <h2>{{ 'Guichês' | t }}</h2>
          <button class="secondary small" (click)="openCounter(null)" [disabled]="queues().length === 0"><app-icon name="plus" [size]="15" />{{ 'Novo guichê' | t }}</button>
        </div>
        @if (counters().length === 0) {
          <p class="label">{{ 'Nenhum guichê.' | t }}</p>
        } @else {
          <table class="stack">
            <tr><th>{{ 'Nome' | t }}</th><th>{{ 'Atende' | t }}</th><th>{{ 'Situação' | t }}</th><th></th></tr>
            @for (counter of counters(); track counter.id) {
              <tr>
                <td [attr.data-label]="'Nome' | t"><b>{{ counter.name }}</b></td>
                <td [attr.data-label]="'Atende' | t">{{ servedBy(counter) }}</td>
                <td [attr.data-label]="'Situação' | t"><app-pill [tone]="counter.active ? 'ok' : 'neutral'" [icon]="counter.active ? 'circle-check' : 'circle-pause'">{{ (counter.active ? 'Ativo' : 'Desativado') | t }}</app-pill></td>
                <td class="row-actions">
                  <button class="secondary small" (click)="openCounter(counter)" [attr.aria-label]="'Editar o guichê {name}' | t: { name: counter.name }"><app-icon name="pencil" [size]="15" /></button>
                  <button class="danger small" (click)="removeCounter(counter)" [attr.aria-label]="'Excluir o guichê {name}' | t: { name: counter.name }"><app-icon name="trash" [size]="15" /></button>
                </td>
              </tr>
            }
          </table>
        }
      </section>
    }

    @if (modal() === 'queue') {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="saveQueue()">
          <h2>{{ (editingQueue ? 'Editar fila' : 'Nova fila') | t }}</h2>
          <div class="field">
            <label for="queue-name">{{ 'Nome da fila' | t }}</label>
            <input id="queue-name" name="name" required maxlength="60" [placeholder]="'Ex.: Atendimento geral' | t" [ngModel]="queueName()" (ngModelChange)="queueName.set($event)" />
          </div>
          <div class="field">
            <label for="queue-prefix">{{ 'Letra da senha' | t }}</label>
            <input id="queue-prefix" name="prefix" required maxlength="3" class="prefix" [placeholder]="'Ex.: A' | t" [ngModel]="queuePrefix()" (ngModelChange)="queuePrefix.set($event.toUpperCase())" />
            <p class="label">{{ 'De 1 a 3 letras ou números. As senhas ficam A001, A002... e a numeração recomeça todo dia.' | t }}</p>
          </div>
          <div class="field">
            <label class="check"><input type="checkbox" name="priority" [ngModel]="queuePriority()" (ngModelChange)="queuePriority.set($event)" /> {{ 'Fila prioritária (idosos, gestantes, pessoas com deficiência)' | t }}</label>
            <p class="label">{{ 'As prioritárias são chamadas primeiro, mas a cada 3 chamadas seguidas uma senha normal é chamada.' | t }}</p>
          </div>
          @if (editingQueue) {
            <div class="field">
              <label class="check"><input type="checkbox" name="active" [ngModel]="queueActive()" (ngModelChange)="queueActive.set($event)" /> {{ 'Fila ativa (emite senhas)' | t }}</label>
            </div>
          }
          <div class="field">
            <label class="check"><input type="checkbox" name="remote" [ngModel]="queueRemote()" (ngModelChange)="queueRemote.set($event)" /> {{ 'Permitir pegar a senha pelo celular (QR Code)' | t }}</label>
            <p class="label">{{ 'Quem escanear o QR Code da fila pega a senha no próprio celular e vê a posição na fila, sem instalar nada.' | t }}</p>
          </div>
          @if (editingQueue && editingQueue.remoteEnabled && editingQueue.publicToken && queueRemote()) {
            <div class="qr-box">
              <canvas #qrCanvas [width]="imageSize" [height]="imageSize" [attr.aria-label]="'QR Code da fila' | t"></canvas>
              <div class="qr-side">
                <p class="label">{{ 'Imprima e cole perto da entrada. O endereço abaixo abre a mesma página.' | t }}</p>
                <input readonly [value]="publicLink()" [attr.aria-label]="'Endereço da fila' | t" />
                <div class="actions">
                  <button type="button" class="secondary small" (click)="copyLink()"><app-icon name="copy" [size]="15" />{{ 'Copiar o endereço' | t }}</button>
                  <button type="button" class="secondary small" (click)="downloadQr()"><app-icon name="download" [size]="15" />{{ 'Baixar o QR Code' | t }}</button>
                </div>
              </div>
            </div>
          }
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">{{ 'Cancelar' | t }}</button>
            <button type="submit" class="primary" [disabled]="busy() || !queueName().trim() || !queuePrefix().trim()">{{ 'Salvar' | t }}</button>
          </div>
        </form>
      </div>
    }

    @if (modal() === 'counter') {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="saveCounter()">
          <h2>{{ (editingCounter ? 'Editar guichê' : 'Novo guichê') | t }}</h2>
          <div class="field">
            <label for="counter-name">{{ 'Nome do guichê' | t }}</label>
            <input id="counter-name" name="name" required maxlength="40" [placeholder]="'Ex.: Guichê 1' | t" [ngModel]="counterName()" (ngModelChange)="counterName.set($event)" />
          </div>
          <div class="field">
            <label>{{ 'Filas que este guichê atende' | t }}</label>
            <div class="chips">
              @for (queue of queues(); track queue.id) {
                <label class="check">
                  <input type="checkbox" [name]="'cq-' + queue.id" [ngModel]="counterQueues().includes(queue.id)" (ngModelChange)="toggleCounterQueue(queue.id)" />{{ queue.name }}
                </label>
              }
            </div>
            <p class="label">{{ 'Sem nenhuma marcada, o guichê atende todas as filas.' | t }}</p>
          </div>
          @if (editingCounter) {
            <div class="field">
              <label class="check"><input type="checkbox" name="cactive" [ngModel]="counterActive()" (ngModelChange)="counterActive.set($event)" /> {{ 'Guichê ativo' | t }}</label>
            </div>
          }
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">{{ 'Cancelar' | t }}</button>
            <button type="submit" class="primary" [disabled]="busy() || !counterName().trim()">{{ 'Salvar' | t }}</button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .today { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
    .cols { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; margin-bottom: 16px; }
    section.card h2 { margin: 0 0 12px; font-size: 16px; }
    .section-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
    .section-head h2 { margin: 0; }
    .current { display: flex; flex-direction: column; align-items: center; gap: 2px; padding: 18px 12px; margin: 4px 0 14px; border: 1px solid var(--line); border-radius: var(--r-lg); background: var(--panel-2); text-align: center; }
    .current.calling { border-color: var(--primary); background: var(--primary-soft); }
    .current .who { font-weight: 600; }
    .current small { color: var(--muted); text-transform: uppercase; letter-spacing: 0.08em; font-size: 11.5px; }
    .current .big { font-size: 44px; line-height: 1.05; letter-spacing: 0.02em; color: var(--primary); font-variant-numeric: tabular-nums; }
    :host-context(:root[data-theme='dark']) .current .big { color: var(--link); }
    .actions { display: flex; flex-wrap: wrap; gap: 8px; }
    .big-button { width: 100%; justify-content: center; padding: 16px; font-size: 16px; }
    .issue { display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 10px; }
    .issue-button { display: grid; grid-template-columns: auto 1fr; grid-template-rows: auto auto; column-gap: 12px; align-items: center; text-align: left; padding: 12px 14px; }
    .issue-button b { grid-row: 1 / span 2; font-size: 26px; color: var(--primary); }
    :host-context(:root[data-theme='dark']) .issue-button b { color: var(--link); }
    .issue-button span { font-weight: 600; display: inline-flex; align-items: center; gap: 6px; }
    .issue-button small { color: var(--muted); }
    .prefix { max-width: 110px; text-transform: uppercase; }
    .qr-box { display: flex; gap: 16px; align-items: flex-start; margin: 4px 0 14px; }
    .qr-box canvas { width: 180px; height: 180px; flex: none; border: 1px solid var(--line); border-radius: 10px; }
    .qr-side { display: flex; flex-direction: column; gap: 8px; min-width: 0; flex: 1; }
    @media (max-width: 560px) { .qr-box { flex-direction: column; } }
    td app-pill { margin-left: 8px; }
    .chips { display: flex; flex-wrap: wrap; gap: 6px 14px; }
    @media (max-width: 860px) {
      .cols { grid-template-columns: 1fr; }
    }
  `,
})
export class AttendancePage implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly loaded = signal(false);
  readonly queues = signal<QueueInfo[]>([]);
  readonly counters = signal<CounterInfo[]>([]);
  readonly waiting = signal<WaitingTicket[]>([]);
  readonly today = signal<AttendanceToday | null>(null);
  readonly selectedCounterId = signal<number | null>(readCounter());
  readonly customerName = signal('');
  readonly busy = signal(false);
  readonly config = signal(false);
  readonly modal = signal<'queue' | 'counter' | null>(null);
  readonly notice = new Notice();

  readonly activeQueues = computed(() => this.queues().filter((queue) => queue.active));
  readonly selectedCounter = computed(() => this.counters().find((counter) => counter.id === this.selectedCounterId()) ?? null);
  readonly wait = formatWait;

  // formulários
  editingQueue: QueueInfo | null = null;
  readonly queueName = signal('');
  readonly queuePrefix = signal('');
  readonly queuePriority = signal(false);
  readonly queueActive = signal(true);
  readonly queueRemote = signal(false);
  readonly imageSize = QR_IMAGE_SIZE;
  private readonly qrCanvas = viewChild<ElementRef<HTMLCanvasElement>>('qrCanvas');
  editingCounter: CounterInfo | null = null;
  readonly counterName = signal('');
  readonly counterQueues = signal<number[]>([]);
  readonly counterActive = signal(true);

  private started = false;

  constructor() {
    // O QR Code da fila se desenha sozinho quando a janela abre (e se o segredo mudar).
    effect(() => {
      const canvas = this.qrCanvas()?.nativeElement;
      const link = this.publicLink();
      const ctx = canvas?.getContext('2d');
      if (canvas && ctx && link) {
        try {
          drawQr(ctx, { text: link, title: this.editingQueue?.name ?? '', caption: tr('Escaneie para pegar a sua senha'), theme: 'claro' });
        } catch {
          ctx.clearRect(0, 0, canvas.width, canvas.height);
        }
      }
    });
  }

  /** O endereço público da fila (o do QR Code): o painel e a página da senha ficam no mesmo site. */
  publicLink(): string {
    const token = this.editingQueue?.publicToken;
    return token ? `${location.origin}/fila/${token}` : '';
  }

  copyLink(): void {
    const link = this.publicLink();
    if (!link) {
      return;
    }
    navigator.clipboard?.writeText(link).then(
      () => this.notice.show(tr('Endereço copiado.')),
      () => this.notice.show(tr('Não foi possível copiar. Selecione o endereço e copie à mão.')),
    );
  }

  downloadQr(): void {
    const canvas = this.qrCanvas()?.nativeElement;
    if (!canvas) {
      return;
    }
    canvas.toBlob((blob) => {
      if (!blob) {
        return;
      }
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `qr-fila-${(this.editingQueue?.prefix ?? 'fila').toLowerCase()}.png`;
      link.click();
      URL.revokeObjectURL(url);
    }, 'image/png');
  }

  ngOnInit(): void {
    // Uma vez só: a detecção automática de mudanças pode chamar de novo depois de uma chamada manual (testes).
    if (this.started) {
      return;
    }
    this.started = true;
    this.refresh();
    const timer = setInterval(() => this.refresh(), POLL_MS);
    this.destroyRef.onDestroy(() => {
      clearInterval(timer);
      this.notice.dispose();
    });
  }

  /** Consulta a fila (a cada poucos segundos e depois de cada ação). Silenciosa: um tropeço de rede não enche a tela de avisos. */
  refresh(): void {
    this.api.attendanceOverview().subscribe({
      next: (overview) => {
        this.queues.set(overview.queues);
        this.counters.set(overview.counters);
        this.waiting.set(overview.waiting);
        this.today.set(overview.today);
        this.loaded.set(true);
        // Um guichê que saiu do cadastro deixa de ser o escolhido.
        if (this.selectedCounterId() !== null && !overview.counters.some((counter) => counter.id === this.selectedCounterId())) {
          this.selectedCounterId.set(null);
        }
      },
      error: () => undefined,
    });
  }

  chooseCounter(id: number | null): void {
    this.selectedCounterId.set(id);
    saveCounter(id);
  }

  servedBy(counter: CounterInfo): string {
    if (counter.queueIds.length === 0) {
      return tr('Todas as filas');
    }
    return counter.queueIds.map((id) => this.queues().find((queue) => queue.id === id)?.name ?? '?').join(', ');
  }

  // ---------- senhas ----------

  issue(queue: QueueInfo): void {
    this.run(this.api.issueTicket(queue.id, this.customerName().trim() || null), (ticket) => {
      this.customerName.set('');
      this.notice.show(tr('Senha {label} emitida na fila "{queue}".', { label: ticket.label, queue: queue.name }));
    });
  }

  callNext(counter: CounterInfo): void {
    this.run(this.api.callNextTicket(counter.id), (ticket) => {
      this.notice.show(tr('Chamando a senha {label}.', { label: ticket.label }));
    });
  }

  act(ticket: Ticket, action: TicketAction): void {
    this.run(this.api.ticketAction(ticket.id, action), () => undefined);
  }

  cancelTicket(ticket: WaitingTicket): void {
    if (!confirm(tr('Cancelar a senha {label}? A pessoa sai da fila.', { label: ticket.label }))) {
      return;
    }
    this.run(this.api.ticketAction(ticket.id, 'cancel'), () => this.notice.show(tr('Senha {label} cancelada.', { label: ticket.label })));
  }

  private run<T>(call: Observable<T>, done: (result: T) => void): void {
    this.busy.set(true);
    call.subscribe({
      next: (result) => {
        this.busy.set(false);
        done(result);
        this.refresh();
      },
      // O motivo (ex.: "Não há senhas aguardando") já foi mostrado pelo aviso de erro.
      error: () => {
        this.busy.set(false);
        this.refresh();
      },
    });
  }

  // ---------- filas ----------

  openQueue(queue: QueueInfo | null): void {
    this.editingQueue = queue;
    this.queueName.set(queue?.name ?? '');
    this.queuePrefix.set(queue?.prefix ?? '');
    this.queuePriority.set(queue?.priority ?? false);
    this.queueActive.set(queue?.active ?? true);
    this.queueRemote.set(queue?.remoteEnabled ?? false);
    this.modal.set('queue');
  }

  saveQueue(): void {
    const body = {
      name: this.queueName().trim(),
      prefix: this.queuePrefix().trim().toUpperCase(),
      priority: this.queuePriority(),
      active: this.queueActive(),
      remoteEnabled: this.queueRemote(),
    };
    const request = this.editingQueue ? this.api.updateQueue(this.editingQueue.id, body) : this.api.createQueue(body);
    this.busy.set(true);
    request.subscribe({
      next: (queue) => {
        this.busy.set(false);
        this.closeModal();
        this.notice.show(
          queue.remoteEnabled
            ? tr('Fila "{name}" salva. Abra a fila de novo para baixar o QR Code.', { name: queue.name })
            : tr('Fila "{name}" salva.', { name: queue.name }),
        );
        this.refresh();
      },
      error: () => this.busy.set(false),
    });
  }

  removeQueue(queue: QueueInfo): void {
    if (!confirm(tr('Excluir a fila "{name}"?', { name: queue.name }))) {
      return;
    }
    this.api.deleteQueue(queue.id).subscribe({
      next: () => {
        this.notice.show(tr('Fila "{name}" excluída.', { name: queue.name }));
        this.refresh();
      },
      error: () => undefined,
    });
  }

  // ---------- guichês ----------

  openCounter(counter: CounterInfo | null): void {
    this.editingCounter = counter;
    this.counterName.set(counter?.name ?? '');
    this.counterQueues.set(counter ? [...counter.queueIds] : []);
    this.counterActive.set(counter?.active ?? true);
    this.modal.set('counter');
  }

  toggleCounterQueue(id: number): void {
    this.counterQueues.update((current) => (current.includes(id) ? current.filter((item) => item !== id) : [...current, id]));
  }

  saveCounter(): void {
    const body = { name: this.counterName().trim(), queueIds: this.counterQueues(), active: this.counterActive() };
    const request = this.editingCounter ? this.api.updateCounter(this.editingCounter.id, body) : this.api.createCounter(body);
    this.busy.set(true);
    request.subscribe({
      next: (counter) => {
        this.busy.set(false);
        this.closeModal();
        this.notice.show(tr('Guichê "{name}" salvo.', { name: counter.name }));
        this.refresh();
      },
      error: () => this.busy.set(false),
    });
  }

  removeCounter(counter: CounterInfo): void {
    if (!confirm(tr('Excluir o guichê "{name}"?', { name: counter.name }))) {
      return;
    }
    this.api.deleteCounter(counter.id).subscribe({
      next: () => {
        this.notice.show(tr('Guichê "{name}" excluído.', { name: counter.name }));
        this.refresh();
      },
      error: () => undefined,
    });
  }

  closeModal(): void {
    this.modal.set(null);
  }
}

/** O guichê escolhido fica guardado no navegador (cada atendente no seu computador). */
function readCounter(): number | null {
  try {
    const value = Number(localStorage.getItem(COUNTER_KEY));
    return Number.isInteger(value) && value > 0 ? value : null;
  } catch {
    return null;
  }
}

function saveCounter(id: number | null): void {
  try {
    if (id === null) {
      localStorage.removeItem(COUNTER_KEY);
    } else {
      localStorage.setItem(COUNTER_KEY, String(id));
    }
  } catch {
    // sem armazenamento (janela privada): o guichê só não fica lembrado
  }
}
