import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { FileSaver } from '../core/download';
import { formatDateTimeMs, humanSeconds } from '../core/format';
import { TPipe, formatMs } from '../core/i18n';
import { Campaign, Device, Media, ProofDaily, ProofFilters, ProofReport, ProofRow, ProofVerification, ReportRange } from '../core/models';
import { IconComponent } from '../shared/icon';
import { IconName } from '../shared/icons';
import { EmptyStateComponent, PillComponent, SkeletonComponent } from '../shared/ui';

const PAGE = 100;
const KIND_ICON: Record<string, IconName> = { IMAGE: 'image', VIDEO: 'film', WEB: 'globe' };

/**
 * Comprovação de exibição: cada vez que uma mídia foi ao ar numa TV, com o início e a duração em milissegundos medidos pela
 * própria TV. A corrente de hashes (cada registro leva o hash do anterior) permite conferir que nada foi apagado ou alterado.
 */
@Component({
  selector: 'app-proof-report',
  imports: [FormsModule, TPipe, IconComponent, PillComponent, EmptyStateComponent, SkeletonComponent],
  template: `
    <div class="intro">
      <app-icon name="shield-check" [size]="20" />
      <p>
        {{ 'Cada exibição é medida pela própria TV: hora de início e duração em milissegundos. Os registros formam uma corrente de hashes (cada um leva o hash do anterior): apagar ou alterar um deles é detectável. Exige o Vyzzu 0.9 ou mais novo na TV.' | t }}
      </p>
    </div>

    <div class="filters">
      <select [attr.aria-label]="'TV' | t" [ngModel]="deviceId()" (ngModelChange)="setDevice($event)">
        <option [ngValue]="null">{{ 'Todas as TVs' | t }}</option>
        @for (device of devices(); track device.id) {
          <option [ngValue]="device.id">{{ device.name }}</option>
        }
      </select>
      <select [attr.aria-label]="'Mídia' | t" [ngModel]="mediaId()" (ngModelChange)="setMedia($event)">
        <option [ngValue]="null">{{ 'Todas as mídias' | t }}</option>
        @for (item of media(); track item.id) {
          <option [ngValue]="item.id">{{ item.name }}</option>
        }
      </select>
      <select [attr.aria-label]="'Campanha' | t" [ngModel]="campaignId()" (ngModelChange)="setCampaign($event)">
        <option [ngValue]="null">{{ 'Todas as campanhas' | t }}</option>
        @for (campaign of campaigns(); track campaign.id) {
          <option [ngValue]="campaign.id">{{ campaign.name }}</option>
        }
      </select>
      <span class="spacer"></span>
      <button type="button" class="secondary small" (click)="verify()" [disabled]="verifying()"><app-icon name="shield-check" [size]="15" />{{ (verifying() ? 'Conferindo…' : 'Verificar integridade') | t }}</button>
      <button type="button" class="secondary small" (click)="downloadCsv()" [disabled]="downloading()"><app-icon name="download" [size]="15" />{{ (downloading() ? 'Gerando…' : 'Baixar CSV') | t }}</button>
    </div>

    @if (verification(); as check) {
      <div class="verify" [class.bad]="!check.ok" role="status" data-testid="verification">
        <div class="verify-head">
          <app-pill [tone]="check.ok ? 'ok' : 'warn'" [icon]="check.ok ? 'badge-check' : 'triangle-alert'">
            {{ (check.devices.length === 0 ? 'Nada a conferir neste período' : check.ok ? 'Corrente íntegra' : 'Atenção: há registros com problema') | t }}
          </app-pill>
          @if (check.digest) {
            <span class="digest" [title]="'Guarde este resumo: com ele se prova, depois, que nenhum registro do período mudou.' | t">{{ 'Resumo do período (SHA-256)' | t }}: <code>{{ check.digest }}</code></span>
          }
        </div>
        @if (check.ok && check.devices.length > 0) {
          <p class="explain">{{ 'Nenhum registro faltando, alterado ou fora de ordem.' | t }}</p>
        }
        @if (check.devices.length > 0) {
          <table class="stack">
            <tr><th>{{ 'TV' | t }}</th><th>{{ 'Registros' | t }}</th><th>{{ 'Números de' | t }}</th><th>{{ 'Faltando' | t }}</th><th>{{ 'Elos quebrados' | t }}</th><th>{{ 'Hash alterado' | t }}</th><th>{{ 'Situação' | t }}</th></tr>
            @for (device of check.devices; track device.deviceId) {
              <tr>
                <td class="first"><b>{{ device.deviceName }}</b></td>
                <td [attr.data-label]="'Registros' | t"><span class="num">{{ device.records }}</span></td>
                <td [attr.data-label]="'Números de' | t"><span class="num">{{ device.firstSeq }} – {{ device.lastSeq }}</span></td>
                <td [attr.data-label]="'Faltando' | t"><span class="num">{{ device.missing }}</span></td>
                <td [attr.data-label]="'Elos quebrados' | t"><span class="num">{{ device.brokenLinks }}</span></td>
                <td [attr.data-label]="'Hash alterado' | t"><span class="num">{{ device.hashMismatches }}</span></td>
                <td [attr.data-label]="'Situação' | t"><app-pill [tone]="device.status === 'OK' ? 'ok' : 'warn'" [icon]="device.status === 'OK' ? 'circle-check' : 'triangle-alert'">{{ (device.status === 'OK' ? 'OK' : 'Atenção') | t }}</app-pill></td>
              </tr>
            }
          </table>
        }
      </div>
    }

    @if (loading()) {
      <app-skeleton [rows]="5" />
    } @else if (report(); as data) {
      <div class="summary">
        <div class="sum"><span class="sum-icon"><app-icon name="monitor-play" [size]="20" /></span><div><span class="label">{{ 'Exibições no período' | t }}</span><div class="big num">{{ data.total }}</div></div></div>
        <div class="sum"><span class="sum-icon"><app-icon name="timer" [size]="20" /></span><div><span class="label">{{ 'Tempo total na tela' | t }}</span><div class="big num">{{ human(data.totalDurationMs / 1000) }}</div></div></div>
        <div class="sum"><span class="sum-icon"><app-icon name="circle-check" [size]="20" /></span><div><span class="label">{{ 'Foram até o fim' | t }}</span><div class="big num">{{ data.total === 0 ? '—' : percent(data) + '%' }}</div></div></div>
      </div>

      @if (rows().length === 0) {
        <app-empty-state [compact]="true" icon="shield-check" [title]="'Nenhuma exibição registrada neste período.' | t"
          [text]="'Os registros chegam do Player 0.9 ou mais novo, a cada contato com o servidor (também os feitos quando a TV ficou sem internet).' | t" />
      } @else {
        <table class="stack proof">
          <tr><th>{{ 'Início' | t }}</th><th>{{ 'TV' | t }}</th><th>{{ 'Mídia' | t }}</th><th>{{ 'Zona e campanha' | t }}</th><th>{{ 'Duração' | t }}</th><th>{{ 'Resultado' | t }}</th><th>{{ 'Registro' | t }}</th></tr>
          @for (row of rows(); track row.id) {
            <tr [class.flagged]="!row.hashValid || !row.continuous">
              <td class="first nowrap"><span class="num">{{ when(row.startedAt) }}</span>@if (row.clockOffsetMs !== 0) {<small class="clock" [title]="'O relógio da TV estava desajustado: o servidor corrigiu o horário.' | t"><app-icon name="clock" [size]="12" />{{ 'relógio corrigido' | t }}</small>}</td>
              <td [attr.data-label]="'TV' | t">{{ row.deviceName }}</td>
              <td [attr.data-label]="'Mídia' | t"><span class="media"><app-icon [name]="kind(row.mediaType)" [size]="15" />{{ row.mediaName }}</span></td>
              <td [attr.data-label]="'Zona e campanha' | t"><span class="col">{{ row.zoneName || '—' }}@if (row.campaignName) {<small class="sub"><app-icon name="megaphone" [size]="12" />{{ row.campaignName }}</small>}</span></td>
              <td [attr.data-label]="'Duração' | t"><span class="col"><b class="num">{{ ms(row.durationMs) }}</b>@if (row.plannedMs !== null) {<small class="sub num">{{ 'prevista' | t }} {{ ms(row.plannedMs) }}</small>}</span></td>
              <td [attr.data-label]="'Resultado' | t"><app-pill [tone]="row.completed ? 'ok' : 'warn'" [icon]="row.completed ? 'circle-check' : 'circle-pause'">{{ (row.completed ? 'Completa' : 'Interrompida') | t }}</app-pill></td>
              <td [attr.data-label]="'Registro' | t">
                <span class="col">
                  <span class="num">#{{ row.seq }}</span>
                  <small class="sub mono" [title]="row.hash">{{ row.hash.slice(0, 10) }}…</small>
                  @if (!row.hashValid || !row.continuous) {
                    <app-pill tone="err" icon="triangle-alert">{{ 'Corrente com problema' | t }}</app-pill>
                  }
                </span>
              </td>
            </tr>
          }
        </table>
        @if (rows().length < data.total) {
          <div class="more"><button type="button" class="secondary" (click)="loadMore()" [disabled]="loadingMore()"><app-icon name="chevron-down" [size]="16" />{{ (loadingMore() ? 'Carregando…' : 'Carregar mais') | t }}</button><small class="sub">{{ '{shown} de {total}' | t: { shown: rows().length, total: data.total } }}</small></div>
        }
      }

      @if (daily(); as days) {
        @if (days.length > 0) {
          <h3 class="sec"><app-icon name="calendar" [size]="18" />{{ 'Resumo por dia (fica guardado por um ano)' | t }}</h3>
          <table class="stack">
            <tr><th>{{ 'Dia' | t }}</th><th>{{ 'TV' | t }}</th><th>{{ 'Exibições' | t }}</th><th>{{ 'Tempo na tela' | t }}</th><th>{{ 'Números de' | t }}</th><th>{{ 'Último hash' | t }}</th></tr>
            @for (day of days; track day.deviceId + day.day) {
              <tr>
                <td class="first"><span class="num">{{ day.day }}</span></td>
                <td [attr.data-label]="'TV' | t">{{ day.deviceName }}</td>
                <td [attr.data-label]="'Exibições' | t"><span class="num">{{ day.plays }}</span></td>
                <td [attr.data-label]="'Tempo na tela' | t"><span class="num">{{ human(day.totalMs / 1000) }}</span></td>
                <td [attr.data-label]="'Números de' | t"><span class="num">{{ day.firstSeq }} – {{ day.lastSeq }}</span></td>
                <td [attr.data-label]="'Último hash' | t"><small class="mono" [title]="day.lastHash">{{ day.lastHash.slice(0, 16) }}…</small></td>
              </tr>
            }
          </table>
        }
      }
    }
  `,
  styles: `
    .intro { display: flex; gap: 10px; align-items: flex-start; margin-bottom: 14px; padding: 12px 14px; border-radius: var(--r); background: var(--info-bg); border: 1px solid var(--info-line); color: var(--info-fg); }
    .intro app-icon { flex: none; margin-top: 1px; }
    .intro p { margin: 0; color: var(--text); }
    .filters { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; margin-bottom: 14px; }
    .filters select { width: auto; min-width: 150px; max-width: 180px; }
    .spacer { flex: 1; }
    .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; margin-bottom: 18px; }
    .sum { display: flex; align-items: center; gap: 14px; padding: 14px 16px; background: var(--panel-2); border: 1px solid var(--line); border-radius: var(--r-lg); }
    .sum-icon { flex: none; display: grid; place-items: center; width: 44px; height: 44px; border-radius: 13px; color: var(--primary); background: var(--primary-soft); border: 1px solid var(--primary-line); }
    :host-context(:root[data-theme='dark']) .sum-icon { color: var(--link); }
    .big { font-size: 26px; font-weight: 700; margin-top: 2px; letter-spacing: -0.02em; line-height: 1.15; }
    .verify { margin-bottom: 16px; padding: 14px; border-radius: var(--r-lg); background: var(--ok-bg); border: 1px solid var(--ok-line); }
    .verify.bad { background: var(--warn-bg); border-color: var(--warn-line); }
    .verify-head { display: flex; flex-wrap: wrap; align-items: center; gap: 10px 16px; margin-bottom: 6px; }
    .explain { margin: 0 0 8px; color: var(--text-2); }
    .digest { color: var(--text-2); font-size: 13px; word-break: break-all; }
    .digest code { font-size: 12px; background: var(--panel); border: 1px solid var(--line); padding: 1px 6px; border-radius: 6px; }
    .verify table { margin-top: 8px; background: var(--panel); border-radius: var(--r); }
    .nowrap { white-space: nowrap; }
    .col { display: flex; flex-direction: column; align-items: flex-start; gap: 3px; }
    .sub { display: inline-flex; align-items: center; gap: 5px; color: var(--muted); }
    .media { display: inline-flex; align-items: center; gap: 7px; }
    .media app-icon { color: var(--muted); flex: none; }
    .mono { font-family: ui-monospace, 'Cascadia Mono', Consolas, monospace; font-size: 12px; }
    .clock { display: flex; align-items: center; gap: 4px; margin-top: 2px; color: var(--warn-fg); }
    tr.flagged td { background: var(--err-bg); }
    .more { display: flex; align-items: center; gap: 12px; justify-content: center; padding-top: 14px; }
    .sec { display: flex; align-items: center; gap: 8px; margin: 22px 0 8px; }
    .sec app-icon { color: var(--primary); }
    :host-context(:root[data-theme='dark']) .sec app-icon { color: var(--link); }
    @media (max-width: 720px) {
      .filters select, .filters button { width: 100%; min-width: 0; max-width: none; }
      .spacer { display: none; }
    }
  `,
})
export class ProofReportComponent {
  private readonly api = inject(ApiService);
  private readonly files = inject(FileSaver);

  readonly from = input.required<string>();
  readonly to = input.required<string>();
  readonly devices = input<Device[]>([]);

  readonly media = signal<Media[]>([]);
  readonly campaigns = signal<Campaign[]>([]);
  readonly deviceId = signal<number | null>(null);
  readonly mediaId = signal<number | null>(null);
  readonly campaignId = signal<number | null>(null);

  readonly report = signal<ProofReport | null>(null);
  readonly rows = signal<ProofRow[]>([]);
  readonly daily = signal<ProofDaily[] | null>(null);
  readonly verification = signal<ProofVerification | null>(null);
  readonly loading = signal(false);
  readonly loadingMore = signal(false);
  readonly verifying = signal(false);
  readonly downloading = signal(false);

  readonly when = formatDateTimeMs;
  readonly ms = formatMs;
  readonly human = humanSeconds;
  readonly kind = (type: string): IconName => KIND_ICON[type] ?? 'image';
  readonly percent = (report: ProofReport) => Math.round((report.completed * 100) / Math.max(1, report.total));

  constructor() {
    this.api.mediaList().subscribe({ next: (list) => this.media.set(list), error: () => undefined });
    this.api.campaigns().subscribe({ next: (list) => this.campaigns.set(list), error: () => undefined });
    // Mudou o período (ou a tela abriu): recarrega.
    effect(() => {
      this.from();
      this.to();
      untracked(() => this.reload());
    });
  }

  setDevice(id: number | null): void {
    this.deviceId.set(id);
    this.reload();
  }

  setMedia(id: number | null): void {
    this.mediaId.set(id);
    this.reload();
  }

  setCampaign(id: number | null): void {
    this.campaignId.set(id);
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.verification.set(null);
    const range = this.range();
    this.api.proofReport(range, this.filters(), 0, PAGE).subscribe({
      next: (report) => {
        this.report.set(report);
        this.rows.set(report.rows);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    this.api.proofDaily(range, this.deviceId()).subscribe({ next: (days) => this.daily.set(days), error: () => this.daily.set(null) });
  }

  loadMore(): void {
    this.loadingMore.set(true);
    this.api.proofReport(this.range(), this.filters(), this.rows().length, PAGE).subscribe({
      next: (report) => {
        this.rows.update((current) => [...current, ...report.rows]);
        this.loadingMore.set(false);
      },
      error: () => this.loadingMore.set(false),
    });
  }

  verify(): void {
    this.verifying.set(true);
    this.api.proofVerify(this.range(), this.deviceId()).subscribe({
      next: (result) => {
        this.verification.set(result);
        this.verifying.set(false);
      },
      error: () => this.verifying.set(false),
    });
  }

  downloadCsv(): void {
    this.downloading.set(true);
    const range = this.range();
    this.api.reportCsv('proof', { from: range.from, to: range.to, ...this.filters() }).subscribe({
      next: (blob) => {
        this.downloading.set(false);
        this.files.save(blob, `comprovacao-${range.from}-a-${range.to}.csv`);
      },
      error: () => this.downloading.set(false),
    });
  }

  private range(): ReportRange {
    return { from: this.from() || undefined, to: this.to() || undefined };
  }

  private filters(): ProofFilters {
    return { deviceId: this.deviceId(), mediaId: this.mediaId(), campaignId: this.campaignId() };
  }
}
