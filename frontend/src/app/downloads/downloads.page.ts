import { Component, OnInit, computed, inject, signal } from '@angular/core';

import { ApiService } from '../core/api.service';
import { ConfigService } from '../core/config.service';
import { formatBytes } from '../core/format';
import { FDatePipe, TPipe, currentLang } from '../core/i18n';
import { ReleaseInfo } from '../core/models';

@Component({
  selector: 'app-downloads',
  imports: [FDatePipe, TPipe],
  template: `
    <div class="head">
      <div>
        <h1>{{ 'Downloads e manuais' | t }}</h1>
        <p>{{ 'O aplicativo da TV e os manuais, sempre na versão mais recente.' | t }}</p>
      </div>
    </div>

    <div class="card latest">
      <div class="label">{{ 'Aplicativo para TV — versão mais recente' | t }}</div>
      @if (latest(); as release) {
        <h2 class="version">
          {{ release.versionName }}
          <span class="muted">· {{ release.publishedAt | fdate }} · {{ size(release.size) }}</span>
        </h2>
        @if (release.notes) {
          <p>{{ release.notes }}</p>
        }
        <p>
          <a class="primary btn" [href]="apkUrl(release)" download>{{ 'Baixar APK {version}' | t: { version: release.versionName } }}</a>
        </p>
        <p class="muted">
          {{ 'Para instalar com o app' | t }} <b>Downloader</b> {{ '(TCL e Fire TV Stick), digite na TV:' | t }} <code>{{ shortLink }}</code>
        </p>
        <p class="sha">SHA-256: {{ release.sha256 }}</p>
      } @else if (loaded()) {
        <p class="muted">{{ 'Não foi possível carregar a versão mais recente agora.' | t }}</p>
      } @else {
        <p class="muted">{{ 'Carregando…' | t }}</p>
      }
    </div>

    <div class="card">
      <h3>{{ 'Manuais (PDF)' | t }}</h3>
      <ul class="manuals">
        <li>
          <a [href]="manualInstallUrl" target="_blank" rel="noopener">📘 {{ 'Manual de instalação na TV' | t }}</a>
          <span class="muted">{{ 'TCL Android TV e Fire TV Stick, passo a passo, com ilustrações' | t }}</span>
        </li>
        <li>
          <a [href]="manualPanelUrl" target="_blank" rel="noopener">📗 {{ 'Manual do painel' | t }}</a>
          <span class="muted">{{ 'Como ativar TVs, enviar mídias e montar playlists' | t }}</span>
        </li>
      </ul>
      <p class="muted">{{ 'Os manuais e o aplicativo são atualizados automaticamente a cada nova versão.' | t }}</p>
      @if (english()) {
        <p class="muted">{{ 'Os manuais estão disponíveis apenas em português por enquanto.' | t }}</p>
      }
    </div>

    @if (releases().length > 1) {
      <div class="card">
        <h3>{{ 'Versões anteriores' | t }}</h3>
        <table class="stack">
          <tr><th>{{ 'Versão' | t }}</th><th>{{ 'Data' | t }}</th><th>{{ 'Tamanho' | t }}</th><th>{{ 'Novidades' | t }}</th><th></th></tr>
          @for (release of releases(); track release.versionCode) {
            <tr>
              <td class="first"><b>{{ release.versionName }}</b></td>
              <td [attr.data-label]="'Data' | t">{{ release.publishedAt | fdate }}</td>
              <td [attr.data-label]="'Tamanho' | t">{{ size(release.size) }}</td>
              <td [attr.data-label]="'Novidades' | t">{{ release.notes || '—' }}</td>
              <td class="row-actions"><a [href]="apkUrl(release)" download>{{ 'Baixar' | t }}</a></td>
            </tr>
          }
        </table>
      </div>
    }
  `,
  styles: `
    .btn { display: inline-block; color: var(--on-primary); }
    .version { margin: 0.2em 0 0.4em; }
    .sha { font-family: ui-monospace, Consolas, monospace; font-size: 12px; word-break: break-all; color: var(--muted); }
    .manuals { list-style: none; padding: 0; margin: 0 0 10px; display: grid; gap: 10px; }
    .manuals li { display: flex; flex-direction: column; gap: 2px; }
    .manuals a { font-weight: 600; font-size: 16px; }
    code { background: var(--panel-2); border: 1px solid var(--line); padding: 1px 6px; border-radius: 6px; word-break: break-all; }
    @media (max-width: 720px) { .btn { display: block; text-align: center; padding: 12px 16px; } }
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
