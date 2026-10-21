/** Cores escolhidas pela pessoa (widgets e QR Codes): conferir o formato e medir o contraste. */

export type Rgb = [number, number, number];

const HEX = /^#[0-9a-f]{6}$/i;

/** "#rrggbb" (maiúsculas ou minúsculas) é uma cor válida? */
export function isHexColor(value: string | null | undefined): value is string {
  return typeof value === 'string' && HEX.test(value);
}

/** Normaliza o que a pessoa digitou: "8b5cf6", "#8B5CF6" e " #8b5cf6 " viram "#8b5cf6"; o que não for cor devolve null. */
export function normalizeHex(value: string | null | undefined): string | null {
  const text = (value ?? '').trim();
  const withHash = text.startsWith('#') ? text : `#${text}`;
  return HEX.test(withHash) ? withHash.toLowerCase() : null;
}

export function toRgb(hex: string): Rgb {
  return [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16)) as Rgb;
}

/** Luminância relativa (WCAG), de 0 (preto) a 1 (branco). */
export function luminance([r, g, b]: Rgb): number {
  const [lr, lg, lb] = [r, g, b].map((v) => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * lr + 0.7152 * lg + 0.0722 * lb;
}

/** Razão de contraste (WCAG) entre duas cores #rrggbb, de 1 (iguais) a 21 (preto e branco). */
export function contrastRatio(a: string, b: string): number {
  const la = luminance(toRgb(a));
  const lb = luminance(toRgb(b));
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

/** A cor de texto (claro ou escuro) que melhor se lê sobre o fundo dado: a mesma regra que o widget usa. */
export function readableOn(background: string): string {
  return luminance(toRgb(background)) < 0.36 ? '#fafafc' : '#14101f';
}
