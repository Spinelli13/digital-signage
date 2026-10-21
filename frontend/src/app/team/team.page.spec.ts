import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthService } from '../core/auth.service';
import { ConfigService } from '../core/config.service';
import { errorInterceptor } from '../core/error.interceptor';
import { ErrorService } from '../core/error.service';
import { Role, TeamMember } from '../core/models';
import { meFixture } from '../testing/fixtures';
import { TeamPage } from './team.page';

const API = 'http://api.test/api/v1';

const member = (id: number, name: string, role: Role = 'EDITOR', active = true): TeamMember => ({
  id,
  name,
  email: `${name.toLowerCase()}@empresa.com`,
  role,
  active,
  createdAt: '',
});

describe('TeamPage', () => {
  let page: TeamPage;
  let backend: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<TeamPage>>;

  beforeEach(() => {
    localStorage.setItem('signage.token', 'jwt');
    TestBed.configureTestingModule({ providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()] });
    TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
    TestBed.inject(AuthService).me.set(meFixture('ADMIN')); // id 10
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TeamPage);
    page = fixture.componentInstance;
    page.ngOnInit();
    backend.expectOne(`${API}/users`).flush([member(10, 'Ana', 'ADMIN'), member(11, 'Beto', 'EDITOR'), member(12, 'Carla', 'VIEWER', false)]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
    backend.verify();
  });

  it('lists the team', () => {
    expect(page.items().map((m) => m.name)).toEqual(['Ana', 'Beto', 'Carla']);
    expect(page.loaded()).toBe(true);
  });

  it('shows how many seats the plan allows', () => {
    fixture.detectChanges(); // desenhar chama o ngOnInit de novo
    backend.expectOne(`${API}/users`).flush([member(10, 'Ana', 'ADMIN')]);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Usuários ativos: 2 de 5');
  });

  it('knows which member is the logged-in user', () => {
    expect(page.isMe(page.items()[0])).toBe(true);
    expect(page.isMe(page.items()[1])).toBe(false);
  });

  it('requires a name, an e-mail and a password of 8+ characters to add somebody', () => {
    page.openNew();
    expect(page.canSubmit()).toBe(false);

    page.name.set('Dani');
    page.email.set('dani@empresa.com');
    page.password.set('curta');
    expect(page.canSubmit()).toBe(false);

    page.password.set('senha-forte-1');
    expect(page.canSubmit()).toBe(true);
  });

  it('adds a member with the chosen role and refreshes the list', () => {
    page.openNew();
    page.name.set('  Dani ');
    page.email.set(' dani@empresa.com ');
    page.password.set('senha-forte-1');
    page.role.set('VIEWER');

    page.submit();

    const request = backend.expectOne(`${API}/users`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Dani', email: 'dani@empresa.com', password: 'senha-forte-1', role: 'VIEWER' });
    request.flush(member(13, 'Dani', 'VIEWER'));
    expect(page.notice.text()).toBe('Usuário "Dani" adicionado.');
    expect(page.modal()).toBeNull();
    backend.expectOne(`${API}/users`).flush([]);
  });

  it('keeps the window open and shows the server message when the plan has no more seats', () => {
    const errors = TestBed.inject(ErrorService);
    page.openNew();
    page.name.set('Dani');
    page.email.set('dani@empresa.com');
    page.password.set('senha-forte-1');

    page.submit();
    backend
      .expectOne(`${API}/users`)
      .flush({ error: 'O plano Básico permite 5 usuário(s)' }, { status: 402, statusText: 'Payment Required' });

    expect(page.modal()).toBe('new');
    expect(page.busy()).toBe(false);
    expect(errors.errors()[0].message).toContain('permite 5 usuário(s)');
  });

  it('edits the role and the access without sending a password that was left blank', () => {
    page.openEdit(page.items()[1]);
    expect(page.role()).toBe('EDITOR');
    page.role.set('ADMIN');
    page.active.set(false);

    page.submit();

    const request = backend.expectOne(`${API}/users/11`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ name: 'Beto', role: 'ADMIN', active: false });
    request.flush(member(11, 'Beto', 'ADMIN', false));
    expect(page.notice.text()).toBe('Usuário "Beto" salvo.');
    backend.expectOne(`${API}/users`).flush([]);
  });

  it('sends a new password only when one is typed', () => {
    page.openEdit(page.items()[1]);
    page.password.set('nova-senha-1');

    page.submit();

    const request = backend.expectOne(`${API}/users/11`);
    expect((request.request.body as { password?: string }).password).toBe('nova-senha-1');
    request.flush(member(11, 'Beto'));
    backend.expectOne(`${API}/users`).flush([]);
  });

  it('refuses a too-short replacement password', () => {
    page.openEdit(page.items()[1]);
    page.password.set('curta');

    expect(page.canSubmit()).toBe(false);
  });

  it('does not let you deactivate yourself', () => {
    page.openEdit(page.items()[0]);

    expect(page.editingSelf()).toBe(true);
    page.openEdit(page.items()[1]);
    expect(page.editingSelf()).toBe(false);
  });

  it('reloads the account when the edit was on yourself (your role or name may have changed)', () => {
    page.openEdit(page.items()[0]);
    page.name.set('Ana Maria');

    page.submit();
    backend.expectOne(`${API}/users/10`).flush(member(10, 'Ana Maria', 'ADMIN'));

    backend.expectOne(`${API}/users`).flush([]);
    backend.expectOne(`${API}/auth/me`).flush(meFixture('ADMIN', { name: 'Ana Maria' }));
  });

  it('asks before deleting and refreshes the list and the seat count', () => {
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(true);

    page.remove(page.items()[1]);

    expect(confirmation).toHaveBeenCalledWith(expect.stringContaining('Beto'));
    const request = backend.expectOne(`${API}/users/11`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    expect(page.notice.text()).toBe('Usuário "Beto" excluído.');
    backend.expectOne(`${API}/users`).flush([]);
    backend.expectOne(`${API}/auth/me`).flush(meFixture('ADMIN'));
  });

  it('does nothing when the deletion is not confirmed', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    page.remove(page.items()[1]);

    backend.expectNone(`${API}/users/11`);
  });

  it('explains each role', () => {
    expect(page.roleText('EDITOR')).toBe('Editor');
    expect(page.helpText('VIEWER')).toContain('Só consulta');
  });
});
