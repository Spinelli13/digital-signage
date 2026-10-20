import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { Playlist } from '../core/models';
import { formatDuration, totalSeconds } from './playlist-editor';

@Component({
  selector: 'app-playlists',
  imports: [RouterLink],
  template: `
    <div class="head">
      <div><h1>Playlists</h1><p>Defina a ordem e a duração dos conteúdos exibidos nas telas.</p></div>
      <a class="primary btn" routerLink="/playlists/nova">+ Nova playlist</a>
    </div>

    <div class="card">
      @if (items().length === 0) {
        <div class="empty">Nenhuma playlist ainda. Crie uma e adicione mídias da biblioteca.</div>
      } @else {
        <table>
          <tr><th>Playlist</th><th>Itens</th><th>Duração do ciclo</th><th>Versão</th><th></th></tr>
          @for (playlist of items(); track playlist.id) {
            <tr>
              <td><a [routerLink]="['/playlists', playlist.id]"><b>{{ playlist.name }}</b></a><br /><small>{{ playlist.description }}</small></td>
              <td>{{ playlist.items.length }}</td>
              <td>{{ duration(playlist) }}</td>
              <td>v{{ playlist.configVersion }}</td>
              <td class="row-actions">
                <a class="secondary small btn" [routerLink]="['/playlists', playlist.id]">Editar</a>
                <button class="danger small" (click)="remove(playlist)">Excluir</button>
              </td>
            </tr>
          }
        </table>
      }
    </div>
  `,
  styles: `
    .btn { display: inline-block; color: #fff; }
    a.secondary.btn { color: var(--text); }
    .row-actions { white-space: nowrap; text-align: right; }
    .row-actions > * { margin-left: 6px; }
  `,
})
export class PlaylistsPage implements OnInit {
  private readonly api = inject(ApiService);

  readonly items = signal<Playlist[]>([]);

  ngOnInit(): void {
    this.load();
  }

  duration(playlist: Playlist): string {
    return formatDuration(totalSeconds(playlist.items));
  }

  remove(playlist: Playlist): void {
    if (!confirm(`Excluir a playlist "${playlist.name}"? Dispositivos que a usam ficam sem playlist.`)) {
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
