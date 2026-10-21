import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { TPipe, tr } from '../core/i18n';
import { hasFilters } from '../core/device-filters';
import { connectionOf, deviceState, timeAgo } from '../core/format';
import {
  BulkAction,
  BulkRequest,
  Connection,
  Device,
  DeviceFilters,
  DeviceGroup,
  Layout,
  Location,
  Playlist,
  ReleaseInfo,
  ScreenOrientation,
} from '../core/models';
import { Notice } from '../core/notice';
import { ORIENTATIONS, orientationBadge, orientationOption } from '../core/orientation';
import { isOutdated } from '../core/versions';
import { SITUATION } from '../shared/connection';
import { IconComponent } from '../shared/icon';
import { EmptyStateComponent, MenuComponent, PageHeaderComponent, PillComponent, SkeletonComponent } from '../shared/ui';

const REFRESH_MS = 10_000;
const SEARCH_DEBOUNCE_MS = 300;

/** Escolha do local no formulário: '' = sem local, 'new' = digitar um local novo, ou o id de um local. */
type LocationChoice = string;

@Component({
  selector: 'app-devices',
  imports: [FormsModule, RouterLink, TPipe, IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent, SkeletonComponent, MenuComponent],
  template: `
    <app-page-header [title]="'Dispositivos' | t" [subtitle]="'Gerencie todas as telas e Players conectados.' | t" icon="monitor">
      @if (auth.can('admin')) {
        <button class="secondary" (click)="updateAll()" [title]="'Pede a todas as TVs que verifiquem agora se há versão nova do Player' | t"><app-icon name="refresh-cw" />{{ 'Atualizar todas as TVs' | t }}</button>
        <button class="primary" (click)="openActivate()"><app-icon name="plus" />{{ 'Adicionar dispositivo' | t }}</button>
      }
    </app-page-header>

    @if (notice.text(); as text) {
      <div class="notice" role="status"><app-icon name="circle-check" [size]="18" /><span>{{ text }}</span></div>
    }

    <div class="card filters">
      <div class="search-wrap">
        <app-icon name="search" [size]="18" />
        <input
          type="search"
          class="search"
          [attr.aria-label]="'Buscar TV' | t"
          [placeholder]="'Buscar por nome ou local…' | t"
          [ngModel]="filters().q ?? ''"
          (ngModelChange)="onSearch($event)"
        />
      </div>
      <div class="chips-row" role="group" [attr.aria-label]="'Situação' | t">
        <button type="button" class="chip-btn" [class.on]="!filters().connection" (click)="setConnection('')">{{ 'Todas' | t }}</button>
        <button type="button" class="chip-btn" [class.on]="filters().connection === 'ONLINE'" (click)="setConnection('ONLINE')"><app-icon name="monitor-check" [size]="15" />{{ 'Online' | t }}</button>
        <button type="button" class="chip-btn" [class.on]="filters().connection === 'OFFLINE'" (click)="setConnection('OFFLINE')"><app-icon name="wifi-off" [size]="15" />{{ 'Offline' | t }}</button>
        <button type="button" class="chip-btn" [class.on]="filters().connection === 'NEVER'" (click)="setConnection('NEVER')"><app-icon name="plug-zap" [size]="15" />{{ 'Nunca conectou' | t }}</button>
        <button type="button" class="chip-btn" [class.on]="filters().connection === 'PENDING'" (click)="setConnection('PENDING')"><app-icon name="hourglass" [size]="15" />{{ 'Aguardando ativação' | t }}</button>
        <button type="button" class="chip-btn" [class.on]="filters().connection === 'DISABLED'" (click)="setConnection('DISABLED')"><app-icon name="monitor-off" [size]="15" />{{ 'Desativado' | t }}</button>
      </div>
      <div class="selects">
        <select [attr.aria-label]="'Local' | t" [ngModel]="locationFilterValue()" (ngModelChange)="setLocationFilter($event)">
          <option value="">{{ 'Todos os locais' | t }}</option>
          <option value="none">{{ 'Sem local' | t }}</option>
          @for (location of locations(); track location.id) {
            <option [value]="location.id">{{ location.name }}</option>
          }
        </select>
        <select [attr.aria-label]="'Grupo' | t" [ngModel]="groupFilterValue()" (ngModelChange)="setGroupFilter($event)">
          <option value="">{{ 'Todos os grupos' | t }}</option>
          @for (group of groups(); track group.id) {
            <option [value]="group.id">{{ group.name }}</option>
          }
        </select>
        @if (filtersActive()) {
          <button class="ghost small" (click)="clearFilters()"><app-icon name="x" [size]="15" />{{ 'Limpar filtros' | t }}</button>
        }
        <span class="count num">{{ '{n} TV(s)' | t: { n: devices().length } }}</span>
      </div>
    </div>

    @if (selectedCount() > 0 && auth.can('content')) {
      <div class="bulk" role="region" [attr.aria-label]="'Ações nas TVs selecionadas' | t">
        <b class="bulk-count"><app-icon name="check" [size]="16" />{{ '{n} selecionada(s)' | t: { n: selectedCount() } }}</b>
        <select [attr.aria-label]="'Playlist das selecionadas' | t" (change)="onBulkPlaylist($event)">
          <option value="">{{ 'Escolher playlist…' | t }}</option>
          <option value="none">{{ 'Nenhuma playlist' | t }}</option>
          @for (playlist of playlists(); track playlist.id) {
            <option [value]="playlist.id">{{ playlist.name }}</option>
          }
        </select>
        @if (layouts().length > 0) {
          <select [attr.aria-label]="'Layout das selecionadas' | t" (change)="onBulkLayout($event)">
            <option value="">{{ 'Aplicar layout…' | t }}</option>
            <option value="none">{{ 'Tela cheia' | t }}</option>
            @for (layout of layouts(); track layout.id) {
              <option [value]="layout.id">{{ layout.name }}</option>
            }
          </select>
        }
        @if (auth.can('admin')) {
          <button class="secondary small" (click)="bulkSimple('DISABLE')"><app-icon name="circle-pause" [size]="15" />{{ 'Desativar' | t }}</button>
          <button class="secondary small" (click)="bulkSimple('ENABLE')"><app-icon name="play" [size]="15" />{{ 'Reativar' | t }}</button>
          <button class="secondary small" (click)="bulkSimple('CHECK_UPDATE')"><app-icon name="refresh-cw" [size]="15" />{{ 'Atualizar app' | t }}</button>
          <select [attr.aria-label]="'Orientação das selecionadas' | t" (change)="onBulkOrientation($event)">
            <option value="">{{ 'Orientação…' | t }}</option>
            @for (option of orientations; track option.value) {
              <option [value]="option.value">{{ option.label | t }}</option>
            }
          </select>
          <select [attr.aria-label]="'Mover as selecionadas para um local' | t" (change)="onBulkLocation($event)">
            <option value="">{{ 'Mover para local…' | t }}</option>
            <option value="none">{{ 'Sem local' | t }}</option>
            @for (location of locations(); track location.id) {
              <option [value]="location.id">{{ location.name }}</option>
            }
          </select>
          @if (groups().length > 0) {
            <select [attr.aria-label]="'Adicionar as selecionadas a um grupo' | t" (change)="onBulkGroup($event, true)">
              <option value="">{{ 'Adicionar ao grupo…' | t }}</option>
              @for (group of groups(); track group.id) {
                <option [value]="group.id">{{ group.name }}</option>
              }
            </select>
            <select [attr.aria-label]="'Tirar as selecionadas de um grupo' | t" (change)="onBulkGroup($event, false)">
              <option value="">{{ 'Tirar do grupo…' | t }}</option>
              @for (group of groups(); track group.id) {
                <option [value]="group.id">{{ group.name }}</option>
              }
            </select>
          }
        }
        <button class="ghost small" (click)="clearSelection()"><app-icon name="x" [size]="15" />{{ 'Limpar seleção' | t }}</button>
      </div>
    }

    <div class="card list">
      @if (devices().length === 0) {
        @if (!loaded()) {
          <app-skeleton [rows]="5" />
        } @else if (filtersActive()) {
          <app-empty-state icon="search" [title]="'Nenhuma TV com esses filtros.' | t" [text]="'Tente outra busca ou limpe os filtros.' | t">
            <button class="secondary" (click)="clearFilters()"><app-icon name="x" [size]="16" />{{ 'Limpar filtros' | t }}</button>
          </app-empty-state>
        } @else {
          <app-empty-state icon="monitor-play" [title]="'Nenhum dispositivo ainda' | t"
            [text]="'Abra o app Player na TV: ele mostra um código de 6 caracteres. Depois clique em Adicionar dispositivo e digite esse código.' | t">
            @if (auth.can('admin')) {
              <button class="primary" (click)="openActivate()"><app-icon name="plus" />{{ 'Adicionar dispositivo' | t }}</button>
            }
          </app-empty-state>
        }
      } @else {
        <table class="stack">
          <tr>
            <th class="check-col">
              <input type="checkbox" [attr.aria-label]="'Selecionar todas as TVs da lista' | t" [checked]="allSelected()" (change)="toggleAll()" />
            </th>
            <th>{{ 'Dispositivo' | t }}</th><th>{{ 'Status' | t }}</th><th>{{ 'Último contato' | t }}</th><th>{{ 'Conteúdo' | t }}</th><th></th>
          </tr>
          @for (device of devices(); track device.id) {
            <tr [class.selected]="isSelected(device.id)">
              <td class="check-col">
                <input type="checkbox" [attr.aria-label]="'Selecionar {name}' | t: { name: device.name }" [checked]="isSelected(device.id)" (change)="toggle(device.id)" />
              </td>
              <td class="first">
                <div class="tv-name">
                  <span class="tv-icon" [class]="sit(device).tone"><app-icon [name]="sit(device).icon" [size]="18" /></span>
                  <span class="tv-text">
                    <b>{{ device.name }}</b>
                    <small>{{ device.location || ('Sem local' | t) }}</small>
                  </span>
                </div>
                <div class="tv-tags">
                  @for (group of groupsOf(device); track group.id) {
                    <app-pill icon="layers">{{ group.name }}</app-pill>
                  }
                  @if (device.layoutName) {
                    <app-pill tone="brand" icon="layout-template" [title]="'Layout da tela desta TV' | t">{{ device.layoutName }}</app-pill>
                  }
                  @if (orientationBadge(device.orientation); as badge) {
                    <app-pill tone="info" icon="smartphone" [title]="'Orientação da tela' | t">{{ badge }}</app-pill>
                  }
                </div>
              </td>
              <td [attr.data-label]="'Status' | t"><app-pill [tone]="sit(device).tone" [icon]="sit(device).icon">{{ state(device).label }}</app-pill></td>
              <td [attr.data-label]="'Último contato' | t">
                <span class="contact">
                  <span class="num">{{ ago(device.lastSeenAt) }}</span>
                  @if (device.playerVersion) {
                    <small class="mono-v">{{ 'v' + device.playerVersion }}</small>
                  }
                  @if (outdated(device)) {
                    <app-pill tone="warn" icon="download" [title]="'Há uma versão mais nova do Player; ele se atualiza sozinho (alguém precisa confirmar na TV).' | t">{{ 'v{version} disponível' | t: { version: latest()?.versionName } }}</app-pill>
                  }
                </span>
              </td>
              <td [attr.data-label]="'Conteúdo' | t">
                @if (device.status === 'ACTIVE') {
                  <span class="plist">
                    <select [ngModel]="device.currentPlaylistId" [disabled]="!auth.can('content')" [attr.aria-label]="'Playlist' | t" (ngModelChange)="assignPlaylist(device, $event)">
                      <option [ngValue]="null">{{ 'Nenhuma playlist' | t }}</option>
                      @for (playlist of playlists(); track playlist.id) {
                        <option [ngValue]="playlist.id">{{ playlist.name }}</option>
                      }
                    </select>
                    @if (device.campaignName) {
                      <app-pill tone="brand" icon="megaphone" [title]="'Uma campanha vale agora para esta TV e tem prioridade sobre a playlist dela.' | t">{{ 'Agora: campanha {name}' | t: { name: device.campaignName } }}</app-pill>
                    }
                    @if (device.currentMediaName) {
                      <small class="onscreen" [title]="'A mídia que o Player disse estar exibindo no último contato.' | t"><app-icon name="play" [size]="12" />{{ 'Na tela: {name}' | t: { name: device.currentMediaName } }}</small>
                    }
                    @if (syncing(device)) {
                      <small class="syncing"><app-icon name="refresh-cw" [size]="12" />{{ 'sincronizando…' | t }}</small>
                    }
                  </span>
                } @else {
                  <span class="muted">—</span>
                }
              </td>
              <td class="row-actions">
                <a class="icon-btn bordered" [routerLink]="['/dispositivos', device.id, 'historico']" [title]="'Quando conectou, caiu, mudou de conteúdo e o que o Player relatou' | t">
                  <app-icon name="rotate-ccw-clock" [size]="18" /><span class="sr-only">{{ 'Histórico' | t }}</span>
                </a>
                @if (auth.can('content')) {
                  <button class="secondary small edit" (click)="openEdit(device)" [attr.aria-label]="'Editar' | t"><app-icon name="pencil" [size]="15" /><span class="lbl">{{ 'Editar' | t }}</span></button>
                }
                @if (auth.can('admin')) {
                  <app-menu [label]="'Mais ações' | t">
                    @if (device.status === 'ACTIVE') {
                      <button type="button" class="menu-item" (click)="checkUpdate(device)" [title]="'Pede que esta TV verifique agora se há versão nova do Player' | t"><app-icon name="refresh-cw" />{{ 'Atualizar app' | t }}</button>
                      <button type="button" class="menu-item" (click)="setStatus(device, 'disabled')"><app-icon name="circle-pause" />{{ 'Desativar' | t }}</button>
                    } @else if (device.status === 'DISABLED') {
                      <button type="button" class="menu-item" (click)="setStatus(device, 'active')"><app-icon name="play" />{{ 'Reativar' | t }}</button>
                    }
                    <div class="menu-sep"></div>
                    <button type="button" class="menu-item danger" (click)="remove(device)"><app-icon name="trash" />{{ 'Excluir' | t }}</button>
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
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="submitModal()">
          @if (modal() === 'activate') {
            <h2>{{ 'Adicionar dispositivo' | t }}</h2>
            <p class="label">{{ 'Vincule um novo Player usando o código exibido na TV.' | t }}</p>
            <div class="field">
              <label for="code">{{ 'Código de ativação' | t }}</label>
              <input id="code" name="code" class="mono code-input" maxlength="6" [placeholder]="'Ex.: 8F32A9' | t" required
                [ngModel]="code()" (ngModelChange)="code.set($event.toUpperCase())" />
            </div>
          } @else {
            <h2>{{ 'Editar dispositivo' | t }}</h2>
          }
          @if (auth.can('admin')) {
          <div class="field">
            <label for="name">{{ 'Nome do dispositivo' | t }}</label>
            <input id="name" name="name" [placeholder]="'Ex.: TV Recepção' | t" required
              [ngModel]="name()" (ngModelChange)="name.set($event)" />
          </div>
          <div class="field">
            <label for="location">{{ 'Local' | t }}</label>
            <select id="location" name="location" [ngModel]="locationChoice()" (ngModelChange)="locationChoice.set($event)">
              <option value="">{{ 'Sem local' | t }}</option>
              @for (location of locations(); track location.id) {
                <option [value]="location.id">{{ location.name }}</option>
              }
              <option value="new">{{ '+ Novo local…' | t }}</option>
            </select>
            @if (locationChoice() === 'new') {
              <input id="new-location" name="newLocation" class="below" [placeholder]="'Nome do novo local (ex.: Loja Centro)' | t"
                [ngModel]="newLocation()" (ngModelChange)="newLocation.set($event)" />
            }
          </div>
          } @else {
            <p class="label">{{ '{name} — como Editor, você escolhe o layout da tela. Nome, local e grupos são do Administrador.' | t: { name: name() } }}</p>
          }
          @if (modal() === 'edit' && layouts().length > 0) {
            <div class="field">
              <label for="layout">{{ 'Layout da tela' | t }}</label>
              <select id="layout" name="layout" [ngModel]="layoutChoice()" (ngModelChange)="layoutChoice.set($event)">
                <option value="">{{ 'Tela cheia (um conteúdo só)' | t }}</option>
                @for (layout of layouts(); track layout.id) {
                  <option [value]="layout.id">{{ layout.name }}</option>
                }
              </select>
            </div>
          }
          @if (modal() === 'edit' && auth.can('admin')) {
            <div class="field">
              <label for="orientation">{{ 'Como a TV foi instalada' | t }}</label>
              <div class="orient-pick">
                <select id="orientation" name="orientation" [ngModel]="orientationChoice()" (ngModelChange)="orientationChoice.set($event)">
                  @for (option of orientations; track option.value) {
                    <option [value]="option.value">{{ option.label | t }}</option>
                  }
                </select>
                <div class="tv-draw" role="img" [attr.aria-label]="'Desenho de como a TV está na parede' | t">
                  <div class="tv-box" [style.transform]="'rotate(' + orientationTurn() + 'deg)'">
                    <i class="tv-top" [title]="'topo da TV' | t"></i>
                    <span [style.transform]="'rotate(' + (360 - orientationTurn()) + 'deg)'">{{ sample }}</span>
                  </div>
                </div>
              </div>
              <p class="label">{{ orientationHint() | t }}</p>
              <p class="label">{{ 'Escolha como a TV está na parede. O Player gira o conteúdo para ele ficar de pé para quem olha, mesmo que a caixa de TV mande o vídeo sempre deitado. Em TV vertical, o layout usa a versão em pé.' | t }}</p>
            </div>
          }
          @if (groups().length > 0 && auth.can('admin')) {
            <div class="field">
              <label>{{ 'Grupos' | t }}</label>
              <div class="chips">
                @for (group of groups(); track group.id) {
                  <label class="check">
                    <input type="checkbox" [checked]="modalGroups().has(group.id)" (change)="toggleModalGroup(group.id)" />
                    {{ group.name }}
                  </label>
                }
              </div>
            </div>
          }
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">{{ 'Cancelar' | t }}</button>
            <button type="submit" class="primary" [disabled]="busy()">
              {{ (modal() === 'activate' ? 'Ativar dispositivo' : 'Salvar') | t }}
            </button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    select { min-width: 170px; }
    .filters { display: grid; gap: 14px; margin-bottom: 14px; padding: 16px; }
    .search-wrap { position: relative; }
    .search-wrap > app-icon { position: absolute; left: 14px; top: 50%; transform: translateY(-50%); color: var(--muted); pointer-events: none; }
    .search { padding-left: 42px; height: 44px; }
    .chips-row { display: flex; gap: 8px; flex-wrap: wrap; }
    .chip-btn {
      display: inline-flex; align-items: center; gap: 6px; height: 34px; padding: 0 13px; border-radius: 999px; border: 1px solid var(--line-strong);
      background: var(--panel); color: var(--text-2); font-size: 13.5px; font-weight: 600; transition: all 0.15s var(--ease);
    }
    .chip-btn:hover { background: var(--panel-2); border-color: var(--silver); }
    .chip-btn.on { background: var(--primary-soft); color: var(--primary); border-color: var(--primary); box-shadow: inset 0 0 0 1px var(--primary); }
    :host-context(:root[data-theme='dark']) .chip-btn.on { color: var(--link); }
    .selects { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
    .selects select { width: auto; min-width: 170px; }
    .count { margin-left: auto; color: var(--muted); font-size: 13px; }

    .bulk {
      position: sticky; top: 10px; z-index: 20; display: flex; gap: 8px; flex-wrap: wrap; align-items: center; margin-bottom: 14px; padding: 10px 14px;
      background: var(--primary-soft); border: 1px solid var(--primary-line); border-radius: var(--r-lg); box-shadow: var(--sh-2);
    }
    .bulk-count { display: inline-flex; align-items: center; gap: 6px; margin-right: 6px; color: var(--primary); }
    :host-context(:root[data-theme='dark']) .bulk-count { color: var(--link); }
    .bulk select { min-width: 150px; width: auto; height: 36px; padding-top: 6px; padding-bottom: 6px; font-size: 13.5px; }

    .list { padding: 6px 10px 10px; }
    .check-col { width: 40px; }
    tr.selected td { background: var(--primary-soft) !important; }
    .tv-name { display: flex; align-items: center; gap: 12px; }
    .tv-icon { flex: none; display: grid; place-items: center; width: 38px; height: 38px; border-radius: 11px; background: var(--panel-3); color: var(--text-2); }
    .tv-icon.ok { background: var(--ok-bg); color: var(--ok-fg); }
    .tv-icon.err { background: var(--err-bg); color: var(--err-fg); }
    .tv-icon.warn { background: var(--warn-bg); color: var(--warn-fg); }
    .tv-icon.info { background: var(--info-bg); color: var(--info-fg); }
    .tv-text { display: flex; flex-direction: column; min-width: 0; line-height: 1.3; }
    .orient-pick { display: flex; align-items: center; gap: 14px; }
    .orient-pick select { flex: 1; min-width: 0; }
    .tv-draw { flex: none; width: 76px; height: 76px; display: grid; place-items: center; }
    .tv-box { position: relative; width: 62px; height: 36px; border: 2px solid var(--primary); border-radius: 5px; background: var(--panel-2); display: grid; place-items: center; transition: transform 0.25s ease; }
    .tv-box span { font-weight: 700; font-size: 14px; color: var(--text); transition: transform 0.25s ease; }
    .tv-top { position: absolute; left: 50%; top: -5px; width: 18px; height: 4px; margin-left: -9px; border-radius: 2px; background: var(--primary); }
    .tv-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; padding-left: 50px; }
    .tv-tags:empty { display: none; }
    .tv-tags app-pill { height: 22px; font-size: 11.5px; max-width: 190px; }
    .contact { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; }
    .contact app-pill { max-width: 150px; height: 22px; font-size: 11.5px; padding: 0 8px 0 6px; }
    /* em telas médias a ação Editar vira só o lápis (o rótulo continua para o leitor de tela) para a tabela caber sem rolar */
    @media (min-width: 721px) and (max-width: 1500px) { .edit .lbl { display: none; } .edit { padding-left: 10px; padding-right: 10px; } }
    .plist app-pill { max-width: 100%; }
    td { max-width: 280px; }
    td.check-col, td.row-actions { max-width: none; }
    .mono-v { font-family: ui-monospace, 'Cascadia Mono', Consolas, monospace; font-size: 12px; }
    .plist { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; width: 210px; max-width: 100%; }
    .plist select { width: 100%; min-width: 0; }
    .plist select { height: 38px; padding-top: 6px; padding-bottom: 6px; font-size: 14px; }
    .onscreen, .syncing { display: inline-flex; align-items: center; gap: 5px; color: var(--muted); }
    .syncing { color: var(--info-fg); }
    .row-actions { white-space: nowrap; text-align: right; }
    .row-actions > * { vertical-align: middle; margin-left: 6px; }
    .chips { display: flex; gap: 6px 14px; flex-wrap: wrap; }
    .below { margin-top: 8px; }
    .code-input { font-size: 22px; letter-spacing: 6px; text-align: center; height: 54px; text-transform: uppercase; }

    @media (max-width: 720px) {
      .filters { padding: 12px; gap: 12px; }
      .chips-row { flex-wrap: nowrap; overflow-x: auto; margin: 0 -12px; padding: 0 12px 4px; scrollbar-width: none; }
      .chips-row::-webkit-scrollbar { display: none; }
      .chip-btn { flex: none; height: 40px; }
      .selects { flex-direction: column; align-items: stretch; }
      .selects select { width: 100%; }
      .count { margin-left: 0; }
      .bulk { flex-direction: column; align-items: stretch; top: calc(var(--appbar-h) + env(safe-area-inset-top) + 8px); }
      .bulk select { width: 100%; height: 44px; }
      .list { padding: 10px; background: transparent; border: 0; box-shadow: none; }
      td { max-width: none; }
      .plist { width: auto; flex: 1 1 0; min-width: 0; align-items: flex-end; }
      .plist select { width: 100%; }
      .contact { align-items: flex-end; }
      table.stack td.first { padding-right: 40px; }
      .tv-tags { padding-left: 0; }
      table.stack tr.selected { border-color: var(--primary); }
      .row-actions { text-align: left; }
      .row-actions > * { margin-left: 0; }
    }
  `,
})
export class DevicesPage implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);

  readonly devices = signal<Device[]>([]);
  readonly playlists = signal<Playlist[]>([]);
  readonly locations = signal<Location[]>([]);
  readonly groups = signal<DeviceGroup[]>([]);
  readonly layouts = signal<Layout[]>([]);
  /** Versão mais recente do Player publicada, para destacar TVs desatualizadas. */
  readonly latest = signal<ReleaseInfo | null>(null);
  readonly loaded = signal(false);
  readonly notice = new Notice();

  readonly filters = signal<DeviceFilters>({});
  readonly filtersActive = computed(() => hasFilters(this.filters()));
  readonly locationFilterValue = computed(() => String(this.filters().location ?? ''));
  readonly groupFilterValue = computed(() => String(this.filters().groupId ?? ''));
  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  readonly selected = signal<Set<number>>(new Set());
  readonly selectedCount = computed(() => this.selected().size);
  readonly allSelected = computed(() => this.devices().length > 0 && this.devices().every((d) => this.selected().has(d.id)));

  readonly modal = signal<'activate' | 'edit' | null>(null);
  readonly busy = signal(false);
  readonly code = signal('');
  readonly name = signal('');
  readonly locationChoice = signal<LocationChoice>('');
  readonly newLocation = signal('');
  /** Layout escolhido no formulário: '' = tela cheia, ou o id de um layout. */
  readonly layoutChoice = signal('');
  readonly orientations = ORIENTATIONS;
  /** O texto do desenho da TV (só mostra se o conteúdo fica de pé). */
  readonly sample = 'Aa';
  readonly orientationBadge = orientationBadge;
  readonly orientationChoice = signal<ScreenOrientation>('AUTO');
  readonly orientationTurn = computed(() => orientationOption(this.orientationChoice()).turn);
  readonly orientationHint = computed(() => orientationOption(this.orientationChoice()).hint);
  readonly modalGroups = signal<Set<number>>(new Set());
  private editing: Device | null = null;

  readonly state = deviceState;
  readonly sit = (device: Device) => SITUATION[connectionOf(device)];
  readonly ago = (iso: string | null) => timeAgo(iso);

  outdated(device: Device): boolean {
    return isOutdated(device.playerVersion, this.latest()?.versionName);
  }

  /** A TV ainda não sincronizou o conteúdo que deveria estar exibindo. Servidor antigo (sem a versão desejada): compara com a playlist. */
  syncing(device: Device): boolean {
    if (device.status !== 'ACTIVE') {
      return false;
    }
    if (device.desiredConfigVersion !== undefined) {
      return device.configVersion !== device.desiredConfigVersion;
    }
    return device.currentPlaylistId !== null && device.configVersion < this.versionOf(device.effectivePlaylistId ?? device.currentPlaylistId);
  }

  groupsOf(device: Device): DeviceGroup[] {
    return this.groups().filter((group) => device.groupIds.includes(group.id));
  }

  ngOnInit(): void {
    this.readQueryParams();
    // Em silêncio: sem o site de downloads só deixamos de destacar TVs desatualizadas.
    this.api.releases(true).subscribe({ next: (list) => this.latest.set(list[0] ?? null), error: () => undefined });
    this.refresh();
    const timer = setInterval(() => this.refresh(), REFRESH_MS);
    this.destroyRef.onDestroy(() => {
      clearInterval(timer);
      clearTimeout(this.searchTimer);
      this.notice.dispose();
    });
  }

  /** Links do dashboard, dos locais e dos grupos chegam aqui já filtrados (?local=3, ?grupo=2, ?situacao=OFFLINE). */
  private readQueryParams(): void {
    const params = this.route.snapshot.queryParamMap;
    const filters: DeviceFilters = {};
    const local = params.get('local');
    if (local === 'none') {
      filters.location = 'none';
    } else if (local && !Number.isNaN(Number(local))) {
      filters.location = Number(local);
    }
    const grupo = params.get('grupo');
    if (grupo && !Number.isNaN(Number(grupo))) {
      filters.groupId = Number(grupo);
    }
    const situacao = params.get('situacao')?.toUpperCase();
    if (situacao && ['ONLINE', 'OFFLINE', 'NEVER', 'PENDING', 'DISABLED'].includes(situacao)) {
      filters.connection = situacao as Connection;
    }
    this.filters.set(filters);
  }

  versionOf(playlistId: number): number {
    return this.playlists().find((p) => p.id === playlistId)?.configVersion ?? 0;
  }

  // ---------- filtros ----------

  onSearch(text: string): void {
    this.filters.update((f) => ({ ...f, q: text }));
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.refresh(), SEARCH_DEBOUNCE_MS);
  }

  setConnection(value: Connection | ''): void {
    this.filters.update((f) => ({ ...f, connection: value }));
    this.refresh();
  }

  setLocationFilter(value: string): void {
    const location = value === '' ? null : value === 'none' ? 'none' : Number(value);
    this.filters.update((f) => ({ ...f, location }));
    this.refresh();
  }

  setGroupFilter(value: string): void {
    this.filters.update((f) => ({ ...f, groupId: value === '' ? null : Number(value) }));
    this.refresh();
  }

  clearFilters(): void {
    this.filters.set({});
    this.refresh();
  }

  // ---------- seleção ----------

  isSelected(id: number): boolean {
    return this.selected().has(id);
  }

  toggle(id: number): void {
    const next = new Set(this.selected());
    if (!next.delete(id)) {
      next.add(id);
    }
    this.selected.set(next);
  }

  /** Marca todas as TVs da lista atual (respeitando os filtros); se já estão todas marcadas, desmarca. */
  toggleAll(): void {
    this.selected.set(this.allSelected() ? new Set() : new Set(this.devices().map((d) => d.id)));
  }

  clearSelection(): void {
    this.selected.set(new Set());
  }

  // ---------- ações em massa ----------

  onBulkPlaylist(event: Event): void {
    const value = this.takeValue(event);
    if (!value) {
      return;
    }
    if (value === 'none') {
      this.runBulk({ action: 'REMOVE_PLAYLIST' });
    } else {
      this.runBulk({ action: 'ASSIGN_PLAYLIST', playlistId: Number(value) });
    }
  }

  onBulkLocation(event: Event): void {
    const value = this.takeValue(event);
    if (!value) {
      return;
    }
    if (value === 'none') {
      this.runBulk({ action: 'CLEAR_LOCATION' });
    } else {
      this.runBulk({ action: 'SET_LOCATION', locationId: Number(value) });
    }
  }

  onBulkLayout(event: Event): void {
    const value = this.takeValue(event);
    if (!value) {
      return;
    }
    if (value === 'none') {
      this.runBulk({ action: 'REMOVE_LAYOUT' });
    } else {
      this.runBulk({ action: 'ASSIGN_LAYOUT', layoutId: Number(value) });
    }
  }

  onBulkOrientation(event: Event): void {
    const value = this.takeValue(event);
    if (!value) {
      return;
    }
    this.runBulk({ action: 'SET_ORIENTATION', orientation: value as ScreenOrientation });
  }

  onBulkGroup(event: Event, add: boolean): void {
    const value = this.takeValue(event);
    if (!value) {
      return;
    }
    this.runBulk({ action: add ? 'ADD_TO_GROUP' : 'REMOVE_FROM_GROUP', groupId: Number(value) });
  }

  bulkSimple(action: 'DISABLE' | 'ENABLE' | 'CHECK_UPDATE'): void {
    if (action === 'DISABLE' && !confirm(tr('Desativar {n} TV(s)? Elas param de exibir até serem reativadas.', { n: this.selectedCount() }))) {
      return;
    }
    this.runBulk({ action });
  }

  private takeValue(event: Event): string {
    const select = event.target as HTMLSelectElement;
    const value = select.value;
    select.value = ''; // volta para o título ("Escolher…") depois de usar
    return value;
  }

  private runBulk(request: Omit<BulkRequest, 'deviceIds'>): void {
    const ids = [...this.selected()];
    if (ids.length === 0) {
      return;
    }
    this.api.bulk({ ...request, deviceIds: ids }).subscribe({
      next: ({ affected }) => {
        this.notice.show(this.bulkMessage(request.action, affected, ids.length));
        this.refresh();
      },
      error: () => undefined,
    });
  }

  private bulkMessage(action: BulkAction, affected: number, selected: number): string {
    if (affected === 0) {
      return tr('Nenhuma das TVs selecionadas estava no estado certo para esta ação.');
    }
    const partial =
      affected < selected ? ' ' + tr('({n} já estavam no estado pedido ou não se aplicam)', { n: selected - affected }) : '';
    switch (action) {
      case 'ASSIGN_PLAYLIST':
        return tr('Playlist aplicada a {n} TV(s). Elas atualizam em até 1 minuto.', { n: affected });
      case 'REMOVE_PLAYLIST':
        return tr('Playlist removida de {n} TV(s).', { n: affected });
      case 'ENABLE':
        return tr('{n} TV(s) reativada(s){partial}.', { n: affected, partial });
      case 'DISABLE':
        return tr('{n} TV(s) desativada(s){partial}.', { n: affected, partial });
      case 'CHECK_UPDATE':
        return tr('Pedido enviado a {n} TV(s){partial}. Se houver versão nova, alguém precisa confirmar em cada TV.', { n: affected, partial });
      case 'SET_LOCATION':
        return tr('{n} TV(s) movida(s) de local.', { n: affected });
      case 'CLEAR_LOCATION':
        return tr('{n} TV(s) agora sem local.', { n: affected });
      case 'ADD_TO_GROUP':
        return tr('{n} TV(s) adicionada(s) ao grupo.', { n: affected });
      case 'REMOVE_FROM_GROUP':
        return tr('{n} TV(s) retirada(s) do grupo.', { n: affected });
      case 'ASSIGN_LAYOUT':
        return tr('Layout aplicado a {n} TV(s). Elas atualizam em até 1 minuto.', { n: affected });
      case 'REMOVE_LAYOUT':
        return tr('{n} TV(s) voltaram para a tela cheia.', { n: affected });
      case 'SET_ORIENTATION':
        return tr('Orientação aplicada a {n} TV(s). Elas atualizam em até 1 minuto.', { n: affected });
    }
  }

  // ---------- janela de ativar/editar ----------

  openActivate(): void {
    this.code.set('');
    this.name.set('');
    this.locationChoice.set('');
    this.newLocation.set('');
    this.modalGroups.set(new Set());
    this.editing = null;
    this.modal.set('activate');
  }

  openEdit(device: Device): void {
    this.name.set(device.name);
    this.locationChoice.set(device.locationId === null ? '' : String(device.locationId));
    this.newLocation.set('');
    this.modalGroups.set(new Set(device.groupIds));
    this.layoutChoice.set(device.layoutId == null ? '' : String(device.layoutId));
    this.orientationChoice.set(device.orientation ?? 'AUTO');
    this.editing = device;
    this.modal.set('edit');
  }

  closeModal(): void {
    this.modal.set(null);
  }

  toggleModalGroup(id: number): void {
    const next = new Set(this.modalGroups());
    if (!next.delete(id)) {
      next.add(id);
    }
    this.modalGroups.set(next);
  }

  submitModal(): void {
    this.busy.set(true);
    const choice = this.locationChoice();
    const typed = this.newLocation().trim();
    const groupIds = [...this.modalGroups()];

    // Local escolhido na lista = por id; digitado = por nome (o servidor cria se ainda não existir).
    // Ao editar, "sem local" precisa ser dito (location vazio); ao ativar, basta omitir.
    const noLocation = choice === '' || (choice === 'new' && !typed);
    const locationFields = noLocation ? { location: '' } : choice === 'new' ? { location: typed } : { locationId: Number(choice) };

    const request =
      this.modal() === 'activate'
        ? this.api.activateDevice({
            activationCode: this.code().trim(),
            name: this.name().trim(),
            groupIds,
            ...(noLocation ? {} : locationFields),
          })
        : this.api.updateDevice(this.editing!.id, {
            name: this.name().trim(),
            // O Editor só escolhe o layout: nome, local e grupos seriam recusados pelo servidor (403).
            ...(this.auth.can('admin') ? { groupIds, ...locationFields } : {}),
            // A orientação só vai quando mudou (o servidor só deixa o Administrador mexer nela).
            ...(this.auth.can('admin') && this.orientationChoice() !== (this.editing!.orientation ?? 'AUTO') ? { orientation: this.orientationChoice() } : {}),
            ...(this.layoutChoice() === '' ? {} : { layoutId: Number(this.layoutChoice()) }),
          });

    request.subscribe({
      next: () => {
        // "Tela cheia" precisa de uma chamada própria: layoutId ausente no PUT significa "não mudar".
        const leavingLayout = this.modal() === 'edit' && this.layoutChoice() === '' && this.editing?.layoutId != null;
        if (leavingLayout) {
          this.api.removeDeviceLayout(this.editing!.id).subscribe({ next: () => this.finishModal(), error: () => this.finishModal() });
        } else {
          this.finishModal();
        }
      },
      // O aviso aparece por cima do formulário (interceptor de erros); o formulário continua aberto.
      error: () => this.busy.set(false),
    });
  }

  private finishModal(): void {
    this.busy.set(false);
    this.closeModal();
    this.refresh();
  }

  // ---------- ações de uma TV ----------

  assignPlaylist(device: Device, playlistId: number | null): void {
    if (playlistId === device.currentPlaylistId) {
      return;
    }
    const request =
      playlistId === null
        ? this.api.removeDevicePlaylist(device.id)
        : this.api.updateDevice(device.id, { name: device.name, currentPlaylistId: playlistId });
    request.subscribe({
      next: () => this.refresh(),
      error: () => this.refresh(),
    });
  }

  setStatus(device: Device, status: 'active' | 'disabled'): void {
    this.api.updateDevice(device.id, { name: device.name, status }).subscribe({
      next: () => this.refresh(),
      error: () => undefined,
    });
  }

  checkUpdate(device: Device): void {
    this.api.requestUpdateCheck(device.id).subscribe({
      next: () =>
        this.notice.show(
          tr('Pedido enviado a "{name}". Ela verifica em até 30 segundos; se houver versão nova, alguém precisa confirmar na TV.', { name: device.name }),
        ),
      error: () => undefined,
    });
  }

  updateAll(): void {
    this.api.requestUpdateCheckAll().subscribe({
      next: ({ requested }) =>
        this.notice.show(
          requested === 0
            ? tr('Nenhuma TV ativa para avisar.')
            : tr('Pedido enviado a {n} TV(s). Elas verificam em até 30 segundos; se houver versão nova, alguém precisa confirmar em cada TV.', {
                n: requested,
              }),
        ),
      error: () => undefined,
    });
  }

  remove(device: Device): void {
    if (!confirm(tr('Excluir "{name}"? A TV volta a pedir um novo código de ativação.', { name: device.name }))) {
      return;
    }
    this.api.deleteDevice(device.id).subscribe({
      next: () => this.refresh(),
      error: () => undefined,
    });
  }

  private refresh(): void {
    forkJoin({
      devices: this.api.devices(this.filters()),
      playlists: this.api.playlists(),
      locations: this.api.locations(),
      groups: this.api.groups(),
      layouts: this.api.layouts(),
    }).subscribe({
      next: ({ devices, playlists, locations, groups, layouts }) => {
        this.devices.set(devices);
        this.playlists.set(playlists);
        this.locations.set(locations);
        this.groups.set(groups);
        this.layouts.set(layouts);
        this.loaded.set(true);
        // TVs que saíram da lista (filtro novo, exclusão) deixam de estar selecionadas
        const visible = new Set(devices.map((d) => d.id));
        const kept = [...this.selected()].filter((id) => visible.has(id));
        if (kept.length !== this.selected().size) {
          this.selected.set(new Set(kept));
        }
      },
      error: () => this.loaded.set(true),
    });
  }
}
