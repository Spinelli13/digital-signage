import { Component, OnInit, computed, inject, signal } from '@angular/core';

import { ApiService } from '../core/api.service';
import { ConfigService } from '../core/config.service';
import { formatBytes } from '../core/format';
import { FDatePipe, TPipe, currentLang } from '../core/i18n';
import { ReleaseInfo } from '../core/models';
import { IconComponent } from '../shared/icon';
import { EmptyStateComponent, PageHeaderComponent, PillComponent, SkeletonComponent } from '../shared/ui';

@Component({
  selector: 'app-downloads',
  imports: [FDatePipe, TPipe, IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent],
  template: `
    <app-page-header [title]="'Downloads e manuais' | t" [subtitle]="'O aplicativo da TV e os manuais, sempre na versão mais recente.' | t" icon="download"></app-page-header>

    <div class="card latest">
      <div class="label">{{ 'Aplicativo para TV — versão mais recente' | t }}</div>
      @if (latest(); as release) {
        <div class="rel">
          <span class="rel-icon"><app-icon name="monitor-play" [size]="30" [stroke]="1.7" /></span>
          <div class="rel-main">
            <h2 class="version num">{{ release.versionName }}</h2>
            <div class="rel-facts">
              <app-pill icon="calendar">{{ release.publishedAt | fdate }}</app-pill>
              <app-pill icon="hard-drive">{{ size(release.size) }}</app-pill>
            </div>
          </div>
          <a class="primary btn" [href]="apkUrl(release)" download><app-icon name="download" [size]="18" />{{ 'Baixar APK {version}' | t: { version: release.versionName } }}</a>
        </div>
        @if (release.notes) {
          <p class="notes">{{ release.notes }}</p>
        }
        <div class="howto">
          <app-icon name="info" [size]="18" />
          <p>{{ 'Para instalar com o app' | t }} <b>Downloader</b> {{ '(TCL e Fire TV Stick), digite na TV:' | t }} <code>{{ shortLink }}</code></p>
        </div>
        <p class="sha"><app-icon name="shield-check" [size]="14" />SHA-256: {{ release.sha256 }}</p>
      } @else if (loaded()) {
        <app-empty-state [compact]="true" icon="circle-alert" [title]="'Não foi possível carregar a versão mais recente agora.' | t" />
      } @else {
        <app-skeleton [rows]="2" [height]="56" />
      }
    </div>

    <div class="card">
      <h3 class="sec"><app-icon name="book-open" [size]="18" />{{ 'Manuais (PDF)' | t }}</h3>
      <ul class="manuals">
        <li>
          <a class="manual" [href]="manualInstallUrl" target="_blank" rel="noopener">
            <span class="m-icon"><app-icon name="monitor-play" [size]="22" /></span>
            <span class="m-text"><b>{{ 'Manual de instalação na TV' | t }}</b><span class="muted">{{ 'TCL Android TV e Fire TV Stick, passo a passo, com ilustrações' | t }}</span></span>
            <app-icon name="external-link" [size]="17" />
          </a>
        </li>
        <li>
          <a class="manual" [href]="manualPanelUrl" target="_blank" rel="noopener">
            <span class="m-icon"><app-icon name="layout-dashboard" [size]="22" /></span>
            <span class="m-text"><b>{{ 'Manual do painel' | t }}</b><span class="muted">{{ 'Como ativar TVs, enviar mídias e montar playlists' | t }}</span></span>
            <app-icon name="external-link" [size]="17" />
          </a>
        </li>
      </ul>
      <p class="muted">{{ 'Os manuais e o aplicativo são atualizados automaticamente a cada nova versão.' | t }}</p>
      @if (english()) {
        <p class="muted">{{ 'Os manuais estão disponíveis apenas em português por enquanto.' | t }}</p>
      }
    </div>

    @if (releases().length > 1) {
      <div class="card">
        <h3 class="sec"><app-icon name="rotate-ccw-clock" [size]="18" />{{ 'Versões anteriores' | t }}</h3>
        <table class="stack">
          <tr><th>{{ 'Versão' | t }}</th><th>{{ 'Data' | t }}</th><th>{{ 'Tamanho' | t }}</th><th>{{ 'Novidades' | t }}</th><th></th></tr>
          @for (release of releases(); track release.versionCode) {
            <tr>
              <td class="first"><b class="num">{{ release.versionName }}</b></td>
              <td [attr.data-label]="'Data' | t">{{ release.publishedAt | fdate }}</td>
              <td [attr.data-label]="'Tamanho' | t">{{ size(release.size) }}</td>
              <td [attr.data-label]="'Novidades' | t">{{ release.notes || '—' }}</td>
              <td class="row-actions"><a class="secondary small btn" [href]="apkUrl(release)" download><app-icon name="download" [size]="15" />{{ 'Baixar' | t }}</a></td>
            </tr>
          }
        </table>
      </div>
    }
  `,
  styles: `
    .btn { display: inline-flex; align-items: center; justify-content: center; gap: 8px; color: var(--on-primary); text-decoration: none; }
    a.secondary.btn { color: var(--text); }
    .latest { background: var(--grad-hero-soft, var(--panel)); }
    .rel { display: flex; align-items: center; gap: 18px; flex-wrap: wrap; margin-top: 10px; }
    .rel-icon { flex: none; display: grid; place-items: center; width: 64px; height: 64px; border-radius: 20px; color: #fff; background: var(--grad-primary); box-shadow: var(--sh-2); }
    .rel-main { flex: 1 1 200px; min-width: 0; }
    .version { margin: 0 0 6px; font-size: 30px; letter-spacing: -0.02em; }
    .rel-facts { display: flex; gap: 8px; flex-wrap: wrap; }
    .notes { margin: 16px 0 0; }
    .howto { display: flex; gap: 10px; align-items: flex-start; margin-top: 16px; padding: 12px 14px; border-radius: var(--r); background: var(--info-bg); border: 1px solid var(--info-line); color: var(--info-fg); }
    .howto app-icon { flex: none; margin-top: 1px; }
    .howto p { margin: 0; color: var(--text); }
    .sha { display: flex; align-items: flex-start; gap: 6px; margin: 14px 0 0; font-family: ui-monospace, Consolas, monospace; font-size: 12px; word-break: break-all; color: var(--muted); }
    .sha app-icon { flex: none; margin-top: 1px; }
    .sec { display: flex; align-items: center; gap: 8px; }
    .sec app-icon { color: var(--primary); }
    :host-context(:root[data-theme='dark']) .sec app-icon { color: var(--link); }
    .manuals { list-style: none; padding: 0; margin: 12px 0 10px; display: grid; gap: 10px; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); }
    .manual { display: flex; align-items: center; gap: 14px; padding: 14px; border: 1px solid var(--line); border-radius: var(--r-lg); background: var(--panel); color: var(--text); text-decoration: none; transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s; }
    .manual:hover { border-color: var(--primary-line); box-shadow: var(--sh-2); transform: translateY(-1px); }
    .m-icon { flex: none; display: grid; place-items: center; width: 46px; height: 46px; border-radius: 14px; color: var(--primary); background: var(--primary-soft); border: 1px solid var(--primary-line); }
    :host-context(:root[data-theme='dark']) .m-icon { color: var(--link); }
    .m-text { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
    .manual > app-icon { color: var(--muted); }
    code { background: var(--panel-2); border: 1px solid var(--line); padding: 1px 6px; border-radius: 6px; word-break: break-all; }
    @media (max-width: 720px) {
      .btn { width: 100%; padding: 12px 16px; }
      .manuals { grid-template-columns: 1fr; }
    }
    .card { margin-bottom: 16px; }
  `,
})
export class DownloadsPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly config = inject(ConfigService);

  readonly latest = signal<ReleaseInfo | null>(null);
  readonly releases = signal<ReleaseInfo[]>([]);
  readonly loaded = signal(false);
  readonly size = formatBytes;
  readonly english = computed(() => currentLang() === 'en');

  readonly manualInstallUrl = `${this.config.downloadsUrl}/manual-instalacao-tv.pdf`;
  readonly manualPanelUrl = `${this.config.downloadsUrl}/manual-painel.pdf`;
  readonly shortLink = `${this.config.downloadsUrl.replace(/^https?:\/\//, '')}/d`;

  ngOnInit(): void {
    this.api.releases().subscribe({
      next: (list) => {
        this.releases.set(list);
        this.latest.set(list[0] ?? null);
        this.loaded.set(true);
      },
      // O aviso é exibido pelo interceptor de erros.
      error: () => this.loaded.set(true),
    });
  }

  apkUrl(release: ReleaseInfo): string {
    return `${this.config.downloadsUrl}/${release.apk}`;
  }
}
