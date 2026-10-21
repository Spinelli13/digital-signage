import { Component, DestroyRef, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { TIMEZONES } from '../campaigns/campaign-form';
import { ApiService } from '../core/api.service';
import { currentLang } from '../core/i18n';
import { TPipe } from '../core/i18n';
import { Media, PriceList, WidgetCatalog, WidgetKind, WidgetPlace, WidgetRequest, WidgetSettings } from '../core/models';
import { WIDGET_KINDS, defaultWidgetSettings, widgetInfo } from '../core/widgets';
import { IconComponent } from '../shared/icon';
import { WidgetPreviewComponent } from './widget-preview';

export const THEMES: { value: string; label: string }[] = [
  { value: 'dark', label: 'Escuro' },
  { value: 'light', label: 'Claro' },
  { value: 'brand', label: 'Verde Smartplay' },
];
export const ACCENTS: { value: string; label: string }[] = [
  { value: 'brand', label: 'Verde' },
  { value: 'blue', label: 'Azul' },
  { value: 'amber', label: 'Amarelo' },
  { value: 'red', label: 'Vermelho' },
  { value: 'green', label: 'Verde-claro' },
];
const PREVIEW_DELAY_MS = 450;

/**
 * Criar e editar um widget. Passo 1 (só ao criar): escolher o tipo. Passo 2: as escolhas do tipo, com a pré-visualização ao
 * lado, desenhada pela MESMA página que a TV usa e com os dados de agora. O servidor é quem confere as escolhas (as mesmas
 * regras de salvar): o motivo de uma escolha inválida aparece ao lado da pré-visualização e impede de salvar.
 */
@Component({
  selector: 'app-widget-editor',
  imports: [FormsModule, RouterLink, TPipe, IconComponent, WidgetPreviewComponent],
  template: `
    <div class="modal" (click)="closed.emit()">
      <form class="modal-box wide" (click)="$event.stopPropagation()" (ngSubmit)="save()">
        @if (!kind()) {
          <h2 class="mh"><app-icon name="puzzle" [size]="22" />{{ 'Novo widget' | t }}</h2>
          <p class="label">{{ 'Conteúdo que se atualiza sozinho. Escolha o tipo; depois você ajusta e vê como fica na TV.' | t }}</p>
          <div class="kinds">
            @for (info of kinds; track info.kind) {
              <button type="button" class="kind" (click)="choose(info.kind)">
                <span class="kind-icon"><app-icon [name]="info.icon" [size]="24" /></span>
                <b>{{ info.label | t }}</b>
                <small>{{ info.description | t }}</small>
              </button>
            }
          </div>
          <div class="actions"><button type="button" class="secondary" (click)="closed.emit()">{{ 'Cancelar' | t }}</button></div>
        } @else {
          <h2 class="mh"><app-icon [name]="info()!.icon" [size]="22" />{{ info()!.label | t }}</h2>
          <div class="layout">
            <div class="form">
              <div class="field">
                <label for="w-name">{{ 'Nome (opcional)' | t }}</label>
                <input id="w-name" name="name" [placeholder]="info()!.label | t" [ngModel]="name()" (ngModelChange)="name.set($event)" maxlength="255" />
              </div>

              @switch (kind()) {
                @case ('CLOCK') {
                  <div class="field">
                    <label for="w-tz">{{ 'Fuso horário' | t }}</label>
                    <select id="w-tz" name="tz" [ngModel]="text('timezone')" (ngModelChange)="set('timezone', $event)">
                      @for (zone of zones; track zone.value) {
                        <option [value]="zone.value">{{ zone.label | t }}</option>
                      }
                    </select>
                  </div>
                  <div class="field">
                    <label for="w-label">{{ 'Texto acima da hora (opcional)' | t }}</label>
                    <input id="w-label" name="label" [placeholder]="'Ex.: Loja Centro' | t" [ngModel]="text('label')" (ngModelChange)="set('label', $event)" maxlength="60" />
                  </div>
                  <div class="checks">
                    <label class="check"><input type="checkbox" name="showDate" [ngModel]="flag('showDate')" (ngModelChange)="set('showDate', $event)" /> {{ 'Mostrar a data' | t }}</label>
                    <label class="check"><input type="checkbox" name="showSeconds" [ngModel]="flag('showSeconds')" (ngModelChange)="set('showSeconds', $event)" /> {{ 'Mostrar os segundos' | t }}</label>
                    <label class="check"><input type="checkbox" name="hour12" [ngModel]="flag('hour12')" (ngModelChange)="set('hour12', $event)" /> {{ 'Formato de 12 horas (AM/PM)' | t }}</label>
                  </div>
                }
                @case ('WEATHER') {
                  <div class="field">
                    <label for="w-city">{{ 'Cidade' | t }}</label>
                    <div class="search">
                      <input id="w-city" name="city" type="search" autocomplete="off" [placeholder]="'Digite o nome da cidade' | t" [ngModel]="placeQuery()" (ngModelChange)="placeQuery.set($event)" (keydown.enter)="$event.preventDefault(); searchPlaces()" />
                      <button type="button" class="secondary" (click)="searchPlaces()" [disabled]="placeQuery().trim().length < 2"><app-icon name="search" [size]="16" />{{ 'Buscar' | t }}</button>
                    </div>
                    @if (text('city')) {
                      <p class="chosen"><app-icon name="map-pin" [size]="15" />{{ 'Cidade escolhida:' | t }} <b>{{ text('city') }}</b></p>
                    }
                    @if (places().length > 0) {
                      <ul class="places">
                        @for (place of places(); track place.latitude + ',' + place.longitude) {
                          <li><button type="button" (click)="choosePlace(place)"><b>{{ place.name }}</b><small>{{ placeDetail(place) }}</small></button></li>
                        }
                      </ul>
                    } @else if (searched() && places().length === 0) {
                      <p class="label">{{ 'Nenhuma cidade encontrada. Tente outro nome.' | t }}</p>
                    }
                  </div>
                  <div class="field">
                    <label for="w-days">{{ 'Dias de previsão' | t }}</label>
                    <select id="w-days" name="days" [ngModel]="num('days')" (ngModelChange)="set('days', +$event)">
                      <option [ngValue]="0">{{ 'Só o tempo de agora' | t }}</option>
                      <option [ngValue]="1">{{ 'Agora e o dia seguinte' | t }}</option>
                      <option [ngValue]="2">{{ 'Agora e os 2 dias seguintes' | t }}</option>
                      <option [ngValue]="3">{{ 'Agora e os 3 dias seguintes' | t }}</option>
                    </select>
                  </div>
                }
                @case ('NEWS') {
                  <div class="field">
                    <label for="w-source">{{ 'Fonte das notícias' | t }}</label>
                    <select id="w-source" name="source" [ngModel]="text('source')" (ngModelChange)="set('source', $event)">
                      @for (source of catalog()?.newsSources ?? []; track source.id) {
                        <option [value]="source.id">{{ source.name }}</option>
                      }
                      <option value="custom">{{ 'Outro feed (RSS)' | t }}</option>
                    </select>
                  </div>
                  @if (text('source') === 'custom') {
                    <div class="field">
                      <label for="w-feed">{{ 'Endereço do feed' | t }}</label>
                      <input id="w-feed" name="feed" type="url" placeholder="https://…" autocomplete="off" [ngModel]="text('customUrl')" (ngModelChange)="set('customUrl', $event)" />
                      <p class="label">{{ 'Precisa ser https:// e público. Só as manchetes são exibidas, com o nome da fonte.' | t }}</p>
                    </div>
                  }
                  <div class="two">
                    <div class="field">
                      <label for="w-max">{{ 'Manchetes (3 a 12)' | t }}</label>
                      <input id="w-max" name="max" type="number" min="3" max="12" [ngModel]="num('maxItems')" (ngModelChange)="set('maxItems', +$event)" />
                    </div>
                    <div class="field">
                      <label for="w-per">{{ 'Segundos por manchete' | t }}</label>
                      <input id="w-per" name="per" type="number" min="4" max="30" [ngModel]="num('secondsPerItem')" (ngModelChange)="set('secondsPerItem', +$event)" />
                    </div>
                  </div>
                }
                @case ('RATES') {
                  <div class="field">
                    <label>{{ 'Moedas (até 4), em reais' | t }}</label>
                    <div class="chips">
                      @for (code of catalog()?.currencies ?? fallbackCurrencies; track code) {
                        <label class="chip" [class.on]="hasCurrency(code)">
                          <input type="checkbox" [name]="'cur-' + code" [ngModel]="hasCurrency(code)" (ngModelChange)="toggleCurrency(code)" />{{ code }}
                        </label>
                      }
                    </div>
                    <p class="label">{{ 'Cotação de referência do Banco Central Europeu, atualizada nos dias úteis.' | t }}</p>
                  </div>
                }
                @case ('NOTICE') {
                  <div class="field">
                    <label for="w-title">{{ 'Título' | t }}</label>
                    <input id="w-title" name="title" [placeholder]="'Ex.: Fechado na segunda-feira' | t" [ngModel]="text('title')" (ngModelChange)="set('title', $event)" maxlength="80" />
                  </div>
                  <div class="field">
                    <label for="w-message">{{ 'Mensagem' | t }}</label>
                    <textarea id="w-message" name="message" rows="4" maxlength="400" [ngModel]="text('message')" (ngModelChange)="set('message', $event)"></textarea>
                  </div>
                  <div class="two">
                    <div class="field">
                      <label for="w-accent">{{ 'Cor de destaque' | t }}</label>
                      <select id="w-accent" name="accent" [ngModel]="text('accent')" (ngModelChange)="set('accent', $event)">
                        @for (accent of accents; track accent.value) {
                          <option [value]="accent.value">{{ accent.label | t }}</option>
                        }
                      </select>
                    </div>
                    <div class="field">
                      <label for="w-align">{{ 'Alinhamento' | t }}</label>
                      <select id="w-align" name="align" [ngModel]="text('align')" (ngModelChange)="set('align', $event)">
                        <option value="center">{{ 'Centralizado' | t }}</option>
                        <option value="left">{{ 'À esquerda' | t }}</option>
                      </select>
                    </div>
                  </div>
                }
                @case ('PRICES') {
                  <div class="field">
                    <label for="w-list">{{ 'Tabela de preços' | t }}</label>
                    @if (priceLists().length === 0) {
                      <p class="label">{{ 'Você ainda não tem nenhuma tabela de preços.' | t }} <a routerLink="/precos/nova" (click)="closed.emit()">{{ 'Criar a primeira' | t }}</a></p>
                    } @else {
                      <select id="w-list" name="list" [ngModel]="settings()['priceListId']" (ngModelChange)="set('priceListId', $event === null ? null : +$event)">
                        <option [ngValue]="null">{{ 'Escolha uma tabela…' | t }}</option>
                        @for (list of priceLists(); track list.id) {
                          <option [ngValue]="list.id">{{ list.name }}</option>
                        }
                      </select>
                      <p class="label">{{ 'Os itens e os preços (inclusive os por horário) são editados em Preços; o painel acompanha sozinho.' | t }}</p>
                    }
                  </div>
                  <div class="field">
                    <label for="w-ptitle">{{ 'Título do painel (opcional)' | t }}</label>
                    <input id="w-ptitle" name="ptitle" [placeholder]="'Se vazio, usa o nome da tabela' | t" [ngModel]="text('title')" (ngModelChange)="set('title', $event)" maxlength="60" />
                  </div>
                  <div class="two">
                    <div class="field">
                      <label for="w-cols">{{ 'Colunas (tela horizontal)' | t }}</label>
                      <select id="w-cols" name="cols" [ngModel]="num('columns')" (ngModelChange)="set('columns', +$event)">
                        <option [ngValue]="1">1</option>
                        <option [ngValue]="2">2</option>
                        <option [ngValue]="3">3</option>
                      </select>
                    </div>
                    <div class="field">
                      <label for="w-rows">{{ 'Linhas por coluna (4 a 14)' | t }}</label>
                      <input id="w-rows" name="rows" type="number" min="4" max="14" [ngModel]="num('rowsPerColumn')" (ngModelChange)="set('rowsPerColumn', +$event)" />
                    </div>
                  </div>
                  <div class="two">
                    <div class="field">
                      <label for="w-page">{{ 'Segundos por página' | t }}</label>
                      <input id="w-page" name="page" type="number" min="4" max="30" [ngModel]="num('secondsPerPage')" (ngModelChange)="set('secondsPerPage', +$event)" />
                    </div>
                    <div class="field">
                      <label class="check pad"><input type="checkbox" name="cats" [ngModel]="flag('showCategories')" (ngModelChange)="set('showCategories', $event)" /> {{ 'Mostrar as categorias' | t }}</label>
                    </div>
                  </div>
                  <p class="label">{{ 'Quando há mais itens do que cabem, o painel troca de página sozinho: use um tempo na tela longo o bastante para todas as páginas.' | t }}</p>
                }
                @case ('COUNTDOWN') {
                  <div class="field">
                    <label for="w-clabel">{{ 'O que está sendo esperado' | t }}</label>
                    <input id="w-clabel" name="clabel" [placeholder]="'Ex.: Natal' | t" [ngModel]="text('label')" (ngModelChange)="set('label', $event)" maxlength="80" />
                  </div>
                  <div class="two">
                    <div class="field">
                      <label for="w-target">{{ 'Data e hora' | t }}</label>
                      <input id="w-target" name="target" type="datetime-local" [ngModel]="text('target')" (ngModelChange)="set('target', $event)" />
                    </div>
                    <div class="field">
                      <label for="w-ctz">{{ 'Fuso horário' | t }}</label>
                      <select id="w-ctz" name="ctz" [ngModel]="text('timezone')" (ngModelChange)="set('timezone', $event)">
                        @for (zone of zones; track zone.value) {
                          <option [value]="zone.value">{{ zone.label | t }}</option>
                        }
                      </select>
                    </div>
                  </div>
                  <div class="field">
                    <label for="w-done">{{ 'Texto quando a data chegar' | t }}</label>
                    <input id="w-done" name="done" [ngModel]="text('finishedText')" (ngModelChange)="set('finishedText', $event)" maxlength="80" />
                  </div>
                }
              }

              <div class="two">
                <div class="field">
                  <label for="w-theme">{{ 'Cores' | t }}</label>
                  <select id="w-theme" name="theme" [ngModel]="text('theme')" (ngModelChange)="set('theme', $event)">
                    @for (theme of themes; track theme.value) {
                      <option [value]="theme.value">{{ theme.label | t }}</option>
                    }
                  </select>
                </div>
                <div class="field">
                  <label for="w-lang">{{ 'Idioma do widget' | t }}</label>
                  <select id="w-lang" name="lang" [ngModel]="text('lang')" (ngModelChange)="set('lang', $event)">
                    <option value="pt-BR">{{ 'Português' | t }}</option>
                    <option value="en">{{ 'Inglês' | t }}</option>
                  </select>
                </div>
              </div>
              <div class="field">
                <label for="w-seconds">{{ 'Tempo na tela (segundos)' | t }}</label>
                <input id="w-seconds" name="seconds" type="number" min="5" max="3600" [ngModel]="seconds()" (ngModelChange)="seconds.set($event)" />
              </div>
            </div>

            <div class="side">
              <div class="side-head">
                <b>{{ 'Como fica na TV' | t }}</b>
                <div class="orient" role="group" [attr.aria-label]="'Orientação da tela' | t">
                  <button type="button" [class.on]="!portrait()" (click)="portrait.set(false)">{{ 'Horizontal' | t }}</button>
                  <button type="button" [class.on]="portrait()" (click)="portrait.set(true)">{{ 'Em pé' | t }}</button>
                </div>
              </div>
              <app-widget-preview [kind]="kind()!" [settings]="shown()" [data]="data()" [portrait]="portrait()" [title]="'Pré-visualização do widget' | t" />
              @if (problem(); as text) {
                <div class="error" role="alert"><app-icon name="circle-alert" [size]="18" /><span>{{ text }}</span></div>
              } @else if (dataError(); as text) {
                <div class="warn" role="status"><app-icon name="triangle-alert" [size]="18" /><span>{{ text }}. {{ 'Na TV, o widget mostra o último dado guardado ou um aviso discreto.' | t }}</span></div>
              } @else if (info()!.kind === 'WEATHER' || info()!.kind === 'NEWS' || info()!.kind === 'RATES') {
                <p class="label">{{ 'Os dados são atualizados pelo servidor e a TV os guarda: sem internet, ela mostra o último que recebeu.' | t }}</p>
              }
            </div>
          </div>
          <div class="actions">
            <button type="button" class="secondary" (click)="closed.emit()">{{ 'Cancelar' | t }}</button>
            <button type="submit" class="primary" [disabled]="busy() || !!problem() || checking()">{{ (editing() ? 'Salvar alterações' : 'Adicionar à biblioteca') | t }}</button>
          </div>
        }
      </form>
    </div>
  `,
  styles: `
    .modal-box.wide { max-width: 980px; width: 100%; max-height: calc(100vh - 32px); overflow: auto; }
    .mh { display: flex; align-items: center; gap: 10px; }
    .mh app-icon { color: var(--primary); }
    :host-context(:root[data-theme='dark']) .mh app-icon { color: var(--link); }
    .kinds { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 12px; margin: 14px 0; }
    .kind { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; text-align: left; padding: 14px; border: 1px solid var(--line); border-radius: var(--r-lg); background: var(--panel); cursor: pointer; transition: border-color 0.15s, box-shadow 0.15s; }
    .kind:hover { border-color: var(--primary); box-shadow: var(--sh-2); }
    .kind-icon { display: grid; place-items: center; width: 44px; height: 44px; border-radius: 13px; color: var(--primary); background: var(--primary-soft); border: 1px solid var(--primary-line); }
    :host-context(:root[data-theme='dark']) .kind-icon { color: var(--link); }
    .kind small { color: var(--muted); font-size: 13px; line-height: 1.35; }
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 22px; margin-top: 8px; align-items: start; }
    .side { position: sticky; top: 0; }
    .side-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 8px; }
    .orient { display: inline-flex; padding: 3px; gap: 2px; background: var(--panel-3); border: 1px solid var(--line); border-radius: var(--r); }
    .orient button { border: 0; background: transparent; padding: 5px 12px; border-radius: 8px; color: var(--muted); font-weight: 600; font-size: 13px; cursor: pointer; }
    .orient button.on { background: var(--panel); color: var(--primary); box-shadow: var(--sh-1); }
    :host-context(:root[data-theme='dark']) .orient button.on { color: var(--link); }
    .two { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .checks { display: flex; flex-direction: column; gap: 8px; margin-bottom: 16px; }
    .search { display: flex; gap: 8px; }
    .search input { flex: 1; min-width: 0; }
    .search button { display: inline-flex; align-items: center; gap: 6px; }
    .chosen { display: flex; align-items: center; gap: 6px; margin: 8px 0 0; color: var(--ok-fg); }
    .places { list-style: none; margin: 8px 0 0; padding: 0; border: 1px solid var(--line); border-radius: var(--r); overflow: hidden; }
    .places li + li { border-top: 1px solid var(--line); }
    .places button { display: flex; flex-direction: column; align-items: flex-start; width: 100%; padding: 9px 12px; border: 0; background: var(--panel); cursor: pointer; text-align: left; }
    .places button:hover { background: var(--primary-soft); }
    .places small { color: var(--muted); }
    .chips { display: flex; flex-wrap: wrap; gap: 8px; }
    .chip { position: relative; display: inline-flex; align-items: center; padding: 7px 14px; border: 1px solid var(--line); border-radius: 999px; font-weight: 600; cursor: pointer; background: var(--panel); margin: 0; }
    .chip input { position: absolute; opacity: 0; inset: 0; cursor: pointer; }
    .chip.on { background: var(--primary-soft); border-color: var(--primary); color: var(--primary); }
    .check.pad { margin-top: 26px; }
    :host-context(:root[data-theme='dark']) .chip.on { color: var(--link); }
    textarea { width: 100%; resize: vertical; }
    .error, .warn { display: flex; gap: 8px; align-items: flex-start; margin-top: 10px; padding: 10px 12px; border-radius: var(--r); font-size: 14px; }
    .error { background: var(--err-bg); color: var(--err-fg); border: 1px solid var(--err-line); }
    .warn { background: var(--warn-bg); color: var(--warn-fg); border: 1px solid var(--warn-line); }
    .error app-icon, .warn app-icon { flex: none; margin-top: 1px; }
    @media (max-width: 820px) {
      .layout { grid-template-columns: 1fr; }
      .side { position: static; order: -1; }
      .two { grid-template-columns: 1fr; }
    }
  `,
})
export class WidgetEditorComponent {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  /** Preenchido ao editar um widget que já existe (o tipo não muda); vazio ao criar. */
  readonly media = input<Media | null>(null);
  readonly saved = output<Media>();
  readonly closed = output<void>();

  readonly kinds = WIDGET_KINDS;
  readonly zones = TIMEZONES;
  readonly themes = THEMES;
  readonly accents = ACCENTS;
  readonly fallbackCurrencies = ['USD', 'EUR', 'GBP', 'JPY', 'CAD', 'AUD', 'CHF', 'CNY', 'MXN'];

  readonly kind = signal<WidgetKind | null>(null);
  readonly settings = signal<WidgetSettings>({});
  readonly name = signal('');
  readonly seconds = signal<number | null>(null);
  readonly portrait = signal(false);
  readonly busy = signal(false);
  readonly catalog = signal<WidgetCatalog | null>(null);
  /** As tabelas de preços para escolher (só carregadas quando o widget é um painel de preços). */
  readonly priceLists = signal<PriceList[]>([]);

  /** O que o servidor devolveu na última conferência: escolhas limpas, dados e o motivo de uma escolha inválida. */
  readonly shown = signal<WidgetSettings>({});
  readonly data = signal<Record<string, unknown> | null>(null);
  readonly problem = signal<string | null>(null);
  readonly dataError = signal<string | null>(null);
  readonly checking = signal(false);

  readonly placeQuery = signal('');
  readonly places = signal<WidgetPlace[]>([]);
  readonly searched = signal(false);

  readonly editing = signal(false);

  constructor() {
    this.api.widgetCatalog().subscribe({ next: (catalog) => this.catalog.set(catalog), error: () => undefined });

    // Ao editar, começa direto no passo 2 com as escolhas do widget.
    effect(() => {
      const media = this.media();
      if (media?.widget) {
        untracked(() => {
          this.editing.set(true);
          this.kind.set(media.widget!.kind);
          this.loadPriceLists(media.widget!.kind);
          this.settings.set({ ...media.widget!.settings });
          this.shown.set({ ...media.widget!.settings });
          this.name.set(media.name === widgetInfo(media.widget!.kind)?.label ? '' : media.name);
          this.seconds.set(media.durationSeconds);
        });
      }
    });

    // A cada mudança (com uma pequena espera enquanto a pessoa digita), o servidor confere e traz os dados de agora.
    effect((onCleanup) => {
      const kind = this.kind();
      const settings = this.settings();
      if (!kind) {
        return;
      }
      this.checking.set(true);
      const timer = setTimeout(() => this.check(kind, settings), PREVIEW_DELAY_MS);
      onCleanup(() => clearTimeout(timer));
    });
    this.destroyRef.onDestroy(() => undefined);
  }

  info() {
    return widgetInfo(this.kind());
  }

  private loadPriceLists(kind: WidgetKind): void {
    if (kind === 'PRICES') {
      this.api.priceLists().subscribe({ next: (lists) => this.priceLists.set(lists), error: () => undefined });
    }
  }

  choose(kind: WidgetKind): void {
    this.kind.set(kind);
    this.loadPriceLists(kind);
    this.settings.set(defaultWidgetSettings(kind, currentLang() === 'en' ? 'en' : 'pt-BR'));
    this.shown.set({ ...this.settings() });
    this.seconds.set(this.catalog()?.kinds.find((entry) => entry.kind === kind)?.defaultSeconds ?? 10);
  }

  set(key: string, value: string | number | boolean | string[] | null): void {
    this.settings.update((current) => ({ ...current, [key]: value }));
  }

  text(key: string): string {
    const value = this.settings()[key];
    return value === null || value === undefined ? '' : String(value);
  }

  num(key: string): number {
    return Number(this.settings()[key] ?? 0);
  }

  flag(key: string): boolean {
    return this.settings()[key] === true;
  }

  // ---------- moedas ----------

  hasCurrency(code: string): boolean {
    const value = this.settings()['currencies'];
    return Array.isArray(value) && value.includes(code);
  }

  toggleCurrency(code: string): void {
    const current = (this.settings()['currencies'] as string[] | undefined) ?? [];
    this.set('currencies', current.includes(code) ? current.filter((item) => item !== code) : [...current, code]);
  }

  // ---------- cidade ----------

  searchPlaces(): void {
    const query = this.placeQuery().trim();
    if (query.length < 2) {
      return;
    }
    this.api.widgetPlaces(query, currentLang() === 'en' ? 'en' : 'pt').subscribe({
      next: (places) => {
        this.places.set(places);
        this.searched.set(true);
      },
      error: () => {
        this.places.set([]);
        this.searched.set(true);
      },
    });
  }

  choosePlace(place: WidgetPlace): void {
    this.settings.update((current) => ({
      ...current,
      city: place.region ? `${place.name}, ${place.region}` : place.name,
      latitude: place.latitude,
      longitude: place.longitude,
    }));
    this.places.set([]);
    this.searched.set(false);
    this.placeQuery.set('');
  }

  placeDetail(place: WidgetPlace): string {
    return [place.region, place.country].filter(Boolean).join(', ');
  }

  // ---------- conferir e salvar ----------

  private check(kind: WidgetKind, settings: WidgetSettings): void {
    this.api.widgetPreview(kind, settings).subscribe({
      next: (preview) => {
        // Uma resposta atrasada de uma escolha antiga não vale mais.
        if (kind !== this.kind() || settings !== this.settings()) {
          return;
        }
        this.shown.set(preview.settings);
        this.data.set(preview.data);
        this.dataError.set(preview.error);
        this.problem.set(null);
        this.checking.set(false);
      },
      error: (response) => {
        if (kind !== this.kind() || settings !== this.settings()) {
          return;
        }
        this.problem.set((response?.error?.error as string | undefined) ?? 'Confira as escolhas do widget.');
        this.data.set(null);
        this.dataError.set(null);
        this.checking.set(false);
      },
    });
  }

  request(): WidgetRequest {
    return {
      name: this.name().trim() || null,
      kind: this.kind()!,
      settings: this.settings(),
      durationSeconds: this.seconds() ?? null,
    };
  }

  save(): void {
    if (!this.kind() || this.problem() || this.busy()) {
      return;
    }
    this.busy.set(true);
    const media = this.media();
    const call = media ? this.api.updateWidget(media.id, this.request()) : this.api.createWidget(this.request());
    call.subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.saved.emit(saved);
      },
      // O motivo aparece no aviso de erro do painel; a janela continua aberta para corrigir.
      error: () => this.busy.set(false),
    });
  }
}
