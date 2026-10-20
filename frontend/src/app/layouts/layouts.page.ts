import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Layout, LayoutTemplate, Playlist } from '../core/models';
import { Notice } from '../core/notice';
import {
  ZoneDraft,
  addZone,
  blankDrafts,
  draftsFromLayout,
  draftsFromTemplate,
  removeZone,
  setMain,
  toLayoutRequest,
  validateZones,
  zoneColor,
} from './layout-form';

/** Layouts: como a tela da TV é dividida em zonas, cada uma com a sua lista de mídias. */
@Component({
  selector: 'app-layouts',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="head">
      <div>
        <h1>Layouts</h1>
        <p>Divida a tela da TV em zonas: o conteúdo principal e, por exemplo, uma faixa lateral com avisos ou um QR Code. Sem layout, a TV exibe um conteúdo só, em tela cheia.</p>
      </div>
      @if (auth.can('content')) {
        <button class="primary" (click)="openNew()">+ Novo layout</button>
      }
    </div>

    @if (notice.text(); as text) {
      <div class="notice" role="status">{{ text }}</div>
    }

    @if (layouts().length === 0) {
      <div class="card empty">
        @if (loaded()) {
          Nenhum layout ainda. Clique em <b>Novo layout</b> e escolha um modelo pronto (por exemplo, principal + faixa lateral).
        } @else {
          Carregando…
        }
      </div>
    } @else {
      <div class="grid">
        @for (layout of layouts(); track layout.id) {
          <div class="card item">
            <div class="screen" aria-hidden="true">
              @for (zone of layout.zones; track zone.id; let i = $index) {
                <div class="zone" [style.left.%]="zone.x" [style.top.%]="zone.y" [style.width.%]="zone.width" [style.height.%]="zone.height" [style.background]="color(layout.zones, i)">
                  <span>{{ zone.main ? '★ ' : '' }}{{ zone.name }}</span>
                </div>
              }
            </div>
            <div class="meta">
              <b>{{ layout.name }}</b>
              @if (layout.description) {
                <small>{{ layout.description }}</small>
              }
              <small>
                {{ layout.zones.length }} zona(s) ·
                @if (layout.deviceCount > 0) {
                  <a routerLink="/dispositivos">{{ layout.deviceCount }} TV(s)</a>
                } @else {
                  nenhuma TV usa
                }
              </small>
            </div>
            <div class="buttons">
              @if (auth.can('content')) {
                <button class="secondary small" (click)="openEdit(layout)">Editar</button>
                <button class="danger small" (click)="remove(layout)">Excluir</button>
              }
            </div>
          </div>
        }
      </div>
    }

    @if (modal() === 'new') {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box wide" (click)="$event.stopPropagation()" (ngSubmit)="createFromTemplate()">
          <h2>Novo layout</h2>
          <p class="label">Escolha um modelo para começar. Depois você ajusta as zonas como quiser.</p>
          <div class="templates">
            @for (template of templates(); track template.id) {
              <button type="button" class="template" [class.picked]="pickedTemplate()?.id === template.id" (click)="pick(template)">
                <div class="screen small-screen" aria-hidden="true">
                  @for (zone of template.zones; track zone.name; let i = $index) {
                    <div class="zone" [style.left.%]="zone.x" [style.top.%]="zone.y" [style.width.%]="zone.width" [style.height.%]="zone.height" [style.background]="color(template.zones, i)"></div>
                  }
                </div>
                <b>{{ template.name }}</b>
                <small>{{ template.description }}</small>
              </button>
            }
          </div>
          <div class="field">
            <label for="layout-new-name">Nome do layout</label>
            <input id="layout-new-name" name="name" placeholder="Ex.: Loja com faixa de avisos" [ngModel]="name()" (ngModelChange)="name.set($event)" />
          </div>
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">Cancelar</button>
            <button type="button" class="secondary" (click)="openBlank()">Começar do zero</button>
            <button type="submit" class="primary" [disabled]="busy() || !pickedTemplate()">Criar e ajustar</button>
          </div>
        </form>
      </div>
    }

    @if (modal() === 'edit') {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box wide" (click)="$event.stopPropagation()" (ngSubmit)="save()">
          <h2>{{ editing() ? 'Editar layout' : 'Novo layout' }}</h2>
          <div class="two">
            <div class="field">
              <label for="layout-name">Nome</label>
              <input id="layout-name" name="name" required [ngModel]="name()" (ngModelChange)="name.set($event)" />
            </div>
            <div class="field">
              <label for="layout-desc">Descrição (opcional)</label>
              <input id="layout-desc" name="description" [ngModel]="description()" (ngModelChange)="description.set($event)" />
            </div>
          </div>

          <div class="screen big" aria-label="Pré-visualização do layout">
            @for (zone of zones(); track zone.key; let i = $index) {
              <div class="zone" [style.left.%]="zone.x" [style.top.%]="zone.y" [style.width.%]="zone.width" [style.height.%]="zone.height" [style.background]="color(zones(), i)">
                <span>{{ zone.main ? '★ ' : '' }}{{ zone.name }}</span>
              </div>
            }
          </div>

          <table class="zones">
            <tr><th>Zona</th><th>X %</th><th>Y %</th><th>Largura %</th><th>Altura %</th><th>Principal</th><th>Playlist</th><th></th></tr>
            @for (zone of zones(); track zone.key; let i = $index) {
              <tr>
                <td><span class="swatch" [style.background]="color(zones(), i)"></span><input class="name-input" [attr.aria-label]="'Nome da zona ' + (i + 1)" [name]="'zname' + zone.key" [ngModel]="zone.name" (ngModelChange)="change(zone.key, 'name', $event)" /></td>
                <td><input type="number" class="num" min="0" max="95" [attr.aria-label]="'X da zona ' + (i + 1)" [name]="'zx' + zone.key" [ngModel]="zone.x" (ngModelChange)="change(zone.key, 'x', $event)" /></td>
                <td><input type="number" class="num" min="0" max="95" [attr.aria-label]="'Y da zona ' + (i + 1)" [name]="'zy' + zone.key" [ngModel]="zone.y" (ngModelChange)="change(zone.key, 'y', $event)" /></td>
                <td><input type="number" class="num" min="5" max="100" [attr.aria-label]="'Largura da zona ' + (i + 1)" [name]="'zw' + zone.key" [ngModel]="zone.width" (ngModelChange)="change(zone.key, 'width', $event)" /></td>
                <td><input type="number" class="num" min="5" max="100" [attr.aria-label]="'Altura da zona ' + (i + 1)" [name]="'zh' + zone.key" [ngModel]="zone.height" (ngModelChange)="change(zone.key, 'height', $event)" /></td>
                <td class="center"><input type="radio" name="main" [attr.aria-label]="'Zona principal: ' + zone.name" [checked]="zone.main" (change)="makeMain(zone.key)" /></td>
                <td>
                  @if (zone.main) {
                    <span class="muted">a da TV (e das campanhas)</span>
                  } @else {
                    <select [attr.aria-label]="'Playlist da zona ' + (i + 1)" [name]="'zp' + zone.key" [ngModel]="zone.playlistId" (ngModelChange)="change(zone.key, 'playlistId', $event)">
                      <option [ngValue]="null">Sem conteúdo</option>
                      @for (playlist of playlists(); track playlist.id) {
                        <option [ngValue]="playlist.id">{{ playlist.name }}</option>
                      }
                    </select>
                  }
                </td>
                <td><button type="button" class="danger small" [disabled]="zones().length <= 1" (click)="dropZone(zone.key)" [attr.aria-label]="'Remover a zona ' + (i + 1)">✕</button></td>
              </tr>
            }
          </table>
          <button type="button" class="secondary small" (click)="newZone()">+ Adicionar zona</button>

          @if (problem(); as text) {
            <div class="error" role="alert">{{ text }}</div>
          }
          <p class="label">Posição e tamanho em % da tela, a partir do canto superior esquerdo. A zona <b>principal</b> exibe a playlist escolhida para a TV (e as campanhas); as outras exibem a playlist fixa do layout.</p>

          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">Cancelar</button>
            <button type="submit" class="primary" [disabled]="busy() || !!problem() || !name().trim()">Salvar</button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 16px; }
    .item { padding: 14px; display: flex; flex-direction: column; gap: 10px; }
    .screen { position: relative; width: 100%; aspect-ratio: 16 / 9; background: #0b1020; border: 2px solid #1d2742; border-radius: 8px; overflow: hidden; }
    .screen.big { max-width: 560px; margin: 4px auto 16px; }
    .small-screen { margin-bottom: 8px; }
    .zone { position: absolute; border: 1px solid #fff; box-sizing: border-box; display: flex; align-items: center; justify-content: center; overflow: hidden; }
    .zone span { color: #fff; font-size: 12px; font-weight: 600; text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5); padding: 2px; text-align: center; }
    .meta { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .buttons { display: flex; gap: 8px; }
    .modal-box.wide { max-width: 860px; }
    .two { display: grid; grid-template-columns: 1fr 1fr; gap: 0 14px; }
    .templates { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; margin: 12px 0; }
    .template { text-align: left; background: #fff; border: 2px solid var(--line); border-radius: 12px; padding: 10px; display: flex; flex-direction: column; gap: 2px; color: var(--text); }
    .template:hover { border-color: var(--blue); }
    .template.picked { border-color: var(--blue); background: var(--blue2); }
    table.zones { margin-bottom: 10px; }
    table.zones td { padding: 6px 4px; }
    .name-input { width: 130px; }
    .num { width: 70px; }
    .swatch { display: inline-block; width: 10px; height: 10px; border-radius: 3px; margin-right: 6px; }
    .center { text-align: center; }
    select { min-width: 150px; }
    @media (max-width: 720px) { .two { grid-template-columns: 1fr; } }
  `,
})
export class LayoutsPage implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly layouts = signal<Layout[]>([]);
  readonly playlists = signal<Playlist[]>([]);
  readonly templates = signal<LayoutTemplate[]>([]);
  readonly loaded = signal(false);
  readonly notice = new Notice();

  readonly modal = signal<'new' | 'edit' | null>(null);
  readonly busy = signal(false);
  readonly editing = signal<Layout | null>(null);
  readonly name = signal('');
  readonly description = signal('');
  readonly zones = signal<ZoneDraft[]>([]);
  readonly pickedTemplate = signal<LayoutTemplate | null>(null);

  readonly problem = computed(() => validateZones(this.zones()));
  readonly color = zoneColor;

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.notice.dispose());
    this.load();
  }

  // ---------- novo layout (escolhendo um modelo) ----------

  openNew(): void {
    this.name.set('');
    this.pickedTemplate.set(null);
    this.modal.set('new');
    if (this.templates().length === 0) {
      this.api.layoutTemplates().subscribe({ next: (list) => this.templates.set(list), error: () => undefined });
    }
  }

  pick(template: LayoutTemplate): void {
    this.pickedTemplate.set(template);
    if (!this.name().trim()) {
      this.name.set(template.name);
    }
  }

  /** Cria o layout com as zonas do modelo e já abre o editor para o ajuste fino. */
  createFromTemplate(): void {
    const template = this.pickedTemplate();
    if (!template || !this.name().trim()) {
      return;
    }
    this.busy.set(true);
    this.api.createLayoutFromTemplate(template.id, this.name().trim()).subscribe({
      next: (layout) => {
        this.busy.set(false);
        this.notice.show(`Layout "${layout.name}" criado. Ajuste as zonas se quiser.`);
        this.load();
        this.openEdit(layout);
      },
      // O motivo (ex.: "já existe um layout com esse nome") aparece no aviso de erro; a janela continua aberta.
      error: () => this.busy.set(false),
    });
  }

  /** Sem modelo: abre o editor com uma zona principal em tela cheia (o layout só é criado ao salvar). */
  openBlank(): void {
    this.editing.set(null);
    this.description.set('');
    this.zones.set(blankDrafts());
    this.modal.set('edit');
  }

  // ---------- editar ----------

  openEdit(layout: Layout): void {
    this.editing.set(layout);
    this.name.set(layout.name);
    this.description.set(layout.description ?? '');
    this.zones.set(draftsFromLayout(layout));
    this.modal.set('edit');
  }

  closeModal(): void {
    this.modal.set(null);
  }

  change(key: number, field: 'name' | 'x' | 'y' | 'width' | 'height' | 'playlistId', value: unknown): void {
    this.zones.update((zones) =>
      zones.map((zone) => {
        if (zone.key !== key) {
          return zone;
        }
        if (field === 'name') {
          return { ...zone, name: String(value ?? '') };
        }
        if (field === 'playlistId') {
          return { ...zone, playlistId: value === null || value === undefined || value === '' ? null : Number(value) };
        }
        // campo numérico vazio vira NaN: a validação avisa em vez de enviar lixo
        return { ...zone, [field]: value === null || value === '' ? Number.NaN : Number(value) };
      }),
    );
  }

  makeMain(key: number): void {
    this.zones.update((zones) => setMain(zones, key));
  }

  dropZone(key: number): void {
    this.zones.update((zones) => removeZone(zones, key));
  }

  newZone(): void {
    const next = addZone(this.zones());
    if (next === null) {
      this.notice.show('Não há espaço livre para outra zona (ou o limite de 6 zonas foi atingido). Reduza uma zona primeiro.');
      return;
    }
    this.zones.set(next);
  }

  save(): void {
    if (this.problem() || !this.name().trim()) {
      return;
    }
    const body = toLayoutRequest(this.name(), this.description(), this.zones());
    const editing = this.editing();
    this.busy.set(true);
    const request = editing ? this.api.updateLayout(editing.id, body) : this.api.createLayout(body);
    request.subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.closeModal();
        this.notice.show(
          saved.deviceCount > 0
            ? `Layout "${saved.name}" salvo. As ${saved.deviceCount} TV(s) que o usam atualizam em até 1 minuto.`
            : `Layout "${saved.name}" salvo.`,
        );
        this.load();
      },
      error: () => this.busy.set(false),
    });
  }

  remove(layout: Layout): void {
    if (layout.deviceCount > 0) {
      this.notice.show(`O layout "${layout.name}" está em uso por ${layout.deviceCount} TV(s). Escolha outro layout (ou tela cheia) para elas antes de excluir.`);
      return;
    }
    if (!confirm(`Excluir o layout "${layout.name}"?`)) {
      return;
    }
    this.api.deleteLayout(layout.id).subscribe({
      next: () => {
        this.notice.show(`Layout "${layout.name}" excluído.`);
        this.load();
      },
      error: () => undefined,
    });
  }

  private load(): void {
    forkJoin({ layouts: this.api.layouts(), playlists: this.api.playlists() }).subscribe({
      next: ({ layouts, playlists }) => {
        this.layouts.set(layouts);
        this.playlists.set(playlists);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }
}
