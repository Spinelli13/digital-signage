import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { mediaTypeLabel } from '../core/format';
import { TPipe, tr } from '../core/i18n';
import { Media, MediaType, Playlist, PlaylistItem } from '../core/models';
import { MediaThumb } from '../media/media-thumb';
import { IconComponent } from '../shared/icon';
import { IconName } from '../shared/icons';
import { EmptyStateComponent, PageHeaderComponent, PillComponent } from '../shared/ui';
import { addMedia, formatDuration, moveItem, removeAt, setDuration, toRequest, totalSeconds } from './playlist-editor';

@Component({
  selector: 'app-playlist-editor',
  imports: [FormsModule, RouterLink, MediaThumb, TPipe, IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent],
  template: `
    <app-page-header [title]="isNew() ? ('Nova playlist' | t) : (('Playlist:' | t) + ' ' + savedName())" [subtitle]="'Defina a ordem e a duração dos conteúdos. Ao publicar, as telas sincronizam sozinhas.' | t" icon="list-video">
      <div class="top-actions">
        <a class="secondary btn" routerLink="/playlists"><app-icon name="arrow-left" [size]="17" />{{ 'Voltar' | t }}</a>
        @if (auth.can('content')) {
          <button class="primary" (click)="save()" [disabled]="busy() || !canSave()">
            <app-icon name="rocket" [size]="17" />{{ (busy() ? 'Publicando…' : 'Publicar alterações') | t }}
          </button>
        }
      </div>
    </app-page-header>

    @if (notice()) {
      <div class="success" role="status"><app-icon name="circle-check" [size]="18" /><span>{{ notice() }}</span></div>
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

        <h3 class="sec">
          <app-icon name="list-video" [size]="18" />{{ 'Sequência de reprodução' | t }}
          <app-pill icon="timer">{{ 'ciclo de {time}' | t: { time: cycle() } }}</app-pill>
        </h3>
        @if (items().length === 0) {
          <app-empty-state [compact]="true" icon="image-plus" [title]="'Esta playlist está vazia' | t" [text]="'Adicione mídias da biblioteca ao lado.' | t" />
        }
        @for (item of items(); track $index) {
          <div class="row">
            <span class="pos">{{ $index + 1 }}</span>
            <div class="thumb"><app-media-thumb [mediaId]="item.mediaId" [type]="item.mediaType" [name]="item.mediaName" /></div>
            <div class="info">
              <b>{{ item.mediaName }}</b>
              <small class="kind"><app-icon [name]="kindIcon(item.mediaType)" [size]="12" />{{ typeLabel(item.mediaType) }}</small>
            </div>
            <div class="ctrl">
              <label class="dur">
                <input type="number" min="1" [attr.aria-label]="'Duração (segundos)' | t" [ngModel]="item.durationSeconds" (ngModelChange)="changeDuration($index, $event)" />
                <small>{{ 's' | t }}</small>
              </label>
              <button type="button" class="icon-btn bordered" (click)="move($index, -1)" [disabled]="$first" [title]="'Subir' | t" [attr.aria-label]="'Subir' | t"><app-icon name="arrow-up" [size]="17" /></button>
              <button type="button" class="icon-btn bordered" (click)="move($index, 1)" [disabled]="$last" [title]="'Descer' | t" [attr.aria-label]="'Descer' | t"><app-icon name="arrow-down" [size]="17" /></button>
              <button type="button" class="icon-btn bordered danger-ghost" (click)="remove($index)" [title]="'Remover' | t" [attr.aria-label]="'Remover' | t"><app-icon name="trash" [size]="17" /></button>
            </div>
          </div>
        }
      </div>

      <div class="card">
        <h3 class="sec"><app-icon name="image" [size]="18" />{{ 'Biblioteca' | t }}</h3>
        @if (library().length === 0) {
          <app-empty-state [compact]="true" icon="image-plus" [title]="'Biblioteca vazia' | t">
            <a class="secondary btn" routerLink="/biblioteca"><app-icon name="upload" [size]="16" />{{ 'Envie mídias' | t }}</a>
          </app-empty-state>
        }
        <div class="lib">
          @for (media of library(); track media.id) {
            <button class="pick" (click)="add(media)" [title]="'Adicionar {name}' | t: { name: media.name }">
              <div class="thumb"><app-media-thumb [mediaId]="media.id" [type]="media.type" [name]="media.name" /><span class="add"><app-icon name="plus" [size]="18" /></span></div>
              <small class="n">{{ media.name }}</small>
            </button>
          }
        </div>
      </div>
    </div>
  `,
  styles: `
    .top-actions { display: flex; gap: 10px; align-items: center; }
    .btn { display: inline-flex; align-items: center; justify-content: center; gap: 8px; padding: 10px 16px; border-radius: var(--r); border: 1px solid var(--line-strong); background: var(--panel); color: var(--text); font-weight: 600; text-align: center; text-decoration: none; }
    .btn:hover { background: var(--panel-3); }
    .sec { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
    .sec app-icon { color: var(--primary); }
    :host-context(:root[data-theme='dark']) .sec app-icon { color: var(--link); }
    .sec app-pill { margin-left: auto; }
    .kind { display: inline-flex; align-items: center; gap: 5px; color: var(--muted); }
    .danger-ghost { color: var(--err-fg); }
    .danger-ghost:hover { background: var(--err-bg); }
    .add { position: absolute; inset: 0; display: grid; place-items: center; color: #fff; background: rgba(9, 14, 12, 0.6); opacity: 0; transition: opacity 0.15s; }
    .pick:hover .add, .pick:focus-visible .add { opacity: 1; }
    .layout { display: grid; grid-template-columns: minmax(0, 2fr) minmax(240px, 1fr); gap: 18px; align-items: start; }
    .row { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid var(--line); }
    .pos { flex: none; display: grid; place-items: center; width: 26px; height: 26px; border-radius: 50%; background: var(--primary-soft); color: var(--primary); font-weight: 700; font-size: 12.5px; font-variant-numeric: tabular-nums; }
    :host-context(:root[data-theme='dark']) .pos { color: var(--link); }
    .thumb { position: relative; width: 64px; height: 44px; border-radius: 8px; overflow: hidden; flex: none; background: var(--primary-soft); }
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
  readonly kindIcon = (type: MediaType): IconName => (type === 'VIDEO' ? 'film' : type === 'WEB' ? 'globe' : type === 'WIDGET' ? 'puzzle' : 'image');
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
