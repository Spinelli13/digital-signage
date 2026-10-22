import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { TPipe, tr } from '../core/i18n';
import { Notice } from '../core/notice';
import { IconComponent } from '../shared/icon';
import { PageHeaderComponent } from '../shared/ui';

@Component({
  selector: 'app-platform-settings',
  imports: [FormsModule, TPipe, IconComponent, PageHeaderComponent],
  template: `
    <app-page-header [title]="'Configurações da plataforma' | t" [subtitle]="'O texto de pagamento que os clientes veem e as regras da cobrança automática.' | t" icon="settings"></app-page-header>

    @if (notice.text(); as text) {
      <div class="notice" role="status"><app-icon name="circle-check" [size]="18" /><span>{{ text }}</span></div>
    }

    <form class="card" (ngSubmit)="save()">
      <h3 class="sec"><app-icon name="building" [size]="18" />{{ 'Empresa e pagamento' | t }}</h3>
      <div class="field">
        <label for="s-company">{{ 'Nome da empresa' | t }}</label>
        <input id="s-company" name="companyName" required [ngModel]="companyName()" (ngModelChange)="companyName.set($event)" />
      </div>
      <div class="field">
        <label for="s-support">{{ 'E-mail de suporte' | t }}</label>
        <input id="s-support" name="supportEmail" type="email" [ngModel]="supportEmail()" (ngModelChange)="supportEmail.set($event)" />
      </div>
      <div class="field">
        <label for="s-pay">{{ 'Como pagar (aparece em "Plano e cobrança" de cada cliente)' | t }}</label>
        <textarea id="s-pay" name="paymentInstructions" rows="5"
          [placeholder]="'Ex.: PIX: chave 00.000.000/0001-00 (Minha Empresa). Envie o comprovante para o e-mail de suporte.' | t"
          [ngModel]="paymentInstructions()" (ngModelChange)="paymentInstructions.set($event)"></textarea>
      </div>

      <h3 class="sub sec"><app-icon name="shield-check" [size]="18" />{{ 'Privacidade (LGPD)' | t }}</h3>
      <p class="label">{{ 'Quem responde pelo tratamento de dados. Aparece na Política de Privacidade e nos Termos de Uso, que qualquer pessoa pode abrir sem entrar.' | t }}</p>
      <div class="two">
        <div class="field">
          <label for="s-legal-name">{{ 'Razão social' | t }}</label>
          <input id="s-legal-name" name="legalName" maxlength="160" [ngModel]="legalName()" (ngModelChange)="legalName.set($event)" />
        </div>
        <div class="field">
          <label for="s-legal-doc">{{ 'CNPJ' | t }}</label>
          <input id="s-legal-doc" name="legalDocument" maxlength="30" placeholder="00.000.000/0001-00" [ngModel]="legalDocument()" (ngModelChange)="legalDocument.set($event)" />
        </div>
        <div class="field">
          <label for="s-priv-name">{{ 'Encarregado de dados (DPO)' | t }}</label>
          <input id="s-priv-name" name="privacyContactName" maxlength="120" [ngModel]="privacyContactName()" (ngModelChange)="privacyContactName.set($event)" />
        </div>
        <div class="field">
          <label for="s-priv-email">{{ 'E-mail do encarregado' | t }}</label>
          <input id="s-priv-email" name="privacyContactEmail" type="email" maxlength="255" [ngModel]="privacyContactEmail()" (ngModelChange)="privacyContactEmail.set($event)" />
          <small>{{ 'Sem este e-mail, a política mostra o e-mail de suporte.' | t }}</small>
        </div>
      </div>

      <h3 class="sub sec"><app-icon name="receipt-text" [size]="18" />{{ 'Cobrança automática' | t }}</h3>
      <div class="three">
        <div class="field">
          <label for="s-trial">{{ 'Dias de teste' | t }}</label>
          <input id="s-trial" name="trialDays" type="number" min="0" max="90" [ngModel]="trialDays()" (ngModelChange)="trialDays.set(+$event)" />
          <small>{{ 'Para clientes novos.' | t }}</small>
        </div>
        <div class="field">
          <label for="s-grace">{{ 'Dias de tolerância' | t }}</label>
          <input id="s-grace" name="graceDays" type="number" min="0" max="60" [ngModel]="graceDays()" (ngModelChange)="graceDays.set(+$event)" />
          <small>{{ 'Depois do vencimento o cliente fica "em atraso"; passada a tolerância, é suspenso.' | t }}</small>
        </div>
        <div class="field">
          <label for="s-lead">{{ 'Antecedência da fatura' | t }}</label>
          <input id="s-lead" name="invoiceLeadDays" type="number" min="0" max="30" [ngModel]="invoiceLeadDays()" (ngModelChange)="invoiceLeadDays.set(+$event)" />
          <small>{{ 'Dias antes do fim do período em que a fatura é gerada.' | t }}</small>
        </div>
      </div>
      <button type="submit" class="primary" [disabled]="busy() || !companyName().trim()"><app-icon name="check" [size]="16" />{{ 'Salvar' | t }}</button>
    </form>
  `,
  styles: `
    .sub { margin: 22px 0 10px; }
    .sec { display: flex; align-items: center; gap: 8px; }
    .sec app-icon { color: var(--primary); }
    :host-context(:root[data-theme='dark']) .sec app-icon { color: var(--link); }
    .three { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 14px; }
    .two { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 14px; }
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
  readonly legalName = signal('');
  readonly legalDocument = signal('');
  readonly privacyContactName = signal('');
  readonly privacyContactEmail = signal('');
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
        this.legalName.set(settings.legalName ?? '');
        this.legalDocument.set(settings.legalDocument ?? '');
        this.privacyContactName.set(settings.privacyContactName ?? '');
        this.privacyContactEmail.set(settings.privacyContactEmail ?? '');
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
        legalName: this.legalName().trim(),
        legalDocument: this.legalDocument().trim(),
        privacyContactName: this.privacyContactName().trim(),
        privacyContactEmail: this.privacyContactEmail().trim(),
        trialDays: this.trialDays(),
        graceDays: this.graceDays(),
        invoiceLeadDays: this.invoiceLeadDays(),
      })
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.notice.show(tr('Configurações salvas.'));
        },
        error: () => this.busy.set(false),
      });
  }
}
