import { Component, input, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { TPipe } from '../core/i18n';
import { PrivacyNoticeInfo } from '../core/models';
import { IconComponent } from './icon';

/**
 * O aviso de privacidade das páginas públicas (senha da fila e cupom), logo abaixo do campo do nome: para que o nome serve, quem
 * é o responsável, por quantos dias fica guardado e como falar sobre os dados. Quando a pessoa digita um nome, aparece a caixa
 * de concordância (sem ela o nome não é guardado). Sem nome, nada é coletado e não há o que concordar.
 */
@Component({
  selector: 'app-privacy-notice',
  imports: [FormsModule, RouterLink, TPipe, IconComponent],
  template: `
    @if (notice(); as info) {
      <div class="pn">
        <p class="small">
          <app-icon name="shield-check" [size]="15" />
          <span>
            @if (purpose() === 'queue') {
              {{ 'O nome serve só para chamar a sua senha.' | t }}
            } @else {
              {{ 'O nome serve só para a loja achar o seu cupom no caixa.' | t }}
            }
            {{ '{controller} é a responsável por esse dado e o apaga em {n} dia(s).' | t: { controller: info.controllerName || ('A loja' | t), n: info.retentionDays } }}
            @if (info.contactEmail) {
              {{ 'Dúvidas ou pedidos sobre os seus dados: {email}.' | t: { email: info.contactEmail } }}
            }
            <a routerLink="/privacidade" target="_blank" rel="noopener">{{ 'Política de Privacidade' | t }}</a>
          </span>
        </p>
        @if (hasName()) {
          <label class="check consent">
            <input type="checkbox" name="consent" [ngModel]="consent()" (ngModelChange)="consent.set($event)" />
            <span>{{ 'Concordo que meu nome seja usado assim.' | t }}</span>
          </label>
        }
      </div>
    }
  `,
  styles: `
    :host { display: block; text-align: left; }
    .pn { margin: 4px 0 6px; padding: 10px 12px; border-radius: var(--r); background: var(--panel-2); border: 1px solid var(--line); color: var(--text-2); }
    .small { display: flex; gap: 8px; align-items: flex-start; margin: 0; font-size: 13.5px; line-height: 1.45; }
    .small app-icon { flex: none; margin-top: 2px; color: var(--primary); }
    :host-context(:root[data-theme='dark']) .small app-icon { color: var(--link); }
    .consent { display: flex; align-items: flex-start; gap: 8px; margin: 10px 0 0; font-size: 14.5px; font-weight: 600; cursor: pointer; }
    .consent input { margin-top: 3px; }
  `,
})
export class PrivacyNoticeComponent {
  readonly notice = input<PrivacyNoticeInfo | null>(null);
  /** Para que o nome é usado: a fila (chamar a senha) ou o cupom (achar no caixa). */
  readonly purpose = input<'queue' | 'coupon'>('queue');
  /** A pessoa digitou um nome: aí a concordância é necessária. */
  readonly hasName = input(false);
  readonly consent = model(false);
}
