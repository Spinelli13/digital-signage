import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * A identidade do Vyzzu (preto + violeta + magenta + prata) é regra do produto, não gosto: este teste a protege.
 *  1. o PRETO de antes do rebranding continua exatamente igual (um a um, nos dois temas);
 *  2. a paleta da marca está nos tokens;
 *  3. a cor da marca é violeta/magenta (nunca verde, nunca azul);
 *  4. verde só existe como estado de "tudo certo" (ou como o preto preservado) e azul não existe;
 *  5. os pares de cor do dia a dia têm contraste de acessibilidade (AA).
 * O guia da marca fica em docs/MARCA_VYZZU.md.
 */
const ROOT = process.cwd();
const css = readFileSync(join(ROOT, 'src', 'styles.css'), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');

/** Os tokens (`--nome: valor;`) de um bloco como `:root { … }`. */
function tokens(selector: string): Map<string, string> {
  const start = css.indexOf(selector);
  expect(start, `bloco ${selector}`).toBeGreaterThanOrEqual(0);
  const body = css.slice(css.indexOf('{', start) + 1, css.indexOf('\n}', start));
  const found = new Map<string, string>();
  for (const match of body.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) {
    found.set(match[1], match[2].trim());
  }
  return found;
}

const light = tokens(':root {');
const dark = tokens(":root[data-theme='dark'] {");

/** Os pretos e as superfícies escuras de ANTES do rebranding (valores do styles.css original). Não podem mudar. */
const PRESERVED_BLACKS: Record<'light' | 'dark', Record<string, string>> = {
  light: {
    '--text': '#0d1612',
    '--sidebar': '#0a0f0d',
    '--sidebar-2': '#101815',
    '--overlay': 'rgba(6, 12, 9, 0.6)',
    '--screen': '#0b0f0d',
  },
  dark: {
    '--bg': '#090e0c',
    '--panel': '#111917',
    '--panel-2': '#16201c',
    '--panel-3': '#1c2924',
    '--line': '#243029',
    '--line-strong': '#32413a',
    '--sidebar': '#060a08',
    '--sidebar-2': '#0b110f',
    '--overlay': 'rgba(0, 0, 0, 0.72)',
    '--screen': '#050807',
  },
};

/** Os tokens que, por origem, são pretos ou superfícies escuras (podem ter um leve fundo esverdeado de fábrica). */
const BLACK_TOKENS = new Set(['--text', '--sidebar', '--sidebar-2', '--overlay', '--screen', '--bg', '--panel', '--panel-2', '--panel-3', '--line', '--line-strong']);
/** O verde que ficou: só o significado de "tudo certo / online". */
const SUCCESS_TOKENS = new Set(['--green', '--ok-bg', '--ok-fg', '--ok-line']);
/** A cor da marca. */
const BRAND_TOKENS = ['--primary', '--primary-hover', '--primary-soft', '--primary-line', '--link', '--accent', '--violet', '--magenta', '--lavender'];

type Rgb = [number, number, number];

function parse(value: string): Rgb | null {
  const hex = value.match(/^#([0-9a-f]{6})$/i);
  if (hex) {
    return [0, 2, 4].map((i) => parseInt(hex[1].slice(i, i + 2), 16)) as Rgb;
  }
  const rgb = value.match(/^rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/);
  return rgb ? ([Number(rgb[1]), Number(rgb[2]), Number(rgb[3])] as Rgb) : null;
}

/** Tom (0–360), saturação e brilho (0–1). */
function hsv([r, g, b]: Rgb): { hue: number; sat: number; value: number } {
  const [rr, gg, bb] = [r, g, b].map((v) => v / 255);
  const max = Math.max(rr, gg, bb);
  const min = Math.min(rr, gg, bb);
  const delta = max - min;
  let hue = 0;
  if (delta > 0) {
    if (max === rr) hue = 60 * (((gg - bb) / delta) % 6);
    else if (max === gg) hue = 60 * ((bb - rr) / delta + 2);
    else hue = 60 * ((rr - gg) / delta + 4);
  }
  return { hue: (hue + 360) % 360, sat: max === 0 ? 0 : delta / max, value: max };
}

function luminance([r, g, b]: Rgb): number {
  const [lr, lg, lb] = [r, g, b].map((v) => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * lr + 0.7152 * lg + 0.0722 * lb;
}

function contrast(foreground: string, background: string): number {
  const a = luminance(parse(foreground) as Rgb);
  const b = luminance(parse(background) as Rgb);
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}

describe('identidade visual do Vyzzu', () => {
  it('mantém o preto de antes do rebranding, valor por valor, nos dois temas', () => {
    for (const [name, expected] of Object.entries(PRESERVED_BLACKS.light)) {
      expect(light.get(name), `tema claro ${name}`).toBe(expected);
    }
    for (const [name, expected] of Object.entries(PRESERVED_BLACKS.dark)) {
      expect(dark.get(name), `tema escuro ${name}`).toBe(expected);
    }
  });

  it('mantém o preto do Player (fundo das telas da TV)', () => {
    const colors = readFileSync(join(ROOT, '..', 'player', 'app', 'src', 'main', 'res', 'values', 'colors.xml'), 'utf8');
    expect(colors).toMatch(/<color name="brand_background">#070A09<\/color>/);
  });

  it('traz a paleta da marca: violeta, magenta, lavanda e prata', () => {
    expect(light.get('--violet')).toBe('#8b5cf6');
    expect(light.get('--magenta')).toBe('#d946ef');
    expect(light.get('--lavender')).toBe('#c4b5fd');
    expect(dark.get('--silver')).toBe('#a1a1aa');
    expect(dark.get('--text')).toBe('#f4f4f5');
  });

  it('usa só violeta e magenta nas cores da marca (nunca verde nem azul)', () => {
    for (const [theme, set] of [['claro', light], ['escuro', dark]] as const) {
      for (const name of BRAND_TOKENS) {
        const value = set.get(name) ?? light.get(name);
        const color = parse(value ?? '');
        expect(color, `${theme} ${name}`).not.toBeNull();
        const { hue } = hsv(color as Rgb);
        expect(hue, `${theme} ${name} (${value}) deve ser violeta/magenta`).toBeGreaterThanOrEqual(250);
        expect(hue, `${theme} ${name} (${value}) deve ser violeta/magenta`).toBeLessThanOrEqual(330);
      }
    }
  });

  it('só deixa verde nos estados de sucesso e não tem nenhum azul', () => {
    for (const [theme, set] of [['claro', light], ['escuro', dark]] as const) {
      for (const [name, value] of set) {
        const color = parse(value);
        if (!color || BLACK_TOKENS.has(name)) {
          continue;
        }
        const { hue, sat } = hsv(color);
        if (sat < 0.12) {
          continue; // cinza neutro
        }
        const isGreen = hue >= 70 && hue < 175;
        const isBlue = hue >= 175 && hue < 250;
        expect(isBlue, `${theme} ${name} (${value}) é azul`).toBe(false);
        expect(isGreen && !SUCCESS_TOKENS.has(name), `${theme} ${name} (${value}) é verde fora do estado de sucesso`).toBe(false);
      }
    }
  });

  it('não usa mais o verde da marca antiga em lugar nenhum da folha de estilos', () => {
    for (const old of ['#14774f', '#0f6443', '#2fcf92', '#3fe0a2', '#1a6a49', '#168659', '#62d19b', '#11693f', '#2a8f63', '#34a674']) {
      expect(css.toLowerCase(), old).not.toContain(old);
    }
  });

  describe('contraste (AA = 4,5:1 para texto)', () => {
    const pairs: [string, string, string, number][] = [
      ['texto', '--text', '--bg', 7],
      ['texto', '--text', '--panel', 7],
      ['texto secundário', '--text-2', '--panel', 7],
      ['texto auxiliar', '--muted', '--panel', 4.5],
      ['texto auxiliar no fundo', '--muted', '--bg', 4.5],
      ['link', '--link', '--panel', 4.5],
      ['texto do botão', '--on-primary', '--primary', 4.5],
      ['texto do botão (hover)', '--on-primary', '--primary-hover', 4.5],
      ['selo da marca', '--link', '--primary-soft', 4.5],
      ['sucesso', '--ok-fg', '--ok-bg', 4.5],
      ['alerta', '--warn-fg', '--warn-bg', 4.5],
      ['erro', '--err-fg', '--err-bg', 4.5],
      ['informação', '--info-fg', '--info-bg', 4.5],
    ];
    for (const [theme, set] of [['claro', light], ['escuro', dark]] as const) {
      for (const [label, foreground, background, minimum] of pairs) {
        it(`${theme}: ${label} (${foreground} sobre ${background})`, () => {
          const fg = set.get(foreground) ?? light.get(foreground) ?? '';
          const bg = set.get(background) ?? light.get(background) ?? '';
          expect(contrast(fg, bg), `${fg} sobre ${bg}`).toBeGreaterThanOrEqual(minimum);
        });
      }
    }

    it('menu lateral (sempre escuro): texto, texto auxiliar e destaque da marca', () => {
      for (const set of [light, dark]) {
        const sidebar = set.get('--sidebar') ?? '';
        expect(contrast(set.get('--sidebar-text') ?? light.get('--sidebar-text') ?? '', sidebar)).toBeGreaterThanOrEqual(7);
        expect(contrast(set.get('--sidebar-muted') ?? light.get('--sidebar-muted') ?? '', sidebar)).toBeGreaterThanOrEqual(4.5);
        expect(contrast(set.get('--accent') ?? '', sidebar)).toBeGreaterThanOrEqual(4.5);
      }
    });

    it('foco: o anel de foco do violeta aparece sobre o fundo (≥ 3:1 para componentes)', () => {
      expect(contrast('#8b5cf6', light.get('--panel') ?? '')).toBeGreaterThanOrEqual(3);
      expect(contrast('#a78bfa', dark.get('--panel') ?? '')).toBeGreaterThanOrEqual(3);
    });
  });
});
