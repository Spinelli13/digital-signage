import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { Notice } from '../core/notice';

@Component({
  selector: 'app-platform-settings',
  imports: [FormsModule],
  template: `
    <div class="head">
      <div>
        <h1>Configurações da plataforma</h1>
        <p>O texto de pagamento que os clientes veem e as regras da cobrança automática.</p>
      </div>
    </div>

    @if (notice.text(); as text) {
      <div class="notice" role="status">{{ text }}</div>
    }

    <form class="card" (ngSubmit)="save()">
      <h3>Empresa e pagamento</h3>
      <div class="field">
        <label for="s-company">Nome da empresa</label>
        <input id="s-company" name="companyName" required [ngModel]="companyName()" (ngModelChange)="companyName.set($event)" />
      </div>
      <div class="field">
        <label for="s-support">E-mail de suporte</label>
        <input id="s-support" name="supportEmail" type="email" [ngModel]="supportEmail()" (ngModelChange)="supportEmail.set($event)" />
      </div>
      <div class="field">
        <label for="s-pay">Como pagar (aparece em "Plano e cobrança" de cada cliente)</label>
        <textarea id="s-pay" name="paymentInstructions" rows="5"
          placeholder="Ex.: PIX: chave 00.000.000/0001-00 (Minha Empresa). Envie o comprovante para o e-mail de suporte."
          [ngModel]="paymentInstructions()" (ngModelChange)="paymentInstructions.set($event)"></textarea>
      </div>

      <h3 class="sub">Cobrança automática</h3>
      <div class="three">
        <div class="field">
          <label for="s-trial">Dias de teste</label>
          <input id="s-trial" name="trialDays" type="number" min="0" max="90" [ngModel]="trialDays()" (ngModelChange)="trialDays.set(+$event)" />
          <small>Para clientes novos.</small>
        </div>
        <div class="field">
          <label for="s-grace">Dias de tolerância</label>
          <input id="s-grace" name="graceDays" type="number" min="0" max="60" [ngModel]="graceDays()" (ngModelChange)="graceDays.set(+$event)" />
          <small>Depois do vencimento o cliente fica "em atraso"; passada a tolerância, é suspenso.</small>
        </div>
        <div class="field">
          <label for="s-lead">Antecedência da fatura</label>
          <input id="s-lead" name="invoiceLeadDays" type="number" min="0" max="30" [ngModel]="invoiceLeadDays()" (ngModelChange)="invoiceLeadDays.set(+$event)" />
          <small>Dias antes do fim do período em que a fatura é gerada.</small>
        </div>
      </div>
      <button type="submit" class="primary" [disabled]="busy() || !companyName().trim()">Salvar</button>
    </form>
  `,
  styles: `
    .sub { margin: 22px 0 10px; }
    .three { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 14px; }
  `,
})
export class PlatformSettingsPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly notice = new Notice();
  readonly busy = signal(false);
  readonly companyName = signal('');
  readonly supportEmail = signal('');
  readonly paymentInstructions = signal('');
  readonly trialDays = signal(14);
  readonly graceDays = signal(7);
  readonly invoiceLeadDays = signal(5);

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.notice.dispose());
    this.api.platformSettings().subscribe({
      next: (settings) => {
        this.companyName.set(settings.companyName);
        this.supportEmail.set(settings.supportEmail ?? '');
        this.paymentInstructions.set(settings.paymentInstructions ?? '');
        this.trialDays.set(settings.trialDays);
        this.graceDays.set(settings.graceDays);
        this.invoiceLeadDays.set(settings.invoiceLeadDays);
      },
      error: () => undefined,
    });
  }

  save(): void {
    if (!this.companyName().trim()) {
      return;
    }
    this.busy.set(true);
    this.api
      .updatePlatformSettings({
        companyName: this.companyName().trim(),
        supportEmail: this.supportEmail().trim(),
        paymentInstructions: this.paymentInstructions().trim(),
        trialDays: this.trialDays(),
        graceDays: this.graceDays(),
        invoiceLeadDays: this.invoiceLeadDays(),
      })
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.notice.show('Configurações salvas.');
        },
        error: () => this.busy.set(false),
      });
  }
}
