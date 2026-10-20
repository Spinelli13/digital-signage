import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { ConfigService } from '../core/config.service';
import { Me, Role } from '../core/models';
import { meFixture, tenantFixture } from '../testing/fixtures';
import { Shell } from './shell';

const API = 'http://api.test/api/v1';

describe('Shell', () => {
  let backend: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    localStorage.setItem('signage.token', 'jwt');
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => {
    localStorage.clear();
    backend.verify();
  });

  async function render(me: Me): Promise<HTMLElement> {
    return (await renderShell(me)).element;
  }

  async function renderShell(me: Me): Promise<{ element: HTMLElement; component: Shell }> {
    const fixture = TestBed.createComponent(Shell);
    backend.expectOne(`${API}/auth/me`).flush(me);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { element: fixture.nativeElement as HTMLElement, component: fixture.componentInstance };
  }

  const links = (element: HTMLElement) => Array.from(element.querySelectorAll('nav a')).map((a) => a.textContent?.trim() ?? '');

  it('shows who is logged in, the client and the plan', async () => {
    const element = await render(meFixture('ADMIN'));

    expect(element.querySelector('[data-testid="tenant"]')?.textContent).toContain('Minha Empresa');
    expect(element.querySelector('[data-testid="tenant"]')?.textContent).toContain('Básico');
    expect(element.querySelector('.foot')?.textContent).toContain('Ana');
    expect(element.querySelector('.foot')?.textContent).toContain('Administrador');
  });

  it('does not offer team, billing or platform to a viewer', async () => {
    const element = await render(meFixture('VIEWER'));

    const text = links(element).join('|');
    expect(text).toContain('Dispositivos');
    expect(text).not.toContain('Equipe');
    expect(text).not.toContain('Plano e cobrança');
    expect(text).not.toContain('Clientes');
  });

  it('offers team and billing to a client admin, but not the platform', async () => {
    const element = await render(meFixture('ADMIN'));

    const text = links(element).join('|');
    expect(text).toContain('Equipe');
    expect(text).toContain('Plano e cobrança');
    expect(text).not.toContain('Clientes');
  });

  it('offers the platform section to the operator', async () => {
    const element = await render(meFixture('PLATFORM_ADMIN'));

    const text = links(element).join('|');
    expect(text).toContain('Visão geral');
    expect(text).toContain('Clientes');
    expect(text).toContain('Planos');
    expect(text).toContain('Faturas');
    expect(element.textContent).toContain('Plataforma');
  });

  it('warns the operator that they are acting on another client, with a way back', async () => {
    sessionStorage.setItem('signage.acting-tenant', '7');
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
    backend = TestBed.inject(HttpTestingController);

    const element = await render(meFixture('PLATFORM_ADMIN', { tenant: tenantFixture({ id: 7, name: 'Cliente Sete' }) }));

    expect(element.querySelector('.banner.visiting')?.textContent).toContain('Cliente Sete');
    expect(element.querySelector('.banner.visiting button')?.textContent).toContain('Voltar ao meu cliente');
  });

  it('warns the admin when the trial is about to end', async () => {
    const soon = new Date(Date.now() + 3 * 86_400_000).toISOString();
    const element = await render(meFixture('ADMIN', { tenant: tenantFixture({ status: 'TRIAL', trialEndsAt: soon }) }));

    expect(element.querySelector('.banner')?.textContent).toContain('período de teste termina em 3 dia(s)');
  });

  it('warns the admin about an overdue invoice', async () => {
    const element = await render(meFixture('ADMIN', { tenant: tenantFixture({ status: 'PAST_DUE' }) }));

    expect(element.querySelector('.banner')?.textContent).toContain('fatura em atraso');
  });

  it('does not nag a viewer about billing', async () => {
    const element = await render(meFixture('VIEWER', { tenant: tenantFixture({ status: 'PAST_DUE' }) }));

    expect(element.querySelector('.banner')).toBeNull();
  });

  it('sends the admin of a suspended client to "Plano e cobrança" and hides the rest of the menu', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const element = await render(meFixture('ADMIN', { tenant: tenantFixture({ status: 'SUSPENDED' }) }));

    expect(navigate).toHaveBeenCalledWith(['/plano']);
    const text = links(element).join('|');
    expect(text).toContain('Plano e cobrança');
    expect(text).not.toContain('Dispositivos');
    expect(element.querySelector('.banner.danger')?.textContent).toContain('suspensa');
  });

  it('tells a non-admin of a suspended client to talk to the account admin', async () => {
    const element = await render(meFixture('EDITOR', { tenant: tenantFixture({ status: 'SUSPENDED' }) }));

    expect(element.querySelector('main')?.textContent).toContain('Assinatura suspensa');
    expect(element.querySelector('main')?.textContent).toContain('As TVs continuam exibindo');
  });

  it('does not block the operator on a suspended client (support must be able to look)', async () => {
    const element = await render(meFixture('PLATFORM_ADMIN', { tenant: tenantFixture({ status: 'SUSPENDED' }) }));

    expect(links(element).join('|')).toContain('Dispositivos');
  });

  it('changes the password with the current one and tells the user', async () => {
    const { component } = await renderShell(meFixture('ADMIN'));

    component.openAccount();
    component.currentPassword.set('velha-senha');
    component.newPassword.set('nova-senha-1');
    component.confirmPassword.set('nova-senha-1');
    expect(component.passwordProblem()).toBeNull();
    component.changePassword();

    const request = backend.expectOne(`${API}/auth/password`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ currentPassword: 'velha-senha', newPassword: 'nova-senha-1' });
    request.flush(null, { status: 204, statusText: 'No Content' });
    expect(component.notice.text()).toBe('Senha trocada.');
    expect(component.newPassword()).toBe('');
  });

  it('refuses a short or mismatched new password before calling the server', async () => {
    const { component } = await renderShell(meFixture('ADMIN'));

    component.newPassword.set('curta');
    expect(component.passwordProblem()).toContain('8 caracteres');

    component.newPassword.set('senha-comprida');
    component.confirmPassword.set('outra-coisa');
    expect(component.passwordProblem()).toContain('não conferem');

    component.currentPassword.set('x');
    component.changePassword();
    backend.expectNone(`${API}/auth/password`);
  });

  it('logs out and goes to the login', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const { component } = await renderShell(meFixture('ADMIN'));

    component.logout();

    expect(auth.isLoggedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it.each<[Role, boolean]>([
    ['VIEWER', false],
    ['EDITOR', false],
    ['ADMIN', true],
    ['PLATFORM_ADMIN', true],
  ])('%s can administer the client: %s', async (role, expected) => {
    await render(meFixture(role));

    expect(auth.can('admin')).toBe(expected);
  });
});
