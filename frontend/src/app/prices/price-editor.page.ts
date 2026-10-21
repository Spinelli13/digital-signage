import { Component, ElementRef, OnInit, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { TIMEZONES } from '../campaigns/campaign-form';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { FileSaver } from '../core/download';
import { TPipe, currentLang, tr } from '../core/i18n';
import { Notice } from '../core/notice';
import { PriceBoardData, PriceList } from '../core/models';
import { IconComponent } from '../shared/icon';
import { PageHeaderComponent, PillComponent } from '../shared/ui';
import { WidgetPreviewComponent } from '../media/widget-preview';
import { CURRENCIES, ItemDraft, PriceForm, RuleDraft, WEEKDAYS, csvName, emptyForm, emptyItem, emptyRule, formFromList, problems, toRequest } from './price-form';

const PREVIEW_DELAY_MS = 600;

/**
 * Editar uma tabela de preços: itens (à mão ou por planilha), variações por horário e, ao lado, o painel de preços como a TV o
 * desenha, podendo simular qualquer horário (ex.: "como fica no happy hour?"). O servidor calcula os preços da pré-visualização
 * com as mesmas regras que usa para a TV.
 */
@Component({
  selector: 'app-price-editor',
  imports: [FormsModule, RouterLink, TPipe, IconComponent, PillComponent, PageHeaderComponent, WidgetPreviewComponent],
  template: `
    <p class="back"><a routerLink="/precos"><app-icon name="arrow-left" [size]="15" />{{ 'Preços' | t }}</a></p>
    <app-page-header [title]="form().name || ('Nova tabela de preços' | t)" [subtitle]="'Os preços valem para todas as TVs que mostram esta tabela. Variações por horário trocam o preço sozinhas, na hora certa, até sem internet na TV.' | t" icon="badge-dollar-sign">
      @if (auth.can('content')) {
        <div class="head-actions">
          @if (dirty()) {
            <app-pill tone="warn" icon="circle-alert">{{ 'Alterações não salvas' | t }}</app-pill>
          }
          <input #csv type="file" accept=".csv,.txt,text/csv" hidden (change)="onFile($event)" />
          <button class="primary" (click)="save()" [disabled]="busy() || problemList().length > 0"><app-icon name="check" />{{ 'Salvar' | t }}</button>
        </div>
      }
    </app-page-header>

    @if (notice.text(); as text) {
      <div class="notice" role="status"><app-icon name="circle-check" [size]="18" /><span>{{ text }}</span></div>
    }
    @if (skipped().length > 0) {
      <div class="warnbox" role="status">
        <app-icon name="triangle-alert" [size]="18" />
        <div class="warn-list">
          <p><b>{{ 'Algumas linhas da planilha foram ignoradas:' | t }}</b></p>
          @for (line of skipped(); track line) {
            <p>{{ line }}</p>
          }
        </div>
        <button type="button" class="secondary small" (click)="skipped.set([])">{{ 'Entendi' | t }}</button>
      </div>
    }
    @if (problemList().length > 0 && touched()) {
      <div class="warnbox" role="alert">
        <app-icon name="circle-alert" [size]="18" />
        <div class="warn-list">
          @for (text of problemList(); track text) {
            <p>{{ text }}</p>
          }
        </div>
      </div>
    }

    <div class="layout">
      <div class="left">
        <div class="card">
          <div class="three">
            <div class="field">
              <label for="p-name">{{ 'Nome da tabela' | t }}</label>
              <input id="p-name" [placeholder]="'Ex.: Cardápio do bar' | t" [ngModel]="form().name" (ngModelChange)="setField('name', $event)" maxlength="80" [disabled]="!auth.can('content')" />
            </div>
            <div class="field">
              <label for="p-cur">{{ 'Moeda' | t }}</label>
              <select id="p-cur" [ngModel]="form().currency" (ngModelChange)="setField('currency', $event)" [disabled]="!auth.can('content')">
                @for (code of currencies; track code) {
                  <option [value]="code">{{ code }}</option>
                }
              </select>
            </div>
            <div class="field">
              <label for="p-tz">{{ 'Fuso horário dos horários' | t }}</label>
              <select id="p-tz" [ngModel]="form().timezone" (ngModelChange)="setField('timezone', $event)" [disabled]="!auth.can('content')">
                @for (zone of zones; track zone.value) {
                  <option [value]="zone.value">{{ zone.label | t }}</option>
                }
              </select>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="items-head">
            <h3 class="sec"><app-icon name="list" [size]="18" />{{ 'Itens' | t }}</h3>
            @if (auth.can('content')) {
              <div class="sheet" role="group" [attr.aria-label]="'Planilha' | t">
                <button type="button" class="secondary small" [disabled]="!id()" [title]="id() ? '' : ('Salve a tabela primeiro para importar' | t)" (click)="chooseImport('replace')"><app-icon name="upload" [size]="15" />{{ 'Importar planilha' | t }}</button>
                <button type="button" class="secondary small" [disabled]="!id()" (click)="chooseImport('append')"><app-icon name="plus" [size]="15" />{{ 'Acrescentar da planilha' | t }}</button>
                <button type="button" class="secondary small" [disabled]="!id()" (click)="exportCsv()"><app-icon name="download" [size]="15" />{{ 'Baixar planilha' | t }}</button>
              </div>
            }
          </div>
          <datalist id="categories">
            @for (category of categories(); track category) {
              <option [value]="category"></option>
            }
          </datalist>
          <div class="items">
            <div class="irow head" aria-hidden="true">
              <span>{{ 'Categoria' | t }}</span><span>{{ 'Item' | t }}</span><span>{{ 'Preço' | t }}</span><span>{{ 'Unidade' | t }}</span><span class="c">{{ 'Disponível' | t }}</span><span class="c">{{ 'Destaque' | t }}</span><span></span>
            </div>
            @for (item of form().items; track item.id; let i = $index) {
              <div class="irow">
                <input class="cat" list="categories" [placeholder]="'Categoria' | t" [attr.aria-label]="'Categoria' | t" [ngModel]="item.category" (ngModelChange)="setItem(i, { category: $event })" maxlength="40" [disabled]="!auth.can('content')" />
                <div class="namecell">
                  <input [placeholder]="'Nome do item' | t" [attr.aria-label]="'Item' | t" [ngModel]="item.name" (ngModelChange)="setItem(i, { name: $event })" maxlength="80" [disabled]="!auth.can('content')" />
                  <input class="desc" [placeholder]="'Descrição (opcional)' | t" [attr.aria-label]="'Descrição' | t" [ngModel]="item.description" (ngModelChange)="setItem(i, { description: $event })" maxlength="160" [disabled]="!auth.can('content')" />
                </div>
                <input class="num" type="number" min="0" step="0.01" inputmode="decimal" placeholder="0,00" [attr.aria-label]="'Preço' | t" [ngModel]="item.price" (ngModelChange)="setItem(i, { price: $event === '' || $event === null ? null : +$event })" [disabled]="!auth.can('content')" />
                <input class="unit" [placeholder]="'un, kg, porção…' | t" [attr.aria-label]="'Unidade' | t" [ngModel]="item.unit" (ngModelChange)="setItem(i, { unit: $event })" maxlength="20" [disabled]="!auth.can('content')" />
                <label class="c tick"><input type="checkbox" [attr.aria-label]="'Disponível' | t" [ngModel]="item.available" (ngModelChange)="setItem(i, { available: $event })" [disabled]="!auth.can('content')" /><span class="lbl">{{ 'Disponível' | t }}</span></label>
                <label class="c tick"><input type="checkbox" [attr.aria-label]="'Destaque' | t" [ngModel]="item.highlight" (ngModelChange)="setItem(i, { highlight: $event })" [disabled]="!auth.can('content')" /><span class="lbl">{{ 'Destaque' | t }}</span></label>
                @if (auth.can('content')) {
                  <div class="acts">
                    <button type="button" class="icon-btn" [attr.aria-label]="'Subir' | t" (click)="moveItem(i, -1)" [disabled]="i === 0"><app-icon name="arrow-up" [size]="16" /></button>
                    <button type="button" class="icon-btn" [attr.aria-label]="'Descer' | t" (click)="moveItem(i, 1)" [disabled]="i === form().items.length - 1"><app-icon name="arrow-down" [size]="16" /></button>
                    <button type="button" class="icon-btn danger" [attr.aria-label]="'Excluir item' | t" (click)="removeItem(i)"><app-icon name="trash" [size]="16" /></button>
                  </div>
                }
              </div>
            }
          </div>
          @if (auth.can('content')) {
            <button type="button" class="secondary add" (click)="addItem()"><app-icon name="plus" />{{ 'Adicionar item' | t }}</button>
          }
        </div>

        <div class="card">
          <h3 class="sec"><app-icon name="clock" [size]="18" />{{ 'Preços por horário' | t }}</h3>
          <p class="label">{{ 'Crie variações como "Happy hour" (todo dia, das 17h às 20h, 20% de desconto nas bebidas) ou "Almoço executivo" (preço fixo no prato do dia). Quando duas variações valem ao mesmo tempo para o mesmo item, vale a primeira da lista.' | t }}</p>
          @for (rule of form().rules; track rule.key; let r = $index) {
            <div class="rule">
              <div class="rule-head">
                <input class="rname" [placeholder]="'Nome (ex.: Happy hour)' | t" [attr.aria-label]="'Nome da variação' | t" [ngModel]="rule.name" (ngModelChange)="setRule(r, { name: $event })" maxlength="60" [disabled]="!auth.can('content')" />
                @if (auth.can('content')) {
                  <div class="acts">
                    <button type="button" class="icon-btn" [attr.aria-label]="'Subir' | t" (click)="moveRule(r, -1)" [disabled]="r === 0"><app-icon name="arrow-up" [size]="16" /></button>
                    <button type="button" class="icon-btn" [attr.aria-label]="'Descer' | t" (click)="moveRule(r, 1)" [disabled]="r === form().rules.length - 1"><app-icon name="arrow-down" [size]="16" /></button>
                    <button type="button" class="icon-btn danger" [attr.aria-label]="'Excluir variação' | t" (click)="removeRule(r)"><app-icon name="trash" [size]="16" /></button>
                  </div>
                }
              </div>
              <div class="days" role="group" [attr.aria-label]="'Dias da semana' | t">
                @for (day of weekdays; track day.value) {
                  <label class="chip" [class.on]="rule.days.includes(day.value)">
                    <input type="checkbox" [ngModel]="rule.days.includes(day.value)" (ngModelChange)="toggleDay(r, day.value)" [disabled]="!auth.can('content')" />{{ day.label | t }}
                  </label>
                }
                <small class="muted">{{ rule.days.length === 0 ? ('todos os dias' | t) : '' }}</small>
              </div>
              <div class="times">
                <label>{{ 'Das' | t }} <input type="time" [ngModel]="rule.start" (ngModelChange)="setRule(r, { start: $event })" [disabled]="!auth.can('content')" /></label>
                <label>{{ 'às' | t }} <input type="time" [ngModel]="rule.end" (ngModelChange)="setRule(r, { end: $event })" [disabled]="!auth.can('content')" /></label>
                <small class="muted">{{ 'Em branco = o dia todo. Um fim antes do início passa da meia-noite.' | t }}</small>
              </div>
              <div class="mode" role="group" [attr.aria-label]="'O que muda' | t">
                <label class="check"><input type="radio" [name]="'mode-' + rule.key" value="percent" [ngModel]="rule.mode" (ngModelChange)="setRule(r, { mode: $event })" [disabled]="!auth.can('content')" /> {{ 'Percentual' | t }}</label>
                <label class="check"><input type="radio" [name]="'mode-' + rule.key" value="fixed" [ngModel]="rule.mode" (ngModelChange)="setRule(r, { mode: $event })" [disabled]="!auth.can('content')" /> {{ 'Preços fixos por item' | t }}</label>
              </div>
              @if (rule.mode === 'percent') {
                <div class="pct">
                  <label>{{ 'Variação (%)' | t }} <input class="num" type="number" step="1" min="-100" max="1000" [ngModel]="rule.percent" (ngModelChange)="setRule(r, { percent: $event === '' || $event === null ? null : +$event })" [disabled]="!auth.can('content')" /></label>
                  <small class="muted">{{ 'Negativo é desconto (-20), positivo é acréscimo (10).' | t }}</small>
                </div>
                <div class="mode">
                  <label class="check"><input type="radio" [name]="'scope-' + rule.key" value="all" [ngModel]="rule.scope" (ngModelChange)="setRule(r, { scope: $event })" [disabled]="!auth.can('content')" /> {{ 'Todos os itens' | t }}</label>
                  <label class="check"><input type="radio" [name]="'scope-' + rule.key" value="some" [ngModel]="rule.scope" (ngModelChange)="setRule(r, { scope: $event })" [disabled]="!auth.can('content')" /> {{ 'Só alguns itens' | t }}</label>
                </div>
                @if (rule.scope === 'some') {
                  <div class="picks">
                    @for (item of namedItems(); track item.id) {
                      <label class="chip" [class.on]="rule.itemIds.includes(item.id)">
                        <input type="checkbox" [ngModel]="rule.itemIds.includes(item.id)" (ngModelChange)="toggleRuleItem(r, item.id)" [disabled]="!auth.can('content')" />{{ item.name }}
                      </label>
                    }
                  </div>
                }
              } @else {
                <div class="fixed">
                  @for (item of namedItems(); track item.id) {
                    <label class="fx">
                      <span>{{ item.name }} <small class="muted">({{ money(item.price) }})</small></span>
                      <input class="num" type="number" min="0" step="0.01" inputmode="decimal" [placeholder]="'igual' | t" [attr.aria-label]="'Preço fixo de' | t" [ngModel]="rule.fixed[item.id] ?? null" (ngModelChange)="setFixed(r, item.id, $event)" [disabled]="!auth.can('content')" />
                    </label>
                  }
                  @if (namedItems().length === 0) {
                    <small class="muted">{{ 'Adicione itens à tabela primeiro.' | t }}</small>
                  }
                </div>
              }
            </div>
          }
          @if (auth.can('content')) {
            <button type="button" class="secondary add" (click)="addRule()"><app-icon name="plus" />{{ 'Adicionar variação por horário' | t }}</button>
          }
        </div>

        @if (id() && auth.can('content')) {
          <div class="danger-zone">
            <button type="button" class="secondary danger" (click)="remove()"><app-icon name="trash" />{{ 'Excluir esta tabela' | t }}</button>
          </div>
        }
      </div>

      <div class="right">
        <div class="side-head">
          <b>{{ 'Como fica na TV' | t }}</b>
          <div class="orient" role="group" [attr.aria-label]="'Orientação da tela' | t">
            <button type="button" [class.on]="!portrait()" (click)="portrait.set(false)">{{ 'Horizontal' | t }}</button>
            <button type="button" [class.on]="portrait()" (click)="portrait.set(true)">{{ 'Em pé' | t }}</button>
          </div>
        </div>
        <app-widget-preview kind="PRICES" [settings]="previewSettings()" [data]="previewData()" [portrait]="portrait()" [nowMs]="previewNow()" [title]="'Pré-visualização do painel de preços' | t" />
        @if (preview() && segments().length > 1) {
          <div class="field sim">
            <label for="p-sim">{{ 'Simular o horário' | t }}</label>
            <select id="p-sim" [ngModel]="simulate()" (ngModelChange)="simulate.set(+$event)">
              <option [ngValue]="-1">{{ 'Agora' | t }}</option>
              @for (segment of segments(); track segment.fromMs; let s = $index) {
                <option [ngValue]="s">{{ segmentLabel(segment) }}</option>
              }
            </select>
          </div>
        } @else if (preview() === null && problemList().length === 0) {
          <p class="label">{{ 'Adicione itens com preço para ver o painel.' | t }}</p>
        }
        @if (previewProblem(); as text) {
          <div class="error" role="alert"><app-icon name="circle-alert" [size]="18" /><span>{{ text }}</span></div>
        }
        <p class="label">{{ 'Na TV, o painel pagina sozinho quando há mais itens do que cabem. Tela em pé usa uma coluna.' | t }}</p>
      </div>
    </div>
  `,
  styles: `
    .back { margin: 0 0 8px; }
    .back a { display: inline-flex; align-items: center; gap: 6px; color: var(--muted); text-decoration: none; font-weight: 600; }
    .head-actions { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
    .warnbox { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 16px; padding: 12px 14px; border-radius: var(--r); background: var(--warn-bg); color: var(--warn-fg); border: 1px solid var(--warn-line); }
    .warnbox > app-icon { flex: none; margin-top: 2px; }
    .warn-list { flex: 1; min-width: 0; }
    .warn-list p { margin: 0 0 4px; }
    .layout { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 1fr); gap: 18px; align-items: start; }
    .items-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap; margin-bottom: 6px; }
    .items-head .sec { margin: 0; }
    .sheet { display: flex; gap: 6px; flex-wrap: wrap; }
    .sheet button { display: inline-flex; align-items: center; gap: 6px; }
    .left { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
    .right { position: sticky; top: 12px; }
    .three { display: grid; grid-template-columns: 2fr 1fr 2fr; gap: 12px; }
    .sec { display: flex; align-items: center; gap: 8px; margin: 0 0 10px; }
    .sec app-icon { color: var(--primary); }
    :host-context(:root[data-theme='dark']) .sec app-icon { color: var(--link); }
    .irow { display: grid; grid-template-columns: minmax(84px, 1fr) minmax(140px, 2.4fr) minmax(76px, 0.9fr) minmax(76px, 0.9fr) 78px 70px auto; gap: 8px; align-items: start; padding: 8px 0; border-top: 1px solid var(--line); }
    .irow.head { border-top: 0; padding: 0 0 4px; font-size: 12px; font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: var(--muted); }
    .irow input:not([type='checkbox']):not([type='radio']) { width: 100%; min-width: 0; }
    .namecell { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
    .desc { font-size: 13px; padding-top: 6px; padding-bottom: 6px; }
    .c { text-align: center; justify-self: center; }
    .tick { display: inline-flex; align-items: center; gap: 6px; margin: 0; padding-top: 10px; }
    .tick .lbl { display: none; }
    .acts { display: flex; gap: 2px; align-items: center; padding-top: 3px; }
    .add { margin-top: 12px; display: inline-flex; align-items: center; gap: 8px; }
    .rule { border: 1px solid var(--line); border-radius: var(--r-lg); padding: 12px; margin: 12px 0; background: var(--panel-2); display: flex; flex-direction: column; gap: 10px; }
    .rule-head { display: flex; gap: 8px; align-items: center; }
    .rname { flex: 1; min-width: 0; font-weight: 600; }
    .days, .picks { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
    .chip { position: relative; display: inline-flex; align-items: center; padding: 5px 12px; border: 1px solid var(--line); border-radius: 999px; font-weight: 600; font-size: 13px; cursor: pointer; background: var(--panel); margin: 0; }
    .chip input { position: absolute; opacity: 0; inset: 0; cursor: pointer; }
    .chip.on { background: var(--primary-soft); border-color: var(--primary); color: var(--primary); }
    :host-context(:root[data-theme='dark']) .chip.on { color: var(--link); }
    .times, .pct { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
    .times label, .pct label { display: inline-flex; align-items: center; gap: 6px; margin: 0; font-weight: 600; }
    .mode { display: flex; gap: 16px; flex-wrap: wrap; }
    .num { max-width: 130px; }
    .fixed { display: flex; flex-direction: column; gap: 6px; }
    .fx { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin: 0; font-weight: 400; }
    .danger-zone { padding: 4px 0 12px; }
    .side-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 8px; }
    .orient { display: inline-flex; padding: 3px; gap: 2px; background: var(--panel-3); border: 1px solid var(--line); border-radius: var(--r); }
    .orient button { border: 0; background: transparent; padding: 5px 12px; border-radius: 8px; color: var(--muted); font-weight: 600; font-size: 13px; cursor: pointer; }
    .orient button.on { background: var(--panel); color: var(--primary); box-shadow: var(--sh-1); }
    :host-context(:root[data-theme='dark']) .orient button.on { color: var(--link); }
    .sim { margin-top: 12px; }
    .error { display: flex; gap: 8px; align-items: flex-start; margin-top: 10px; padding: 10px 12px; border-radius: var(--r); font-size: 14px; background: var(--err-bg); color: var(--err-fg); border: 1px solid var(--err-line); }
    @media (max-width: 1280px) {
      .layout { grid-template-columns: 1fr; }
      .right { position: static; order: -1; }
    }
    @media (max-width: 720px) {
      .three { grid-template-columns: 1fr; }
      .irow { grid-template-columns: 1fr 1fr; }
      .irow.head { display: none; }
      .namecell { grid-column: 1 / -1; }
      .irow .tick .lbl { display: inline; }
      .c { justify-self: start; }
    }
  `,
})
export class PriceEditorPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly files = inject(FileSaver);
  protected readonly auth = inject(AuthService);

  readonly currencies = CURRENCIES;
  readonly zones = TIMEZONES;
  readonly weekdays = WEEKDAYS;
  readonly notice = new Notice();

  readonly id = signal<number | null>(null);
  readonly form = signal<PriceForm>(emptyForm());
  readonly busy = signal(false);
  readonly touched = signal(false);
  readonly skipped = signal<string[]>([]);
  /** Como estava o formulário quando foi carregado ou salvo (para saber se há alterações não salvas). */
  private readonly baseline = signal(JSON.stringify(toRequest(emptyForm())));

  readonly problemList = computed(() => problems(this.form()));
  readonly dirty = computed(() => JSON.stringify(toRequest(this.form())) !== this.baseline());
  readonly categories = computed(() => [...new Set(this.form().items.map((item) => item.category.trim()).filter(Boolean))]);
  readonly namedItems = computed(() => this.form().items.filter((item) => item.name.trim() !== ''));

  // pré-visualização
  readonly preview = signal<PriceBoardData | null>(null);
  readonly previewProblem = signal<string | null>(null);
  readonly portrait = signal(false);
  readonly simulate = signal(-1);
  readonly segments = computed(() => this.preview()?.segments ?? []);
  /** O mesmo dado, no formato genérico que o quadro de pré-visualização dos widgets aceita. */
  readonly previewData = computed(() => this.preview() as unknown as Record<string, unknown> | null);
  readonly previewNow = computed(() => {
    const index = this.simulate();
    const segment = index >= 0 ? this.segments()[index] : undefined;
    return segment ? segment.fromMs + 1000 : null;
  });
  readonly previewSettings = computed(() => ({
    theme: 'dark',
    lang: currentLang() === 'en' ? 'en' : 'pt-BR',
    title: this.form().name,
    columns: this.portrait() ? 1 : 2,
    rowsPerColumn: 8,
    secondsPerPage: 6,
    showCategories: true,
  }));

  private readonly csv = viewChild<ElementRef<HTMLInputElement>>('csv');
  private importMode: 'replace' | 'append' = 'replace';

  constructor() {
    // A cada mudança (depois de uma pausa na digitação), o servidor calcula os preços das próximas 24 horas.
    effect((onCleanup) => {
      const form = this.form();
      if (problems(form).length > 0 || form.items.every((item) => item.name.trim() === '')) {
        return;
      }
      const timer = setTimeout(() => this.refreshPreview(form), PREVIEW_DELAY_MS);
      onCleanup(() => clearTimeout(timer));
    });
    // Se o formulário voltou a ter problemas, a pré-visualização antiga continua, mas o aviso do servidor some.
    effect(() => {
      if (this.problemList().length > 0) {
        untracked(() => this.previewProblem.set(null));
      }
    });
  }

  ngOnInit(): void {
    const param = this.route.snapshot.paramMap.get('id');
    if (param && param !== 'nova') {
      this.id.set(Number(param));
      this.api.priceList(Number(param)).subscribe({ next: (list) => this.load(list), error: () => this.router.navigate(['/precos']) });
    }
  }

  // ---------- edição ----------

  setField(field: 'name' | 'currency' | 'timezone', value: string): void {
    this.form.update((form) => ({ ...form, [field]: value }));
  }

  setItem(index: number, patch: Partial<ItemDraft>): void {
    this.form.update((form) => ({ ...form, items: form.items.map((item, i) => (i === index ? { ...item, ...patch } : item)) }));
  }

  addItem(): void {
    const last = this.form().items[this.form().items.length - 1];
    this.form.update((form) => ({ ...form, items: [...form.items, emptyItem(last?.category ?? '')] }));
  }

  removeItem(index: number): void {
    const removed = this.form().items[index];
    this.form.update((form) => ({
      ...form,
      items: form.items.filter((_, i) => i !== index),
      // as variações não podem citar um item que não existe mais
      rules: form.rules.map((rule) => ({
        ...rule,
        itemIds: rule.itemIds.filter((id) => id !== removed.id),
        fixed: Object.fromEntries(Object.entries(rule.fixed).filter(([id]) => id !== removed.id)),
      })),
    }));
  }

  moveItem(index: number, delta: -1 | 1): void {
    this.form.update((form) => ({ ...form, items: move(form.items, index, delta) }));
  }

  addRule(): void {
    this.form.update((form) => ({ ...form, rules: [...form.rules, emptyRule()] }));
  }

  setRule(index: number, patch: Partial<RuleDraft>): void {
    this.form.update((form) => ({ ...form, rules: form.rules.map((rule, i) => (i === index ? { ...rule, ...patch } : rule)) }));
  }

  removeRule(index: number): void {
    this.form.update((form) => ({ ...form, rules: form.rules.filter((_, i) => i !== index) }));
  }

  moveRule(index: number, delta: -1 | 1): void {
    this.form.update((form) => ({ ...form, rules: move(form.rules, index, delta) }));
  }

  toggleDay(ruleIndex: number, day: number): void {
    const days = this.form().rules[ruleIndex].days;
    this.setRule(ruleIndex, { days: days.includes(day) ? days.filter((d) => d !== day) : [...days, day] });
  }

  toggleRuleItem(ruleIndex: number, itemId: string): void {
    const ids = this.form().rules[ruleIndex].itemIds;
    this.setRule(ruleIndex, { itemIds: ids.includes(itemId) ? ids.filter((id) => id !== itemId) : [...ids, itemId] });
  }

  setFixed(ruleIndex: number, itemId: string, value: string | number | null): void {
    const fixed = { ...this.form().rules[ruleIndex].fixed };
    if (value === '' || value === null || value === undefined) {
      delete fixed[itemId];
    } else {
      fixed[itemId] = +value;
    }
    this.setRule(ruleIndex, { fixed });
  }

  money(value: number | null): string {
    return value === null ? '—' : new Intl.NumberFormat(currentLang() === 'en' ? 'en-US' : 'pt-BR', { style: 'currency', currency: this.form().currency }).format(value);
  }

  /** "Happy hour, 17:00 às 20:00" (ou "Preço normal, ..."), na hora do fuso da tabela. */
  segmentLabel(segment: { fromMs: number; toMs: number; rule: string | null }): string {
    const format = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit', timeZone: this.form().timezone });
    return `${segment.rule ?? tr('Preço normal')}: ${format.format(segment.fromMs)} – ${format.format(segment.toMs)}`;
  }

  // ---------- salvar, importar, exportar, excluir ----------

  save(): void {
    this.touched.set(true);
    if (this.problemList().length > 0 || this.busy()) {
      return;
    }
    this.busy.set(true);
    const request = toRequest(this.form());
    const id = this.id();
    const call = id ? this.api.updatePriceList(id, request) : this.api.createPriceList(request);
    call.subscribe({
      next: (list) => {
        this.busy.set(false);
        this.load(list);
        this.notice.show(tr('Tabela "{name}" salva. As TVs que mostram esta tabela recebem os novos preços em cerca de um minuto.', { name: list.name }));
        if (!id) {
          this.router.navigate(['/precos', list.id], { replaceUrl: true });
        }
      },
      // O motivo (nome repetido, preço inválido...) aparece no aviso de erro do painel; o formulário continua como está.
      error: () => this.busy.set(false),
    });
  }

  chooseImport(mode: 'replace' | 'append'): void {
    if (this.dirty() && !confirm(tr('As alterações que você ainda não salvou nesta tela serão perdidas ao importar. Continuar?'))) {
      return;
    }
    this.importMode = mode;
    this.csv()?.nativeElement.click();
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    const id = this.id();
    if (!file || !id) {
      return;
    }
    this.busy.set(true);
    this.api.importPriceList(id, file, this.importMode).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.load(result.list);
        this.skipped.set(result.skipped);
        this.notice.show(tr('{n} item(ns) importado(s) da planilha.', { n: result.imported }));
      },
      error: () => this.busy.set(false),
    });
  }

  exportCsv(): void {
    const id = this.id();
    if (!id) {
      return;
    }
    this.api.exportPriceList(id).subscribe({ next: (blob) => this.files.save(blob, csvName(this.form().name)), error: () => undefined });
  }

  remove(): void {
    const id = this.id();
    if (!id || !confirm(tr('Excluir a tabela "{name}"?', { name: this.form().name }))) {
      return;
    }
    this.api.deletePriceList(id).subscribe({
      next: () => this.router.navigate(['/precos']),
      // Em uso por um widget: o motivo aparece no aviso de erro do painel.
      error: () => undefined,
    });
  }

  private load(list: PriceList): void {
    this.id.set(list.id);
    const form = formFromList(list);
    this.form.set(form);
    this.baseline.set(JSON.stringify(toRequest(form)));
    this.touched.set(false);
  }

  private refreshPreview(form: PriceForm): void {
    this.api.previewPriceList(toRequest(form)).subscribe({
      next: (data) => {
        if (form !== this.form()) {
          return;
        }
        this.preview.set(data);
        this.previewProblem.set(null);
        if (this.simulate() >= data.segments.length) {
          this.simulate.set(-1);
        }
      },
      error: (response) => {
        if (form === this.form()) {
          this.previewProblem.set((response?.error?.error as string | undefined) ?? tr('Confira os itens e as variações.'));
        }
      },
    });
  }
}

function move<T>(list: T[], index: number, delta: -1 | 1): T[] {
  const target = index + delta;
  if (target < 0 || target >= list.length) {
    return list;
  }
  const copy = [...list];
  [copy[index], copy[target]] = [copy[target], copy[index]];
  return copy;
}
