import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from './core/auth.service';
import { ConfigService } from './core/config.service';
import { errorInterceptor } from './core/error.interceptor';
import { ErrorService } from './core/error.service';
import { resetLangForTests, setLang } from './core/i18n';
import { DashboardPage } from './dashboard/dashboard.page';
import { LocationsPage } from './locations/locations.page';
import { Location } from './core/models';
import { meFixture } from './testing/fixtures';

const API = 'http://api.test/api/v1';

/** Telas de verdade no idioma inglês: o texto, os avisos e as mensagens do servidor. */
describe('painel em inglês', () => {
  beforeEach(() => {
    localStorage.clear();
    resetLangForTests('pt');
    localStorage.setItem('signage.token', 'jwt');
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting(), provideRouter([])],
    });
    TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
    TestBed.inject(AuthService).me.set(meFixture('ADMIN'));
  });

  afterEach(() => {
    localStorage.clear();
    resetLangForTests('pt');
    TestBed.inject(HttpTestingController).verify();
  });

  const location = (id: number, name: string): Location => ({ id, name, description: null, deviceCount: 2, onlineCount: 1, createdAt: '', updatedAt: '' });

  function show<T>(page: Type<T>) {
    const fixture = TestBed.createComponent(page);
    fixture.detectChanges();
    return { fixture, backend: TestBed.inject(HttpTestingController) };
  }

  const text = (fixture: { nativeElement: unknown }) => (fixture.nativeElement as HTMLElement).textContent ?? '';

  it('mostra a tela de locais em inglês e em português na troca, sem recarregar', async () => {
    const { fixture, backend } = show(LocationsPage);
    backend.expectOne(`${API}/locations`).flush([location(1, 'Loja Centro')]);
    fixture.detectChanges();
    expect(text(fixture)).toContain('Pontos & Locais');
    expect(text(fixture)).toContain('+ Novo local');

    setLang('en');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(text(fixture)).toContain('Sites & Locations');
    expect(text(fixture)).toContain('+ New location');
    expect(text(fixture)).toContain('1 of 2'); // "{online} de {total}"
    expect(text(fixture)).not.toContain('Novo local');
    // o nome que a pessoa digitou nunca é traduzido
    expect(text(fixture)).toContain('Loja Centro');
  });

  it('os avisos de confirmação saem no idioma da hora', () => {
    setLang('en');
    const { fixture, backend } = show(LocationsPage);
    backend.expectOne(`${API}/locations`).flush([]);
    const page = fixture.componentInstance;

    page.openNew();
    page.name.set('Matriz');
    page.submit();
    backend.expectOne(`${API}/locations`).flush(location(2, 'Matriz'));

    expect(page.notice.text()).toBe('Location "Matriz" created.');
    backend.expectOne(`${API}/locations`).flush([]);
  });

  it('traduz a mensagem de erro do servidor e a ação que falhou', () => {
    setLang('en');
    const errors = TestBed.inject(ErrorService);
    const { fixture, backend } = show(LocationsPage);
    backend.expectOne(`${API}/locations`).flush([]);
    const page = fixture.componentInstance;

    page.openNew();
    page.name.set('Loja Centro');
    page.submit();
    backend.expectOne(`${API}/locations`).flush({ error: 'Já existe um local chamado "Loja Centro"' }, { status: 409, statusText: 'Conflict' });

    expect(errors.errors()[0].message).toBe('Create location: A location named "Loja Centro" already exists');
  });

  it('o erro de conexão e os códigos HTTP também saem em inglês', () => {
    setLang('en');
    const errors = TestBed.inject(ErrorService);
    const { fixture, backend } = show(LocationsPage);

    backend.expectOne(`${API}/locations`).flush(null, { status: 500, statusText: 'Server Error' });

    expect(errors.errors()[0].message).toBe('Load the locations: the server hit an error (HTTP 500). Please try again shortly.');
    fixture.destroy();
  });

  it('o dashboard e as situações das TVs ficam em inglês', async () => {
    setLang('en');
    const { fixture, backend } = show(DashboardPage);
    backend.expectOne(`${API}/summary`).flush({
      devices: { total: 3, online: 1, offline: 1, never: 1, pending: 0, disabled: 0 },
      locations: 1,
      groups: 0,
      media: 2,
      playlists: 1,
      campaigns: { total: 2, activeNow: 1 },
      openAlerts: 1,
      byLocation: [{ id: 1, name: 'Centro', total: 3, online: 1 }],
      attention: [{ id: 5, name: 'TV Vitrine', location: 'Centro', connection: 'OFFLINE', lastSeenAt: null }],
    });
    fixture.detectChanges();
    await fixture.whenStable();

    const content = text(fixture);
    expect(content).toContain('Overview of your advertising network.');
    expect(content).toContain('Online now');
    expect(content).toContain('Devices needing attention');
    expect(content).toContain('1 of 3');
    expect(content).toContain('of 2'); // "de {total}" nas campanhas
    expect(content).toContain('Offline');
    expect(content).toContain('TV Vitrine');
    fixture.destroy();
  });
});
