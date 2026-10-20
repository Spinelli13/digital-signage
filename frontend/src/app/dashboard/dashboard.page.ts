import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { stateOfConnection, timeAgo } from '../core/format';
import { Summary } from '../core/models';

const REFRESH_MS = 10_000;

const EMPTY: Summary = {
  devices: { total: 0, online: 0, offline: 0, never: 0, pending: 0, disabled: 0 },
  locations: 0,
  groups: 0,
  media: 0,
  playlists: 0,
  byLocation: [],
  attention: [],
};

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink],
  template: `
    <div class="head">
      <div><h1>Dashboard</h1><p>Visão geral da rede de divulgação.</p></div>
      <a class="primary btn" routerLink="/dispositivos">+ Adicionar dispositivo</a>
    </div>

    <div class="stats">
      <a class="stat" routerLink="/dispositivos"><div class="label">Dispositivos</div><div class="n">{{ summary().devices.total }}</div></a>
      <a class="stat" routerLink="/dispositivos" [queryParams]="{ situacao: 'ONLINE' }"><div class="label">Online agora</div><div class="n ok">{{ summary().devices.online }}</div></a>
      <a class="stat" routerLink="/dispositivos" [queryParams]="{ situacao: 'OFFLINE' }"><div class="label">Offline</div><div class="n" [class.bad]="summary().devices.offline > 0">{{ summary().devices.offline }}</div></a>
      <a class="stat" routerLink="/dispositivos" [queryParams]="{ situacao: 'NEVER' }"><div class="label">Nunca conectaram</div><div class="n" [class.warn]="summary().devices.never > 0">{{ summary().devices.never }}</div></a>
      <a class="stat" routerLink="/dispositivos" [queryParams]="{ situacao: 'PENDING' }"><div class="label">Aguardando ativação</div><div class="n" [class.warn]="summary().devices.pending > 0">{{ summary().devices.pending }}</div></a>
      <a class="stat" routerLink="/dispositivos" [queryParams]="{ situacao: 'DISABLED' }"><div class="label">Desativadas</div><div class="n">{{ summary().devices.disabled }}</div></a>
      <a class="stat" routerLink="/locais"><div class="label">Locais</div><div class="n">{{ summary().locations }}</div></a>
      <a class="stat" routerLink="/grupos"><div class="label">Grupos</div><div class="n">{{ summary().groups }}</div></a>
      <a class="stat" routerLink="/biblioteca"><div class="label">Mídias</div><div class="n">{{ summary().media }}</div></a>
      <a class="stat" routerLink="/playlists"><div class="label">Playlists</div><div class="n">{{ summary().playlists }}</div></a>
    </div>

    <div class="two">
      <div class="card">
        <h3>TVs por local</h3>
        @if (summary().byLocation.length === 0) {
          <div class="empty">Nenhum local ainda. <a routerLink="/locais">Crie o primeiro</a> e escolha-o ao ativar cada TV.</div>
        } @else {
          <table>
            <tr><th>Local</th><th>Online</th></tr>
            @for (location of summary().byLocation; track location.id ?? 'none') {
              <tr>
                <td>
                  <a routerLink="/dispositivos" [queryParams]="{ local: location.id ?? 'none' }">{{ location.name }}</a>
                </td>
                <td>
                  <span class="dot" [class]="location.online === location.total ? 'on' : location.online === 0 ? 'off' : 'warn'"></span>{{ location.online }} de {{ location.total }}
                </td>
              </tr>
            }
          </table>
        }
      </div>

      <div class="card">
        <h3>Dispositivos com atenção necessária</h3>
        @if (summary().attention.length === 0) {
          <div class="empty">Tudo certo por aqui. 🎉</div>
        } @else {
          <table>
            <tr><th>Dispositivo</th><th>Local</th><th>Último contato</th><th>Status</th></tr>
            @for (device of summary().attention; track device.id) {
              <tr>
                <td>{{ device.name }}</td>
                <td>{{ device.location || '—' }}</td>
                <td>{{ ago(device.lastSeenAt) }}</td>
                <td><span class="dot" [class]="stateOf(device.connection).tone"></span>{{ stateOf(device.connection).label }}</td>
              </tr>
            }
          </table>
        }
      </div>
    </div>
  `,
  styles: `
    .btn { display: inline-block; color: #fff; }
    .stat { display: block; color: inherit; text-decoration: none; }
    .stat:hover { border-color: var(--blue); }
    .ok { color: var(--green); }
    .warn { color: var(--orange); }
    .bad { color: #d6455d; }
    .two { display: grid; grid-template-columns: repeat(auto-fit, minmax(340px, 1fr)); gap: 16px; align-items: start; }
  `,
})
export class DashboardPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly summary = signal<Summary>(EMPTY);

  readonly stateOf = stateOfConnection;
  readonly ago = (iso: string | null) => timeAgo(iso);

  ngOnInit(): void {
    this.refresh();
    const timer = setInterval(() => this.refresh(), REFRESH_MS);
    this.destroyRef.onDestroy(() => clearInterval(timer));
  }

  private refresh(): void {
    this.api.summary().subscribe({
      next: (summary) => this.summary.set(summary),
      // O aviso é exibido pelo interceptor de erros; aqui só não deixamos a exceção sem tratamento.
      error: () => undefined,
    });
  }
}
