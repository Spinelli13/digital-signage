import { Injectable, Pipe, PipeTransform, computed, signal } from '@angular/core';

import { EN, SERVER_TEXTS } from './i18n-en';

/**
 * Idiomas do painel. O português é o nativo: o texto em português é a PRÓPRIA CHAVE da tradução (como no gettext),
 * então o código continua legível e, se faltar uma tradução, a tela mostra o português em vez de uma chave
 * técnica. Cada texto novo ganha a sua tradução em ./i18n-en/ (um teste confere que nenhum ficou de fora).
 */
export type Lang = 'pt' | 'en';

export const LANG_KEY = 'signage.lang';

export const LANGUAGES: { code: Lang; label: string; short: string; locale: string; htmlLang: string }[] = [
  { code: 'pt', label: 'Português', short: 'PT', locale: 'pt-BR', htmlLang: 'pt-BR' },
  { code: 'en', label: 'English', short: 'EN', locale: 'en-US', htmlLang: 'en' },
];

export type Params = Record<string, string | number | null | undefined>;

const lang = signal<Lang>(readLang());

/** Idioma atual (um signal: quem lê dentro de um template ou de um computed é atualizado na troca). */
export const currentLang = lang.asReadonly();

export function setLang(next: Lang): void {
  lang.set(next);
  try {
    localStorage.setItem(LANG_KEY, next);
  } catch {
    // sem armazenamento (navegação privada): vale só até fechar a aba
  }
  applyHtmlLang();
}

/** Código de locale do idioma atual ("pt-BR" / "en-US"), para datas, números e moeda. */
export function currentLocale(): string {
  return LANGUAGES.find((l) => l.code === lang())?.locale ?? 'pt-BR';
}

/** Traduz um texto em português (a chave) para o idioma atual, trocando os {valores} pelos parâmetros. */
export function tr(text: string, params?: Params): string {
  const translated = lang() === 'pt' ? text : (EN[text] ?? text);
  return params ? fill(translated, params) : translated;
}

function fill(text: string, params: Params): string {
  return text.replace(/\{(\w+)\}/g, (placeholder, name: string) => {
    const value = params[name];
    return value === null || value === undefined ? placeholder : String(value);
  });
}

// ---------- mensagens que o servidor manda (sempre em português) ----------

interface ServerTemplate {
  pattern: RegExp;
  names: string[];
  english: string;
}

let serverTemplates: ServerTemplate[] | null = null;

/**
 * As chaves com {valores} viram expressões regulares: "Já existe um local chamado \"{name}\"" casa com a mensagem real.
 * Só entram as frases do servidor (SERVER_TEXTS): modelos da tela, como "{used} de {limit}", casariam com qualquer texto.
 */
function templates(): ServerTemplate[] {
  if (serverTemplates === null) {
    serverTemplates = Object.entries(SERVER_TEXTS)
      .filter(([key]) => key.includes('{'))
      .map(([key, english]) => {
        const names: string[] = [];
        const source = key
          .split(/(\{\w+\})/)
          .map((part) => {
            const hole = /^\{(\w+)\}$/.exec(part);
            if (hole) {
              names.push(hole[1]);
              return '([\\s\\S]+?)';
            }
            return part.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
          })
          .join('');
        return { pattern: new RegExp(`^${source}$`), names, english };
      });
  }
  return serverTemplates;
}

/**
 * Traduz uma mensagem de erro do servidor. O servidor responde em português; as conhecidas (inclusive as que
 * levam um nome ou número no meio) ganham a versão em inglês, as demais ficam como vieram.
 */
export function trServer(message: string): string {
  if (lang() === 'pt') {
    return message;
  }
  const exact = SERVER_TEXTS[message];
  if (exact !== undefined) {
    return exact;
  }
  for (const template of templates()) {
    const match = template.pattern.exec(message);
    if (match) {
      const params: Params = {};
      template.names.forEach((name, index) => (params[name] = match[index + 1]));
      return fill(template.english, params);
    }
  }
  return message;
}

/** Só para os testes: reinicia o idioma e o cache. */
export function resetLangForTests(next: Lang = 'pt'): void {
  lang.set(next);
  serverTemplates = null;
}

/** {{ 'Texto em português' | t }} ou {{ 'Local "{name}" salvo.' | t: { name: x } }}. Impuro de propósito: acompanha a troca de idioma. */
@Pipe({ name: 't', pure: false })
export class TPipe implements PipeTransform {
  transform(text: string, params?: Params): string {
    return tr(text, params);
  }
}

// ---------- datas e números no idioma atual ----------

/** "2026-11-05" (sem hora) é um DIA do calendário: vira a meia-noite local, não UTC (que mostraria o dia anterior no Brasil). */
export function toDate(value: string | number | Date): Date {
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const [year, month, day] = value.split('-').map(Number);
    return new Date(year, month - 1, day);
  }
  return new Date(value);
}

/** "20/10/2026" (português) ou "10/20/2026" (inglês). */
export function formatDate(value: string | number | Date | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  return toDate(value).toLocaleDateString(currentLocale(), { day: '2-digit', month: '2-digit', year: 'numeric' });
}

/** "20/10/2026 09:00" (português) ou "10/20/2026, 09:00 AM" (inglês), no fuso do navegador. */
export function formatDateTimeLocal(value: string | number | Date | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  return toDate(value).toLocaleString(currentLocale(), {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** {{ valor | fdate }} (dia) ou {{ valor | fdate: 'datetime' }} (dia e hora), sempre no idioma atual. */
@Pipe({ name: 'fdate', pure: false })
export class FDatePipe implements PipeTransform {
  transform(value: string | number | Date | null | undefined, kind: 'date' | 'datetime' = 'date'): string {
    return kind === 'datetime' ? formatDateTimeLocal(value) : formatDate(value);
  }
}

@Injectable({ providedIn: 'root' })
export class I18nService {
  readonly lang = currentLang;
  readonly languages = LANGUAGES;
  readonly locale = computed(() => LANGUAGES.find((l) => l.code === lang())?.locale ?? 'pt-BR');

  set(next: Lang): void {
    setLang(next);
  }
}

function readLang(): Lang {
  try {
    return localStorage.getItem(LANG_KEY) === 'en' ? 'en' : 'pt';
  } catch {
    return 'pt';
  }
}

function applyHtmlLang(): void {
  try {
    document.documentElement.lang = LANGUAGES.find((l) => l.code === lang())?.htmlLang ?? 'pt-BR';
  } catch {
    // sem documento (testes de servidor): ignora
  }
}

applyHtmlLang();
