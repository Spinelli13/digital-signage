import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';

import { ApiService } from '../core/api.service';
import { ConfigService } from '../core/config.service';
import { formatBytes } from '../core/format';
import { ReleaseInfo } from '../core/models';

@Component({
  selector: 'app-downloads',
  imports: [DatePipe],
  template: `
    <div class="head">
      <div>
        <h1>Downloads e manuais</h1>
        <p>O aplicativo da TV e os manuais, sempre na versão mais recente.</p>
      </div>
    </div>

    <div class="card latest">
      <div class="label">Aplicativo para TV — versão mais recente</div>
      @if (latest(); as release) {
        <h2 class="version">
          {{ release.versionName }}
          <span class="muted">· {{ release.publishedAt | date: 'dd/MM/yyyy' }} · {{ size(release.size) }}</span>
        </h2>
        @if (release.notes) {
          <p>{{ release.notes }}</p>
        }
        <p>
          <a class="primary btn" [href]="apkUrl(release)" download>Baixar APK {{ release.versionName }}</a>
        </p>
        <p class="muted">
          Para instalar com o app <b>Downloader</b> (TCL e Fire TV Stick), digite na TV: <code>{{ shortLink }}</code>
        </p>
        <p class="sha">SHA-256: {{ release.sha256 }}</p>
      } @else if (loaded()) {
        <p class="muted">Não foi possível carregar a versão mais recente agora.</p>
      } @else {
        <p class="muted">Carregando…</p>
      }
    </div>

    <div class="card">
      <h3>Manuais (PDF)</h3>
      <ul class="manuals">
        <li>
          <a [href]="manualInstallUrl" target="_blank" rel="noopener">📘 Manual de instalação na TV</a>
          <span class="muted">TCL Android TV e Fire TV Stick, passo a passo, com ilustrações</span>
        </li>
        <li>
          <a [href]="manualPanelUrl" target="_blank" rel="noopener">📗 Manual do painel</a>
          <span class="muted">Como ativar TVs, enviar mídias e montar playlists</span>
        </li>
      </ul>
      <p class="muted">Os manuais e o aplicativo são atualizados automaticamente a cada nova versão.</p>
    </div>

    @if (releases().length > 1) {
      <div class="card">
        <h3>Versões anteriores</h3>
        <table>
          <tr><th>Versão</th><th>Data</th><th>Tamanho</th><th>Novidades</th><th></th></tr>
          @for (release of releases(); track release.versionCode) {
            <tr>
              <td><b>{{ release.versionName }}</b></td>
              <td>{{ release.publishedAt | date: 'dd/MM/yyyy' }}</td>
              <td>{{ size(release.size) }}</td>
              <td>{{ release.notes || '—' }}</td>
              <td><a [href]="apkUrl(release)" download>Baixar</a></td>
            </tr>
          }
        </table>
      </div>
    }
  `,
  styles: `
    .btn { display: inline-block; color: #fff; }
    .version { margin: 0.2em 0 0.4em; }
    .sha { font-family: ui-monospace, Consolas, monospace; font-size: 12px; word-break: break-all; color: var(--muted); }
    .manuals { list-style: none; padding: 0; margin: 0 0 10px; display: grid; gap: 10px; }
    .manuals li { display: flex; flex-direction: column; gap: 2px; }
    .manuals a { font-weight: 600; font-size: 16px; }
    code { background: #eef1f7; padding: 1px 6px; border-radius: 6px; }
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
