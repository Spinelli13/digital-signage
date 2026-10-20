import { Component, OnDestroy, OnInit, inject, input, signal } from '@angular/core';

import { ApiService } from '../core/api.service';
import { MediaType } from '../core/models';

/**
 * Miniatura de uma mídia. Com armazenamento externo (S3) usa a URL assinada direto; com disco local
 * baixa o arquivo pelo backend (que exige o JWT) e exibe via blob.
 */
@Component({
  selector: 'app-media-thumb',
  template: `
    @if (type() === 'IMAGE' && src()) {
      <img [src]="src()" [alt]="name()" loading="lazy" />
    } @else {
      <div class="ph">{{ type() === 'VIDEO' ? '🎬' : type() === 'WEB' ? '🌐' : '🖼' }}</div>
    }
  `,
  styles: `
    :host { display: block; width: 100%; height: 100%; }
    img { width: 100%; height: 100%; object-fit: cover; display: block; }
    .ph { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; font-size: 34px; background: var(--blue2); }
  `,
})
export class MediaThumb implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);

  readonly mediaId = input.required<number>();
  readonly type = input.required<MediaType>();
  readonly name = input('');
  readonly src = signal<string | null>(null);

  private objectUrl: string | null = null;

  ngOnInit(): void {
    if (this.type() !== 'IMAGE') {
      return;
    }
    this.api.mediaUrl(this.mediaId(), true).subscribe({
      next: (target) => {
        if (!target.requiresAuth) {
          this.src.set(target.url);
          return;
        }
        this.api.mediaBlob(target.url, true).subscribe({
          next: (blob) => {
            this.objectUrl = URL.createObjectURL(blob);
            this.src.set(this.objectUrl);
          },
          error: () => this.src.set(null),
        });
      },
      error: () => this.src.set(null),
    });
  }

  ngOnDestroy(): void {
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
    }
  }
}
