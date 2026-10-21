import { PriceList } from '../core/models';
import { ItemDraft, PriceForm, RuleDraft, csvName, emptyForm, emptyItem, emptyRule, formFromList, newItemId, problems, toRequest } from './price-form';

const item = (overrides: Partial<ItemDraft> = {}): ItemDraft => ({ ...emptyItem(), id: 'a', name: 'Cerveja', price: 10, ...overrides });
const rule = (overrides: Partial<RuleDraft> = {}): RuleDraft => ({ ...emptyRule(), name: 'Happy hour', percent: -20, ...overrides });
const form = (overrides: Partial<PriceForm> = {}): PriceForm => ({ ...emptyForm(), name: 'Cardápio', items: [item()], ...overrides });

describe('price form', () => {
  it('starts with one empty row so the first thing to do is obvious', () => {
    const empty = emptyForm();

    expect(empty.items).toHaveLength(1);
    expect(empty.currency).toBe('BRL');
    expect(empty.timezone).toBe('America/Sao_Paulo');
  });

  it('gives every new item a short stable id', () => {
    expect(newItemId()).toMatch(/^[a-z0-9]{8}$/i);
    expect(emptyItem().id).not.toBe(emptyItem().id);
  });

  describe('toRequest', () => {
    it('trims, drops blank rows and keeps only what the server wants', () => {
      const request = toRequest(form({ name: '  Cardápio  ', items: [item({ category: ' Bebidas ', description: ' gelada ', unit: ' un ' }), emptyItem()] }));

      expect(request.name).toBe('Cardápio');
      expect(request.items).toHaveLength(1);
      expect(request.items[0]).toEqual({ id: 'a', category: 'Bebidas', name: 'Cerveja', description: 'gelada', price: 10, unit: 'un', available: true, highlight: false });
    });

    it('turns empty optional texts into null', () => {
      const request = toRequest(form());

      expect(request.items[0].category).toBeNull();
      expect(request.items[0].description).toBeNull();
      expect(request.items[0].unit).toBeNull();
    });

    it('a percent rule sends the percent and only the chosen items (or all of them)', () => {
      const items = [item(), item({ id: 'b', name: 'Suco' })];

      const all = toRequest(form({ items, rules: [rule({ days: [5, 1], start: '17:00', end: '20:00' })] })).rules[0];
      expect(all.days).toEqual([1, 5]);
      expect(all.start).toBe('17:00');
      expect(all.itemIds).toEqual([]);
      expect(all.percent).toBe(-20);

      const some = toRequest(form({ items, rules: [rule({ scope: 'some', itemIds: ['b', 'fantasma'] })] })).rules[0];
      expect(some.itemIds).toEqual(['b']); // um item que não existe mais é descartado
    });

    it('a fixed rule sends the prices of the filled items only and no percent', () => {
      const items = [item(), item({ id: 'b', name: 'Suco' })];

      const request = toRequest(form({ items, rules: [rule({ mode: 'fixed', percent: 15, fixed: { a: 7.5, b: null, x: 1 } })] }));

      expect(request.rules[0].percent).toBe(0);
      expect(request.rules[0].itemPrices).toEqual({ a: 7.5 });
      expect(request.rules[0].itemIds).toEqual([]);
    });

    it('blank hours mean the whole day', () => {
      const request = toRequest(form({ rules: [rule({ start: '', end: '' })] }));

      expect(request.rules[0].start).toBeNull();
      expect(request.rules[0].end).toBeNull();
    });
  });

  describe('formFromList', () => {
    const list: PriceList = {
      id: 3,
      name: 'Bar',
      currency: 'USD',
      timezone: 'UTC',
      widgetCount: 1,
      updatedAt: '',
      items: [{ id: 'a', category: null, name: 'Cerveja', description: null, price: 10, unit: null, available: false, highlight: true }],
      rules: [
        { name: 'Happy', days: [1, 2], start: '17:00', end: '20:00', percent: -20, itemIds: ['a'], itemPrices: {} },
        { name: 'Executivo', days: [], start: null, end: null, percent: 0, itemIds: [], itemPrices: { a: 7 } },
      ],
    };

    it('brings the table back as the person edits it', () => {
      const loaded = formFromList(list);

      expect(loaded.currency).toBe('USD');
      expect(loaded.items[0]).toMatchObject({ category: '', unit: '', available: false, highlight: true });
      expect(loaded.rules[0]).toMatchObject({ mode: 'percent', scope: 'some', percent: -20, itemIds: ['a'] });
      expect(loaded.rules[1]).toMatchObject({ mode: 'fixed', scope: 'all', percent: null, fixed: { a: 7 }, start: '' });
    });

    it('loading and sending back changes nothing', () => {
      const again = toRequest(formFromList(list));

      expect(again.items).toEqual(list.items);
      expect(again.rules[0]).toEqual(list.rules[0]);
      expect(again.rules[1]).toEqual({ ...list.rules[1], start: null, end: null });
    });
  });

  describe('problems', () => {
    it('a valid table has none', () => {
      expect(problems(form({ rules: [rule()] }))).toEqual([]);
    });

    it('asks for the name, and for a name and a price in every filled row', () => {
      expect(problems(form({ name: ' ' }))).toEqual(['Dê um nome à tabela.']);
      expect(problems(form({ items: [item({ name: '' })] }))).toContain('Todo item precisa de um nome.');
      expect(problems(form({ items: [item({ price: null })] }))).toContain('Todo item precisa de um preço (zero ou mais).');
      expect(problems(form({ items: [item({ price: -1 })] }))).toContain('Todo item precisa de um preço (zero ou mais).');
    });

    it('a blank row is not a problem', () => {
      expect(problems(form({ items: [item(), emptyItem()] }))).toEqual([]);
    });

    it('explains each rule that does nothing', () => {
      expect(problems(form({ rules: [rule({ name: '' })] }))).toEqual(['Dê um nome a cada variação por horário.']);
      expect(problems(form({ rules: [rule({ percent: null })] }))).toEqual(['A variação "Happy hour" não muda nenhum preço.']);
      expect(problems(form({ rules: [rule({ mode: 'fixed', fixed: {} })] }))).toEqual(['A variação "Happy hour" não muda nenhum preço.']);
      expect(problems(form({ rules: [rule({ start: '17:00', end: '17:00' })] }))).toEqual(['A variação "Happy hour" começa e termina na mesma hora.']);
      expect(problems(form({ rules: [rule({ scope: 'some', itemIds: [] })] }))).toEqual(['Escolha os itens da variação "Happy hour" ou deixe valer para todos.']);
    });
  });

  it('names the spreadsheet after the table, without accents or odd characters', () => {
    expect(csvName('Cardápio do Bar & Cia')).toBe('precos-cardapio-do-bar-cia.csv');
    expect(csvName('???')).toBe('precos-tabela.csv');
  });
});
