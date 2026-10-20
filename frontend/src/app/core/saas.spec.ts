import {
  dateInput,
  daysUntil,
  formatLimit,
  formatMoney,
  formatStorage,
  moneyInput,
  parseMoney,
  usagePercent,
  usageRows,
} from './saas';
import { PLAN } from '../testing/fixtures';

describe('saas helpers', () => {
  it('formats money in reais', () => {
    expect(formatMoney(4900).replace(/\s/g, ' ')).toBe('R$ 49,00');
    expect(formatMoney(0).replace(/\s/g, ' ')).toBe('R$ 0,00');
  });

  it('parses what people type as a price', () => {
    expect(parseMoney('49')).toBe(4900);
    expect(parseMoney('49,90')).toBe(4990);
    expect(parseMoney('49.90')).toBe(4990);
    expect(parseMoney('R$ 1.249,90')).toBe(124990);
    expect(parseMoney('')).toBeNull();
    expect(parseMoney('abc')).toBeNull();
    expect(parseMoney('-5')).toBeNull();
  });

  it('shows cents in the input format', () => {
    expect(moneyInput(4990)).toBe('49,90');
    expect(moneyInput(0)).toBe('0,00');
  });

  it('turns a null limit into "ilimitado"', () => {
    expect(formatLimit(null)).toBe('ilimitado');
    expect(formatLimit(5)).toBe('5');
  });

  it('formats storage in MB or GB', () => {
    expect(formatStorage(0)).toBe('0,0 MB');
    expect(formatStorage(300 * 1024 * 1024)).toBe('300 MB');
    expect(formatStorage(2048 * 1024 * 1024)).toBe('2,0 GB');
  });

  it('computes how much of a limit is used, capped at 100 and zero when unlimited', () => {
    expect(usagePercent(1, 4)).toBe(25);
    expect(usagePercent(9, 4)).toBe(100);
    expect(usagePercent(3, null)).toBe(0);
    expect(usagePercent(3, 0)).toBe(0);
  });

  it('builds the usage rows against the plan limits', () => {
    const rows = usageRows(PLAN, { devices: 5, storageBytes: 1024 * 1024 * 1024, users: 1 });

    expect(rows.map((row) => row.label)).toEqual(['TVs', 'Espaço de mídias', 'Usuários']);
    expect(rows[0]).toEqual({ label: 'TVs', text: '5 de 5', percent: 100 });
    expect(rows[1].text).toBe('1,0 GB de 2,0 GB');
    expect(rows[1].percent).toBe(50);
    expect(rows[2].percent).toBe(20);
  });

  it('counts whole days until a date', () => {
    const now = new Date('2026-10-20T12:00:00Z').getTime();

    expect(daysUntil('2026-10-23T12:00:00Z', now)).toBe(3);
    expect(daysUntil('2026-10-20T12:00:01Z', now)).toBe(1);
    expect(daysUntil('2026-10-19T00:00:00Z', now)).toBeLessThanOrEqual(0);
  });

  it('round-trips a date through the date input', () => {
    const iso = new Date(2026, 9, 5, 23, 59, 59).toISOString();

    expect(dateInput(iso)).toBe('2026-10-05');
    expect(dateInput(null)).toBe('');
  });
});
