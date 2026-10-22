import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';

import { ConfigService } from '../core/config.service';
import { errorInterceptor } from '../core/error.interceptor';
import { PublicQueue, PublicTicket } from '../core/models';
import { QueueTakePage, TicketStatusPage, deviceId, rememberedTicket, reasonOf } from './public-ticket.page';

const API = 'http://api.test/api/v1';
const QUEUE_TOKEN = 'a'.repeat(32);
const TICKET_TOKEN = 'b'.repeat(32);

const notice = { controllerName: 'Loja Aurora', contactEmail: 'privacidade@loja.com', retentionDays: 30, version: '2026-10-21' };
const queue = (overrides: Partial<PublicQueue> = {}): PublicQueue => ({ name: 'Atendimento geral', open: true, waiting: 3, notice, ...overrides });

const ticket = (overrides: Partial<PublicTicket> = {}): PublicTicket => ({
  ticketToken: TICKET_TOKEN,
  label: 'A012',
  queueName: 'Atendimento geral',
  status: 'WAITING',
  counter: null,
  ahead: 2,
  waiting: 5,
  calledAtMs: null,
  callCount: 0,
  ...overrides,
});

function setup<T>(component: new () => T, token: string, render = false) {
  localStorage.clear();
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ token }) } } },
    ],
  });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  const backend = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(component);
  if (render) {
    fixture.detectChanges();
  }
  return { page: fixture.componentInstance, fixture, backend, navigate };
}

const textOf = (fixture: { nativeElement: unknown }) => ((fixture.nativeElement as HTMLElement).textContent ?? '').replace(/\s+/g, ' ');

describe('QueueTakePage (QR Code da fila)', () => {
  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
  });

  it('shows the queue and how many wait, and takes a ticket with the typed name', async () => {
    const { page, fixture, backend, navigate } = setup(QueueTakePage, QUEUE_TOKEN, true);
    backend.expectOne(`${API}/public/queues/${QUEUE_TOKEN}`).flush(queue());
    fixture.detectChanges();
    await fixture.whenStable();

    expect(textOf(fixture)).toContain('Atendimento geral');
    expect(textOf(fixture)).toContain('3 pessoa(s) aguardando agora.');
    page.customerName.set('  Maria ');
    page.consent.set(true);
    page.take();

    const request = backend.expectOne(`${API}/public/queues/${QUEUE_TOKEN}/tickets`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ customerName: 'Maria', consent: true });
    // o código deste aparelho vai junto (e é sempre o mesmo neste celular)
    expect(request.request.headers.get('X-Client-Id')).toBe(deviceId());
    request.flush(ticket());
    expect(rememberedTicket(QUEUE_TOKEN)).toBe(TICKET_TOKEN);
    expect(navigate).toHaveBeenCalledWith(['/senha', TICKET_TOKEN]);
    backend.verify();
  });

  it('sends no name when the field is empty', () => {
    const { page, backend } = setup(QueueTakePage, QUEUE_TOKEN);
    page.ngOnInit();
    backend.expectOne(`${API}/public/queues/${QUEUE_TOKEN}`).flush(queue());

    page.take();

    expect(backend.expectOne(`${API}/public/queues/${QUEUE_TOKEN}/tickets`).request.body).toEqual({ customerName: null, consent: false });
  });

  it('shows the privacy notice and only asks for agreement when a name is typed', async () => {
    const { page, fixture, backend } = setup(QueueTakePage, QUEUE_TOKEN, true);
    backend.expectOne(`${API}/public/queues/${QUEUE_TOKEN}`).flush(queue());
    fixture.detectChanges();
    await fixture.whenStable();

    const root = fixture.nativeElement as HTMLElement;
    expect(textOf(fixture)).toContain('O nome serve só para chamar a sua senha.');
    expect(textOf(fixture)).toContain('Loja Aurora é a responsável por esse dado e o apaga em 30 dia(s).');
    expect(textOf(fixture)).toContain('privacidade@loja.com');
    expect(root.querySelector('a[href="/privacidade"]')).not.toBeNull();
    // sem nome não há o que concordar e a pessoa pega a senha
    expect(root.querySelector('input[name=consent]')).toBeNull();
    expect((root.querySelector('button.primary') as HTMLButtonElement).disabled).toBe(false);

    // com um nome, é preciso concordar antes
    page.customerName.set('Maria');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(root.querySelector('input[name=consent]')).not.toBeNull();
    expect((root.querySelector('button.primary') as HTMLButtonElement).disabled).toBe(true);

    page.consent.set(true);
    fixture.detectChanges();
    await fixture.whenStable();
    expect((root.querySelector('button.primary') as HTMLButtonElement).disabled).toBe(false);
  });

  it('never sends the agreement without a name, even if the box was ticked before the name was erased', () => {
    const { page, backend } = setup(QueueTakePage, QUEUE_TOKEN);
    page.ngOnInit();
    backend.expectOne(`${API}/public/queues/${QUEUE_TOKEN}`).flush(queue());
    page.customerName.set('Maria');
    page.consent.set(true);
    page.customerName.set('');

    page.take();

    expect(backend.expectOne(`${API}/public/queues/${QUEUE_TOKEN}/tickets`).request.body).toEqual({ customerName: null, consent: false });
  });

  it('goes straight to the ticket the person already took, without asking the server for a new one', () => {
    const { page, backend, navigate } = setup(QueueTakePage, QUEUE_TOKEN);
    localStorage.setItem(`smartplay.myticket.${QUEUE_TOKEN}`, TICKET_TOKEN);

    page.ngOnInit();

    expect(navigate).toHaveBeenCalledWith(['/senha', TICKET_TOKEN], { replaceUrl: true });
    backend.expectNone(`${API}/public/queues/${QUEUE_TOKEN}`);
    backend.verify();
  });

  it('explains a queue that is not taking tickets on the phone', async () => {
    const { page, fixture, backend } = setup(QueueTakePage, QUEUE_TOKEN, true);
    backend.expectOne(`${API}/public/queues/${QUEUE_TOKEN}`).flush(queue({ open: false }));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(textOf(fixture)).toContain('Esta fila não está recebendo senhas pelo celular agora');
    expect(fixture.nativeElement.querySelector('button.primary')).toBeNull();
    expect(page.queue()?.open).toBe(false);
  });

  it('shows the reason of the server when the QR Code is not valid or the limit was reached', async () => {
    const { page, fixture, backend } = setup(QueueTakePage, QUEUE_TOKEN, true);
    backend.expectOne(`${API}/public/queues/${QUEUE_TOKEN}`).flush({ error: 'Este QR Code não é válido. Procure o atendimento.' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    await fixture.whenStable();
    expect(textOf(fixture)).toContain('Este QR Code não é válido. Procure o atendimento.');

    page.queue.set(queue());
    page.take();
    backend.expectOne(`${API}/public/queues/${QUEUE_TOKEN}/tickets`).flush({ error: 'Você já pegou senhas demais por hora. Procure o atendimento.' }, { status: 429, statusText: 'Too Many Requests' });
    fixture.detectChanges();
    expect(page.problem()).toBe('Você já pegou senhas demais por hora. Procure o atendimento.');
    expect(page.busy()).toBe(false);
  });

  it('creates a code for this phone once and keeps it', () => {
    localStorage.clear();

    const first = deviceId();

    expect(first).toMatch(/^[0-9a-zA-Z-]{8,64}$/);
    expect(deviceId()).toBe(first);
    expect(localStorage.getItem('smartplay.client')).toBe(first);
  });

  it('says something useful when the server cannot be reached at all', () => {
    expect(reasonOf({ status: 0 })).toBe('Não foi possível falar com o servidor agora. Confira a internet e tente de novo.');
    expect(reasonOf(null)).toContain('Não foi possível falar com o servidor');
  });
});

describe('TicketStatusPage (a senha no celular)', () => {
  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
    delete (navigator as unknown as { vibrate?: unknown }).vibrate;
  });

  it('shows the ticket and how many people are ahead', async () => {
    const { fixture, backend } = setup(TicketStatusPage, TICKET_TOKEN, true);
    backend.expectOne(`${API}/public/tickets/${TICKET_TOKEN}`).flush(ticket());
    fixture.detectChanges();
    await fixture.whenStable();

    const text = textOf(fixture);
    expect(text).toContain('A012');
    expect(text).toContain('Atendimento geral');
    expect(text).toContain('Há 2 pessoas na sua frente.');
    expect(text).toContain('Sair da fila');
    backend.verify();
  });

  it('says "you are next" and "1 person ahead" in the singular', () => {
    const { page, backend } = setup(TicketStatusPage, TICKET_TOKEN);
    page.ngOnInit();
    backend.expectOne(`${API}/public/tickets/${TICKET_TOKEN}`).flush(ticket({ ahead: 0 }));
    expect(page.aheadText()).toBe('Você é o próximo da fila.');

    page.refresh();
    backend.expectOne(`${API}/public/tickets/${TICKET_TOKEN}`).flush(ticket({ ahead: 1 }));
    expect(page.aheadText()).toBe('Há 1 pessoa na sua frente.');
  });

  it('asks the server again every few seconds and stops when the service is over', () => {
    vi.useFakeTimers();
    const { page, backend } = setup(TicketStatusPage, TICKET_TOKEN);
    page.ngOnInit();
    backend.expectOne(`${API}/public/tickets/${TICKET_TOKEN}`).flush(ticket());

    vi.advanceTimersByTime(5000);
    backend.expectOne(`${API}/public/tickets/${TICKET_TOKEN}`).flush(ticket({ ahead: 1 }));
    expect(page.ticket()?.ahead).toBe(1);

    vi.advanceTimersByTime(5000);
    backend.expectOne(`${API}/public/tickets/${TICKET_TOKEN}`).flush(ticket({ status: 'DONE', ahead: 0 }));
    vi.advanceTimersByTime(30_000);
    backend.verify();
  });

  it('warns the person when it is their turn: shows the counter and vibrates the phone, once per call', async () => {
    const vibrate = vi.fn();
    (navigator as unknown as { vibrate: unknown }).vibrate = vibrate;
    const { page, fixture, backend } = setup(TicketStatusPage, TICKET_TOKEN, true);
    backend.expectOne(`${API}/public/tickets/${TICKET_TOKEN}`).flush(ticket());
    expect(vibrate).not.toHaveBeenCalled();

    page.refresh();
    backend.expectOne(`${API}/public/tickets/${TICKET_TOKEN}`).flush(ticket({ status: 'CALLING', counter: 'Guichê 3', callCount: 1, calledAtMs: 1 }));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(textOf(fixture)).toContain('É a sua vez! Vá ao Guichê 3.');
    expect(vibrate).toHaveBeenCalledTimes(1);
    page.refresh();
    backend.expectOne(`${API}/public/tickets/${TICKET_TOKEN}`).flush(ticket({ status: 'CALLING', counter: 'Guichê 3', callCount: 1, calledAtMs: 1 }));
    expect(vibrate).toHaveBeenCalledTimes(1);
    page.refresh();
    backend.expectOne(`${API}/public/tickets/${TICKET_TOKEN}`).flush(ticket({ status: 'CALLING', counter: 'Guichê 3', callCount: 2, calledAtMs: 2 }));
    expect(vibrate).toHaveBeenCalledTimes(2);
  });

  it('forgets the remembered ticket when the service ends, so a new one can be taken', () => {
    const { page, backend } = setup(TicketStatusPage, TICKET_TOKEN);
    localStorage.setItem(`smartplay.myticket.${QUEUE_TOKEN}`, TICKET_TOKEN);
    page.ngOnInit();

    backend.expectOne(`${API}/public/tickets/${TICKET_TOKEN}`).flush(ticket({ status: 'NO_SHOW' }));

    expect(rememberedTicket(QUEUE_TOKEN)).toBeNull();
  });

  it('leaves the queue only after the person confirms', () => {
    const { page, backend } = setup(TicketStatusPage, TICKET_TOKEN);
    page.ngOnInit();
    backend.expectOne(`${API}/public/tickets/${TICKET_TOKEN}`).flush(ticket());
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);

    page.leave();
    backend.expectNone(`${API}/public/tickets/${TICKET_TOKEN}/cancel`);
    page.leave();

    const request = backend.expectOne(`${API}/public/tickets/${TICKET_TOKEN}/cancel`);
    expect(request.request.method).toBe('POST');
    request.flush(ticket({ status: 'CANCELED' }));
    expect(page.ticket()?.status).toBe('CANCELED');
    expect(confirmation).toHaveBeenCalledTimes(2);
  });

  it('keeps the ticket on screen when the connection drops, and tells when the ticket no longer exists', () => {
    const { page, backend } = setup(TicketStatusPage, TICKET_TOKEN);
    localStorage.setItem(`smartplay.myticket.${QUEUE_TOKEN}`, TICKET_TOKEN);
    page.ngOnInit();
    backend.expectOne(`${API}/public/tickets/${TICKET_TOKEN}`).flush(ticket());

    page.refresh();
    backend.expectOne(`${API}/public/tickets/${TICKET_TOKEN}`).error(new ProgressEvent('error'), { status: 0 });
    expect(page.ticket()?.label).toBe('A012');
    expect(page.problem()).toContain('Não foi possível falar com o servidor');

    page.refresh();
    backend.expectOne(`${API}/public/tickets/${TICKET_TOKEN}`).flush({ error: 'Esta senha não foi encontrada. Ela pode ter expirado.' }, { status: 404, statusText: 'Not Found' });
    expect(page.problem()).toBe('Esta senha não foi encontrada. Ela pode ter expirado.');
    expect(rememberedTicket(QUEUE_TOKEN)).toBeNull();
  });
});
