import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ConfigService } from '../core/config.service';
import { errorInterceptor } from '../core/error.interceptor';
import { ErrorService } from '../core/error.service';
import { Location } from '../core/models';
import { LocationsPage } from './locations.page';

const API = 'http://api.test/api/v1';

const location = (id: number, name: string, deviceCount = 0): Location => ({
  id,
  name,
  description: null,
  deviceCount,
  onlineCount: 0,
  createdAt: '',
  updatedAt: '',
});

describe('LocationsPage', () => {
  let page: LocationsPage;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting(), provideRouter([])],
    });
    TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
    backend = TestBed.inject(HttpTestingController);
    page = TestBed.createComponent(LocationsPage).componentInstance;
    page.ngOnInit();
    backend.expectOne(`${API}/locations`).flush([location(1, 'Loja Centro', 2)]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    backend.verify();
  });

  it('lists the locations', () => {
    expect(page.items().map((l) => l.name)).toEqual(['Loja Centro']);
    expect(page.loaded()).toBe(true);
  });

  it('creates a location trimming the name and sending an empty description as null', () => {
    page.openNew();
    page.name.set('  Matriz ');
    page.description.set('   ');

    page.submit();

    const request = backend.expectOne(`${API}/locations`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Matriz', description: null });
    request.flush(location(2, 'Matriz'));
    expect(page.notice.text()).toBe('Local "Matriz" criado.');
    expect(page.modal()).toBeNull();
    backend.expectOne(`${API}/locations`).flush([]);
  });

  it('edits an existing location with a PUT', () => {
    page.openEdit(page.items()[0]);
    expect(page.name()).toBe('Loja Centro');
    page.name.set('Loja Centro II');
    page.description.set('Rua das Flores, 100');

    page.submit();

    const request = backend.expectOne(`${API}/locations/1`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ name: 'Loja Centro II', description: 'Rua das Flores, 100' });
    request.flush(location(1, 'Loja Centro II'));
    expect(page.notice.text()).toBe('Local "Loja Centro II" salvo.');
    backend.expectOne(`${API}/locations`).flush([]);
  });

  it('does not send a blank name', () => {
    page.openNew();
    page.name.set('   ');

    page.submit();

    backend.expectNone(`${API}/locations`);
  });

  it('keeps the window open and shows the server message when the name is already used', () => {
    const errors = TestBed.inject(ErrorService);
    page.openNew();
    page.name.set('Loja Centro');

    page.submit();
    backend
      .expectOne(`${API}/locations`)
      .flush({ error: 'Já existe um local chamado "Loja Centro"' }, { status: 409, statusText: 'Conflict' });

    expect(page.modal()).toBe('new');
    expect(page.busy()).toBe(false);
    expect(errors.errors()[0].message).toContain('Já existe um local chamado "Loja Centro"');
  });

  /** O aviso precisa tranquilizar: apagar um local não apaga as TVs. */
  it('warns that the TVs stay when deleting a location that has some', () => {
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(false);

    page.remove(location(1, 'Loja Centro', 2));

    expect(confirmation).toHaveBeenCalledWith(expect.stringContaining('2 TV(s) deste local continuam funcionando'));
    backend.expectNone(`${API}/locations/1`);
  });

  it('deletes after the user confirms and refreshes the list', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    page.remove(location(1, 'Loja Centro', 0));

    const request = backend.expectOne(`${API}/locations/1`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    expect(page.notice.text()).toBe('Local "Loja Centro" excluído.');
    backend.expectOne(`${API}/locations`).flush([]);
    expect(page.items()).toEqual([]);
  });
});
