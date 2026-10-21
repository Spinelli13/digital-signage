import { contrastRatio, isHexColor, normalizeHex, readableOn, toRgb } from './color';

describe('cores escolhidas pela pessoa', () => {
  it('só aceita #rrggbb', () => {
    expect(isHexColor('#8b5cf6')).toBe(true);
    expect(isHexColor('#8B5CF6')).toBe(true);
    for (const invalid of ['8b5cf6', '#fff', '#12345g', 'roxo', '', null, undefined]) {
      expect(isHexColor(invalid), String(invalid)).toBe(false);
    }
  });

  it('normaliza o que a pessoa digita', () => {
    expect(normalizeHex('8b5cf6')).toBe('#8b5cf6');
    expect(normalizeHex(' #8B5CF6 ')).toBe('#8b5cf6');
    expect(normalizeHex('#8b5c')).toBeNull();
    expect(normalizeHex('')).toBeNull();
    expect(normalizeHex(null)).toBeNull();
  });

  it('converte para RGB', () => {
    expect(toRgb('#8b5cf6')).toEqual([139, 92, 246]);
  });

  it('mede o contraste como o WCAG', () => {
    expect(contrastRatio('#000000', '#ffffff')).toBeCloseTo(21, 0);
    expect(contrastRatio('#777777', '#777777')).toBe(1);
    expect(contrastRatio('#ffffff', '#7c3aed')).toBeGreaterThan(5);
    expect(contrastRatio('#8b5cf6', '#a78bfa')).toBeLessThan(2);
  });

  it('escolhe texto claro sobre fundo escuro e escuro sobre fundo claro', () => {
    expect(contrastRatio(readableOn('#0b0710'), '#0b0710')).toBeGreaterThan(12);
    expect(contrastRatio(readableOn('#faf8ff'), '#faf8ff')).toBeGreaterThan(12);
    expect(contrastRatio(readableOn('#6d28d9'), '#6d28d9')).toBeGreaterThan(4.5);
  });
});
