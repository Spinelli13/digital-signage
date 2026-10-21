import { Injectable } from '@angular/core';

import { IconName } from '../shared/icons';
import { WidgetKind, WidgetSettings } from './models';

export interface WidgetKindInfo {
  kind: WidgetKind;
  icon: IconName;
  label: string;
  description: string;
}

/** Os tipos de widget, na ordem em que aparecem para escolher. O tempo padrão de cada um vem do servidor (catálogo). */
export const WIDGET_KINDS: WidgetKindInfo[] = [
  { kind: 'CLOCK', icon: 'clock', label: 'Relógio e data', description: 'A hora certa e a data, no fuso que você escolher. A hora é a do servidor: não depende do relógio da TV.' },
  { kind: 'WEATHER', icon: 'cloud-sun', label: 'Previsão do tempo', description: 'Temperatura, céu e os próximos dias da cidade que você escolher.' },
  { kind: 'NEWS', icon: 'newspaper', label: 'Notícias', description: 'Manchetes de um portal (G1, Agência Brasil, BBC, CNN, UOL ou outro feed RSS), trocando sozinhas.' },
  { kind: 'RATES', icon: 'badge-dollar-sign', label: 'Cotações', description: 'Dólar, euro e outras moedas em reais, com a variação desde o dia anterior.' },
  { kind: 'NOTICE', icon: 'megaphone', label: 'Recado', description: 'Um título e uma mensagem em destaque: horário especial, promoção, recado.' },
  { kind: 'COUNTDOWN', icon: 'timer', label: 'Contagem regressiva', description: 'Quanto falta para uma data: inauguração, promoção, Natal.' },
];

export function widgetInfo(kind: WidgetKind | null | undefined): WidgetKindInfo | undefined {
  return WIDGET_KINDS.find((info) => info.kind === kind);
}

/** As escolhas iniciais de cada tipo (o servidor completa o que faltar e recusa o que não faz sentido). */
export function defaultWidgetSettings(kind: WidgetKind, lang: string): WidgetSettings {
  const common = { theme: 'dark', lang };
  switch (kind) {
    case 'CLOCK':
      return { ...common, timezone: 'America/Sao_Paulo', label: '', hour12: false, showSeconds: false, showDate: true };
    case 'WEATHER':
      return { ...common, city: '', latitude: null, longitude: null, days: 3 };
    case 'NEWS':
      return { ...common, source: 'g1', customUrl: '', maxItems: 8, secondsPerItem: 8 };
    case 'RATES':
      return { ...common, currencies: ['USD', 'EUR'] };
    case 'NOTICE':
      return { ...common, title: '', message: '', accent: 'brand', align: 'center' };
    case 'COUNTDOWN':
      return { ...common, label: '', target: '', timezone: 'America/Sao_Paulo', finishedText: 'Chegou o dia!' };
  }
}

const MARK_CSS = '/*__CSS__*/';
const MARK_JS = '/*__JS__*/';

/**
 * Os modelos visuais dos widgets (a pasta public/widgets, a MESMA que a TV usa): buscados uma vez e montados numa
 * página só, para a pré-visualização ser idêntica ao que aparece na TV.
 */
@Injectable({ providedIn: 'root' })
export class WidgetTemplates {
  private parts: Promise<{ html: string; css: string; js: string }> | null = null;

  /** A página pronta, já começando com a configuração dada (JSON escapado: um "<" no texto nunca fecha o script). */
  async page(init: unknown): Promise<string> {
    const { html, css, js } = await this.load();
    const start = `Smartplay.start(${JSON.stringify(init).replace(/</g, '\\u003c')});`;
    // Funções (e não texto) como substituto: o "$'" do "R$'" do JavaScript do modelo não é um padrão de substituição.
    return html.replace(MARK_CSS, () => css).replace(MARK_JS, () => `${js}\n${start}`);
  }

  private load(): Promise<{ html: string; css: string; js: string }> {
    this.parts ??= Promise.all(['widget.html', 'widget.css', 'widget.js'].map((name) => this.fetchText(name))).then(([html, css, js]) => ({ html, css, js }));
    return this.parts.catch((error) => {
      this.parts = null;
      throw error;
    });
  }

  protected async fetchText(name: string): Promise<string> {
    const response = await fetch(new URL(`widgets/${name}`, document.baseURI).toString());
    if (!response.ok) {
      throw new Error(`Não foi possível carregar o modelo do widget (${name})`);
    }
    return response.text();
  }
}
