import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DestroyRef } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ApiService } from '../core/api.service';
import { deviceState, needsAttention, timeAgo } from '../core/format';
import { Device } from '../core/models';

const REFRESH_MS = 10_000;

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink],
  template: `
    <div class="head">
      <div><h1>Dashboard</h1><p>Visão geral da rede de divulgação.</p></div>
      <a class="primary btn" routerLink="/dispositivos">+ Adicionar dispositivo</a>
    </div>

    <div class="stats">
      <div class="stat"><div class="label">Dispositivos</div><div class="n">{{ devices().length }}</div></div>
      <div class="stat"><div class="label">Online agora</div><div class="n ok">{{ online() }}</div></div>
      <div class="stat"><div class="label">Aguardando ativação</div><div class="n">{{ pending() }}</div></div>
      <div class="stat"><div class="label">Mídias</div><div class="n">{{ mediaCount() }}</div></div>
      <div class="stat"><div class="label">Playlists</div><div class="n">{{ playlistCount() }}</div></div>
    </div>

    <div class="card">
      <h3>Dispositivos com atenção necessária</h3>
      @if (attention().length === 0) {
        <div class="empty">Tudo certo por aqui. 🎉</div>
      } @else {
        <table>
          <tr><th>Dispositivo</th><th>Local</th><th>Último contato</th><th>Status</th></tr>
          @for (device of attention(); track device.id) {
            <tr>
              <td>{{ device.name }}</td>
              <td>{{ device.location || '—' }}</td>
              <td>{{ ago(device.lastSeenAt) }}</td>
              <td><span class="dot" [class]="state(device).tone"></span>{{ state(device).label }}</td>
            </tr>
          }
        </table>
      }
    </div>
  `,
  styles: `
    .btn { display: inline-block; color: #fff; }
    .ok { color: var(--green); }
  `,
})
export class DashboardPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly devices = signal<Device[]>([]);
  readonly mediaCount = signal(0);
  readonly playlistCount = signal(0);

  readonly online = computed(() => this.devices().filter((d) => d.status === 'ACTIVE' && d.online).length);
  readonly pending = computed(() => this.devices().filter((d) => d.status === 'PENDING').length);
  readonly attention = computed(() => this.devices().filter(needsAttention));

  readonly state = deviceState;
  readonly ago = (iso: string | null) => timeAgo(iso);

  ngOnInit(): void {
    this.refresh();
    const timer = setInterval(() => this.refresh(), REFRESH_MS);
    this.destroyRef.onDestroy(() => clearInterval(timer));
  }

  private refresh(): void {
    forkJoin({ devices: this.api.devices(), media: this.api.mediaList(), playlists: this.api.playlists() }).subscribe({
      next: ({ devices, media, playlists }) => {
        this.devices.set(devices);
        this.mediaCount.set(media.length);
        this.playlistCount.set(playlists.length);
      },
      // O aviso é exibido pelo interceptor de erros; aqui só não deixamos a exceção sem tratamento.
      error: () => undefined,
    });
  }
}
