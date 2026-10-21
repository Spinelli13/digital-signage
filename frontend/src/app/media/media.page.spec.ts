import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ConfigService } from '../core/config.service';
import { errorInterceptor } from '../core/error.interceptor';
import { ErrorService } from '../core/error.service';
import { Media } from '../core/models';
import { buildVideo } from '../testing/mp4';
import { DEFAULT_QR_CAPTION, DEFAULT_WEB_SECONDS, MediaPage } from './media.page';

const API = 'http://api.test/api/v1';

const media = (id: number, name: string, overrides: Partial<Media> = {}): Media => ({
  id,
  name,
  type: 'IMAGE',
  mimeType: 'image/png',
  url: `/api/v1/media/${id}/file`,
  fileSize: 2048,
  durationSeconds: null,
  checksum: null,
  createdAt: '',
  ...overrides,
});

function setup(items: Media[] = [], render = false) {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
  });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  const backend = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(MediaPage);
  const page = fixture.componentInstance;
  if (render) {
    fixture.detectChanges();
  } else {
    page.ngOnInit();
  }
  backend.expectOne(`${API}/media`).flush(items);
  return { page, backend, fixture };
}

describe('MediaPage', () => {
  afterEach(() => vi.restoreAllMocks());

  it('shows the type of each item, hides the size of a web page and shows its address', async () => {
    const { fixture, backend } = setup(
      [media(1, 'Logo'), media(2, 'Painel', { type: 'WEB', url: 'https://painel.empresa.com/tv', fileSize: 0, durationSeconds: 45 })],
      true,
    );
    fixture.detectChanges();
    await fixture.whenStable();

    const items = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.item')).map((el) => el.textContent ?? '');
    expect(items[0]).toContain('Imagem');
    expect(items[0]).toContain('2.0 KB');
    expect(items[1]).toContain('Página web');
    expect(items[1]).toContain('45s');
    expect(items[1]).toContain('https://painel.empresa.com/tv');
    expect(items[1]).not.toContain('0 B');
    // só a imagem pede miniatura; a página web mostra um ícone
    backend.expectOne(`${API}/media/1/url`).flush({ url: 'https://cdn.test/logo.png', requiresAuth: false });
    backend.verify();
  });

  // ---------- página web ----------

  it('opens the web window with the default time and no leftovers from last time', () => {
    const { page, backend } = setup();
    page.webUrl.set('https://velho.com');

    page.openWeb();

    expect(page.modal()).toBe('web');
    expect(page.webUrl()).toBe('');
    expect(page.webSeconds()).toBe(DEFAULT_WEB_SECONDS);
    backend.verify();
  });

  it('saves a web page with a trimmed address and an empty name as null', () => {
    const { page, backend } = setup();
    page.openWeb();
    page.webUrl.set('  https://painel.empresa.com/tv ');
    page.webName.set('   ');
    page.webSeconds.set(45);

    page.saveWeb();

    const request = backend.expectOne(`${API}/media/web`);
    expect(request.request.body).toEqual({ name: null, url: 'https://painel.empresa.com/tv', durationSeconds: 45 });
    request.flush(media(7, 'painel.empresa.com', { type: 'WEB' }));
    expect(page.modal()).toBeNull();
    expect(page.notice.text()).toContain('Página "painel.empresa.com" adicionada');
    backend.expectOne(`${API}/media`).flush([]);
    backend.verify();
  });

  it('does not send a blank address and falls back to the default time when the field is cleared', () => {
    const { page, backend } = setup();
    page.openWeb();
    page.webUrl.set('   ');
    page.saveWeb();
    backend.expectNone(`${API}/media/web`);

    page.webUrl.set('https://site.com');
    page.webSeconds.set(null);
    page.saveWeb();

    expect(backend.expectOne(`${API}/media/web`).request.body.durationSeconds).toBe(DEFAULT_WEB_SECONDS);
    backend.verify();
  });

  it('keeps the window open and explains when the server refuses the address', () => {
    const { page, backend } = setup();
    const errors = TestBed.inject(ErrorService);
    page.openWeb();
    page.webUrl.set('http://inseguro.com');

    page.saveWeb();
    backend
      .expectOne(`${API}/media/web`)
      .flush({ error: 'O endereço da página precisa começar com https://' }, { status: 400, statusText: 'Bad Request' });

    expect(page.modal()).toBe('web');
    expect(page.busy()).toBe(false);
    expect(errors.errors()[0].message).toContain('https://');
    backend.verify();
  });

  // ---------- QR Code ----------

  it('opens the QR window with the default caption and the dark theme', () => {
    const { page, backend } = setup();
    page.qrText.set('algo antigo');

    page.openQr();

    expect(page.modal()).toBe('qr');
    expect(page.qrText()).toBe('');
    expect(page.qrCaption()).toBe(DEFAULT_QR_CAPTION);
    expect(page.qrTheme()).toBe('escuro');
    backend.verify();
  });

  it('draws the preview in the canvas as the user types and warns when the text is too long', async () => {
    const calls: string[] = [];
    const context = {
      fillStyle: '',
      font: '',
      textAlign: '',
      fillRect: () => calls.push('rect'),
      fillText: () => calls.push('text'),
      clearRect: () => calls.push('clear'),
      measureText: () => ({ width: 10 }),
    };
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(context as unknown as CanvasRenderingContext2D);
    const { page, fixture, backend } = setup([], true);
    page.openQr();
    fixture.detectChanges();
    await fixture.whenStable();

    page.qrText.set('https://exemplo.com');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(calls.filter((c) => c === 'rect').length).toBeGreaterThan(50);
    expect(page.qrProblem()).toBeNull();

    page.qrText.set('x'.repeat(5000));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(page.qrProblem()).toContain('grande demais');
    backend.verify();
  });

  it('saves the QR image to the library as a PNG named after the title', async () => {
    const blob = new Blob(['png'], { type: 'image/png' });
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((callback) => callback(blob));
    const { page, fixture, backend } = setup([], true);
    page.openQr();
    fixture.detectChanges();
    await fixture.whenStable();
    page.qrText.set('https://exemplo.com/cardapio');
    page.qrTitle.set('Cardápio');

    page.saveQr();

    const request = backend.expectOne(`${API}/media`);
    const file = (request.request.body as FormData).get('file') as File;
    expect(file.name).toBe('QR Code - Cardápio.png');
    expect(file.type).toBe('image/png');
    request.flush(media(9, 'QR Code - Cardápio.png'));
    expect(page.modal()).toBeNull();
    expect(page.notice.text()).toContain('QR Code "QR Code - Cardápio" salvo na biblioteca');
    backend.expectOne(`${API}/media`).flush([]);
    backend.verify();
  });

  it('does not save without a text', () => {
    const { page, backend } = setup();
    page.openQr();

    page.saveQr();

    backend.expectNone(`${API}/media`);
    backend.verify();
  });

  describe('vídeos do iPhone', () => {
    const fileOf = (blob: Blob, name: string, type = '') => new File([blob], name, { type });

    /** Escolhe os arquivos como o seletor do navegador faria e deixa o envio andar até o fim. */
    async function choose(page: MediaPage, backend: HttpTestingController, files: File[]) {
      const input = document.createElement('input');
      Object.defineProperty(input, 'files', { value: files });
      const done = page.onFiles({ target: input } as unknown as Event);
      for (const file of files) {
        const request = await vi.waitFor(() => backend.expectOne((r) => r.url === `${API}/media` && r.method === 'POST'));
        expect((request.request.body as FormData).get('file')).toBe(file);
        request.flush(media(100, file.name));
      }
      await done;
    }

    it('aceita um .MOV em HEVC (sem tipo informado), lê a duração do próprio arquivo e avisa sobre o HEVC', async () => {
      const { page, backend } = setup();
      const mov = fileOf(buildVideo({ codec: 'hvc1', width: 1920, height: 1080, rotation: 90, timescale: 600, duration: 7200 }), 'IMG_0042.MOV');

      const input = document.createElement('input');
      Object.defineProperty(input, 'files', { value: [mov] });
      const done = page.onFiles({ target: input } as unknown as Event);
      const request = await vi.waitFor(() => backend.expectOne((r) => r.url === `${API}/media` && r.method === 'POST'));
      expect((request.request.body as FormData).get('durationSeconds')).toBe('12');
      request.flush(media(7, 'IMG_0042.MOV', { type: 'VIDEO', mimeType: 'video/quicktime' }));
      await done;

      backend.match(`${API}/media`).forEach((r) => r.flush([])); // recarrega a biblioteca (a detecção automática de mudanças chama ngOnInit de novo)
      expect(page.warnings().join(' ')).toContain('HEVC');
      expect(page.warnings().join(' ')).toContain('IMG_0042.MOV');
    });

    it('recusa vídeo acima de 1080p, explica como regravar e segue com os outros arquivos', async () => {
      const { page, backend } = setup();
      const errors = TestBed.inject(ErrorService);
      const big = fileOf(buildVideo({ codec: 'hvc1', width: 3840, height: 2160 }), 'cinema-4k.mov', 'video/quicktime');
      const ok = fileOf(buildVideo({ codec: 'avc1', width: 1920, height: 1080, duration: 3000 }), 'normal.mp4', 'video/mp4');

      const input = document.createElement('input');
      Object.defineProperty(input, 'files', { value: [big, ok] });
      const done = page.onFiles({ target: input } as unknown as Event);
      const request = await vi.waitFor(() => backend.expectOne((r) => r.url === `${API}/media` && r.method === 'POST'));
      expect((request.request.body as FormData).get('file')).toBe(ok);
      request.flush(media(8, 'normal.mp4', { type: 'VIDEO' }));
      await done;
      backend.match(`${API}/media`).forEach((r) => r.flush([]));

      expect(errors.errors()[0].message).toContain('cinema-4k.mov');
      expect(errors.errors()[0].message).toContain('3840×2160');
      expect(errors.errors()[0].message).toContain('1080p');
      expect(page.warnings()).toEqual([]);
    });

    it('um vídeo em pé de 1080×1920 (celular) é aceito: o limite vale para o lado maior e o menor', async () => {
      const { page, backend } = setup();
      const portrait = fileOf(buildVideo({ codec: 'avc1', width: 1080, height: 1920 }), 'story.mp4', 'video/mp4');

      await choose(page, backend, [portrait]);

      backend.match(`${API}/media`).forEach((r) => r.flush([]));
      expect(TestBed.inject(ErrorService).errors()).toHaveLength(0);
    });
  });
});
