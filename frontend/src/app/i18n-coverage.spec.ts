import { BindingPipe, Conditional, LiteralPrimitive, RecursiveAstVisitor, parseTemplate } from '@angular/compiler';
import { readFileSync, readdirSync } from 'node:fs';
import { join, relative } from 'node:path';

import { BADGES, TIMEZONES, DAY_LABELS } from './campaigns/campaign-form';
import { ACTIONS } from './core/http-errors';
import { EN, DICTIONARY_CONFLICTS } from './core/i18n-en';
import { MEDIA_TYPE_LABELS, STATES } from './core/format';
import { ALERT_LABELS, EVENT_LABELS, LEVEL_LABELS } from './core/monitoring-labels';
import { FEATURES, INVOICE_STATUS_LABELS, ROLE_HELP, ROLE_LABELS, TENANT_STATUS_LABELS } from './core/saas';
import { DEFAULT_QR_CAPTION } from './media/media.page';
import { QR_THEMES } from './media/qr';
import { ACCENTS as WIDGET_ACCENTS, THEMES as WIDGET_THEMES } from './media/widget-editor';
import { METHOD_LABELS } from './platform/platform-invoices.page';
import { TABS } from './reports/reports.page';
import { THEME_LABELS } from './shared/preferences';
import { WIDGET_KINDS } from './core/widgets';
import { ORIENTATIONS } from './core/orientation';

/**
 * O painel tem dois idiomas (português é a chave, inglês fica em core/i18n-en). Este teste lê o código-fonte e garante:
 *  1. todo texto passado pelo pipe `t` ou por `tr()` tem tradução em inglês;
 *  2. nenhum texto fixo ficou "solto" num template (sem passar pelo pipe);
 *  3. as tabelas de rótulos (papéis, situações, eventos…) também estão traduzidas;
 *  4. a tradução tem os mesmos {valores} da chave.
 */
const ROOT = join(process.cwd(), 'src', 'app');

function sourceFiles(dir: string): string[] {
  const found: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      found.push(...sourceFiles(full));
    } else if (entry.name.endsWith('.ts') && !entry.name.endsWith('.spec.ts') && !entry.name.endsWith('.d.ts')) {
      found.push(full);
    }
  }
  return found;
}

/** Textos fixos que podem ficar sem tradução (endereços, nomes próprios e siglas). */
const STATIC_ALLOWED = new Set(['https://', 'https://…', 'placeholder="https://…"', 'Downloader', 'SHA-256:', 'X %', 'Y %']);

class LiteralCollector extends RecursiveAstVisitor {
  readonly found: string[] = [];
  /** Num `cond ? 'a' : 'b'` só as duas saídas são texto; a condição (ex.: modal() === 'new') não. */
  override visitConditional(ast: Conditional, context: unknown): void {
    this.visit(ast.trueExp, context);
    this.visit(ast.falseExp, context);
  }
  override visitLiteralPrimitive(ast: LiteralPrimitive, context: unknown): void {
    if (typeof ast.value === 'string') {
      this.found.push(ast.value);
    }
    super.visitLiteralPrimitive(ast, context);
  }
}

/** Acha os literais que passam pelo pipe `t`. */
class PipeCollector extends RecursiveAstVisitor {
  readonly keys: string[] = [];
  override visitPipe(ast: BindingPipe, context: unknown): void {
    if (ast.name === 't') {
      const literals = new LiteralCollector();
      literals.visit(ast.exp, null);
      this.keys.push(...literals.found);
    }
    super.visitPipe(ast, context);
  }
}

interface Finding {
  file: string;
  keys: string[];
  loose: string[];
}

/* eslint-disable @typescript-eslint/no-explicit-any */
function inspectTemplate(template: string, file: string): { keys: string[]; loose: string[] } {
  const keys: string[] = [];
  const loose: string[] = [];
  const hasLetter = /\p{L}/u;

  const expr = (value: any) => {
    if (value?.ast) {
      const collector = new PipeCollector();
      collector.visit(value.ast, null);
      keys.push(...collector.keys);
    }
  };

  const visit = (nodes: any[] | undefined) => {
    for (const node of nodes ?? []) {
      const kind = node.constructor.name;
      if (kind === 'Text' && hasLetter.test(String(node.value))) {
        loose.push(String(node.value).trim());
      }
      if (kind === 'BoundText') {
        expr(node.value);
        for (const part of node.value?.ast?.strings ?? []) {
          if (hasLetter.test(part)) {
            loose.push(String(part).trim());
          }
        }
      }
      for (const attr of node.attributes ?? []) {
        if (['title', 'placeholder', 'aria-label', 'alt', 'label'].includes(attr.name) && hasLetter.test(attr.value)) {
          loose.push(`${attr.name}="${attr.value}"`);
        }
      }
      for (const input of node.inputs ?? []) {
        expr(input.value);
      }
      for (const branch of node.branches ?? []) {
        expr(branch.expression);
        visit(branch.children);
      }
      // @switch: cada grupo tem os seus @case (cases) e o conteúdo (children)
      for (const group of node.groups ?? []) {
        for (const option of group.cases ?? []) {
          expr(option.expression);
        }
        visit(group.children);
      }
      if (kind === 'ForLoopBlock') {
        expr(node.expression);
        visit(node.empty?.children);
      }
      if (kind === 'SwitchBlock') {
        expr(node.expression);
      }
      visit(node.children);
    }
  };

  const parsed = parseTemplate(template, file, { preserveWhitespaces: false });
  expect(parsed.errors ?? [], `${file}: template com erro`).toEqual([]);
  visit(parsed.nodes as any[]);
  return { keys, loose };
}

function unescape(literal: string): string {
  return literal.replace(/\\(.)/g, '$1');
}

function scan(): Finding[] {
  const findings: Finding[] = [];
  for (const path of sourceFiles(ROOT)) {
    const source = readFileSync(path, 'utf8');
    const file = relative(ROOT, path);
    const keys: string[] = [];
    const loose: string[] = [];

    // um arquivo pode ter vários componentes: cada `template: `...`` é inspecionado (a crase fecha o texto)
    let code = '';
    let cursor = 0;
    for (let start = source.indexOf('template: `'); start >= 0; start = source.indexOf('template: `', cursor)) {
      const from = start + 'template: `'.length;
      const end = source.indexOf('`', from);
      const result = inspectTemplate(source.slice(from, end), file);
      keys.push(...result.keys);
      loose.push(...result.loose.filter((text) => !STATIC_ALLOWED.has(text)));
      code += source.slice(cursor, start);
      cursor = end + 1;
    }
    code += source.slice(cursor);
    // chamadas tr('...') no código TypeScript (a chave é sempre um texto literal)
    for (const match of code.matchAll(/\btr\(\s*'((?:[^'\\\n]|\\.)*)'/g)) {
      keys.push(unescape(match[1]));
    }
    findings.push({ file, keys, loose });
  }
  return findings;
}

const findings = scan();

/** Chaves montadas em tempo de execução (não aparecem como literais acima). */
const DYNAMIC_KEYS = [
  '{n} local',
  '{n} locais',
  '{n} grupo',
  '{n} grupos',
  '{n} TV',
  '{n} TVs',
  'Mídia',
  'TV',
  'Dia',
  'Zona',
  'Principal',
  '(sem nome)',
  // a tela do Shell escolhe entre duas frases de acordo com a situação da assinatura
  'Assinatura encerrada',
  'Assinatura suspensa',
  'suspensa',
  'encerrada',
  // texto padrão de errorMessage()
  'Algo deu errado. Tente novamente.',
  DEFAULT_QR_CAPTION,
];

const TABLES: Record<string, string[]> = {
  papéis: Object.values(ROLE_LABELS),
  'ajuda dos papéis': Object.values(ROLE_HELP),
  'situação do cliente': Object.values(TENANT_STATUS_LABELS),
  'situação da fatura': Object.values(INVOICE_STATUS_LABELS),
  'forma de pagamento': Object.values(METHOD_LABELS),
  'situação da TV': Object.values(STATES).map((state) => state.label),
  'tipo de mídia': Object.values(MEDIA_TYPE_LABELS),
  eventos: Object.values(EVENT_LABELS).map((event) => event.label),
  níveis: Object.values(LEVEL_LABELS),
  'tipos de alerta': Object.values(ALERT_LABELS),
  'situação da campanha': Object.values(BADGES).flatMap((badge) => [badge.label, badge.hint]),
  'dias da semana': Object.values(DAY_LABELS),
  'fusos horários': TIMEZONES.map((zone) => zone.label),
  'abas dos relatórios': TABS.map((tab) => tab.label),
  tema: Object.values(THEME_LABELS),
  'cores do QR Code': Object.values(QR_THEMES).map((theme) => theme.label),
  'ações dos avisos de erro': ACTIONS.map((action) => action[2]),
  'tipos de widget': WIDGET_KINDS.flatMap((info) => [info.label, info.description]),
  'cores dos widgets': [...WIDGET_THEMES, ...WIDGET_ACCENTS].map((color) => color.label),
  'recursos dos planos': FEATURES.flatMap((feature) => [feature.label, feature.help]),
  'orientação da TV': ORIENTATIONS.flatMap((option) => [option.label, option.hint]),
};

const placeholders = (text: string) => [...text.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();

describe('idioma: inglês', () => {
  it('encontra os componentes (o teste não pode passar vazio)', () => {
    expect(findings.length).toBeGreaterThan(20);
    expect(findings.flatMap((f) => f.keys).length).toBeGreaterThan(500);
  });

  it('traduz todo texto passado pelo pipe t ou por tr()', () => {
    const missing = findings.flatMap((f) => f.keys.filter((key) => !(key in EN)).map((key) => `${f.file}: ${key}`));
    expect(missing).toEqual([]);
  });

  it('não deixa texto fixo solto nos templates (tudo passa pelo pipe t)', () => {
    const loose = findings.flatMap((f) => f.loose.map((text) => `${f.file}: ${text}`));
    expect(loose).toEqual([]);
  });

  for (const [name, values] of Object.entries(TABLES)) {
    it(`traduz a tabela de ${name}`, () => {
      expect(values.length).toBeGreaterThan(0);
      expect(values.filter((value) => !(value in EN))).toEqual([]);
    });
  }

  it('traduz as chaves montadas em tempo de execução', () => {
    expect(DYNAMIC_KEYS.filter((key) => !(key in EN))).toEqual([]);
  });

  it('usa os mesmos {valores} na chave e na tradução', () => {
    const wrong = Object.entries(EN).filter(([key, value]) => placeholders(key).join() !== placeholders(value).join());
    expect(wrong.map(([key]) => key)).toEqual([]);
  });

  it('não tem tradução vazia e não repete chave com traduções diferentes', () => {
    expect(Object.entries(EN).filter(([, value]) => !value.trim())).toEqual([]);
    expect(DICTIONARY_CONFLICTS).toEqual([]);
  });

  it('não guarda traduções que nenhum código usa (dicionário limpo)', () => {
    const used = new Set<string>([...findings.flatMap((f) => f.keys), ...DYNAMIC_KEYS, ...Object.values(TABLES).flat()]);
    // as mensagens do servidor (seção SERVER) são casadas em tempo de execução, não aparecem no código
    const unused = Object.keys(EN).filter((key) => !used.has(key) && !SERVER_KEYS.has(key));
    expect(unused).toEqual([]);
  });
});

import { SERVER } from './core/i18n-en/server';
import { SERVER_DATA } from './core/i18n-en/server-data';

const SERVER_KEYS = new Set([...Object.keys(SERVER), ...Object.keys(SERVER_DATA)]);
