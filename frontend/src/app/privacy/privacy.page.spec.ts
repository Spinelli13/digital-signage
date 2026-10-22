import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ConfigService } from '../core/config.service';
import { FileSaver } from '../core/download';
import { PrivacyRequestEntry, PrivacySearch, PrivacySettings } from '../core/models';
import { PrivacyPage } from './privacy.page';

const API = 'http://api.test/api/v1';

const SETTINGS: PrivacySettings = {
  contactName: 'Ana Souza',
  contactEmail: 'privacidade@loja.com',
  ticketRetentionDays: 30,
  couponNameRetentionDays: 90,
  eventsRetentionDays: 90,
  playLogRetentionDays: 14,
};

const FOUND: PrivacySearch = {
  tickets: [{ id: 1, queue: 'Geral', label: 'A001', day: '2026-11-05', name: 'Maria Souza', source: 'REMOTE', status: 'DONE', issuedAt: '2026-11-05T12:00:00Z', consentAt: '2026-11-05T12:00:00Z' }],
  coupons: [{ id: 2, code: 'ABCD-2345', promotion: '10% na primeira compra', name: 'Maria Souza', status: 'ISSUED', claimedAt: '2026-11-05T12:00:00Z', expiresAt: null, redeemedAt: null, consentAt: null }],
  users: [{ id: 3, name: 'Maria Souza', email: 'maria@loja.com', role: 'VIEWER', active: true, createdAt: '2026-10-01T12:00:00Z' }],
  truncated: false,
};

const HISTORY: PrivacyRequestEntry[] = [
  { id: 9, kind: 'EXPORT', tickets: 1, coupons: 1, users: 1, note: 'Protocolo 123', performedBy: 'admin@loja.com', performedAt: '2026-11-05T13:00:00Z' },
];

function setup() {
  const saver = { save: vi.fn() };
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: FileSaver, useValue: saver }],
  });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  const backend = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(PrivacyPage);
  const page = fixture.componentInstance;
  fixture.detectChanges();
  backend.expectOne(`${API}/privacy/settings`).flush(SETTINGS);
  backend.expectOne(`${API}/privacy/requests`).flush(HISTORY);
  fixture.detectChanges();
  return { page, fixture, backend, saver, text: () => ((fixture.nativeElement as HTMLElement).textContent ?? '').replace(/\s+/g, ' ') };
}

describe('PrivacyPage (Privacidade e dados)', () => {
  afterEach(() => vi.restoreAllMocks());

  it('loads the contact, the retention periods and the requests already handled', () => {
    const { page, text, backend } = setup();

    expect(page.contactEmail()).toBe('privacidade@loja.com');
    expect(page.ticketDays()).toBe(30);
    expect(page.couponDays()).toBe(90);
    expect(page.eventsDays()).toBe(90);
    expect(page.playDays()).toBe(14);
    expect(text()).toContain('Pedidos atendidos');
    expect(text()).toContain('Protocolo 123');
    expect(text()).toContain('1 senha(s), 1 cupom(ns), 1 usuário(s)');
    backend.verify();
  });

  it('saves the contact and the four retention periods', () => {
    const { page, backend } = setup();
    page.contactName.set(' Bruno ');
    page.ticketDays.set(15);
    page.couponDays.set(45);

    page.saveSettings();

    const request = backend.expectOne(`${API}/privacy/settings`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      contactName: 'Bruno',
      contactEmail: 'privacidade@loja.com',
      ticketRetentionDays: 15,
      couponNameRetentionDays: 45,
      eventsRetentionDays: 90,
      playLogRetentionDays: 14,
    });
    request.flush({ ...SETTINGS, contactName: 'Bruno', ticketRetentionDays: 15, couponNameRetentionDays: 45 });
    expect(page.notice.text()).toBe('Configurações de privacidade salvas.');
    expect(page.ticketDays()).toBe(15);
    backend.verify();
  });

  it('does not search with less than 3 characters', () => {
    const { page, backend } = setup();
    page.term.set('ma');

    page.search();

    backend.expectNone((r) => r.url === `${API}/privacy/search`);
  });

  it('shows what was found for a person: tickets, coupons and team members', () => {
    const { page, fixture, backend, text } = setup();
    page.term.set(' Maria Souza ');

    page.search();
    const request = backend.expectOne((r) => r.url === `${API}/privacy/search`);
    expect(request.request.params.get('q')).toBe('Maria Souza');
    request.flush(FOUND);
    fixture.detectChanges();

    expect(text()).toContain('A001');
    expect(text()).toContain('ABCD-2345');
    expect(text()).toContain('maria@loja.com');
    expect(text()).toContain('Para excluir um usuário do painel, use a tela Equipe');
    backend.verify();
  });

  it('says so when nothing is found', () => {
    const { page, fixture, backend, text } = setup();
    page.term.set('ninguém');

    page.search();
    backend.expectOne((r) => r.url === `${API}/privacy/search`).flush({ tickets: [], coupons: [], users: [], truncated: false });
    fixture.detectChanges();

    expect(text()).toContain('Nada encontrado com esse texto.');
  });

  it('downloads the copy of the SEARCHED person (not of what was typed afterwards) with the note, and reloads the history', () => {
    const { page, fixture, backend, saver } = setup();
    page.term.set('Maria Souza');
    page.search();
    backend.expectOne((r) => r.url === `${API}/privacy/search`).flush(FOUND);
    fixture.detectChanges();
    page.term.set('Outra Pessoa');
    page.note.set(' Protocolo 456 ');

    page.exportData();

    const request = backend.expectOne(`${API}/privacy/export`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ term: 'Maria Souza', note: 'Protocolo 456' });
    request.flush(new Blob(['{}'], { type: 'application/json' }));
    expect(saver.save).toHaveBeenCalledTimes(1);
    expect(saver.save.mock.calls[0][1]).toMatch(/^dados-pessoais-\d{4}-\d{2}-\d{2}\.json$/);
    expect(page.notice.text()).toContain('Cópia dos dados baixada');
    backend.expectOne(`${API}/privacy/requests`).flush(HISTORY);
    backend.verify();
  });

  it('only erases after the confirmation, then searches again and reloads the history', () => {
    const { page, fixture, backend } = setup();
    page.term.set('Maria Souza');
    page.search();
    backend.expectOne((r) => r.url === `${API}/privacy/search`).flush(FOUND);
    fixture.detectChanges();

    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(false);
    page.erase();
    expect(confirmation).toHaveBeenCalledTimes(1);
    expect(confirmation.mock.calls[0][0]).toContain('1 senha(s) e 1 cupom(ns)');
    backend.expectNone(`${API}/privacy/erase`);

    confirmation.mockReturnValue(true);
    page.note.set('Protocolo 789');
    page.erase();
    const request = backend.expectOne(`${API}/privacy/erase`);
    expect(request.request.body).toEqual({ term: 'Maria Souza', note: 'Protocolo 789' });
    request.flush({ tickets: 1, coupons: 1, usersFound: 1 });
    expect(page.notice.text()).toBe('Dados apagados: 1 senha(s) e 1 cupom(ns). O pedido ficou registrado.');
    backend.expectOne(`${API}/privacy/requests`).flush(HISTORY);
    const again = backend.expectOne((r) => r.url === `${API}/privacy/search`);
    expect(again.request.params.get('q')).toBe('Maria Souza');
    again.flush({ tickets: [], coupons: [], users: FOUND.users, truncated: false });
    backend.verify();
  });
});
