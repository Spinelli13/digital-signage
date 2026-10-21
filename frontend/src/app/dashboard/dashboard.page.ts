import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { stateOfConnection, timeAgo } from '../core/format';
import { TPipe } from '../core/i18n';
import { Summary } from '../core/models';

const REFRESH_MS = 10_000;

const EMPTY: Summary = {
  devices: { total: 0, online: 0, offline: 0, never: 0, pending: 0, disabled: 0 },
  locations: 0,
  groups: 0,
  media: 0,
  playlists: 0,
  campaigns: { total: 0, activeNow: 0 },
  openAlerts: 0,
  byLocation: [],
  attention: [],
};

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, TPipe],
  template: `
    <div class="head">
      <div><h1>{{ 'Dashboard' | t }}</h1><p>{{ 'Visão geral da rede de divulgação.' | t }}</p></div>
      <a class="primary btn" routerLink="/dispositivos">{{ '+ Adicionar dispositivo' | t }}</a>
    </div>

    <div class="stats">
      <a class="stat" routerLink="/dispositivos"><div class="label">{{ 'Dispositivos' | t }}</div><div class="n">{{ summary().devices.total }}</div></a>
      <a class="stat" routerLink="/dispositivos" [queryParams]="{ situacao: 'ONLINE' }"><div class="label">{{ 'Online agora' | t }}</div><div class="n ok">{{ summary().devices.online }}</div></a>
      <a class="stat" routerLink="/dispositivos" [queryParams]="{ situacao: 'OFFLINE' }"><div class="label">{{ 'Offline' | t }}</div><div class="n" [class.bad]="summary().devices.offline > 0">{{ summary().devices.offline }}</div></a>
      <a class="stat" routerLink="/dispositivos" [queryParams]="{ situacao: 'NEVER' }"><div class="label">{{ 'Nunca conectaram' | t }}</div><div class="n" [class.warn]="summary().devices.never > 0">{{ summary().devices.never }}</div></a>
      <a class="stat" routerLink="/dispositivos" [queryParams]="{ situacao: 'PENDING' }"><div class="label">{{ 'Aguardando ativação' | t }}</div><div class="n" [class.warn]="summary().devices.pending > 0">{{ summary().devices.pending }}</div></a>
      <a class="stat" routerLink="/dispositivos" [queryParams]="{ situacao: 'DISABLED' }"><div class="label">{{ 'Desativadas' | t }}</div><div class="n">{{ summary().devices.disabled }}</div></a>
      <a class="stat" routerLink="/locais"><div class="label">{{ 'Locais' | t }}</div><div class="n">{{ summary().locations }}</div></a>
      <a class="stat" routerLink="/grupos"><div class="label">{{ 'Grupos' | t }}</div><div class="n">{{ summary().groups }}</div></a>
      <a class="stat" routerLink="/biblioteca"><div class="label">{{ 'Mídias' | t }}</div><div class="n">{{ summary().media }}</div></a>
      <a class="stat" routerLink="/playlists"><div class="label">{{ 'Playlists' | t }}</div><div class="n">{{ summary().playlists }}</div></a>
      <a class="stat" routerLink="/alertas"><div class="label">{{ 'Alertas abertos' | t }}</div><div class="n" [class.bad]="summary().openAlerts > 0" [class.ok]="summary().openAlerts === 0">{{ summary().openAlerts }}</div></a>
      <a class="stat" routerLink="/campanhas"><div class="label">{{ 'Campanhas ativas agora' | t }}</div><div class="n" [class.ok]="summary().campaigns.activeNow > 0">{{ summary().campaigns.activeNow }}<small> {{ 'de {total}' | t: { total: summary().campaigns.total } }}</small></div></a>
    </div>

    <div class="two">
      <div class="card">
        <h3>{{ 'TVs por local' | t }}</h3>
        @if (summary().byLocation.length === 0) {
          <div class="empty">{{ 'Nenhum local ainda.' | t }} <a routerLink="/locais">{{ 'Crie o primeiro' | t }}</a> {{ 'e escolha-o ao ativar cada TV.' | t }}</div>
        } @else {
          <table class="stack">
            <tr><th>{{ 'Local' | t }}</th><th>{{ 'Online' | t }}</th></tr>
            @for (location of summary().byLocation; track location.id ?? 'none') {
              <tr>
                <td class="first">
                  <a routerLink="/dispositivos" [queryParams]="{ local: location.id ?? 'none' }">{{ location.name }}</a>
                </td>
                <td [attr.data-label]="'Online' | t">
                  <span class="st"><span class="dot" [class]="location.online === location.total ? 'on' : location.online === 0 ? 'off' : 'warn'"></span>{{ '{online} de {total}' | t: { online: location.online, total: location.total } }}</span>
                </td>
              </tr>
            }
          </table>
        }
      </div>

      <div class="card">
        <h3>{{ 'Dispositivos com atenção necessária' | t }}</h3>
        @if (summary().attention.length === 0) {
          <div class="empty">{{ 'Tudo certo por aqui.' | t }} 🎉</div>
        } @else {
          <table class="stack">
            <tr><th>{{ 'Dispositivo' | t }}</th><th>{{ 'Local' | t }}</th><th>{{ 'Último contato' | t }}</th><th>{{ 'Status' | t }}</th></tr>
            @for (device of summary().attention; track device.id) {
              <tr>
                <td class="first">{{ device.name }}</td>
                <td [attr.data-label]="'Local' | t">{{ device.location || '—' }}</td>
                <td [attr.data-label]="'Último contato' | t">{{ ago(device.lastSeenAt) }}</td>
                <td [attr.data-label]="'Status' | t"><span class="st"><span class="dot" [class]="stateOf(device.connection).tone"></span>{{ stateOf(device.connection).label }}</span></td>
              </tr>
            }
          </table>
        }
      </div>
    </div>
  `,
  styles: `
    .btn { display: inline-block; color: var(--on-primary); text-align: center; }
    .stat { display: block; color: inherit; text-decoration: none; }
    .stat:hover { border-color: var(--primary); }
    .ok { color: var(--green); }
    .warn { color: var(--orange); }
    .bad { color: var(--red); }
    .two { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(340px, 100%), 1fr)); gap: 16px; align-items: start; }
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
