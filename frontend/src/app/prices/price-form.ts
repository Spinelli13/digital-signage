import { tr } from '../core/i18n';
import { PriceItem, PriceList, PriceListRequest, PriceRule } from '../core/models';

/** Uma linha da tabela como a pessoa edita (o preço pode estar em branco enquanto ela digita). */
export interface ItemDraft {
  id: string;
  category: string;
  name: string;
  description: string;
  price: number | null;
  unit: string;
  available: boolean;
  highlight: boolean;
}

/** Uma variação por horário como a pessoa edita: ou um percentual (em todos ou em alguns itens) ou preços fixos por item. */
export interface RuleDraft {
  key: number;
  name: string;
  days: number[];
  start: string;
  end: string;
  mode: 'percent' | 'fixed';
  percent: number | null;
  scope: 'all' | 'some';
  itemIds: string[];
  fixed: Record<string, number | null>;
}

export interface PriceForm {
  name: string;
  currency: string;
  timezone: string;
  items: ItemDraft[];
  rules: RuleDraft[];
}

export const CURRENCIES = ['BRL', 'USD', 'EUR'];
export const WEEKDAYS: { value: number; label: string }[] = [
  { value: 1, label: 'Seg' },
  { value: 2, label: 'Ter' },
  { value: 3, label: 'Qua' },
  { value: 4, label: 'Qui' },
  { value: 5, label: 'Sex' },
  { value: 6, label: 'Sáb' },
  { value: 7, label: 'Dom' },
];

let keys = 0;

/** Identificador curto e estável do item (as variações citam itens por ele; quem edita nunca o vê). */
export function newItemId(): string {
  const random = typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `${Date.now()}${Math.random()}`;
  return random.replace(/[^a-z0-9]/gi, '').slice(0, 8);
}

export function emptyItem(category = ''): ItemDraft {
  return { id: newItemId(), category, name: '', description: '', price: null, unit: '', available: true, highlight: false };
}

export function emptyRule(): RuleDraft {
  return { key: ++keys, name: '', days: [], start: '', end: '', mode: 'percent', percent: null, scope: 'all', itemIds: [], fixed: {} };
}

export function emptyForm(): PriceForm {
  return { name: '', currency: 'BRL', timezone: 'America/Sao_Paulo', items: [emptyItem()], rules: [] };
}

export function formFromList(list: PriceList): PriceForm {
  return {
    name: list.name,
    currency: list.currency,
    timezone: list.timezone,
    items: list.items.map((item) => ({
      id: item.id,
      category: item.category ?? '',
      name: item.name,
      description: item.description ?? '',
      price: item.price,
      unit: item.unit ?? '',
      available: item.available,
      highlight: item.highlight,
    })),
    rules: list.rules.map((rule) => ruleDraft(rule)),
  };
}

function ruleDraft(rule: PriceRule): RuleDraft {
  const fixed = Object.keys(rule.itemPrices ?? {}).length > 0;
  return {
    key: ++keys,
    name: rule.name,
    days: [...(rule.days ?? [])],
    start: rule.start ?? '',
    end: rule.end ?? '',
    mode: fixed ? 'fixed' : 'percent',
    percent: fixed ? null : rule.percent,
    scope: rule.itemIds && rule.itemIds.length > 0 ? 'some' : 'all',
    itemIds: [...(rule.itemIds ?? [])],
    fixed: { ...(rule.itemPrices ?? {}) },
  };
}

/** O que vai para o servidor: itens sem nome e linhas em branco ficam de fora, preços fixos só dos itens escolhidos. */
export function toRequest(form: PriceForm): PriceListRequest {
  const items: PriceItem[] = form.items
    .filter((item) => item.name.trim() !== '' || item.price !== null)
    .map((item) => ({
      id: item.id,
      category: item.category.trim() || null,
      name: item.name.trim(),
      description: item.description.trim() || null,
      price: item.price ?? 0,
      unit: item.unit.trim() || null,
      available: item.available,
      highlight: item.highlight,
    }));
  const known = new Set(items.map((item) => item.id));
  const rules: PriceRule[] = form.rules.map((rule) => {
    const fixedMode = rule.mode === 'fixed';
    const itemPrices: Record<string, number> = {};
    if (fixedMode) {
      for (const [id, price] of Object.entries(rule.fixed)) {
        if (known.has(id) && price !== null && price !== undefined) {
          itemPrices[id] = price;
        }
      }
    }
    return {
      name: rule.name.trim(),
      days: [...rule.days].sort((a, b) => a - b),
      start: rule.start || null,
      end: rule.end || null,
      percent: fixedMode ? 0 : (rule.percent ?? 0),
      itemIds: !fixedMode && rule.scope === 'some' ? rule.itemIds.filter((id) => known.has(id)) : [],
      itemPrices,
    };
  });
  return { name: form.name.trim(), currency: form.currency, timezone: form.timezone, items, rules };
}

/** O que falta preencher (frases curtas, na ordem da tela). O servidor confere de novo as mesmas regras. */
export function problems(form: PriceForm): string[] {
  const found: string[] = [];
  if (!form.name.trim()) {
    found.push(tr('Dê um nome à tabela.'));
  }
  const filled = form.items.filter((item) => item.name.trim() !== '' || item.price !== null);
  if (filled.some((item) => item.name.trim() === '')) {
    found.push(tr('Todo item precisa de um nome.'));
  }
  if (filled.some((item) => item.price === null || item.price < 0)) {
    found.push(tr('Todo item precisa de um preço (zero ou mais).'));
  }
  for (const rule of form.rules) {
    if (!rule.name.trim()) {
      found.push(tr('Dê um nome a cada variação por horário.'));
      break;
    }
  }
  for (const rule of form.rules) {
    const noEffect = rule.mode === 'percent' ? !rule.percent : Object.values(rule.fixed).every((price) => price === null || price === undefined);
    if (rule.name.trim() && noEffect) {
      found.push(tr('A variação "{name}" não muda nenhum preço.', { name: rule.name.trim() }));
    }
    if (rule.name.trim() && rule.start && rule.start === rule.end) {
      found.push(tr('A variação "{name}" começa e termina na mesma hora.', { name: rule.name.trim() }));
    }
    if (rule.name.trim() && rule.mode === 'percent' && rule.scope === 'some' && rule.itemIds.length === 0) {
      found.push(tr('Escolha os itens da variação "{name}" ou deixe valer para todos.', { name: rule.name.trim() }));
    }
  }
  return found;
}

/** Um nome de arquivo seguro para o CSV exportado. */
export function csvName(name: string): string {
  const plain = name.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
  return `precos-${plain || 'tabela'}.csv`;
}
