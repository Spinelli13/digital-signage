import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { Location } from '../core/models';
import { Notice } from '../core/notice';

@Component({
  selector: 'app-locations',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="head">
      <div>
        <h1>Pontos &amp; Locais</h1>
        <p>Onde estão as suas TVs. Cada TV pertence a um local; use os locais para filtrar e agir em várias TVs de uma vez.</p>
      </div>
      <button class="primary" (click)="openNew()">+ Novo local</button>
    </div>

    @if (notice.text(); as text) {
      <div class="notice" role="status">{{ text }}</div>
    }

    <div class="card">
      @if (items().length === 0) {
        <div class="empty">
          @if (loaded()) {
            Nenhum local ainda. Crie o primeiro (ex.: "Loja Centro") e escolha-o ao ativar cada TV.
          } @else {
            Carregando…
          }
        </div>
      } @else {
        <table>
          <tr><th>Local</th><th>TVs</th><th>Online</th><th></th></tr>
          @for (location of items(); track location.id) {
            <tr>
              <td>
                <b>{{ location.name }}</b>
                @if (location.description) {
                  <br /><small>{{ location.description }}</small>
                }
              </td>
              <td>
                @if (location.deviceCount > 0) {
                  <a routerLink="/dispositivos" [queryParams]="{ local: location.id }">{{ location.deviceCount }}</a>
                } @else {
                  0
                }
              </td>
              <td>
                @if (location.deviceCount > 0) {
                  <span class="dot" [class]="location.onlineCount === location.deviceCount ? 'on' : 'warn'"></span>{{ location.onlineCount }} de {{ location.deviceCount }}
                } @else {
                  <span class="muted">—</span>
                }
              </td>
              <td class="row-actions">
                <button class="secondary small" (click)="openEdit(location)">Editar</button>
                <button class="danger small" (click)="remove(location)">Excluir</button>
              </td>
            </tr>
          }
        </table>
      }
    </div>

    @if (modal() !== null) {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="submit()">
          <h2>{{ modal() === 'new' ? 'Novo local' : 'Editar local' }}</h2>
          <div class="field">
            <label for="loc-name">Nome do local</label>
            <input id="loc-name" name="name" placeholder="Ex.: Loja Centro" required [ngModel]="name()" (ngModelChange)="name.set($event)" />
          </div>
          <div class="field">
            <label for="loc-desc">Descrição (opcional)</label>
            <input id="loc-desc" name="description" placeholder="Ex.: Rua das Flores, 100" [ngModel]="description()" (ngModelChange)="description.set($event)" />
          </div>
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">Cancelar</button>
            <button type="submit" class="primary" [disabled]="busy() || !name().trim()">Salvar</button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .row-actions { white-space: nowrap; text-align: right; }
    .row-actions button { margin-left: 6px; }
  `,
})
export class LocationsPage implements OnInit {
  private readonly api = inject(ApiService);
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
        this.notice.show(this.editing ? `Local "${saved.name}" salvo.` : `Local "${saved.name}" criado.`);
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
        : ` As ${location.deviceCount} TV(s) deste local continuam funcionando e ficam sem local.`;
    if (!confirm(`Excluir o local "${location.name}"?${tvs}`)) {
      return;
    }
    this.api.deleteLocation(location.id).subscribe({
      next: () => {
        this.notice.show(`Local "${location.name}" excluído.`);
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
