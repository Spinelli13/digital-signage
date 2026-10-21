import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { TPipe, tr } from '../core/i18n';
import { Playlist } from '../core/models';
import { formatDuration, totalSeconds } from './playlist-editor';

@Component({
  selector: 'app-playlists',
  imports: [RouterLink, TPipe],
  template: `
    <div class="head">
      <div><h1>{{ 'Playlists' | t }}</h1><p>{{ 'Defina a ordem e a duração dos conteúdos exibidos nas telas.' | t }}</p></div>
      @if (auth.can('content')) {
        <a class="primary btn" routerLink="/playlists/nova">{{ '+ Nova playlist' | t }}</a>
      }
    </div>

    <div class="card">
      @if (items().length === 0) {
        <div class="empty">{{ 'Nenhuma playlist ainda. Crie uma e adicione mídias da biblioteca.' | t }}</div>
      } @else {
        <table class="stack">
          <tr><th>{{ 'Playlist' | t }}</th><th>{{ 'Itens' | t }}</th><th>{{ 'Duração do ciclo' | t }}</th><th>{{ 'Versão' | t }}</th><th></th></tr>
          @for (playlist of items(); track playlist.id) {
            <tr>
              <td class="first"><a [routerLink]="['/playlists', playlist.id]"><b>{{ playlist.name }}</b></a><br /><small>{{ playlist.description }}</small></td>
              <td [attr.data-label]="'Itens' | t">{{ playlist.items.length }}</td>
              <td [attr.data-label]="'Duração do ciclo' | t">{{ duration(playlist) }}</td>
              <td [attr.data-label]="'Versão' | t">{{ 'v' + playlist.configVersion }}</td>
              <td class="row-actions">
                <a class="secondary small btn" [routerLink]="['/playlists', playlist.id]">{{ (auth.can('content') ? 'Editar' : 'Ver') | t }}</a>
                @if (auth.can('content')) {
                  <button class="danger small" (click)="remove(playlist)">{{ 'Excluir' | t }}</button>
                }
              </td>
            </tr>
          }
        </table>
      }
    </div>
  `,
  styles: `
    .btn { display: inline-block; color: var(--on-primary); text-align: center; }
    a.secondary.btn { color: var(--text); }
    .row-actions { white-space: nowrap; text-align: right; }
    .row-actions > * { margin-left: 6px; }
  `,
})
export class PlaylistsPage implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  readonly items = signal<Playlist[]>([]);

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
      next: (playlists) => this.items.set(playlists),
      error: () => undefined,
    });
  }
}
