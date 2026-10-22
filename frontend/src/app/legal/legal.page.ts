import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { TPipe } from '../core/i18n';
import { PublicPrivacy } from '../core/models';
import { IconComponent } from '../shared/icon';
import { PreferencesBar } from '../shared/preferences';
import { WordmarkComponent } from '../shared/wordmark';
import { LegalSection, legalContext, privacySections, termsSections } from './legal-content';

/**
 * A Política de Privacidade (/privacidade) e os Termos de Uso (/termos): páginas abertas, sem login, com o texto em português
 * (a versão oficial) e os dados de quem responde pelo tratamento vindos da configuração da plataforma.
 */
@Component({
  selector: 'app-legal',
  imports: [RouterLink, TPipe, IconComponent, PreferencesBar, WordmarkComponent],
  template: `
    <div class="prefs-corner"><app-preferences /></div>
    <main class="wrap">
      <div class="brand"><img src="favicon.svg" alt="" width="32" height="32" /><app-wordmark /></div>

      <nav class="tabs" [attr.aria-label]="'Documentos legais' | t">
        <a routerLink="/privacidade" [class.on]="kind() === 'privacy'"><app-icon name="shield-check" [size]="16" />{{ 'Política de Privacidade' | t }}</a>
        <a routerLink="/termos" [class.on]="kind() === 'terms'"><app-icon name="file-text" [size]="16" />{{ 'Termos de Uso' | t }}</a>
      </nav>

      <h1>{{ (kind() === 'privacy' ? 'Política de Privacidade' : 'Termos de Uso') | t }}</h1>
      <p class="meta">
        {{ 'Versão {version}' | t: { version: version() } }}
        <span aria-hidden="true">·</span>
        {{ 'Documento em português, a versão oficial para a lei brasileira.' | t }}
      </p>

      <article class="doc">
        @for (section of sections(); track section.title) {
          <section>
            <h2>{{ section.title }}</h2>
            @for (block of section.blocks; track $index) {
              @if (isList(block)) {
                <ul>
                  @for (item of asList(block); track $index) {
                    <li>{{ item }}</li>
                  }
                </ul>
              } @else {
                <p>{{ block }}</p>
              }
            }
          </section>
        }
      </article>

      <p class="foot"><a routerLink="/login"><app-icon name="arrow-left" [size]="15" />{{ 'Voltar ao painel' | t }}</a></p>
    </main>
  `,
  styles: `
    :host { display: block; min-height: 100vh; background: var(--bg); color: var(--text); }
    .prefs-corner { position: absolute; top: calc(12px + env(safe-area-inset-top)); right: 12px; }
    .wrap { max-width: 760px; margin: 0 auto; padding: calc(28px + env(safe-area-inset-top)) 16px 56px; }
    .brand { display: flex; align-items: center; gap: 10px; font-size: 20px; color: var(--text-2); margin-bottom: 18px; }
    .brand img { border-radius: 10px; }
    .tabs { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }
    .tabs a { display: inline-flex; align-items: center; gap: 6px; padding: 8px 14px; border: 1px solid var(--line); border-radius: 999px; color: var(--text-2); font-weight: 600; text-decoration: none; background: var(--panel); }
    .tabs a.on { border-color: var(--primary); color: var(--primary); background: var(--primary-soft); }
    :host-context(:root[data-theme='dark']) .tabs a.on { color: var(--link); }
    h1 { margin: 0 0 6px; font-size: 28px; }
    .meta { margin: 0 0 22px; color: var(--muted); font-size: 14px; }
    .doc section { margin-bottom: 22px; }
    .doc h2 { margin: 0 0 8px; font-size: 18px; }
    .doc p, .doc li { line-height: 1.6; color: var(--text-2); }
    .doc p { margin: 0 0 10px; }
    .doc ul { margin: 0 0 10px; padding-left: 20px; }
    .doc li { margin-bottom: 6px; }
    .foot { margin-top: 28px; }
    .foot a { display: inline-flex; align-items: center; gap: 6px; }
  `,
})
export class LegalPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  readonly kind = signal<'privacy' | 'terms'>('privacy');
  readonly info = signal<PublicPrivacy | null>(null);
  readonly version = computed(() => this.info()?.version ?? '');
  readonly sections = computed<LegalSection[]>(() => {
    const context = legalContext(this.info());
    return this.kind() === 'privacy' ? privacySections(context) : termsSections(context);
  });

  ngOnInit(): void {
    this.route.data.subscribe((data) => this.kind.set(data['legal'] === 'terms' ? 'terms' : 'privacy'));
    // Sem resposta do servidor o texto aparece do mesmo jeito, só com os dados genéricos da plataforma.
    this.api.publicPrivacy().subscribe({ next: (info) => this.info.set(info), error: () => undefined });
  }

  isList(block: string | string[]): block is string[] {
    return Array.isArray(block);
  }

  asList(block: string | string[]): string[] {
    return Array.isArray(block) ? block : [block];
  }
}
