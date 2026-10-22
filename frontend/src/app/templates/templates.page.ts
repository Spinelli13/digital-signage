import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { TPipe, currentLang, trServer } from '../core/i18n';
import { Device, TemplateApplyRequest, TemplateCatalog, TemplateInfo, TemplateResult, WidgetPlace } from '../core/models';
import { IconComponent } from '../shared/icon';
import { IconName } from '../shared/icons';
import { EmptyStateComponent, PageHeaderComponent, SkeletonComponent } from '../shared/ui';

/** Só o lugar da busca de cidade (nome, estado, país) em uma linha. */
export function placeLabel(place: WidgetPlace): string {
  return [place.name, place.region, place.country].filter((part) => !!part).join(', ');
}

/**
 * Modelos prontos por segmento (barbearia, restaurante, clínica, academia, loja): a pessoa confere os textos, escolhe a cidade do
 * clima (opcional) e as TVs (opcional) e um clique cria os avisos, a tabela de preços, as playlists e o layout. Depois tudo se
 * edita como qualquer coisa criada à mão.
 */
@Component({
  selector: 'app-templates',
  imports: [FormsModule, RouterLink, TPipe, IconComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent],
  template: `
    <app-page-header
      [title]="'Modelos prontos' | t"
      [subtitle]="'Escolha o seu tipo de negócio e o Vyzzu monta de uma vez os avisos, a tabela de preços, as playlists e o layout, com os seus textos. Depois é só ajustar.' | t"
      icon="sparkles"></app-page-header>

    @if (catalog(); as data) {
      @if (!data.available.layouts || !data.available.prices) {
        <div class="warn-box" role="status" data-testid="plan-note">
          <app-icon name="info" [size]="18" />
          <span>
            @if (!data.available.layouts) { {{ 'O seu plano não inclui layouts: o modelo cria tudo numa playlist só.' | t }} }
            @if (!data.available.prices) { {{ 'O seu plano não inclui tabelas de preços: o modelo não cria a tabela.' | t }} }
          </span>
        </div>
      }
      <div class="grid">
        @for (template of data.templates; track template.id) {
          <div class="card tpl" data-testid="template-card">
            <div class="tpl-head">
              <span class="tpl-ico"><app-icon [name]="iconOf(template)" [size]="22" /></span>
              <h3>{{ serverText(template.name) }}</h3>
            </div>
            <p class="muted">{{ serverText(template.description) }}</p>
            <small class="inc">{{ 'Inclui' | t }}: {{ includesText(template) }}</small>
            <div class="actions"><button type="button" class="primary" (click)="open(template)"><app-icon name="sparkles" [size]="16" />{{ 'Usar este modelo' | t }}</button></div>
          </div>
        }
      </div>
    } @else if (loading()) {
      <div class="card"><app-skeleton [rows]="4" /></div>
    } @else {
      <div class="card"><app-empty-state icon="sparkles" [title]="'Não foi possível carregar os modelos' | t" [text]="'Tente de novo em instantes.' | t" /></div>
    }

    @if (selected(); as template) {
      <div class="modal" (click)="close()">
        <div class="modal-box" role="dialog" aria-modal="true" (click)="$event.stopPropagation()">
          @if (result(); as done) {
            <h3 class="sec"><app-icon name="circle-check" [size]="20" />{{ 'Modelo criado' | t }}</h3>
            <p class="muted">{{ 'Tudo foi criado e já pode ser editado. Falta só escolher o que a TV vai exibir (se você não marcou a TV agora).' | t }}</p>
            <ul class="made" data-testid="made">
              @if (done.layout) { <li><b>{{ 'Layout' | t }}:</b> {{ done.layout.name }} · <a routerLink="/layouts" (click)="close()">{{ 'Abrir' | t }}</a></li> }
              @for (playlist of done.playlists; track playlist.id) {
                <li><b>{{ 'Playlist' | t }}:</b> {{ playlist.name }} · <a [routerLink]="['/playlists', playlist.id]" (click)="close()">{{ 'Abrir' | t }}</a></li>
              }
              @if (done.priceList) { <li><b>{{ 'Tabela de preços' | t }}:</b> {{ done.priceList.name }} · <a [routerLink]="['/precos', done.priceList.id]" (click)="close()">{{ 'Abrir' | t }}</a></li> }
              <li><b>{{ 'Avisos e widgets' | t }}:</b> {{ widgetNames(done) }} · <a routerLink="/biblioteca" (click)="close()">{{ 'Editar os textos' | t }}</a></li>
            </ul>
            @for (note of done.notes; track note) {
              <p class="note"><app-icon name="info" [size]="15" />{{ serverText(note) }}</p>
            }
            @if (done.devices > 0) {
              <p class="note ok"><app-icon name="monitor-check" [size]="15" />{{ 'Colocado em {n} TV(s). Elas atualizam em instantes.' | t: { n: done.devices } }}</p>
            }
            <div class="actions"><button type="button" class="primary" (click)="close()">{{ 'Concluir' | t }}</button></div>
          } @else {
            <h3 class="sec"><app-icon [name]="iconOf(template)" [size]="20" />{{ 'Modelo: {name}' | t: { name: serverText(template.name) } }}</h3>
            <p class="muted">{{ 'Confira os textos. Você pode mudar tudo depois, na Biblioteca.' | t }}</p>
            @for (field of template.fields; track field.key) {
              <div class="field">
                <label [for]="'tpl-' + field.key">{{ serverText(field.label) }}</label>
                <input [id]="'tpl-' + field.key" [name]="field.key" [attr.maxlength]="field.maxLength" [ngModel]="values()[field.key]" (ngModelChange)="setValue(field.key, $event)" />
              </div>
            }

            <div class="field">
              <label for="tpl-city">{{ 'Cidade da previsão do tempo (opcional)' | t }}</label>
              @if (city(); as chosen) {
                <div class="chosen"><span>{{ placeLabel(chosen) }}</span><button type="button" class="secondary small" (click)="city.set(null)">{{ 'Trocar' | t }}</button></div>
              } @else {
                <div class="search">
                  <input id="tpl-city" name="city" autocomplete="off" [placeholder]="'Digite a cidade' | t" [ngModel]="cityQuery()" (ngModelChange)="cityQuery.set($event)" (keydown.enter)="$event.preventDefault(); searchCity()" />
                  <button type="button" class="secondary" (click)="searchCity()" [disabled]="cityQuery().trim().length < 2 || searching()"><app-icon name="search" [size]="16" />{{ 'Buscar' | t }}</button>
                </div>
                @if (cityResults().length > 0) {
                  <ul class="places">
                    @for (place of cityResults(); track place.latitude + ',' + place.longitude) {
                      <li><button type="button" class="pick" (click)="chooseCity(place)">{{ placeLabel(place) }}</button></li>
                    }
                  </ul>
                } @else if (searched()) {
                  <small class="muted">{{ 'Nenhuma cidade encontrada.' | t }}</small>
                }
              }
            </div>

            @if (devices().length > 0) {
              <fieldset class="tvs">
                <legend>{{ 'Colocar nas TVs agora (opcional)' | t }}</legend>
                @for (device of devices(); track device.id) {
                  <label class="check"><input type="checkbox" [checked]="chosenDevices().has(device.id)" (change)="toggleDevice(device.id)" /> {{ device.name }}</label>
                }
                <small class="muted">{{ 'Marcar uma TV troca o que ela está exibindo agora pelo modelo.' | t }}</small>
              </fieldset>
            }

            <div class="actions">
              <button type="button" class="secondary" (click)="close()">{{ 'Cancelar' | t }}</button>
              <button type="button" class="primary" data-testid="apply" (click)="apply()" [disabled]="busy()"><app-icon name="sparkles" [size]="16" />{{ (busy() ? 'Criando…' : 'Criar o modelo') | t }}</button>
            </div>
          }
        </div>
      </div>
    }
  `,
  styles: `
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
    .tpl { display: flex; flex-direction: column; gap: 8px; }
    .tpl-head { display: flex; align-items: center; gap: 12px; }
    .tpl-head h3 { margin: 0; }
    .tpl-ico { flex: none; display: grid; place-items: center; width: 44px; height: 44px; border-radius: 13px; background: var(--primary-soft); color: var(--primary); border: 1px solid var(--primary-line); }
    :host-context(:root[data-theme='dark']) .tpl-ico { color: var(--link); }
    .inc { color: var(--muted); }
    .tpl .actions { margin-top: auto; padding-top: 6px; }
    .warn-box { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 16px; padding: 11px 14px; border-radius: var(--r); background: var(--warn-bg); color: var(--warn-fg); border: 1px solid var(--warn-line); }
    .warn-box app-icon { flex: none; margin-top: 1px; }
    .sec { display: flex; align-items: center; gap: 8px; }
    .sec app-icon { color: var(--primary); }
    :host-context(:root[data-theme='dark']) .sec app-icon { color: var(--link); }
    .search { display: flex; gap: 8px; }
    .search input { flex: 1; min-width: 0; }
    .chosen { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 8px 12px; border-radius: var(--r); border: 1px solid var(--line); background: var(--panel-2); }
    .places { list-style: none; margin: 8px 0 0; padding: 0; display: grid; gap: 6px; max-height: 180px; overflow: auto; }
    .pick { width: 100%; text-align: left; padding: 8px 12px; border-radius: var(--r); border: 1px solid var(--line); background: var(--panel); color: var(--text); }
    .pick:hover { border-color: var(--primary); background: var(--primary-soft); }
    fieldset.tvs { border: 0; margin: 4px 0 14px; padding: 0; display: grid; gap: 6px; }
    fieldset.tvs legend { padding: 0 0 6px; font-weight: 600; font-size: 13.5px; color: var(--text-2); }
    .made { margin: 12px 0; padding-left: 18px; display: grid; gap: 6px; }
    .note { display: flex; align-items: flex-start; gap: 8px; margin: 6px 0; color: var(--muted); }
    .note.ok { color: var(--ok-fg); }
  `,
})
export class TemplatesPage implements OnInit {
  private readonly api = inject(ApiService);

  readonly catalog = signal<TemplateCatalog | null>(null);
  readonly loading = signal(true);
  readonly selected = signal<TemplateInfo | null>(null);
  readonly values = signal<Record<string, string>>({});
  readonly city = signal<WidgetPlace | null>(null);
  readonly cityQuery = signal('');
  readonly cityResults = signal<WidgetPlace[]>([]);
  readonly searched = signal(false);
  readonly searching = signal(false);
  readonly devices = signal<Device[]>([]);
  readonly chosenDevices = signal<ReadonlySet<number>>(new Set());
  readonly busy = signal(false);
  readonly result = signal<TemplateResult | null>(null);

  readonly placeLabel = placeLabel;
  /** O texto que vem do servidor (sempre em português) no idioma da tela. */
  readonly serverText = (text: string): string => trServer(text);
  readonly iconOf = (template: TemplateInfo): IconName => template.icon as IconName;

  ngOnInit(): void {
    this.api.templates().subscribe({
      next: (catalog) => {
        this.catalog.set(catalog);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    this.api.devices().subscribe({ next: (devices) => this.devices.set(devices.filter((device) => device.status === 'ACTIVE')), error: () => undefined });
  }

  includesText(template: TemplateInfo): string {
    return template.includes.map((item) => trServer(item)).join(', ');
  }

  widgetNames(done: TemplateResult): string {
    return done.widgets.map((widget) => widget.name).join(' · ');
  }

  open(template: TemplateInfo): void {
    this.values.set(Object.fromEntries(template.fields.map((field) => [field.key, field.defaultValue])));
    this.city.set(null);
    this.cityQuery.set('');
    this.cityResults.set([]);
    this.searched.set(false);
    this.chosenDevices.set(new Set());
    this.result.set(null);
    this.selected.set(template);
  }

  close(): void {
    this.selected.set(null);
    this.result.set(null);
  }

  setValue(key: string, value: string): void {
    this.values.update((values) => ({ ...values, [key]: value }));
  }

  searchCity(): void {
    const query = this.cityQuery().trim();
    if (query.length < 2) {
      return;
    }
    this.searching.set(true);
    this.api.widgetPlaces(query, currentLang()).subscribe({
      next: (places) => {
        this.searching.set(false);
        this.searched.set(true);
        this.cityResults.set(places);
      },
      error: () => {
        this.searching.set(false);
        this.searched.set(true);
        this.cityResults.set([]);
      },
    });
  }

  chooseCity(place: WidgetPlace): void {
    this.city.set(place);
    this.cityResults.set([]);
  }

  toggleDevice(id: number): void {
    this.chosenDevices.update((chosen) => {
      const next = new Set(chosen);
      if (!next.delete(id)) {
        next.add(id);
      }
      return next;
    });
  }

  apply(): void {
    const template = this.selected();
    if (!template || this.busy()) {
      return;
    }
    const city = this.city();
    const body: TemplateApplyRequest = {
      fields: this.values(),
      ...(city ? { city: { name: city.name, latitude: city.latitude, longitude: city.longitude, timezone: city.timezone } } : {}),
      ...(this.chosenDevices().size > 0 ? { deviceIds: [...this.chosenDevices()] } : {}),
    };
    this.busy.set(true);
    this.api.applyTemplate(template.id, body).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.result.set(result);
      },
      // O motivo (ex.: "O plano ... não inclui widgets") aparece no aviso de erro do painel; o que foi digitado fica.
      error: () => this.busy.set(false),
    });
  }
}
