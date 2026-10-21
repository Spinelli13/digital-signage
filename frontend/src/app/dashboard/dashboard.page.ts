import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { stateOfConnection, timeAgo } from '../core/format';
import { TPipe, tr } from '../core/i18n';
import { Connection, Summary } from '../core/models';
import { IconComponent } from '../shared/icon';
import { SITUATION } from '../shared/connection';
import { EmptyStateComponent, PageHeaderComponent, PillComponent } from '../shared/ui';

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
  imports: [RouterLink, TPipe, IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent],
  template: `
    <app-page-header [title]="'Dashboard' | t" [subtitle]="greeting()" icon="layout-dashboard">
      <a class="primary" routerLink="/dispositivos"><app-icon name="plus" />{{ 'Adicionar dispositivo' | t }}</a>
    </app-page-header>

    @if (summary().devices.total === 0 && loaded()) {
      <section class="card onboarding">
        <div class="ob-head">
          <span class="ob-icon"><app-icon name="rocket" [size]="26" [stroke]="1.8" /></span>
          <div>
            <h2>{{ 'Vamos colocar a sua primeira tela no ar' | t }}</h2>
            <p>{{ 'Em quatro passos a sua propaganda aparece na TV.' | t }}</p>
          </div>
        </div>
        <ol class="steps">
          <li><span class="n">1</span><div><b>{{ 'Ative a TV' | t }}</b><small>{{ 'Abra o app Player na TV: ele mostra um código. Digite-o em Dispositivos.' | t }}</small></div><a class="secondary small" routerLink="/dispositivos">{{ 'Dispositivos' | t }}<app-icon name="arrow-right" [size]="15" /></a></li>
          <li><span class="n">2</span><div><b>{{ 'Envie as mídias' | t }}</b><small>{{ 'Imagens, vídeos, páginas da web ou QR Codes.' | t }}</small></div><a class="secondary small" routerLink="/biblioteca">{{ 'Biblioteca' | t }}<app-icon name="arrow-right" [size]="15" /></a></li>
          <li><span class="n">3</span><div><b>{{ 'Monte uma playlist' | t }}</b><small>{{ 'Escolha a ordem e o tempo de cada mídia.' | t }}</small></div><a class="secondary small" routerLink="/playlists">{{ 'Playlists' | t }}<app-icon name="arrow-right" [size]="15" /></a></li>
          <li><span class="n">4</span><div><b>{{ 'Escolha o que a TV exibe' | t }}</b><small>{{ 'Selecione a playlist na lista de dispositivos.' | t }}</small></div><a class="secondary small" routerLink="/dispositivos">{{ 'Dispositivos' | t }}<app-icon name="arrow-right" [size]="15" /></a></li>
        </ol>
      </section>
    } @else {
      <section class="hero">
        <div class="ring" [class.empty]="health() === null" role="img" [attr.aria-label]="ringLabel()">
          <svg viewBox="0 0 120 120">
            <defs>
              <linearGradient id="ringGrad" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0" stop-color="#7cf0c4" />
                <stop offset="1" stop-color="#2fcf92" />
              </linearGradient>
            </defs>
            <circle class="track" cx="60" cy="60" r="50" />
            <circle class="arc" [class.hide]="!health()" cx="60" cy="60" r="50" [attr.stroke-dasharray]="dash()" transform="rotate(-90 60 60)" />
          </svg>
          <div class="ring-text">
            <b class="num">{{ health() === null ? '—' : health() + '%' }}</b>
            <small>{{ 'online' | t }}</small>
          </div>
        </div>

        <div class="hero-main">
          <span class="eyebrow">{{ 'Saúde da rede' | t }}</span>
          <h2 class="num">{{ '{online} de {active} TVs online' | t: { online: summary().devices.online, active: active() } }}</h2>
          <p>{{ headline() }}</p>
          <div class="chips">
            <a [routerLink]="['/dispositivos']" [queryParams]="{ situacao: 'ONLINE' }"><app-pill tone="ok" icon="monitor-check">{{ 'Online' | t }} · {{ summary().devices.online }}</app-pill></a>
            <a [routerLink]="['/dispositivos']" [queryParams]="{ situacao: 'OFFLINE' }"><app-pill [tone]="summary().devices.offline > 0 ? 'err' : 'neutral'" icon="wifi-off">{{ 'Offline' | t }} · {{ summary().devices.offline }}</app-pill></a>
            <a [routerLink]="['/dispositivos']" [queryParams]="{ situacao: 'NEVER' }"><app-pill [tone]="summary().devices.never > 0 ? 'warn' : 'neutral'" icon="plug-zap">{{ 'Nunca conectaram' | t }} · {{ summary().devices.never }}</app-pill></a>
            <a [routerLink]="['/dispositivos']" [queryParams]="{ situacao: 'PENDING' }"><app-pill [tone]="summary().devices.pending > 0 ? 'info' : 'neutral'" icon="hourglass">{{ 'Aguardando ativação' | t }} · {{ summary().devices.pending }}</app-pill></a>
            <a [routerLink]="['/dispositivos']" [queryParams]="{ situacao: 'DISABLED' }"><app-pill icon="monitor-off">{{ 'Desativadas' | t }} · {{ summary().devices.disabled }}</app-pill></a>
          </div>
        </div>
      </section>

      <div class="kpis">
        <a class="kpi" routerLink="/alertas" [class.alert]="summary().openAlerts > 0">
          <span class="kpi-icon"><app-icon name="bell" [size]="20" /></span>
          <span class="kpi-body"><small>{{ 'Alertas abertos' | t }}</small><b class="num">{{ summary().openAlerts }}</b></span>
        </a>
        <a class="kpi" routerLink="/campanhas">
          <span class="kpi-icon"><app-icon name="megaphone" [size]="20" /></span>
          <span class="kpi-body"><small>{{ 'Campanhas ativas agora' | t }}</small><b class="num">{{ summary().campaigns.activeNow }}<span class="of"> {{ 'de {total}' | t: { total: summary().campaigns.total } }}</span></b></span>
        </a>
        <a class="kpi" routerLink="/biblioteca">
          <span class="kpi-icon"><app-icon name="image" [size]="20" /></span>
          <span class="kpi-body"><small>{{ 'Mídias' | t }}</small><b class="num">{{ summary().media }}</b></span>
        </a>
        <a class="kpi" routerLink="/playlists">
          <span class="kpi-icon"><app-icon name="list-video" [size]="20" /></span>
          <span class="kpi-body"><small>{{ 'Playlists' | t }}</small><b class="num">{{ summary().playlists }}</b></span>
        </a>
        <a class="kpi" routerLink="/locais">
          <span class="kpi-icon"><app-icon name="map-pin" [size]="20" /></span>
          <span class="kpi-body"><small>{{ 'Locais' | t }}</small><b class="num">{{ summary().locations }}</b></span>
        </a>
        <a class="kpi" routerLink="/grupos">
          <span class="kpi-icon"><app-icon name="layers" [size]="20" /></span>
          <span class="kpi-body"><small>{{ 'Grupos' | t }}</small><b class="num">{{ summary().groups }}</b></span>
        </a>
      </div>

      <div class="two">
        <section class="card">
          <h3><app-icon name="triangle-alert" [size]="18" />{{ 'Precisa de atenção' | t }}</h3>
          @if (summary().attention.length === 0) {
            <app-empty-state icon="circle-check" [compact]="true" [title]="'Tudo certo por aqui.' | t" [text]="'Nenhuma TV precisa de você agora.' | t" />
          } @else {
            <ul class="attn">
              @for (device of summary().attention; track device.id) {
                <li>
                  <span class="a-icon" [class]="sit(device.connection).tone"><app-icon [name]="sit(device.connection).icon" [size]="18" /></span>
                  <div class="a-text">
                    <b>{{ device.name }}</b>
                    <small>{{ device.location || ('Sem local' | t) }} · {{ stateOf(device.connection).label }} · {{ ago(device.lastSeenAt) }}</small>
                  </div>
                  <a class="secondary small" [routerLink]="device.connection === 'PENDING' ? ['/dispositivos'] : ['/dispositivos', device.id, 'historico']">
                    {{ (device.connection === 'PENDING' ? 'Ativar' : 'Ver histórico') | t }}
                  </a>
                </li>
              }
            </ul>
          }
        </section>

        <section class="card">
          <h3><app-icon name="map-pin" [size]="18" />{{ 'TVs por local' | t }}</h3>
          @if (summary().byLocation.length === 0) {
            <app-empty-state icon="map-pin" [compact]="true" [title]="'Nenhum local ainda.' | t" [text]="'Crie o primeiro e escolha-o ao ativar cada TV.' | t">
              <a class="secondary small" routerLink="/locais">{{ 'Criar local' | t }}</a>
            </app-empty-state>
          } @else {
            <ul class="locs">
              @for (location of summary().byLocation; track location.id ?? 'none') {
                <li>
                  <div class="l-head">
                    <a [routerLink]="['/dispositivos']" [queryParams]="{ local: location.id ?? 'none' }">{{ location.name }}</a>
                    <span class="num">{{ '{online} de {total}' | t: { online: location.online, total: location.total } }}</span>
                  </div>
                  <div class="bar" [class.low]="location.online === 0" [class.mid]="location.online > 0 && location.online < location.total">
                    <span [style.width.%]="location.total > 0 ? (location.online * 100) / location.total : 0"></span>
                  </div>
                </li>
              }
            </ul>
          }
        </section>
      </div>
    }
  `,
  styles: `
    .hero {
      position: relative; overflow: hidden; display: flex; align-items: center; gap: 32px; padding: 28px 32px; margin-bottom: 18px;
      border-radius: var(--r-xl); color: #fff; background: var(--grad-hero); box-shadow: var(--sh-2);
    }
    .hero::after { content: ''; position: absolute; right: -80px; top: -120px; width: 380px; height: 380px; border-radius: 50%; background: radial-gradient(circle, rgba(63, 224, 162, 0.3), transparent 65%); pointer-events: none; }
    .ring { position: relative; flex: none; width: 140px; height: 140px; }
    .ring svg { width: 100%; height: 100%; }
    .ring .track { fill: none; stroke: rgba(255, 255, 255, 0.16); stroke-width: 12; }
    .ring .arc { fill: none; stroke: url(#ringGrad); stroke-width: 12; stroke-linecap: round; transition: stroke-dasharray 0.6s var(--ease); }
    .ring .arc.hide { stroke: transparent; }
    .ring-text { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; line-height: 1.1; }
    .ring-text b { font-size: 30px; font-weight: 750; letter-spacing: -0.02em; }
    .ring-text small { color: rgba(255, 255, 255, 0.75); font-size: 12px; }
    .hero-main { position: relative; min-width: 0; }
    .eyebrow { display: inline-block; font-size: 12px; font-weight: 700; letter-spacing: 0.1em; text-transform: uppercase; color: var(--accent); }
    .hero h2 { margin: 4px 0 4px; font-size: 28px; letter-spacing: -0.02em; font-weight: 750; }
    .hero p { color: rgba(255, 255, 255, 0.82); margin: 0 0 16px; }
    .chips { display: flex; flex-wrap: wrap; gap: 8px; }
    .chips a { text-decoration: none; }
    .chips a:hover app-pill { filter: brightness(1.05); transform: translateY(-1px); }
    .chips app-pill { transition: transform 0.15s var(--ease); }

    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(158px, 1fr)); gap: 14px; margin-bottom: 18px; }
    .kpi {
      display: flex; align-items: center; gap: 14px; padding: 16px; background: var(--panel); border: 1px solid var(--line); border-radius: var(--r-lg);
      box-shadow: var(--sh-1); color: inherit; text-decoration: none; transition: transform 0.18s var(--ease), box-shadow 0.18s var(--ease), border-color 0.18s var(--ease);
    }
    .kpi:hover { transform: translateY(-2px); box-shadow: var(--sh-2); border-color: var(--primary-line); text-decoration: none; }
    .kpi-icon { flex: none; display: grid; place-items: center; width: 44px; height: 44px; border-radius: 13px; color: var(--primary); background: var(--primary-soft); }
    :host-context(:root[data-theme='dark']) .kpi-icon { color: var(--link); }
    .kpi.alert .kpi-icon { color: var(--red); background: var(--err-bg); }
    .kpi.alert b { color: var(--red); }
    .kpi-body { display: flex; flex-direction: column; min-width: 0; line-height: 1.2; }
    .kpi-body small { line-height: 1.25; }
    .kpi-body b { font-size: 26px; font-weight: 750; letter-spacing: -0.02em; }
    .of { font-size: 13px; font-weight: 500; color: var(--muted); letter-spacing: 0; }

    .two { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(380px, 100%), 1fr)); gap: 16px; align-items: start; }
    .attn, .locs { list-style: none; margin: 0; padding: 0; display: grid; gap: 4px; }
    .attn li { display: flex; align-items: center; gap: 12px; padding: 10px 6px; border-radius: var(--r); }
    .attn li:hover { background: var(--panel-2); }
    .a-icon { flex: none; display: grid; place-items: center; width: 38px; height: 38px; border-radius: 11px; background: var(--panel-3); color: var(--text-2); }
    .a-icon.err { background: var(--err-bg); color: var(--err-fg); }
    .a-icon.warn { background: var(--warn-bg); color: var(--warn-fg); }
    .a-icon.info { background: var(--info-bg); color: var(--info-fg); }
    .a-text { display: flex; flex-direction: column; min-width: 0; flex: 1; line-height: 1.3; }
    .a-text b { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .a-text small { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .locs { gap: 14px; }
    .l-head { display: flex; justify-content: space-between; align-items: baseline; gap: 12px; margin-bottom: 6px; font-weight: 600; }
    .l-head .num { color: var(--muted); font-weight: 500; font-size: 13px; }
    .bar { height: 8px; border-radius: 999px; background: var(--panel-3); overflow: hidden; }
    .bar span { display: block; height: 100%; border-radius: 999px; background: var(--green); transition: width 0.5s var(--ease); }
    .bar.mid span { background: var(--orange); }
    .bar.low span { background: var(--red); }

    .onboarding { padding: 26px; }
    .ob-head { display: flex; align-items: center; gap: 16px; margin-bottom: 18px; }
    .ob-head h2 { margin: 0; }
    .ob-head p { color: var(--muted); }
    .ob-icon { flex: none; display: grid; place-items: center; width: 56px; height: 56px; border-radius: 17px; color: #fff; background: var(--grad-primary); box-shadow: 0 6px 16px rgba(15, 95, 66, 0.3); }
    .steps { list-style: none; margin: 0; padding: 0; display: grid; gap: 10px; }
    .steps li { display: flex; align-items: center; gap: 14px; padding: 14px; border: 1px solid var(--line); border-radius: var(--r); background: var(--panel-2); }
    .steps .n { flex: none; display: grid; place-items: center; width: 32px; height: 32px; border-radius: 50%; font-weight: 700; color: var(--primary); background: var(--primary-soft); border: 1px solid var(--primary-line); }
    .steps li > div { display: flex; flex-direction: column; flex: 1; min-width: 0; }

    @media (max-width: 720px) {
      .hero { flex-direction: column; align-items: flex-start; gap: 18px; padding: 22px 20px; border-radius: var(--r-lg); }
      .ring { width: 116px; height: 116px; }
      .ring-text b { font-size: 25px; }
      .hero h2 { font-size: 23px; }
      .kpis { grid-template-columns: repeat(2, 1fr); gap: 10px; }
      .kpi { flex-direction: column; align-items: flex-start; gap: 10px; padding: 14px; }
      .attn li { flex-wrap: wrap; }
      .attn li .secondary { margin-left: 50px; }
      .steps li { flex-wrap: wrap; }
      .steps li .secondary { margin-left: 46px; }
      .ob-head { align-items: flex-start; }
    }
  `,
})
export class DashboardPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly summary = signal<Summary>(EMPTY);
  readonly loaded = signal(false);

  readonly stateOf = stateOfConnection;
  readonly ago = (iso: string | null) => timeAgo(iso);
  readonly sit = (connection: Connection) => SITUATION[connection];

  /** TVs que deveriam estar no ar (ativas): as que aguardam ativação ou foram desativadas não entram na conta. */
  readonly active = computed(() => {
    const d = this.summary().devices;
    return d.total - d.pending - d.disabled;
  });

  /** Percentual de TVs ativas que estão online agora (nulo enquanto não há nenhuma ativa). */
  readonly health = computed(() => {
    const active = this.active();
    return active > 0 ? Math.round((this.summary().devices.online * 100) / active) : null;
  });

  /** Arco do anel (comprimento de um círculo de raio 50 = 314,16). */
  readonly dash = computed(() => {
    const circumference = 2 * Math.PI * 50;
    const filled = ((this.health() ?? 0) / 100) * circumference;
    return `${filled} ${circumference}`;
  });

  readonly ringLabel = computed(() =>
    this.health() === null ? tr('Nenhuma TV ativa ainda') : tr('{percent}% das TVs ativas estão online', { percent: this.health() }),
  );

  readonly greeting = computed(() => {
    const first = (this.auth.me()?.name ?? '').trim().split(/\s+/)[0];
    return first ? tr('Olá, {name}. Veja como está a sua rede agora.', { name: first }) : tr('Visão geral da rede de divulgação.');
  });

  readonly headline = computed(() => {
    const d = this.summary().devices;
    if (this.active() === 0) {
      return tr('Ative uma TV para começar a acompanhar a rede.');
    }
    const trouble = d.offline + d.never;
    return trouble === 0 ? tr('Tudo funcionando: todas as TVs ativas estão no ar.') : tr('{n} TV(s) precisam de atenção.', { n: trouble });
  });

  ngOnInit(): void {
    this.refresh();
    const timer = setInterval(() => this.refresh(), REFRESH_MS);
    this.destroyRef.onDestroy(() => clearInterval(timer));
  }

  private refresh(): void {
    this.api.summary().subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.loaded.set(true);
      },
      // O aviso é exibido pelo interceptor de erros; aqui só não deixamos a exceção sem tratamento.
      error: () => undefined,
    });
  }
}
