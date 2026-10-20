import { HttpEventType } from '@angular/common/http';
import { Component, ElementRef, OnInit, effect, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { ErrorService } from '../core/error.service';
import { formatBytes, mediaTypeLabel } from '../core/format';
import { Media } from '../core/models';
import { Notice } from '../core/notice';
import { MediaThumb } from './media-thumb';
import { QR_THEMES, QR_IMAGE_SIZE, QrTheme, drawQr, qrFileName } from './qr';

const ACCEPTED = 'image/jpeg,image/png,image/webp,video/mp4';
export const DEFAULT_WEB_SECONDS = 30;
export const DEFAULT_QR_CAPTION = 'Aponte a câmera do celular';

@Component({
  selector: 'app-media',
  imports: [MediaThumb, FormsModule],
  template: `
    <div class="head">
      <div><h1>Biblioteca de Mídia</h1><p>Imagens (JPG, PNG, WEBP), vídeos (MP4), páginas da web e QR Codes disponíveis para as playlists.</p></div>
      <div class="head-actions">
        <button class="secondary" (click)="openWeb()">🌐 Página web</button>
        <button class="secondary" (click)="openQr()">▦ QR Code</button>
        <label class="primary upload">
          + Enviar mídia
          <input type="file" multiple [accept]="accepted" (change)="onFiles($event)" [disabled]="uploading()" hidden />
        </label>
      </div>
    </div>

    @if (notice.text(); as text) {
      <div class="notice" role="status">{{ text }}</div>
    }

    @if (uploading()) {
      <div class="card progress">
        Enviando <b>{{ currentFile() }}</b>… {{ progress() }}%
        <div class="bar"><div class="fill" [style.width.%]="progress()"></div></div>
      </div>
    }

    @if (items().length === 0 && !uploading()) {
      <div class="card empty">Nenhuma mídia ainda. Envie sua primeira imagem ou vídeo.</div>
    } @else {
      <div class="grid">
        @for (media of items(); track media.id) {
          <div class="card item">
            <div class="thumb"><app-media-thumb [mediaId]="media.id" [type]="media.type" [name]="media.name" /></div>
            <div class="meta">
              <b class="name" [title]="media.name">{{ media.name }}</b>
              <small>
                {{ typeLabel(media.type) }}
                @if (media.type !== 'WEB') { · {{ size(media.fileSize) }} }
                @if (media.durationSeconds) { · {{ media.durationSeconds }}s }
              </small>
              @if (media.type === 'WEB') {
                <small class="url" [title]="media.url">{{ media.url }}</small>
              }
            </div>
            <button class="danger small" (click)="remove(media)">Excluir</button>
          </div>
        }
      </div>
    }

    @if (modal() === 'web') {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="saveWeb()">
          <h2>Página da web</h2>
          <p class="label">A TV abre a página e a exibe pelo tempo escolhido (um painel de vendas, o cardápio online, a previsão do tempo…). Precisa de internet na TV e o endereço precisa ser <b>https://</b>. A página não fica guardada na TV.</p>
          <div class="field">
            <label for="web-url">Endereço da página</label>
            <input id="web-url" name="url" type="url" placeholder="https://…" required autocomplete="off" [ngModel]="webUrl()" (ngModelChange)="webUrl.set($event)" />
          </div>
          <div class="field">
            <label for="web-name">Nome (opcional)</label>
            <input id="web-name" name="name" placeholder="Ex.: Painel de vendas" [ngModel]="webName()" (ngModelChange)="webName.set($event)" />
          </div>
          <div class="field">
            <label for="web-seconds">Tempo na tela (segundos)</label>
            <input id="web-seconds" name="seconds" type="number" min="5" max="3600" [ngModel]="webSeconds()" (ngModelChange)="webSeconds.set($event)" />
          </div>
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">Cancelar</button>
            <button type="submit" class="primary" [disabled]="busy() || !webUrl().trim()">Adicionar</button>
          </div>
        </form>
      </div>
    }

    @if (modal() === 'qr') {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box qr" (click)="$event.stopPropagation()" (ngSubmit)="saveQr()">
          <h2>QR Code</h2>
          <p class="label">Gera uma imagem com o QR Code (um endereço, um Wi‑Fi, um cardápio…) e a guarda na biblioteca, pronta para entrar numa playlist.</p>
          <div class="qr-layout">
            <div>
              <div class="field">
                <label for="qr-text">Endereço ou texto</label>
                <input id="qr-text" name="text" placeholder="https://…" required autocomplete="off" [ngModel]="qrText()" (ngModelChange)="qrText.set($event)" />
              </div>
              <div class="field">
                <label for="qr-title">Título (opcional)</label>
                <input id="qr-title" name="title" placeholder="Ex.: Cardápio digital" [ngModel]="qrTitle()" (ngModelChange)="qrTitle.set($event)" />
              </div>
              <div class="field">
                <label for="qr-caption">Legenda (opcional)</label>
                <input id="qr-caption" name="caption" [ngModel]="qrCaption()" (ngModelChange)="qrCaption.set($event)" />
              </div>
              <div class="field">
                <label for="qr-theme">Cores</label>
                <select id="qr-theme" name="theme" [ngModel]="qrTheme()" (ngModelChange)="qrTheme.set($event)">
                  @for (theme of themes; track theme[0]) {
                    <option [value]="theme[0]">{{ theme[1].label }}</option>
                  }
                </select>
              </div>
            </div>
            <div class="preview">
              <canvas #qrCanvas [width]="imageSize" [height]="imageSize" aria-label="Pré-visualização do QR Code"></canvas>
              @if (qrProblem(); as text) {
                <div class="error" role="alert">{{ text }}</div>
              }
            </div>
          </div>
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">Cancelar</button>
            <button type="submit" class="primary" [disabled]="busy() || !qrText().trim() || !!qrProblem()">Salvar na biblioteca</button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .upload { cursor: pointer; display: inline-block; }
    .head-actions { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
    .progress { margin-bottom: 16px; }
    .bar { height: 8px; background: var(--blue2); border-radius: 8px; margin-top: 10px; overflow: hidden; }
    .fill { height: 100%; background: var(--blue); transition: width 0.2s; }
    .item { padding: 0; overflow: hidden; display: flex; flex-direction: column; }
    .thumb { height: 120px; background: var(--blue2); }
    .meta { padding: 10px 12px 4px; display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .name, .url { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .item button { margin: 8px 12px 12px; }
    .modal-box.qr { max-width: 820px; }
    .qr-layout { display: grid; grid-template-columns: 1fr 300px; gap: 18px; align-items: start; }
    .preview canvas { width: 100%; height: auto; aspect-ratio: 1; border: 1px solid var(--line); border-radius: 10px; background: #fff; display: block; }
    .preview .error { margin-top: 10px; }
    @media (max-width: 720px) { .qr-layout { grid-template-columns: 1fr; } }
  `,
})
export class MediaPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly errors = inject(ErrorService);

  readonly accepted = ACCEPTED;
  readonly items = signal<Media[]>([]);
  readonly uploading = signal(false);
  readonly progress = signal(0);
  readonly currentFile = signal('');
  readonly size = formatBytes;
  readonly typeLabel = mediaTypeLabel;
  readonly notice = new Notice();

  readonly modal = signal<'web' | 'qr' | null>(null);
  readonly busy = signal(false);

  readonly webUrl = signal('');
  readonly webName = signal('');
  readonly webSeconds = signal<number | null>(DEFAULT_WEB_SECONDS);

  readonly qrText = signal('');
  readonly qrTitle = signal('');
  readonly qrCaption = signal(DEFAULT_QR_CAPTION);
  readonly qrTheme = signal<QrTheme>('escuro');
  readonly qrProblem = signal<string | null>(null);
  readonly themes = Object.entries(QR_THEMES);
  readonly imageSize = QR_IMAGE_SIZE;
  private readonly qrCanvas = viewChild<ElementRef<HTMLCanvasElement>>('qrCanvas');

  constructor() {
    // A pré-visualização é redesenhada a cada tecla (e quando a janela abre e o canvas passa a existir).
    effect(() => {
      const canvas = this.qrCanvas()?.nativeElement;
      const drawing = { text: this.qrText(), title: this.qrTitle(), caption: this.qrCaption(), theme: this.qrTheme() };
      if (canvas) {
        this.paintQr(canvas, drawing);
      }
    });
  }

  ngOnInit(): void {
    this.load();
  }

  // ---------- página web ----------

  openWeb(): void {
    this.webUrl.set('');
    this.webName.set('');
    this.webSeconds.set(DEFAULT_WEB_SECONDS);
    this.modal.set('web');
  }

  saveWeb(): void {
    const url = this.webUrl().trim();
    if (!url) {
      return;
    }
    this.busy.set(true);
    this.api
      .createWebContent({ name: this.webName().trim() || null, url, durationSeconds: this.webSeconds() ?? DEFAULT_WEB_SECONDS })
      .subscribe({
        next: (media) => {
          this.busy.set(false);
          this.closeModal();
          this.notice.show(`Página "${media.name}" adicionada à biblioteca. Coloque-a numa playlist para exibi-la.`);
          this.load();
        },
        // O motivo (ex.: "precisa começar com https://") aparece no aviso de erro; a janela continua aberta.
        error: () => this.busy.set(false),
      });
  }

  // ---------- QR Code ----------

  openQr(): void {
    this.qrText.set('');
    this.qrTitle.set('');
    this.qrCaption.set(DEFAULT_QR_CAPTION);
    this.qrTheme.set('escuro');
    this.qrProblem.set(null);
    this.modal.set('qr');
  }

  saveQr(): void {
    const canvas = this.qrCanvas()?.nativeElement;
    const drawing = { text: this.qrText(), title: this.qrTitle(), caption: this.qrCaption(), theme: this.qrTheme() };
    if (!canvas || this.qrProblem() || !drawing.text.trim()) {
      return;
    }
    this.busy.set(true);
    canvas.toBlob((blob) => {
      if (!blob) {
        this.busy.set(false);
        this.errors.show('Não foi possível gerar a imagem do QR Code neste navegador.');
        return;
      }
      const file = new File([blob], qrFileName(drawing), { type: 'image/png' });
      this.api.uploadMedia(file, null).subscribe({
        next: (event) => {
          if (event.type === HttpEventType.Response) {
            this.busy.set(false);
            this.closeModal();
            this.notice.show(`QR Code "${file.name.replace(/\.png$/, '')}" salvo na biblioteca. Coloque-o numa playlist para exibi-lo.`);
            this.load();
          }
        },
        error: () => this.busy.set(false),
      });
    }, 'image/png');
  }

  private paintQr(canvas: HTMLCanvasElement, drawing: { text: string; title: string; caption: string; theme: QrTheme }): void {
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return;
    }
    if (!drawing.text.trim()) {
      this.qrProblem.set(null);
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      return;
    }
    try {
      drawQr(ctx, drawing);
      this.qrProblem.set(null);
    } catch (error) {
      this.qrProblem.set(error instanceof Error ? error.message : 'Não foi possível gerar o QR Code.');
    }
  }

  closeModal(): void {
    this.modal.set(null);
  }

  // ---------- arquivos ----------

  async onFiles(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (files.length === 0) {
      return;
    }
    this.uploading.set(true);
    for (const file of files) {
      this.currentFile.set(file.name);
      this.progress.set(0);
      try {
        const duration = file.type.startsWith('video/') ? await readVideoDuration(file) : null;
        await this.upload(file, duration);
      } catch {
        // O motivo já foi mostrado pelo interceptor; aqui dizemos qual arquivo falhou e o que aconteceu com a fila.
        this.errors.show(`Não foi possível enviar "${file.name}". Os arquivos seguintes da fila não foram enviados.`);
        break;
      }
    }
    this.uploading.set(false);
    this.load();
  }

  remove(media: Media): void {
    if (!confirm(`Excluir "${media.name}"?`)) {
      return;
    }
    this.api.deleteMedia(media.id).subscribe({
      next: () => this.load(),
      // 409: a mídia ainda está em uma playlist (mensagem vem da API)
      error: () => undefined,
    });
  }

  private upload(file: File, duration: number | null): Promise<void> {
    return new Promise((resolve, reject) => {
      this.api.uploadMedia(file, duration).subscribe({
        next: (event) => {
          if (event.type === HttpEventType.UploadProgress && event.total) {
            this.progress.set(Math.round((100 * event.loaded) / event.total));
          }
          if (event.type === HttpEventType.Response) {
            resolve();
          }
        },
        error: reject,
      });
    });
  }

  private load(): void {
    this.api.mediaList().subscribe({
      next: (media) => this.items.set(media),
      error: () => undefined,
    });
  }
}

/** O servidor não lê metadados de vídeo (decisão do projeto), então a duração vem do navegador. */
function readVideoDuration(file: File): Promise<number | null> {
  return new Promise((resolve) => {
    const video = document.createElement('video');
    const url = URL.createObjectURL(file);
    const finish = (value: number | null) => {
      URL.revokeObjectURL(url);
      resolve(value);
    };
    video.preload = 'metadata';
    video.onloadedmetadata = () => finish(Number.isFinite(video.duration) ? Math.max(1, Math.round(video.duration)) : null);
    video.onerror = () => finish(null);
    video.src = url;
  });
}
