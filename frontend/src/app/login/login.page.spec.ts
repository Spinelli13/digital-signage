import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';

import { ConfigService } from '../core/config.service';
import { meFixture } from '../testing/fixtures';
import { LoginPage } from './login.page';

function setup(returnUrl: string | null) {
  localStorage.clear();
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(returnUrl === null ? {} : { returnUrl }) } } },
    ],
  });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  const backend = TestBed.inject(HttpTestingController);
  const page = TestBed.createComponent(LoginPage).componentInstance;
  page.email.set('ana@empresa.com');
  page.password.set('senha');
  return { page, backend, navigate };
}

async function signIn(returnUrl: string | null) {
  const { page, backend, navigate } = setup(returnUrl);
  const done = page.submit();
  backend.expectOne('http://api.test/api/v1/auth/login').flush({ token: 'jwt', tokenType: 'Bearer', expiresInSeconds: 3600 });
  await Promise.resolve();
  await new Promise((resolve) => setTimeout(resolve));
  backend.expectOne('http://api.test/api/v1/auth/me').flush(meFixture('ADMIN'));
  await done;
  return navigate;
}

describe('LoginPage — para onde volta depois de entrar', () => {
  it('goes back to the page the person was opening (the TV QR Code)', async () => {
    expect(await signIn('/parear/ABC234')).toHaveBeenCalledWith('/parear/ABC234');
  });

  it('goes to the dashboard when nothing was asked for', async () => {
    expect(await signIn(null)).toHaveBeenCalledWith('/dashboard');
  });

  it('never follows an address to another site', async () => {
    expect(await signIn('//evil.example/phish')).toHaveBeenCalledWith('/dashboard');
    TestBed.resetTestingModule();
    expect(await signIn('https://evil.example')).toHaveBeenCalledWith('/dashboard');
  });
});

describe('LoginPage — esqueci a senha', () => {
  it('links to the password recovery page', async () => {
    setup(null);
    const fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();
    await fixture.whenStable();

    const link = (fixture.nativeElement as HTMLElement).querySelector('.forgot a');
    expect(link?.getAttribute('href')).toBe('/esqueci-a-senha');
  });
});
