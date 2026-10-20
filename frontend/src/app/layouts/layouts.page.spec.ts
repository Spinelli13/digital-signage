import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ConfigService } from '../core/config.service';
import { errorInterceptor } from '../core/error.interceptor';
import { ErrorService } from '../core/error.service';
import { Layout, LayoutTemplate, Playlist } from '../core/models';
import { LayoutsPage } from './layouts.page';

const API = 'http://api.test/api/v1';

const layout = (id: number, name: string, deviceCount = 0): Layout => ({
  id,
  name,
  description: null,
  configVersion: 2,
  deviceCount,
  createdAt: '',
  updatedAt: '',
  zones: [
    { id: id * 10, name: 'Principal', x: 0, y: 0, width: 75, height: 100, main: true, playlistId: null },
    { id: id * 10 + 1, name: 'Lateral', x: 75, y: 0, width: 25, height: 100, main: false, playlistId: 3 },
  ],
});

const playlist = (id: number, name: string): Playlist => ({ id, name, description: null, configVersion: 1, items: [], createdAt: '', updatedAt: '' });

const TEMPLATES: LayoutTemplate[] = [
  {
    id: 'principal-lateral',
    name: 'Principal + faixa lateral',
    description: 'Conteúdo principal à esquerda e uma faixa à direita',
    zones: [
      { name: 'Principal', x: 0, y: 0, width: 75, height: 100, main: true, playlistId: null },
      { name: 'Lateral', x: 75, y: 0, width: 25, height: 100, main: false, playlistId: null },
    ],
  },
];

function setup(layouts: Layout[] = [layout(1, 'Loja com lateral', 2)], render = false) {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting(), provideRouter([])],
  });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  const backend = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(LayoutsPage);
  const page = fixture.componentInstance;
  // Sem desenhar a tela chamamos o ngOnInit à mão; desenhando, é o primeiro detectChanges que o chama.
  if (render) {
    fixture.detectChanges();
  } else {
    page.ngOnInit();
  }
  backend.expectOne(`${API}/layouts`).flush(layouts);
  backend.expectOne(`${API}/playlists`).flush([playlist(3, 'Promoções'), playlist(4, 'Avisos')]);
  return { page, backend, fixture };
}

describe('LayoutsPage', () => {
  afterEach(() => vi.restoreAllMocks());

  it('lists the layouts with a preview of their zones and how many TVs use each', async () => {
    const { fixture, backend } = setup([layout(1, 'Loja com lateral', 2)], true);
    fixture.detectChanges();
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Loja com lateral');
    expect(element.textContent).toContain('2 zona(s)');
    expect(element.textContent).toContain('2 TV(s)');
    expect(element.querySelectorAll('.screen .zone')).toHaveLength(2);
    backend.verify();
  });

  it('invites the user to create the first layout', async () => {
    const { fixture, backend } = setup([], true);
    fixture.detectChanges();
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nenhum layout ainda');
    backend.verify();
  });

  it('loads the templates once when the new-layout window opens and suggests the template name', () => {
    const { page, backend } = setup();

    page.openNew();
    backend.expectOne(`${API}/layouts/templates`).flush(TEMPLATES);
    page.pick(TEMPLATES[0]);
    page.openNew(); // reabrir não pede de novo
    backend.verify();

    expect(page.templates()).toHaveLength(1);
    expect(page.pickedTemplate()).toBeNull();
    page.pick(TEMPLATES[0]);
    expect(page.name()).toBe('Principal + faixa lateral');
  });

  it('keeps a name the user already typed when picking a template', () => {
    const { page, backend } = setup();
    page.openNew();
    backend.expectOne(`${API}/layouts/templates`).flush(TEMPLATES);
    page.name.set('Minha loja');

    page.pick(TEMPLATES[0]);

    expect(page.name()).toBe('Minha loja');
  });

  it('creates from the template and opens the editor on the new layout', () => {
    const { page, backend } = setup();
    page.openNew();
    backend.expectOne(`${API}/layouts/templates`).flush(TEMPLATES);
    page.pick(TEMPLATES[0]);
    page.name.set('  Loja Norte ');

    page.createFromTemplate();

    const request = backend.expectOne(`${API}/layouts/from-template`);
    expect(request.request.body).toEqual({ templateId: 'principal-lateral', name: 'Loja Norte' });
    request.flush(layout(9, 'Loja Norte'));
    backend.expectOne(`${API}/layouts`).flush([layout(9, 'Loja Norte')]);
    backend.expectOne(`${API}/playlists`).flush([]);
    expect(page.modal()).toBe('edit');
    expect(page.editing()?.id).toBe(9);
    expect(page.zones()).toHaveLength(2);
    expect(page.notice.text()).toContain('Layout "Loja Norte" criado');
    backend.verify();
  });

  it('keeps the window open and shows the server explanation when the name is taken', () => {
    const { page, backend } = setup();
    const errors = TestBed.inject(ErrorService);
    page.openNew();
    backend.expectOne(`${API}/layouts/templates`).flush(TEMPLATES);
    page.pick(TEMPLATES[0]);

    page.createFromTemplate();
    backend
      .expectOne(`${API}/layouts/from-template`)
      .flush({ error: 'Já existe um layout chamado "Principal + faixa lateral"' }, { status: 409, statusText: 'Conflict' });

    expect(page.modal()).toBe('new');
    expect(page.busy()).toBe(false);
    expect(errors.errors()[0].message).toContain('Já existe um layout');
    backend.verify();
  });

  it('starts from scratch with one full-screen main zone and creates with a POST on save', () => {
    const { page, backend } = setup();
    page.openNew();
    backend.expectOne(`${API}/layouts/templates`).flush(TEMPLATES);
    page.openBlank();
    page.name.set('Do zero');

    expect(page.zones()).toHaveLength(1);
    expect(page.problem()).toBeNull();
    page.save();

    const request = backend.expectOne(`${API}/layouts`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      name: 'Do zero',
      description: null,
      zones: [{ name: 'Principal', x: 0, y: 0, width: 100, height: 100, main: true, playlistId: null }],
    });
    request.flush(layout(5, 'Do zero'));
    expect(page.notice.text()).toBe('Layout "Do zero" salvo.');
    backend.expectOne(`${API}/layouts`).flush([]);
    backend.expectOne(`${API}/playlists`).flush([]);
    backend.verify();
  });

  it('edits the zones: numbers, name, fixed playlist and which one is the main', () => {
    const { page, backend } = setup();
    page.openEdit(page.layouts()[0]);
    const [main, side] = page.zones();

    page.change(side.key, 'name', 'Faixa');
    page.change(side.key, 'x', '70');
    page.change(side.key, 'width', 30);
    page.change(side.key, 'playlistId', 4);
    page.change(main.key, 'width', '70');

    const zones = page.zones();
    expect(zones[1]).toMatchObject({ name: 'Faixa', x: 70, width: 30, playlistId: 4 });
    expect(zones[0].width).toBe(70);
    expect(page.problem()).toBeNull();

    page.makeMain(side.key);
    expect(page.zones().map((z) => z.main)).toEqual([false, true]);
    expect(page.zones()[1].playlistId).toBeNull();
    backend.verify();
  });

  it('explains what is wrong in the layout and blocks saving', () => {
    const { page, backend } = setup();
    page.openEdit(page.layouts()[0]);
    page.change(page.zones()[0].key, 'width', 90); // passa por cima da lateral

    expect(page.problem()).toBe('As zonas "Principal" e "Lateral" se sobrepõem');
    page.save();
    page.change(page.zones()[0].key, 'width', '');
    expect(page.problem()).toContain('números inteiros');
    backend.expectNone(`${API}/layouts/1`);
    backend.verify();
  });

  it('saves with a PUT, sends the playlist only for the secondary zones and says how many TVs will update', () => {
    const { page, backend } = setup([layout(1, 'Loja com lateral', 2)]);
    page.openEdit(page.layouts()[0]);
    page.description.set('  Lateral com promoções ');

    page.save();

    const request = backend.expectOne(`${API}/layouts/1`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.description).toBe('Lateral com promoções');
    expect(request.request.body.zones[0].playlistId).toBeNull();
    expect(request.request.body.zones[1].playlistId).toBe(3);
    request.flush(layout(1, 'Loja com lateral', 2));
    expect(page.notice.text()).toContain('As 2 TV(s) que o usam atualizam em até 1 minuto');
    expect(page.modal()).toBeNull();
    backend.expectOne(`${API}/layouts`).flush([]);
    backend.expectOne(`${API}/playlists`).flush([]);
    backend.verify();
  });

  it('adds zones in free space and says when there is no room', () => {
    const { page, backend } = setup();
    page.openBlank();

    page.newZone(); // a principal ocupa a tela toda
    expect(page.zones()).toHaveLength(1);
    expect(page.notice.text()).toContain('Não há espaço livre');

    page.change(page.zones()[0].key, 'width', 75);
    page.newZone();
    expect(page.zones()).toHaveLength(2);
    expect(page.problem()).toBeNull();
    backend.verify();
  });

  it('removes a zone and hands the main role to the one that is left', () => {
    const { page, backend } = setup();
    page.openEdit(page.layouts()[0]);

    page.dropZone(page.zones()[0].key);

    expect(page.zones()).toHaveLength(1);
    expect(page.zones()[0].main).toBe(true);
    backend.verify();
  });

  it('does not delete a layout that TVs are using and says why', () => {
    const { page, backend } = setup([layout(1, 'Em uso', 2)]);
    const confirmation = vi.spyOn(window, 'confirm');

    page.remove(page.layouts()[0]);

    expect(confirmation).not.toHaveBeenCalled();
    expect(page.notice.text()).toContain('está em uso por 2 TV(s)');
    backend.verify();
  });

  it('deletes an unused layout after the user confirms', () => {
    const { page, backend } = setup([layout(1, 'Livre', 0)]);
    vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);

    page.remove(page.layouts()[0]);
    backend.expectNone(`${API}/layouts/1`);
    page.remove(page.layouts()[0]);

    const request = backend.expectOne(`${API}/layouts/1`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    expect(page.notice.text()).toBe('Layout "Livre" excluído.');
    backend.expectOne(`${API}/layouts`).flush([]);
    backend.expectOne(`${API}/playlists`).flush([]);
    backend.verify();
  });
});
