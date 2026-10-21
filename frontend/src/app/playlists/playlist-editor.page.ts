import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { mediaTypeLabel } from '../core/format';
import { TPipe, tr } from '../core/i18n';
import { Media, Playlist, PlaylistItem } from '../core/models';
import { MediaThumb } from '../media/media-thumb';
import { addMedia, formatDuration, moveItem, removeAt, setDuration, toRequest, totalSeconds } from './playlist-editor';

@Component({
  selector: 'app-playlist-editor',
  imports: [FormsModule, RouterLink, MediaThumb, TPipe],
  template: `
    <div class="head">
      <div>
        <h1>{{ isNew() ? ('Nova playlist' | t) : (('Playlist:' | t) + ' ' + savedName()) }}</h1>
        <p>{{ 'Defina a ordem e a duração dos conteúdos. Ao publicar, as telas sincronizam sozinhas.' | t }}</p>
      </div>
      <div class="top-actions">
        <a class="secondary btn" routerLink="/playlists">{{ 'Voltar' | t }}</a>
        @if (auth.can('content')) {
          <button class="primary" (click)="save()" [disabled]="busy() || !canSave()">
            {{ (busy() ? 'Publicando…' : 'Publicar alterações') | t }}
          </button>
        }
      </div>
    </div>

    @if (notice()) {
      <div class="success">{{ notice() }}</div>
    }

    <div class="layout">
      <div class="card">
        <div class="field">
          <label for="pname">{{ 'Nome' | t }}</label>
          <input id="pname" [ngModel]="name()" (ngModelChange)="name.set($event)" [placeholder]="'Ex.: Loja Centro' | t" />
        </div>
        <div class="field">
          <label for="pdesc">{{ 'Descrição' | t }}</label>
          <input id="pdesc" [ngModel]="description()" (ngModelChange)="description.set($event)" [placeholder]="'Opcional' | t" />
        </div>

        <h3>{{ 'Sequência de reprodução' | t }} <small>· {{ 'ciclo de {time}' | t: { time: cycle() } }}</small></h3>
        @if (items().length === 0) {
          <div class="empty">{{ 'Adicione mídias da biblioteca ao lado.' | t }}</div>
        }
        @for (item of items(); track $index) {
          <div class="row">
            <span class="pos">{{ $index + 1 }}</span>
            <div class="thumb"><app-media-thumb [mediaId]="item.mediaId" [type]="item.mediaType" [name]="item.mediaName" /></div>
            <div class="info">
              <b>{{ item.mediaName }}</b>
              <small>{{ typeLabel(item.mediaType) }}</small>
            </div>
            <div class="ctrl">
              <label class="dur">
                <input type="number" min="1" [attr.aria-label]="'Duração (segundos)' | t" [ngModel]="item.durationSeconds" (ngModelChange)="changeDuration($index, $event)" />
                <small>{{ 's' | t }}</small>
              </label>
              <button class="secondary small" (click)="move($index, -1)" [disabled]="$first" [title]="'Subir' | t">↑</button>
              <button class="secondary small" (click)="move($index, 1)" [disabled]="$last" [title]="'Descer' | t">↓</button>
              <button class="danger small" (click)="remove($index)" [title]="'Remover' | t">✕</button>
            </div>
          </div>
        }
      </div>

      <div class="card">
        <h3>{{ 'Biblioteca' | t }}</h3>
        @if (library().length === 0) {
          <div class="empty">{{ 'Biblioteca vazia.' | t }} <a routerLink="/biblioteca">{{ 'Envie mídias' | t }}</a> {{ 'primeiro.' | t }}</div>
        }
        <div class="lib">
          @for (media of library(); track media.id) {
            <button class="pick" (click)="add(media)" [title]="'Adicionar {name}' | t: { name: media.name }">
              <div class="thumb"><app-media-thumb [mediaId]="media.id" [type]="media.type" [name]="media.name" /></div>
              <small class="n">{{ media.name }}</small>
            </button>
          }
        </div>
      </div>
    </div>
  `,
  styles: `
    .top-actions { display: flex; gap: 10px; align-items: center; }
    .btn { display: inline-block; padding: 10px 16px; border-radius: 10px; border: 1px solid var(--line); background: var(--panel); color: var(--text); font-weight: 600; text-align: center; }
    .layout { display: grid; grid-template-columns: minmax(0, 2fr) minmax(240px, 1fr); gap: 18px; align-items: start; }
    .row { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid var(--line); }
    .pos { width: 22px; text-align: center; color: var(--muted); font-weight: 700; }
    .thumb { width: 64px; height: 44px; border-radius: 8px; overflow: hidden; flex: none; background: var(--primary-soft); }
    .info { flex: 1; display: flex; flex-direction: column; min-width: 0; }
    .info b { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .ctrl { display: flex; align-items: center; gap: 8px; }
    .dur { display: flex; align-items: center; gap: 4px; }
    .dur input { width: 70px; padding: 6px 8px; }
    .lib { display: grid; grid-template-columns: repeat(auto-fill, minmax(96px, 1fr)); gap: 10px; }
    .pick { background: var(--panel); color: var(--text); border: 1px solid var(--line); border-radius: 10px; padding: 6px; text-align: left; }
    .pick:hover { border-color: var(--primary); background: var(--primary-soft); }
    .pick .thumb { width: 100%; height: 62px; }
    .pick .n { display: block; margin-top: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    @media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }
    @media (max-width: 720px) {
      .top-actions { width: 100%; }
      .top-actions > * { flex: 1; }
      .row { flex-wrap: wrap; }
      .info { flex: 1 1 calc(100% - 130px); }
      .ctrl { width: 100%; justify-content: flex-end; padding-left: 32px; }
      .dur input { width: 80px; }
    }
  `,
})
export class PlaylistEditorPage implements OnInit {
  readonly typeLabel = mediaTypeLabel;
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly name = signal('');
  readonly description = signal('');
  readonly items = signal<PlaylistItem[]>([]);
  readonly library = signal<Media[]>([]);
  readonly savedName = signal('');
  readonly notice = signal('');
  readonly busy = signal(false);

  private readonly playlistId = signal<number | null>(null);

  readonly isNew = computed(() => this.playlistId() === null);
  readonly canSave = computed(() => this.name().trim().length > 0);
  readonly cycle = computed(() => formatDuration(totalSeconds(this.items())));

  ngOnInit(): void {
    this.api.mediaList().subscribe({
      next: (media) => this.library.set(media),
      error: () => undefined,
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (id !== null) {
      this.playlistId.set(Number(id));
      this.api.playlist(Number(id)).subscribe({
        next: (playlist) => this.apply(playlist),
        error: () => undefined,
      });
    }
  }

  add(media: Media): void {
    this.items.update((items) => addMedia(items, media));
  }

  remove(index: number): void {
    this.items.update((items) => removeAt(items, index));
  }

  move(index: number, delta: -1 | 1): void {
    this.items.update((items) => moveItem(items, index, delta));
  }

  changeDuration(index: number, seconds: number): void {
    this.items.update((items) => setDuration(items, index, Number(seconds)));
  }

  save(): void {
    this.busy.set(true);
    this.notice.set('');
    const body = toRequest(this.name(), this.description(), this.items());

    const currentId = this.playlistId();
    const request = currentId === null ? this.api.createPlaylist(body) : this.api.updatePlaylist(currentId, body);
    request.subscribe({
      next: (playlist) => {
        this.busy.set(false);
        const wasNew = currentId === null;
        this.apply(playlist);
        this.notice.set(tr('Publicado — versão {version}. As telas vinculadas sincronizam em instantes.', { version: playlist.configVersion }));
        if (wasNew) {
          void this.router.navigate(['/playlists', playlist.id], { replaceUrl: true });
        }
      },
      error: () => this.busy.set(false),
    });
  }

  private apply(playlist: Playlist): void {
    this.playlistId.set(playlist.id);
    this.savedName.set(playlist.name);
    this.name.set(playlist.name);
    this.description.set(playlist.description ?? '');
    this.items.set(playlist.items);
  }
}
