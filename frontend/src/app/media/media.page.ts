import { HttpEventType } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';

import { ApiService } from '../core/api.service';
import { ErrorService } from '../core/error.service';
import { formatBytes } from '../core/format';
import { Media } from '../core/models';
import { MediaThumb } from './media-thumb';

const ACCEPTED = 'image/jpeg,image/png,image/webp,video/mp4';

@Component({
  selector: 'app-media',
  imports: [MediaThumb],
  template: `
    <div class="head">
      <div><h1>Biblioteca de Mídia</h1><p>Imagens (JPG, PNG, WEBP) e vídeos (MP4) disponíveis para as playlists.</p></div>
      <label class="primary upload">
        + Enviar mídia
        <input type="file" multiple [accept]="accepted" (change)="onFiles($event)" [disabled]="uploading()" hidden />
      </label>
    </div>

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
                {{ media.type === 'VIDEO' ? 'Vídeo' : 'Imagem' }} · {{ size(media.fileSize) }}
                @if (media.durationSeconds) { · {{ media.durationSeconds }}s }
              </small>
            </div>
            <button class="danger small" (click)="remove(media)">Excluir</button>
          </div>
        }
      </div>
    }
  `,
  styles: `
    .upload { cursor: pointer; display: inline-block; }
    .progress { margin-bottom: 16px; }
    .bar { height: 8px; background: var(--blue2); border-radius: 8px; margin-top: 10px; overflow: hidden; }
    .fill { height: 100%; background: var(--blue); transition: width 0.2s; }
    .item { padding: 0; overflow: hidden; display: flex; flex-direction: column; }
    .thumb { height: 120px; background: var(--blue2); }
    .meta { padding: 10px 12px 4px; display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .name { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .item button { margin: 8px 12px 12px; }
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

  ngOnInit(): void {
    this.load();
  }

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
