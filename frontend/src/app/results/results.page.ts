import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { FileSaver } from '../core/download';
import { daysAgo } from '../core/format';
import { FDatePipe, TPipe } from '../core/i18n';
import { Promotion, ResultsDay, ResultsResponse } from '../core/models';
import { IconComponent } from '../shared/icon';
import { EmptyStateComponent, PageHeaderComponent, SkeletonComponent } from '../shared/ui';
import { activeDays, barHeights, formatCount, formatRate, headline, originLabel } from './results';

type Period = '7' | '30' | '90' | 'custom';

/**
 * "Resultados": o painel simples para o cliente. Em uma frase, quantas vezes o conteúdo rodou e quantos cupons foram usados; depois
 * por dia, por TV, por QR Code e por promoção, com o CSV para o Excel. Só números: nenhum dado de pessoa.
 */
@Component({
  selector: 'app-results',
  imports: [FormsModule, RouterLink, TPipe, FDatePipe, IconComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent],
  template: `
    <app-page-header
      [title]="'Resultados' | t"
      [subtitle]="'O que o seu conteúdo e os QR Codes renderam: quantas vezes rodou, quantas pessoas escanearam e quantos cupons foram usados.' | t"
      icon="trending-up"></app-page-header>

    <div class="card filters">
      <div class="field">
        <label for="res-period">{{ 'Período' | t }}</label>
        <select id="res-period" name="period" [ngModel]="period()" (ngModelChange)="setPeriod($event)">
          <option value="7">{{ 'Últimos 7 dias' | t }}</option>
          <option value="30">{{ 'Últimos 30 dias' | t }}</option>
          <option value="90">{{ 'Últimos 90 dias' | t }}</option>
          <option value="custom">{{ 'Escolher as datas' | t }}</option>
        </select>
      </div>
      @if (period() === 'custom') {
        <div class="field">
          <label for="res-from">{{ 'De' | t }}</label>
          <input id="res-from" type="date" [ngModel]="from()" (ngModelChange)="changeDates($event, to())" />
        </div>
        <div class="field">
          <label for="res-to">{{ 'Até' | t }}</label>
          <input id="res-to" type="date" [ngModel]="to()" (ngModelChange)="changeDates(from(), $event)" />
        </div>
      }
      @if (couponsEnabled() && promotions().length > 0) {
        <div class="field">
          <label for="res-promo">{{ 'Promoção' | t }}</label>
          <select id="res-promo" name="promotion" [ngModel]="promotionId()" (ngModelChange)="setPromotion($event)">
            <option [ngValue]="null">{{ 'Todas as promoções' | t }}</option>
            @for (promotion of promotions(); track promotion.id) {
              <option [ngValue]="promotion.id">{{ promotion.name }}</option>
            }
          </select>
        </div>
      }
      <div class="downloads">
        <span class="muted">{{ 'Baixar CSV' | t }}:</span>
        <button type="button" class="secondary small" (click)="download('day')" [disabled]="downloading()"><app-icon name="download" [size]="15" />{{ 'por dia' | t }}</button>
        <button type="button" class="secondary small" (click)="download('tv')" [disabled]="downloading()"><app-icon name="download" [size]="15" />{{ 'por TV' | t }}</button>
        @if (couponsEnabled()) {
          <button type="button" class="secondary small" (click)="download('promotion')" [disabled]="downloading()"><app-icon name="download" [size]="15" />{{ 'por promoção' | t }}</button>
        }
      </div>
    </div>

    @if (loading()) {
      <div class="card"><app-skeleton [rows]="4" /></div>
    } @else if (data(); as response) {
      <div class="card hero" data-testid="headline">
        @for (line of lines(); track line) {
          <p class="line">{{ line }}</p>
        }
        <p class="muted">{{ 'De {from} a {to}.' | t: { from: (response.report.from | fdate), to: (response.report.to | fdate) } }}</p>
      </div>

      <div class="kpis">
        <div class="kpi"><small>{{ 'Exibições do conteúdo' | t }}</small><b data-testid="kpi-plays">{{ count(response.report.plays) }}</b><span>{{ 'em {n} TV(s)' | t: { n: response.report.devicesWithPlays } }}</span></div>
        @if (couponsEnabled()) {
          <div class="kpi"><small>{{ 'QR Codes escaneados' | t }}</small><b data-testid="kpi-scans">{{ count(response.report.scans) }}</b><span>{{ 'aberturas da página da promoção' | t }}</span></div>
          <div class="kpi"><small>{{ 'Cupons pegos' | t }}</small><b data-testid="kpi-claimed">{{ count(response.report.claimed) }}</b></div>
          <div class="kpi"><small>{{ 'Cupons usados' | t }}</small><b data-testid="kpi-redeemed">{{ count(response.report.redeemed) }}</b><span>{{ 'Taxa de uso: {rate}' | t: { rate: rate(response.report.claimed, response.report.redeemed) } }}</span></div>
        }
      </div>

      @if (empty()) {
        <div class="card">
          <app-empty-state icon="trending-up" [title]="'Sem movimento neste período' | t"
            [text]="'Quando as TVs exibirem o conteúdo e as pessoas escanearem os QR Codes, os números aparecem aqui.' | t">
            @if (couponsEnabled()) {
              <a class="secondary btn" routerLink="/cupons"><app-icon name="ticket" [size]="16" />{{ 'Ver os cupons' | t }}</a>
            }
          </app-empty-state>
        </div>
      } @else {
        <div class="card">
          <h3 class="sec"><app-icon name="chart-column" [size]="18" />{{ 'Por dia' | t }}</h3>
          <div class="bars" aria-hidden="true">
            @for (day of response.report.days; track day.day; let i = $index) {
              <span class="bar" [style.height.%]="playBars()[i]" [title]="(day.day | fdate) + ': ' + count(day.plays)"></span>
            }
          </div>
          <p class="muted cap">{{ 'Exibições do conteúdo por dia' | t }}</p>
          <div class="scroll">
            <table class="stack">
              <tr>
                <th>{{ 'Dia' | t }}</th><th class="num">{{ 'Exibições' | t }}</th>
                @if (couponsEnabled()) {
                  <th class="num">{{ 'QR Codes escaneados' | t }}</th><th class="num">{{ 'Cupons pegos' | t }}</th><th class="num">{{ 'Cupons usados' | t }}</th>
                }
              </tr>
              @for (day of daysWithActivity(); track day.day) {
                <tr data-testid="day-row">
                  <td class="first">{{ day.day | fdate }}</td><td class="num">{{ count(day.plays) }}</td>
                  @if (couponsEnabled()) {
                    <td class="num">{{ count(day.scans) }}</td><td class="num">{{ count(day.claimed) }}</td><td class="num">{{ count(day.redeemed) }}</td>
                  }
                </tr>
              }
            </table>
          </div>
        </div>

        @if (response.report.tvs.length > 0) {
          <div class="card">
            <h3 class="sec"><app-icon name="monitor" [size]="18" />{{ 'Por TV' | t }}</h3>
            <div class="scroll">
              <table class="stack">
                <tr>
                  <th>{{ 'TV' | t }}</th><th class="num">{{ 'Exibições' | t }}</th>
                  @if (couponsEnabled()) {
                    <th class="num">{{ 'QR Codes escaneados' | t }}</th><th class="num">{{ 'Cupons pegos' | t }}</th><th class="num">{{ 'Cupons usados' | t }}</th>
                  }
                </tr>
                @for (tv of response.report.tvs; track tv.deviceId) {
                  <tr data-testid="tv-row">
                    <td class="first">{{ tv.name ?? ('TV excluída' | t) }}</td><td class="num">{{ count(tv.plays) }}</td>
                    @if (couponsEnabled()) {
                      <td class="num">{{ count(tv.scans) }}</td><td class="num">{{ count(tv.claimed) }}</td><td class="num">{{ count(tv.redeemed) }}</td>
                    }
                  </tr>
                }
              </table>
            </div>
            @if (couponsEnabled()) {
              <p class="muted">{{ 'Para saber quanto cada TV rende, use um QR Code por TV (em Cupons, abra a promoção e escolha de onde é o QR Code).' | t }}</p>
            }
          </div>
        }

        @if (couponsEnabled() && response.report.origins.length > 0) {
          <div class="card">
            <h3 class="sec"><app-icon name="qr-code" [size]="18" />{{ 'Outros QR Codes' | t }}</h3>
            <div class="scroll">
              <table class="stack">
                <tr><th>{{ 'QR Code' | t }}</th><th class="num">{{ 'Escaneados' | t }}</th><th class="num">{{ 'Cupons pegos' | t }}</th><th class="num">{{ 'Cupons usados' | t }}</th></tr>
                @for (origin of response.report.origins; track origin.origin) {
                  <tr data-testid="origin-row">
                    <td class="first">{{ label(origin) }}</td><td class="num">{{ count(origin.scans) }}</td><td class="num">{{ count(origin.claimed) }}</td><td class="num">{{ count(origin.redeemed) }}</td>
                  </tr>
                }
              </table>
            </div>
          </div>
        }

        @if (couponsEnabled() && response.report.promotions.length > 0) {
          <div class="card">
            <h3 class="sec"><app-icon name="ticket" [size]="18" />{{ 'Por promoção' | t }}</h3>
            <div class="scroll">
              <table class="stack">
                <tr><th>{{ 'Promoção' | t }}</th><th class="num">{{ 'QR Codes escaneados' | t }}</th><th class="num">{{ 'Cupons pegos' | t }}</th><th class="num">{{ 'Cupons usados' | t }}</th><th class="num">{{ 'Taxa de uso' | t }}</th></tr>
                @for (promotion of response.report.promotions; track promotion.id) {
                  <tr data-testid="promotion-row">
                    <td class="first">{{ promotion.name }}</td><td class="num">{{ count(promotion.scans) }}</td><td class="num">{{ count(promotion.claimed) }}</td><td class="num">{{ count(promotion.redeemed) }}</td>
                    <td class="num">{{ rate(promotion.claimed, promotion.redeemed) }}</td>
                  </tr>
                }
              </table>
            </div>
          </div>
        }

        @if (response.report.topMedia.length > 0) {
          <div class="card">
            <h3 class="sec"><app-icon name="film" [size]="18" />{{ 'O que mais rodou' | t }}</h3>
            <ol class="top">
              @for (media of response.report.topMedia; track media.name) {
                <li><span>{{ media.name }}</span><b>{{ count(media.plays) }}</b></li>
              }
            </ol>
          </div>
        }
      }
    }
  `,
  styles: `
    .filters { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 0 16px; margin-bottom: 16px; }
    .filters .field { min-width: 170px; margin-bottom: 8px; }
    .downloads { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-left: auto; padding-bottom: 12px; }
    .btn { display: inline-flex; align-items: center; gap: 7px; text-decoration: none; }
    .hero { margin-bottom: 16px; }
    .line { margin: 0 0 6px; font-size: 20px; font-weight: 700; line-height: 1.3; }
    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 14px; margin-bottom: 16px; }
    .kpi { display: flex; flex-direction: column; gap: 2px; padding: 16px 18px; border-radius: var(--r); border: 1px solid var(--line); background: var(--panel); }
    .kpi small { color: var(--muted); font-weight: 600; }
    .kpi b { font-size: 30px; line-height: 1.1; font-variant-numeric: tabular-nums; }
    .kpi span { color: var(--muted); font-size: 13px; }
    .sec { display: flex; align-items: center; gap: 8px; }
    .sec app-icon { color: var(--primary); }
    :host-context(:root[data-theme='dark']) .sec app-icon { color: var(--link); }
    .card { margin-bottom: 16px; }
    .bars { display: flex; align-items: flex-end; gap: 2px; height: 120px; padding: 6px 0; border-bottom: 1px solid var(--line); }
    .bar { flex: 1 1 0; min-width: 2px; background: var(--primary); border-radius: 3px 3px 0 0; opacity: 0.85; }
    .cap { margin: 6px 0 14px; }
    .scroll { overflow-x: auto; }
    th.num, td.num { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
    .top { list-style: none; margin: 0; padding: 0; display: grid; gap: 6px; }
    .top li { display: flex; justify-content: space-between; gap: 12px; padding: 8px 0; border-bottom: 1px solid var(--line); }
    .top li span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    @media (max-width: 720px) {
      .downloads { margin-left: 0; width: 100%; }
      .line { font-size: 17px; }
    }
  `,
})
export class ResultsPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly files = inject(FileSaver);

  readonly period = signal<Period>('30');
  readonly from = signal(daysAgo(29));
  readonly to = signal(daysAgo(0));
  readonly promotionId = signal<number | null>(null);
  readonly promotions = signal<Promotion[]>([]);
  readonly data = signal<ResultsResponse | null>(null);
  readonly loading = signal(true);
  readonly downloading = signal(false);

  readonly couponsEnabled = computed(() => this.data()?.couponsEnabled ?? true);
  readonly count = formatCount;
  readonly rate = formatRate;
  readonly label = originLabel;

  readonly lines = computed(() => {
    const report = this.data()?.report;
    return report
      ? headline(report.plays, report.devicesWithPlays, report.scans, report.claimed, report.redeemed, this.couponsEnabled())
      : [];
  });
  readonly daysWithActivity = computed<ResultsDay[]>(() => activeDays(this.data()?.report.days ?? []));
  readonly playBars = computed(() => barHeights((this.data()?.report.days ?? []).map((day) => day.plays)));
  /** Nada aconteceu no período (nem exibição, nem QR Code, nem cupom). */
  readonly empty = computed(() => {
    const report = this.data()?.report;
    return !report || report.plays + report.scans + report.claimed + report.redeemed === 0;
  });

  ngOnInit(): void {
    this.api.promotions().subscribe({ next: (list) => this.promotions.set(list), error: () => undefined });
    this.load();
  }

  setPeriod(period: Period): void {
    this.period.set(period);
    if (period !== 'custom') {
      this.from.set(daysAgo(Number(period) - 1));
      this.to.set(daysAgo(0));
      this.load();
    }
  }

  changeDates(from: string, to: string): void {
    this.from.set(from);
    this.to.set(to);
    if (from && to && from <= to) {
      this.load();
    }
  }

  setPromotion(id: number | null): void {
    this.promotionId.set(id);
    this.load();
  }

  download(by: 'day' | 'tv' | 'promotion'): void {
    this.downloading.set(true);
    const range = { from: this.from(), to: this.to() };
    this.api.resultsCsv(range, by, this.promotionId()).subscribe({
      next: (blob) => {
        this.downloading.set(false);
        const names = { day: 'resultados-por-dia', tv: 'resultados-por-tv', promotion: 'resultados-por-promocao' };
        this.files.save(blob, `${names[by]}-${range.from}-a-${range.to}.csv`);
      },
      error: () => this.downloading.set(false),
    });
  }

  private load(): void {
    this.loading.set(true);
    this.api.results({ from: this.from(), to: this.to() }, this.promotionId()).subscribe({
      next: (response) => {
        this.data.set(response);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
