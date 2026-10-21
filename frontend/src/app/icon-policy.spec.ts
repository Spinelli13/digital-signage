import { readFileSync, readdirSync } from 'node:fs';
import { join, relative } from 'node:path';

import { ICONS } from './shared/icons';

/**
 * Política de ícones do painel: só ícones (Lucide, embutidos como SVG em shared/icons.ts), nunca emoji nem símbolo
 * decorativo digitado no texto. O motivo: o emoji muda de desenho em cada sistema, não herda a cor do tema, não tem o
 * mesmo peso visual do resto e o leitor de tela o lê por extenso ("foguete", "marca de verificação").
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

/** Tira os comentários (neles uma seta de documentação é inofensiva); sobra o que o usuário pode ver. */
function withoutComments(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/.*$/gm, '$1');
}

/** Emoji e pictogramas, símbolos diversos e dingbats, setas, formas geométricas e símbolos técnicos. */
const DECORATIVE = /[\u{1F000}-\u{1FFFF}\u{2190}-\u{21FF}\u{2300}-\u{23FF}\u{25A0}-\u{25FF}\u{2600}-\u{27BF}\u{2B00}-\u{2BFF}\u{2713}\u{2715}]/u;

describe('política de ícones', () => {
  it('nenhum emoji nem símbolo decorativo no texto do painel (use <app-icon>)', () => {
    const offenders: string[] = [];
    for (const path of sourceFiles(ROOT)) {
      if (path.endsWith('icons.ts')) {
        continue; // o registro só tem desenho SVG
      }
      withoutComments(readFileSync(path, 'utf8'))
        .split('\n')
        .forEach((line, index) => {
          const match = DECORATIVE.exec(line);
          if (match) {
            offenders.push(`${relative(ROOT, path)}:${index + 1} ${JSON.stringify(match[0])}`);
          }
        });
    }
    expect(offenders).toEqual([]);
  });

  it('os ícones usados por nome estático existem no registro', () => {
    const missing: string[] = [];
    for (const path of sourceFiles(ROOT)) {
      const source = readFileSync(path, 'utf8');
      for (const match of source.matchAll(/<app-icon\b[^>]*?\sname="([a-z0-9-]+)"/g)) {
        if (!(match[1] in ICONS)) {
          missing.push(`${relative(ROOT, path)}: ${match[1]}`);
        }
      }
      for (const match of source.matchAll(/\sicon="([a-z0-9-]+)"/g)) {
        if (!(match[1] in ICONS)) {
          missing.push(`${relative(ROOT, path)}: icon ${match[1]}`);
        }
      }
    }
    expect(missing).toEqual([]);
  });

  it('o registro é todo Lucide: desenhos de traço, na cor do texto, e sem script', () => {
    for (const [name, markup] of Object.entries(ICONS)) {
      expect(markup, name).not.toMatch(/<script|on\w+=|javascript:/i);
      expect(markup, name).not.toMatch(/\sfill="(?!none|currentColor)/); // só os pontinhos do Lucide levam fill=currentColor
    }
    expect(Object.keys(ICONS).length).toBeGreaterThan(80);
  });

  it('as licenças de terceiros (Lucide, Feather e Inter) estão registradas', () => {
    const notices = readFileSync(join(process.cwd(), 'THIRD_PARTY_NOTICES.md'), 'utf8');
    expect(notices).toContain('Lucide');
    expect(notices).toContain('Feather');
    expect(notices).toContain('Inter');
  });
});
