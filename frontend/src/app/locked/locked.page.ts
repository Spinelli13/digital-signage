import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { TPipe, tr } from '../core/i18n';
import { FeatureCode } from '../core/models';
import { featureLabel, planText } from '../core/saas';
import { IconComponent } from '../shared/icon';
import { EmptyStateComponent, PageHeaderComponent } from '../shared/ui';

/** Para onde o painel manda quem abre um recurso que o plano do cliente não inclui. */
@Component({
  selector: 'app-locked',
  imports: [RouterLink, TPipe, IconComponent, PageHeaderComponent, EmptyStateComponent],
  template: `
    <app-page-header [title]="title()" icon="lock" />
    <div class="card">
      <app-empty-state icon="lock" [title]="'Este recurso não está no seu plano' | t" [text]="text()">
        @if (auth.can('admin')) {
          <a class="primary" routerLink="/plano"><app-icon name="credit-card" />{{ 'Ver planos' | t }}</a>
        } @else {
          <a class="secondary" routerLink="/dashboard">{{ 'Voltar ao início' | t }}</a>
        }
      </app-empty-state>
    </div>
  `,
})
export class LockedPage {
  protected readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  private readonly code = this.route.snapshot.paramMap.get('code') as FeatureCode;

  readonly title = computed(() => featureLabel(this.code));
  readonly text = computed(() =>
    tr('O plano {plan} não inclui {feature}. Peça um plano que inclua este recurso em "Plano e cobrança".', {
      plan: planText((this.auth.me()?.tenant.effectivePlan ?? this.auth.me()?.tenant.plan)?.name),
      feature: featureLabel(this.code),
    }),
  );
}
