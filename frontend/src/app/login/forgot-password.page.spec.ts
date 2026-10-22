import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ConfigService } from '../core/config.service';
import { errorInterceptor } from '../core/error.interceptor';
import { ErrorService } from '../core/error.service';
import { ForgotPasswordPage } from './forgot-password.page';

const URL = 'http://api.test/api/v1/auth/forgot-password';

function setup() {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting(), provideRouter([])],
  });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  const backend = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(ForgotPasswordPage);
  return { page: fixture.componentInstance, backend, fixture };
}

describe('ForgotPasswordPage', () => {
  it('asks for the link with the typed e-mail (trimmed) and then shows the same answer for anyone', async () => {
    const { page, backend, fixture } = setup();
    fixture.detectChanges();
    page.email.set('  ana@empresa.com ');

    page.submit();
    expect(page.busy()).toBe(true);
    const request = backend.expectOne(URL);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'ana@empresa.com' });
    request.flush(null, { status: 204, statusText: 'No Content' });

    expect(page.busy()).toBe(false);
    expect(page.sent()).toBe(true);
    fixture.detectChanges();
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-testid="forgot-sent"]')).not.toBeNull();
    // não diz se a conta existe: a frase é condicional
    expect(element.textContent).toContain('Se existir uma conta');
    backend.verify();
  });

  it('sends nothing while the e-mail is blank', () => {
    const { page, backend } = setup();
    page.email.set('   ');

    page.submit();

    backend.expectNone(URL);
    expect(page.sent()).toBe(false);
    backend.verify();
  });

  it('stays on the form and shows the problem when the server cannot be reached', () => {
    const { page, backend } = setup();
    const errors = TestBed.inject(ErrorService);
    page.email.set('ana@empresa.com');

    page.submit();
    backend.expectOne(URL).flush(null, { status: 500, statusText: 'Internal Server Error' });

    expect(page.busy()).toBe(false);
    expect(page.sent()).toBe(false);
    expect(errors.errors()).toHaveLength(1);
    backend.verify();
  });
});
