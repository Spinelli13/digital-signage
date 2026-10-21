import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { TPipe, tr } from '../core/i18n';
import { Playlist } from '../core/models';
import { IconComponent } from '../shared/icon';
import { EmptyStateComponent, MenuComponent, PageHeaderComponent, PillComponent, SkeletonComponent } from '../shared/ui';
import { formatDuration, totalSeconds } from './playlist-editor';

@Component({
  selector: 'app-playlists',
  imports: [RouterLink, TPipe, IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent, MenuComponent],
  template: `
    <app-page-header [title]="'Playlists' | t" [subtitle]="'Defina a ordem e a duração dos conteúdos exibidos nas telas.' | t" icon="list-video">
      @if (auth.can('content')) {
        <a class="primary btn" routerLink="/playlists/nova"><app-icon name="plus" />{{ 'Nova playlist' | t }}</a>
      }
    </app-page-header>

    <div class="card">
      @if (items().length === 0) {
        @if (loaded()) {
          <app-empty-state icon="list-video" [title]="'Nenhuma playlist ainda' | t"
            [text]="'Crie uma e adicione mídias da biblioteca: a ordem e a duração de cada item você define na hora.' | t">
            @if (auth.can('content')) {
              <a class="primary btn" routerLink="/playlists/nova"><app-icon name="plus" />{{ 'Nova playlist' | t }}</a>
            }
          </app-empty-state>
        } @else {
          <app-skeleton [rows]="4" />
        }
      } @else {
        <table class="stack">
          <tr><th>{{ 'Playlist' | t }}</th><th>{{ 'Itens' | t }}</th><th>{{ 'Duração do ciclo' | t }}</th><th>{{ 'Versão' | t }}</th><th></th></tr>
          @for (playlist of items(); track playlist.id) {
            <tr>
              <td class="first">
                <span class="place">
                  <span class="place-icon"><app-icon name="list-video" [size]="18" /></span>
                  <span class="place-text">
                    <a [routerLink]="['/playlists', playlist.id]"><b>{{ playlist.name }}</b></a>
                    @if (playlist.description) {
                      <small>{{ playlist.description }}</small>
                    }
                  </span>
                </span>
              </td>
              <td [attr.data-label]="'Itens' | t"><span class="num">{{ playlist.items.length }}</span></td>
              <td [attr.data-label]="'Duração do ciclo' | t"><span class="when"><app-icon name="timer" [size]="15" /><span class="num">{{ duration(playlist) }}</span></span></td>
              <td [attr.data-label]="'Versão' | t"><app-pill><span class="num">{{ 'v' + playlist.configVersion }}</span></app-pill></td>
              <td class="row-actions">
                <a class="secondary small btn" [routerLink]="['/playlists', playlist.id]"><app-icon [name]="auth.can('content') ? 'pencil' : 'eye'" [size]="15" />{{ (auth.can('content') ? 'Editar' : 'Ver') | t }}</a>
                @if (auth.can('content')) {
                  <app-menu [label]="'Mais ações' | t">
                    <button type="button" class="menu-item danger" (click)="remove(playlist)"><app-icon name="trash" />{{ 'Excluir' | t }}</button>
                  </app-menu>
                }
              </td>
            </tr>
          }
        </table>
      }
    </div>
  `,
  styles: `
    .btn { display: inline-flex; align-items: center; justify-content: center; gap: 8px; color: var(--on-primary); text-align: center; text-decoration: none; }
    a.secondary.btn { color: var(--text); }
    .row-actions { white-space: nowrap; text-align: right; }
    .row-actions > * { margin-left: 6px; vertical-align: middle; }
    .place { display: flex; align-items: center; gap: 12px; min-width: 0; }
    .place-icon { flex: none; display: grid; place-items: center; width: 38px; height: 38px; border-radius: 12px; color: var(--primary); background: var(--primary-soft); border: 1px solid var(--primary-line); }
    :host-context(:root[data-theme='dark']) .place-icon { color: var(--link); }
    .place-text { display: flex; flex-direction: column; min-width: 0; }
    .place-text small { color: var(--muted); }
    .when { display: inline-flex; align-items: center; gap: 7px; }
    .when app-icon { color: var(--muted); }
  `,
})
export class PlaylistsPage implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  readonly items = signal<Playlist[]>([]);
  readonly loaded = signal(false);

  ngOnInit(): void {
    this.load();
  }

  duration(playlist: Playlist): string {
    return formatDuration(totalSeconds(playlist.items));
  }

  remove(playlist: Playlist): void {
    if (!confirm(tr('Excluir a playlist "{name}"? Dispositivos que a usam ficam sem playlist.', { name: playlist.name }))) {
      return;
    }
    this.api.deletePlaylist(playlist.id).subscribe({
      next: () => this.load(),
      error: () => undefined,
    });
  }

  private load(): void {
    this.api.playlists().subscribe({
      next: (playlists) => {
        this.items.set(playlists);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }
}
