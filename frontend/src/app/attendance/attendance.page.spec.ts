import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthService } from '../core/auth.service';
import { ConfigService } from '../core/config.service';
import { errorInterceptor } from '../core/error.interceptor';
import { AttendanceOverview, CounterInfo, QueueInfo, Role, Ticket, WaitingTicket } from '../core/models';
import { meFixture } from '../testing/fixtures';
import { AttendancePage, formatWait } from './attendance.page';

const API = 'http://api.test/api/v1';

const queue = (id: number, name: string, prefix: string, overrides: Partial<QueueInfo> = {}): QueueInfo => ({
  id,
  name,
  prefix,
  priority: false,
  active: true,
  timezone: 'America/Sao_Paulo',
  waiting: 0,
  ...overrides,
});

const ticket = (overrides: Partial<Ticket> = {}): Ticket => ({
  id: 10,
  label: 'A001',
  queueId: 1,
  queueName: 'Atendimento geral',
  priority: false,
  status: 'CALLING',
  counterId: 7,
  counterName: 'Guichê 1',
  customerName: 'Maria',
  source: 'PANEL',
  issuedAt: '2026-10-21T15:00:00Z',
  calledAt: '2026-10-21T15:02:00Z',
  startedAt: null,
  finishedAt: null,
  callCount: 1,
  ...overrides,
});

const counter = (id: number, name: string, overrides: Partial<CounterInfo> = {}): CounterInfo => ({ id, name, queueIds: [], active: true, current: null, ...overrides });

const waiting = (id: number, label: string, overrides: Partial<WaitingTicket> = {}): WaitingTicket => ({
  id,
  label,
  queueId: 1,
  queueName: 'Atendimento geral',
  priority: false,
  customerName: null,
  source: 'PANEL',
  issuedAt: '2026-10-21T15:00:00Z',
  waitingSeconds: 130,
  ...overrides,
});

const overview = (overrides: Partial<AttendanceOverview> = {}): AttendanceOverview => ({
  queues: [queue(1, 'Atendimento geral', 'A', { waiting: 2 }), queue(2, 'Prioritário', 'P', { priority: true })],
  counters: [counter(7, 'Guichê 1'), counter(8, 'Guichê 2')],
  waiting: [waiting(11, 'A002'), waiting(12, 'A003', { customerName: 'Joana', waitingSeconds: 45 })],
  today: { issued: 5, waiting: 2, serving: 0, served: 3, noShow: 0, canceled: 0, averageWaitSeconds: 180, averageServiceSeconds: 420 },
  serverTimeMs: 0,
  ...overrides,
});

function setup(role: Role = 'EDITOR', data: AttendanceOverview = overview(), render = false) {
  localStorage.clear();
  TestBed.configureTestingModule({
    providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
  });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  TestBed.inject(AuthService).me.set(meFixture(role));
  const backend = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(AttendancePage);
  const page = fixture.componentInstance;
  if (render) {
    fixture.detectChanges();
  } else {
    page.ngOnInit();
  }
  backend.expectOne(`${API}/attendance/overview`).flush(data);
  return { page, backend, fixture };
}

describe('formatWait', () => {
  it('reads seconds, minutes and hours the way a person says them', () => {
    expect(formatWait(null)).toBe('-');
    expect(formatWait(0)).toBe('0 s');
    expect(formatWait(45)).toBe('45 s');
    expect(formatWait(130)).toBe('2 min');
    expect(formatWait(3600 + 5 * 60)).toBe('1 h 05');
    expect(formatWait(-4)).toBe('0 s');
  });
});

describe('AttendancePage', () => {
  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
  });

  it('shows the numbers of the day, the queues to issue from and the waiting list', async () => {
    const { fixture, backend } = setup('EDITOR', overview(), true);
    fixture.detectChanges();
    await fixture.whenStable();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('5 emitidas hoje');
    expect(text).toContain('3 atendidas');
    expect(text).toContain('espera média 3 min');
    expect(text).toContain('atendimento médio 7 min');
    expect(text).toContain('Atendimento geral');
    expect(text).toContain('Prioritário');
    expect(text).toContain('A002');
    expect(text).toContain('Joana');
    expect(text).toContain('2 min'); // A002 espera 130 s
    backend.verify();
  });

  it('remembers the counter chosen by the attendant in this browser', () => {
    const { page, backend } = setup();

    page.chooseCounter(8);

    expect(page.selectedCounter()?.name).toBe('Guichê 2');
    expect(localStorage.getItem('smartplay.counter')).toBe('8');
    page.chooseCounter(null);
    expect(localStorage.getItem('smartplay.counter')).toBeNull();
    backend.verify();
  });

  it('forgets a counter that was removed from the setup', () => {
    localStorage.setItem('smartplay.counter', '99');
    const { page, backend } = setup();

    expect(page.selectedCounterId()).toBeNull();
    backend.verify();
  });

  it('issues a ticket with the typed name, tells the number and reloads the queue', () => {
    const { page, backend } = setup();
    page.customerName.set('  Maria Silva ');

    page.issue(page.queues()[0]);

    const request = backend.expectOne(`${API}/attendance/tickets`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ queueId: 1, customerName: 'Maria Silva' });
    request.flush(ticket({ id: 20, label: 'A004', status: 'WAITING' }));
    expect(page.notice.text()).toBe('Senha A004 emitida na fila "Atendimento geral".');
    expect(page.customerName()).toBe('');
    backend.expectOne(`${API}/attendance/overview`).flush(overview());
    backend.verify();
  });

  it('issues without a name when the field is empty', () => {
    const { page, backend } = setup();

    page.issue(page.queues()[1]);

    expect(backend.expectOne(`${API}/attendance/tickets`).request.body).toEqual({ queueId: 2, customerName: null });
  });

  it('calls the next ticket of the chosen counter and shows it as the current one', () => {
    const { page, backend } = setup();
    page.chooseCounter(7);

    page.callNext(page.selectedCounter()!);

    const request = backend.expectOne(`${API}/attendance/counters/7/call-next`);
    expect(request.request.method).toBe('POST');
    request.flush(ticket());
    expect(page.notice.text()).toBe('Chamando a senha A001.');
    backend.expectOne(`${API}/attendance/overview`).flush(overview({ counters: [counter(7, 'Guichê 1', { current: ticket() }), counter(8, 'Guichê 2')] }));
    expect(page.selectedCounter()?.current?.label).toBe('A001');
    backend.verify();
  });

  it('sends each step of the service (recall, start, finish, no-show) to the right address', () => {
    const { page, backend } = setup();
    const current = ticket();

    for (const action of ['recall', 'start', 'finish', 'no-show'] as const) {
      page.act(current, action);
      const request = backend.expectOne(`${API}/attendance/tickets/10/${action}`);
      expect(request.request.method).toBe('POST');
      request.flush(ticket({ status: action === 'start' ? 'SERVING' : 'CALLING' }));
      backend.expectOne(`${API}/attendance/overview`).flush(overview());
    }
    backend.verify();
  });

  it('keeps going when the server refuses (empty queue): the buttons come back and the queue reloads', () => {
    const { page, backend } = setup();
    page.chooseCounter(7);

    page.callNext(page.selectedCounter()!);
    backend.expectOne(`${API}/attendance/counters/7/call-next`).flush({ error: 'Não há senhas aguardando para este guichê' }, { status: 409, statusText: 'Conflict' });

    expect(page.busy()).toBe(false);
    backend.expectOne(`${API}/attendance/overview`).flush(overview());
    backend.verify();
  });

  it('cancels a waiting ticket only after the attendant confirms', () => {
    const { page, backend } = setup();
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);

    page.cancelTicket(page.waiting()[0]);
    backend.expectNone(`${API}/attendance/tickets/11/cancel`);
    page.cancelTicket(page.waiting()[0]);

    backend.expectOne(`${API}/attendance/tickets/11/cancel`).flush(ticket({ id: 11, label: 'A002', status: 'CANCELED' }));
    expect(page.notice.text()).toBe('Senha A002 cancelada.');
    backend.expectOne(`${API}/attendance/overview`).flush(overview());
    expect(confirmation).toHaveBeenCalledTimes(2);
    backend.verify();
  });

  it('the viewer follows the queue but sees no button to issue, call or cancel', async () => {
    const { fixture, backend } = setup('VIEWER', overview(), true);
    fixture.detectChanges();
    await fixture.whenStable();

    const html = fixture.nativeElement as HTMLElement;
    expect(html.textContent).toContain('A002');
    expect(html.textContent).toContain('só o Editor e o Administrador emitem e chamam');
    expect(html.querySelector('.issue-button')).toBeNull();
    expect(html.querySelector('button.danger')).toBeNull();
    backend.verify();
  });

  it('only the administrator sees the setup, and it opens from the header', async () => {
    const editor = setup('EDITOR', overview(), true);
    editor.fixture.detectChanges();
    expect((editor.fixture.nativeElement as HTMLElement).textContent).not.toContain('Filas e guichês');
    editor.backend.verify();
    TestBed.resetTestingModule();

    const admin = setup('ADMIN', overview(), true);
    admin.fixture.detectChanges();
    await admin.fixture.whenStable();
    expect((admin.fixture.nativeElement as HTMLElement).textContent).toContain('Filas e guichês');
    admin.page.config.set(true);
    admin.fixture.detectChanges();
    expect((admin.fixture.nativeElement as HTMLElement).textContent).toContain('Nova fila');
    expect((admin.fixture.nativeElement as HTMLElement).textContent).toContain('Novo guichê');
    admin.backend.verify();
  });

  it('creates a queue with the letter in capitals and the priority flag, and reloads', () => {
    const { page, backend } = setup('ADMIN');
    page.openQueue(null);
    page.queueName.set('  Prioritário ');
    page.queuePrefix.set('p');
    page.queuePriority.set(true);

    page.saveQueue();

    const request = backend.expectOne(`${API}/queues`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Prioritário', prefix: 'P', priority: true, active: true });
    request.flush(queue(3, 'Prioritário', 'P', { priority: true }));
    expect(page.modal()).toBeNull();
    expect(page.notice.text()).toBe('Fila "Prioritário" salva.');
    backend.expectOne(`${API}/attendance/overview`).flush(overview());
    backend.verify();
  });

  it('edits a queue with a PUT and keeps the server explanation open when it refuses', () => {
    const { page, backend } = setup('ADMIN');
    page.openQueue(page.queues()[0]);
    expect(page.queueName()).toBe('Atendimento geral');
    page.queuePrefix.set('G');

    page.saveQueue();

    const request = backend.expectOne(`${API}/queues/1`);
    expect(request.request.method).toBe('PUT');
    request.flush({ error: 'Só dá para trocar a letra da fila quando não há senhas esperando nem em atendimento' }, { status: 409, statusText: 'Conflict' });
    expect(page.modal()).toBe('queue');
    expect(page.busy()).toBe(false);
    backend.verify();
  });

  it('creates a counter that serves some queues, and one that serves them all', () => {
    const { page, backend } = setup('ADMIN');
    page.openCounter(null);
    page.counterName.set('Guichê dos idosos');
    page.toggleCounterQueue(2);

    page.saveCounter();

    const request = backend.expectOne(`${API}/counters`);
    expect(request.request.body).toEqual({ name: 'Guichê dos idosos', queueIds: [2], active: true });
    request.flush(counter(9, 'Guichê dos idosos', { queueIds: [2] }));
    backend.expectOne(`${API}/attendance/overview`).flush(overview());

    page.openCounter(null);
    page.counterName.set('Guichê livre');
    page.saveCounter();
    expect(backend.expectOne(`${API}/counters`).request.body.queueIds).toEqual([]);
  });

  it('says which queues a counter serves', () => {
    const { page, backend } = setup('ADMIN');

    expect(page.servedBy(counter(1, 'A'))).toBe('Todas as filas');
    expect(page.servedBy(counter(1, 'A', { queueIds: [1, 2] }))).toBe('Atendimento geral, Prioritário');
    expect(page.servedBy(counter(1, 'A', { queueIds: [99] }))).toBe('?');
    backend.verify();
  });

  it('deletes a queue or a counter only after the administrator confirms', () => {
    const { page, backend } = setup('ADMIN');
    vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValue(true);

    page.removeQueue(page.queues()[0]);
    backend.expectNone(`${API}/queues/1`);
    page.removeQueue(page.queues()[0]);
    backend.expectOne(`${API}/queues/1`).flush(null);
    expect(page.notice.text()).toBe('Fila "Atendimento geral" excluída.');
    backend.expectOne(`${API}/attendance/overview`).flush(overview());

    page.removeCounter(page.counters()[0]);
    backend.expectOne(`${API}/counters/7`).flush(null);
    expect(page.notice.text()).toBe('Guichê "Guichê 1" excluído.');
    backend.expectOne(`${API}/attendance/overview`).flush(overview());
    backend.verify();
  });

  it('invites the administrator to create the first queue when there is none', async () => {
    const { fixture, backend } = setup('ADMIN', overview({ queues: [], counters: [], waiting: [] }), true);
    fixture.detectChanges();
    await fixture.whenStable();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Nenhuma fila de atendimento ainda');
    expect(text).toContain('Criar a primeira fila');
    backend.verify();
  });

  it('asks the server again every few seconds while the page is open', () => {
    vi.useFakeTimers();
    const { backend, fixture } = setup();

    vi.advanceTimersByTime(4000);
    backend.expectOne(`${API}/attendance/overview`).flush(overview());
    vi.advanceTimersByTime(4000);
    backend.expectOne(`${API}/attendance/overview`).flush(overview());

    fixture.destroy();
    vi.advanceTimersByTime(20_000);
    backend.verify();
  });
});
