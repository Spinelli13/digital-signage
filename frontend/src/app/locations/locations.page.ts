import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Location } from '../core/models';
import { TPipe, tr } from '../core/i18n';
import { Notice } from '../core/notice';
import { IconComponent } from '../shared/icon';
import { EmptyStateComponent, MenuComponent, PageHeaderComponent, PillComponent, SkeletonComponent } from '../shared/ui';

@Component({
  selector: 'app-locations',
  imports: [FormsModule, RouterLink, TPipe, IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent, MenuComponent],
  template: `
    <app-page-header [title]="'Pontos & Locais' | t" [subtitle]="'Onde estão as suas TVs. Cada TV pertence a um local; use os locais para filtrar e agir em várias TVs de uma vez.' | t" icon="map-pin">
      @if (auth.can('admin')) {
        <button class="primary" (click)="openNew()"><app-icon name="plus" />{{ 'Novo local' | t }}</button>
      }
    </app-page-header>

    @if (notice.text(); as text) {
      <div class="notice" role="status"><app-icon name="circle-check" [size]="18" /><span>{{ text }}</span></div>
    }

    <div class="card">
      @if (items().length === 0) {
        @if (loaded()) {
          <app-empty-state icon="map-pin" [title]="'Nenhum local ainda' | t"
            [text]="'Crie o primeiro (por exemplo, Loja Centro) e escolha-o ao ativar cada TV. Assim você filtra e age em várias TVs de uma vez.' | t">
            @if (auth.can('admin')) {
              <button class="primary" (click)="openNew()"><app-icon name="plus" />{{ 'Novo local' | t }}</button>
            }
          </app-empty-state>
        } @else {
          <app-skeleton [rows]="4" />
        }
      } @else {
        <table class="stack">
          <tr><th>{{ 'Local' | t }}</th><th>{{ 'TVs' | t }}</th><th>{{ 'Online' | t }}</th><th></th></tr>
          @for (location of items(); track location.id) {
            <tr>
              <td class="first">
                <span class="place">
                  <span class="place-icon"><app-icon name="map-pin" [size]="18" /></span>
                  <span class="place-text">
                    <b>{{ location.name }}</b>
                    @if (location.description) {
                      <small>{{ location.description }}</small>
                    }
                  </span>
                </span>
              </td>
              <td [attr.data-label]="'TVs' | t">
                @if (location.deviceCount > 0) {
                  <a class="tvs" routerLink="/dispositivos" [queryParams]="{ local: location.id }" [title]="'Ver as TVs deste local' | t"><app-icon name="monitor" [size]="15" /><span class="num">{{ location.deviceCount }}</span></a>
                } @else {
                  <span class="muted num">0</span>
                }
              </td>
              <td [attr.data-label]="'Online' | t">
                @if (location.deviceCount > 0) {
                  <app-pill [tone]="location.onlineCount === location.deviceCount ? 'ok' : location.onlineCount === 0 ? 'err' : 'warn'"
                    [icon]="location.onlineCount === location.deviceCount ? 'monitor-check' : 'wifi-off'">{{ '{online} de {total}' | t: { online: location.onlineCount, total: location.deviceCount } }}</app-pill>
                } @else {
                  <span class="muted">—</span>
                }
              </td>
              <td class="row-actions">
                @if (auth.can('admin')) {
                  <button class="secondary small" (click)="openEdit(location)"><app-icon name="pencil" [size]="15" />{{ 'Editar' | t }}</button>
                  <app-menu [label]="'Mais ações' | t">
                    <button type="button" class="menu-item danger" (click)="remove(location)"><app-icon name="trash" />{{ 'Excluir' | t }}</button>
                  </app-menu>
                }
              </td>
            </tr>
          }
        </table>
      }
    </div>

    @if (modal() !== null) {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="submit()">
          <h2>{{ (modal() === 'new' ? 'Novo local' : 'Editar local') | t }}</h2>
          <div class="field">
            <label for="loc-name">{{ 'Nome do local' | t }}</label>
            <input id="loc-name" name="name" [placeholder]="'Ex.: Loja Centro' | t" required [ngModel]="name()" (ngModelChange)="name.set($event)" />
          </div>
          <div class="field">
            <label for="loc-desc">{{ 'Descrição (opcional)' | t }}</label>
            <input id="loc-desc" name="description" [placeholder]="'Ex.: Rua das Flores, 100' | t" [ngModel]="description()" (ngModelChange)="description.set($event)" />
          </div>
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">{{ 'Cancelar' | t }}</button>
            <button type="submit" class="primary" [disabled]="busy() || !name().trim()">{{ 'Salvar' | t }}</button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .row-actions { white-space: nowrap; text-align: right; }
    .row-actions > * { margin-left: 6px; vertical-align: middle; }
    .place { display: flex; align-items: center; gap: 12px; min-width: 0; }
    .place-icon { flex: none; display: grid; place-items: center; width: 38px; height: 38px; border-radius: 12px; color: var(--primary); background: var(--primary-soft); border: 1px solid var(--primary-line); }
    :host-context(:root[data-theme='dark']) .place-icon { color: var(--link); }
    .place-text { display: flex; flex-direction: column; min-width: 0; }
    .place-text small { color: var(--muted); overflow: hidden; text-overflow: ellipsis; }
    .tvs { display: inline-flex; align-items: center; gap: 6px; font-weight: 600; }
  `,
})
export class LocationsPage implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly items = signal<Location[]>([]);
  readonly loaded = signal(false);
  readonly notice = new Notice();

  readonly modal = signal<'new' | 'edit' | null>(null);
  readonly busy = signal(false);
  readonly name = signal('');
  readonly description = signal('');
  private editing: Location | null = null;

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.notice.dispose());
    this.load();
  }

  openNew(): void {
    this.editing = null;
    this.name.set('');
    this.description.set('');
    this.modal.set('new');
  }

  openEdit(location: Location): void {
    this.editing = location;
    this.name.set(location.name);
    this.description.set(location.description ?? '');
    this.modal.set('edit');
  }

  closeModal(): void {
    this.modal.set(null);
  }

  submit(): void {
    const body = { name: this.name().trim(), description: this.description().trim() || null };
    if (!body.name) {
      return;
    }
    this.busy.set(true);
    const request = this.editing ? this.api.updateLocation(this.editing.id, body) : this.api.createLocation(body);
    request.subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.closeModal();
        this.notice.show(this.editing ? tr('Local "{name}" salvo.', { name: saved.name }) : tr('Local "{name}" criado.', { name: saved.name }));
        this.load();
      },
      // O aviso (ex.: "já existe um local com esse nome") aparece por cima da janela, que continua aberta.
      error: () => this.busy.set(false),
    });
  }

  remove(location: Location): void {
    const tvs =
      location.deviceCount === 0
        ? ''
        : ' ' + tr('As {n} TV(s) deste local continuam funcionando e ficam sem local.', { n: location.deviceCount });
    if (!confirm(tr('Excluir o local "{name}"?', { name: location.name }) + tvs)) {
      return;
    }
    this.api.deleteLocation(location.id).subscribe({
      next: () => {
        this.notice.show(tr('Local "{name}" excluído.', { name: location.name }));
        this.load();
      },
      error: () => undefined,
    });
  }

  private load(): void {
    this.api.locations().subscribe({
      next: (list) => {
        this.items.set(list);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }
}
