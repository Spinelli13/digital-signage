import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { TPipe, tr } from '../core/i18n';
import { formatDateTime } from '../core/format';
import { PriceList } from '../core/models';
import { Notice } from '../core/notice';
import { IconComponent } from '../shared/icon';
import { EmptyStateComponent, MenuComponent, PageHeaderComponent, PillComponent, SkeletonComponent } from '../shared/ui';

/** Tabelas de preços: cardápios e listas de serviços que viram painéis de preço na TV. */
@Component({
  selector: 'app-prices',
  imports: [RouterLink, TPipe, IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent, MenuComponent],
  template: `
    <app-page-header [title]="'Preços' | t" [subtitle]="'Cardápios e listas de serviços com preços por horário (happy hour, almoço executivo). Ao mudar um preço aqui, as TVs que mostram a tabela atualizam sozinhas.' | t" icon="badge-dollar-sign">
      @if (auth.can('content')) {
        <a class="btn primary" routerLink="/precos/nova"><app-icon name="plus" />{{ 'Nova tabela' | t }}</a>
      }
    </app-page-header>

    @if (notice.text(); as text) {
      <div class="notice" role="status"><app-icon name="circle-check" [size]="18" /><span>{{ text }}</span></div>
    }

    @if (lists().length === 0) {
      <div class="card">
        @if (loaded()) {
          <app-empty-state icon="badge-dollar-sign" [title]="'Nenhuma tabela de preços ainda' | t"
            [text]="'Monte a lista de produtos ou serviços (ou importe uma planilha), defina os preços por horário e depois use o widget Tabela de preços na Biblioteca para exibi-la na TV.' | t">
            @if (auth.can('content')) {
              <a class="btn primary" routerLink="/precos/nova"><app-icon name="plus" />{{ 'Nova tabela' | t }}</a>
            }
          </app-empty-state>
        } @else {
          <app-skeleton [rows]="3" [height]="64" />
        }
      </div>
    } @else {
      <div class="grid">
        @for (list of lists(); track list.id) {
          <div class="card hoverable item">
            <div class="top">
              <a class="name" [routerLink]="['/precos', list.id]"><app-icon name="badge-dollar-sign" [size]="20" />{{ list.name }}</a>
              @if (auth.can('content')) {
                <app-menu [label]="'Mais ações' | t">
                  <a class="menu-item" [routerLink]="['/precos', list.id]"><app-icon name="pencil" />{{ 'Editar' | t }}</a>
                  <button type="button" class="menu-item danger" (click)="remove(list)"><app-icon name="trash" />{{ 'Excluir' | t }}</button>
                </app-menu>
              }
            </div>
            <div class="facts">
              <app-pill icon="list">{{ list.items.length === 1 ? ('1 item' | t) : ('{n} itens' | t: { n: list.items.length }) }}</app-pill>
              @if (list.rules.length > 0) {
                <app-pill tone="brand" icon="clock">{{ list.rules.length === 1 ? ('1 variação por horário' | t) : ('{n} variações por horário' | t: { n: list.rules.length }) }}</app-pill>
              }
              @if (list.widgetCount > 0) {
                <app-pill tone="ok" icon="puzzle">{{ list.widgetCount === 1 ? ('em 1 widget' | t) : ('em {n} widgets' | t: { n: list.widgetCount }) }}</app-pill>
              }
            </div>
            <small class="muted">{{ 'atualizada' | t }} {{ when(list.updatedAt) }}</small>
          </div>
        }
      </div>
    }
  `,
  styles: `
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 14px; }
    .item { display: flex; flex-direction: column; gap: 12px; padding: 16px; }
    .top { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
    .name { display: inline-flex; align-items: center; gap: 8px; font-weight: 700; font-size: 17px; color: var(--text); text-decoration: none; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .name app-icon { color: var(--primary); flex: none; }
    :host-context(:root[data-theme='dark']) .name app-icon { color: var(--link); }
    .facts { display: flex; flex-wrap: wrap; gap: 6px; }
    .btn { display: inline-flex; align-items: center; gap: 8px; text-decoration: none; }
  `,
})
export class PricesPage implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  readonly lists = signal<PriceList[]>([]);
  readonly loaded = signal(false);
  readonly notice = new Notice();
  readonly when = (iso: string | null) => formatDateTime(iso);

  ngOnInit(): void {
    this.load();
  }

  remove(list: PriceList): void {
    if (!confirm(tr('Excluir a tabela "{name}"?', { name: list.name }))) {
      return;
    }
    this.api.deletePriceList(list.id).subscribe({
      next: () => {
        this.notice.show(tr('Tabela "{name}" excluída.', { name: list.name }));
        this.load();
      },
      // Em uso por um widget: o motivo aparece no aviso de erro do painel.
      error: () => undefined,
    });
  }

  private load(): void {
    this.api.priceLists().subscribe({
      next: (lists) => {
        this.lists.set(lists);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }
}
