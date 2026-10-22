import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { TPipe, tr } from '../core/i18n';
import { Device, Location, PairingInfo, TenantSummary } from '../core/models';
import { IconComponent } from '../shared/icon';
import { PageHeaderComponent } from '../shared/ui';

type Step = 'loading' | 'client' | 'ready' | 'invalid' | 'forbidden' | 'done';

/**
 * A página que o QR Code da TV abre no celular de quem instala: confere o código, pergunta (só se for preciso) de qual cliente
 * é a TV, o local e o nome, e ativa. O nome já vem sugerido, então dá para parear sem digitar nada.
 */
@Component({
  selector: 'app-pairing',
  imports: [FormsModule, RouterLink, TPipe, IconComponent, PageHeaderComponent],
  template: `
    <app-page-header [title]="'Parear TV' | t" [subtitle]="'Vincule a TV que mostra este QR Code ao cliente.' | t" icon="monitor" />

    <div class="card box">
      @switch (step()) {
        @case ('loading') {
          <p class="label">{{ 'Conferindo o código da TV…' | t }}</p>
        }
        @case ('client') {
          <h2>{{ 'De qual cliente é esta TV?' | t }}</h2>
          <p class="label">{{ 'Você é da plataforma: escolha o cliente e a TV é vinculada a ele.' | t }}</p>
          <ul class="clients" data-testid="clients">
            @for (tenant of tenants(); track tenant.id) {
              <li><button type="button" class="pick" (click)="chooseClient(tenant)"><b>{{ tenant.name }}</b><small>{{ tenant.plan.name }}</small></button></li>
            }
          </ul>
        }
        @case ('ready') {
          <div class="found" role="status">
            <app-icon name="circle-check" [size]="20" />
            <div>
              <b>{{ 'TV encontrada' | t }}</b>
              <small class="mono">{{ code() }}@if (info()?.playerVersion; as version) { · {{ 'Player' | t }} {{ version }} }</small>
            </div>
          </div>
          @if (auth.isVisiting()) {
            <p class="label"><app-icon name="building" [size]="14" /> {{ 'Cliente: {name}' | t: { name: auth.me()?.tenant?.name ?? '' } }}</p>
          }
          <form (ngSubmit)="activate()">
            <div class="field">
              <label for="location">{{ 'Local' | t }}</label>
              <select id="location" name="location" [ngModel]="locationChoice()" (ngModelChange)="chooseLocation($event)">
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
            <div class="field">
              <label for="name">{{ 'Nome da TV' | t }}</label>
              <input id="name" name="name" required [ngModel]="name()" (ngModelChange)="renamed($event)" />
              <p class="label">{{ 'Já vem sugerido: é só tocar em Ativar. Dá para mudar depois.' | t }}</p>
            </div>
            <button type="submit" class="primary wide" [disabled]="busy() || !name().trim()"><app-icon name="check" />{{ 'Ativar TV' | t }}</button>
          </form>
        }
        @case ('done') {
          <div class="found done" role="status">
            <app-icon name="circle-check" [size]="22" />
            <div>
              <b>{{ 'TV ativada' | t }}</b>
              <small>{{ '{name} já está vinculada e começa a receber o conteúdo em instantes.' | t: { name: activated()?.name ?? '' } }}</small>
            </div>
          </div>
          <div class="actions">
            <a class="primary" routerLink="/dispositivos"><app-icon name="monitor" />{{ 'Ver dispositivos' | t }}</a>
          </div>
        }
        @case ('forbidden') {
          <h2>{{ 'Só o Administrador pode parear TVs' | t }}</h2>
          <p class="label">{{ 'Entre com uma conta de Administrador do cliente e abra o QR Code de novo.' | t }}</p>
        }
        @default {
          <h2>{{ 'Código inválido ou expirado' | t }}</h2>
          <p class="label">{{ 'A TV mostra um código novo quando reinicia. Escaneie o QR Code atual ou digite o código em Dispositivos > Adicionar dispositivo.' | t }}</p>
          <div class="actions">
            <a class="secondary" routerLink="/dispositivos"><app-icon name="monitor" />{{ 'Ir para Dispositivos' | t }}</a>
          </div>
        }
      }
    </div>
  `,
  styles: `
    .box { max-width: 520px; display: grid; gap: 14px; }
    h2 { margin: 0; font-size: 20px; }
    .label { margin: 0; color: var(--muted); }
    .clients { list-style: none; margin: 0; padding: 0; display: grid; gap: 8px; }
    .pick { width: 100%; display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 14px 16px; min-height: 52px; border: 1px solid var(--line); border-radius: var(--r); background: var(--panel); cursor: pointer; text-align: left; }
    .pick:hover { background: var(--primary-soft); border-color: var(--primary-line); }
    .pick small { color: var(--muted); }
    .found { display: flex; align-items: center; gap: 12px; padding: 12px 14px; border-radius: var(--r); background: var(--ok-bg); color: var(--ok-fg); }
    .found div { display: grid; gap: 2px; }
    .found small { color: inherit; opacity: 0.85; }
    form { display: grid; gap: 16px; }
    .field { display: grid; gap: 6px; }
    .field input, .field select { min-height: 48px; font-size: 16px; }
    .below { margin-top: 8px; }
    .wide { width: 100%; min-height: 52px; font-size: 17px; justify-content: center; }
    .actions { display: flex; gap: 10px; flex-wrap: wrap; }
    .actions a { display: inline-flex; align-items: center; gap: 8px; text-decoration: none; }
  `,
})
export class PairingPage implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  readonly step = signal<Step>('loading');
  readonly code = signal('');
  readonly info = signal<PairingInfo | null>(null);
  readonly tenants = signal<TenantSummary[]>([]);
  readonly locations = signal<Location[]>([]);
  readonly devices = signal<Device[]>([]);
  readonly locationChoice = signal('');
  readonly newLocation = signal('');
  readonly name = signal('');
  readonly busy = signal(false);
  readonly activated = signal<Device | null>(null);
  private nameTouched = false;

  /** O nome que o painel sugere: "TV 3" ou "Loja Centro - TV 2" (a próxima do local escolhido). */
  readonly suggestion = computed(() => {
    const choice = this.locationChoice();
    const location = this.locations().find((item) => String(item.id) === choice);
    if (location) {
      const here = this.devices().filter((device) => device.locationId === location.id).length;
      return `${location.name} - ${tr('TV')} ${here + 1}`;
    }
    return `${tr('TV')} ${this.devices().length + 1}`;
  });

  async ngOnInit(): Promise<void> {
    this.code.set((this.route.snapshot.paramMap.get('code') ?? '').trim().toUpperCase());
    if (this.auth.me() === null) {
      await this.auth.loadMe().catch(() => undefined);
    }
    const me = this.auth.me();
    if (me?.platformAdmin && this.auth.actingTenantId() === null) {
      this.tenants.set(await firstValueFrom(this.api.platformTenants()).catch(() => []));
      this.step.set('client');
      return;
    }
    await this.check();
  }

  async chooseClient(tenant: TenantSummary): Promise<void> {
    this.step.set('loading');
    await this.auth.enterTenant(tenant.id);
    await this.check();
  }

  private async check(): Promise<void> {
    try {
      this.info.set(await firstValueFrom(this.api.devicePairing(this.code())));
    } catch (error) {
      this.step.set(error instanceof HttpErrorResponse && error.status === 403 ? 'forbidden' : 'invalid');
      return;
    }
    const [locations, devices] = await Promise.all([
      firstValueFrom(this.api.locations()).catch(() => [] as Location[]),
      firstValueFrom(this.api.devices()).catch(() => [] as Device[]),
    ]);
    this.locations.set(locations);
    this.devices.set(devices);
    this.name.set(this.suggestion());
    this.step.set('ready');
  }

  chooseLocation(value: string): void {
    this.locationChoice.set(value);
    if (!this.nameTouched) {
      this.name.set(this.suggestion());
    }
  }

  renamed(value: string): void {
    this.nameTouched = true;
    this.name.set(value);
  }

  async activate(): Promise<void> {
    this.busy.set(true);
    const choice = this.locationChoice();
    const isNew = choice === 'new' && this.newLocation().trim() !== '';
    try {
      const device = await firstValueFrom(
        this.api.activateDevice({
          activationCode: this.code(),
          name: this.name().trim(),
          locationId: choice !== '' && choice !== 'new' ? Number(choice) : null,
          location: isNew ? this.newLocation().trim() : null,
        }),
      );
      this.activated.set(device);
      this.step.set('done');
    } catch {
      // a mensagem (por exemplo, o limite de TVs do plano) é exibida pelo interceptor de erros
    } finally {
      this.busy.set(false);
    }
  }
}
