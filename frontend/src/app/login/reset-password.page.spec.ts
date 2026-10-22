import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { ConfigService } from '../core/config.service';
import { errorInterceptor } from '../core/error.interceptor';
import { ErrorService } from '../core/error.service';
import { ResetPasswordPage } from './reset-password.page';

const URL = 'http://api.test/api/v1/auth/reset-password';
const TOKEN = 'a1b2c3d4e5f6';

function setup() {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ token: TOKEN }) } } },
    ],
  });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  const backend = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(ResetPasswordPage);
  return { page: fixture.componentInstance, backend, fixture };
}

describe('ResetPasswordPage', () => {
  it('saves the new password with the link token and then invites the person to sign in', async () => {
    const { page, backend, fixture } = setup();
    fixture.detectChanges();
    page.password.set('senha-nova-123');
    page.confirm.set('senha-nova-123');
    expect(page.ready()).toBe(true);

    page.submit();
    const request = backend.expectOne(URL);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ token: TOKEN, newPassword: 'senha-nova-123' });
    request.flush(null, { status: 204, statusText: 'No Content' });

    expect(page.done()).toBe(true);
    // a senha não fica na tela depois de salva
    expect(page.password()).toBe('');
    expect(page.confirm()).toBe('');
    fixture.detectChanges();
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="reset-done"]')).not.toBeNull();
    backend.verify();
  });

  it('does not send a short password or one that was typed differently the second time', () => {
    const { page, backend } = setup();

    page.password.set('curta');
    page.confirm.set('curta');
    expect(page.ready()).toBe(false);
    expect(page.problem()).toContain('pelo menos 8 caracteres');
    page.submit();

    page.password.set('senha-nova-123');
    page.confirm.set('senha-nova-124');
    expect(page.ready()).toBe(false);
    expect(page.problem()).toContain('não conferem');
    page.submit();

    backend.expectNone(URL);
    backend.verify();
  });

  it('stays on the form and shows the reason when the link no longer works', () => {
    const { page, backend } = setup();
    const errors = TestBed.inject(ErrorService);
    page.password.set('senha-nova-123');
    page.confirm.set('senha-nova-123');

    page.submit();
    backend.expectOne(URL).flush({ error: 'Este link não vale mais. Peça um novo em "Esqueci a senha".' }, { status: 400, statusText: 'Bad Request' });

    expect(page.busy()).toBe(false);
    expect(page.done()).toBe(false);
    expect(errors.errors()[0].message).toContain('Este link não vale mais');
    backend.verify();
  });
});
