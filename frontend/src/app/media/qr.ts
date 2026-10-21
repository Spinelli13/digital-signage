import qrcode from 'qrcode-generator';

import { contrastRatio, luminance, toRgb } from '../core/color';
import { tr } from '../core/i18n';

export type QrPresetTheme = 'claro' | 'escuro' | 'violeta' | 'verde';
/** Os visuais prontos mais o "personalizado", em que a pessoa escolhe as quatro cores. */
export type QrTheme = QrPresetTheme | 'personalizado';

/** As quatro cores de um QR Code: o fundo da imagem, o texto (título e legenda), o cartão do código e os módulos do código. */
export interface QrColors {
  background: string;
  card: string;
  modules: string;
  text: string;
}

export interface QrThemeColors extends QrColors {
  label: string;
}

/** Os visuais prontos do QR Code. Nos prontos, o cartão do código é claro com módulos escuros: leitores de celular exigem contraste. */
export const QR_THEMES: Record<QrPresetTheme, QrThemeColors> = {
  claro: { label: 'Claro', background: '#ffffff', card: '#ffffff', modules: '#0f0a16', text: '#0f0a16' },
  escuro: { label: 'Escuro', background: '#0f0a16', card: '#ffffff', modules: '#0f0a16', text: '#ffffff' },
  violeta: { label: 'Violeta', background: '#6d28d9', card: '#ffffff', modules: '#0f0a16', text: '#ffffff' },
  verde: { label: 'Verde', background: '#1a6a49', card: '#ffffff', modules: '#0f0a16', text: '#ffffff' },
};

/** As cores com que o "personalizado" começa (as do visual escuro). */
export function defaultQrColors(): QrColors {
  const { background, card, modules, text } = QR_THEMES.escuro;
  return { background, card, modules, text };
}

/**
 * O QR Code só é lido pelo celular com o código ESCURO sobre o cartão CLARO e com bom contraste. Devolve o problema quando as
 * cores escolhidas não permitem isso (e a imagem não deve ser salva), ou null se estiver tudo certo.
 */
export function qrReadProblem(colors: QrColors): 'inverted' | 'low' | null {
  if (luminance(toRgb(colors.modules)) >= luminance(toRgb(colors.card))) {
    return 'inverted';
  }
  return contrastRatio(colors.modules, colors.card) < 3 ? 'low' : null;
}

/** Título e legenda sobre o fundo: contraste menor que 4,5:1 é difícil de ler (só um aviso, não impede). */
export function qrTextContrastLow(colors: QrColors): boolean {
  return contrastRatio(colors.text, colors.background) < 4.5;
}

/** O QR Code como uma grade de módulos escuros/claros. */
export interface QrMatrix {
  size: number;
  isDark(row: number, column: number): boolean;
}

/** Texto longo demais para o QR Code (o limite do tipo mais alto, com correção de erro média, é de uns 2.300 caracteres). */
export class QrTooLongError extends Error {
  constructor() {
    super(tr('O texto é grande demais para um QR Code. Use um endereço mais curto.'));
  }
}

export function buildMatrix(text: string): QrMatrix {
  const content = text.trim();
  if (!content) {
    throw new Error(tr('Informe o endereço ou o texto do QR Code'));
  }
  const code = qrcode(0, 'M'); // tipo automático, correção de erro média
  code.addData(content, 'Byte');
  try {
    code.make();
  } catch {
    throw new QrTooLongError();
  }
  return { size: code.getModuleCount(), isDark: (row, column) => code.isDark(row, column) };
}

export const QR_IMAGE_SIZE = 1080;

export interface QrLayout {
  /** Altura reservada ao título (0 = sem título). */
  titleHeight: number;
  card: { x: number; y: number; size: number };
  /** Posição vertical (linha de base) da legenda. */
  captionY: number;
  titleY: number;
}

/**
 * Onde cada coisa vai na imagem quadrada: título no alto (se houver), o cartão do QR Code no meio e a legenda embaixo.
 * Sem título nem legenda o código ocupa quase tudo (quanto maior, mais fácil de ler de longe).
 */
export function qrLayout(hasTitle: boolean, hasCaption: boolean, size = QR_IMAGE_SIZE): QrLayout {
  const margin = Math.round(size * 0.06);
  const titleHeight = hasTitle ? Math.round(size * 0.14) : 0;
  const captionHeight = hasCaption ? Math.round(size * 0.12) : 0;
  const available = size - 2 * margin - titleHeight - captionHeight;
  const cardSize = Math.min(available, size - 2 * margin);
  const cardY = margin + titleHeight + Math.round((available - cardSize) / 2);
  return {
    titleHeight,
    card: { x: Math.round((size - cardSize) / 2), y: cardY, size: cardSize },
    titleY: margin + Math.round(titleHeight * 0.7),
    captionY: cardY + cardSize + Math.round(captionHeight * 0.7),
  };
}

export interface QrDrawing {
  text: string;
  title: string;
  caption: string;
  theme: QrTheme;
  /** As cores do tema "personalizado" (ignoradas nos temas prontos). */
  colors?: QrColors;
}

/** As cores que valem para o desenho: as do tema pronto ou, no personalizado, as que a pessoa escolheu. */
export function colorsOf(drawing: Pick<QrDrawing, 'theme' | 'colors'>): QrColors {
  return drawing.theme === 'personalizado' ? (drawing.colors ?? defaultQrColors()) : QR_THEMES[drawing.theme];
}

/** Desenha o QR Code (fundo, título, cartão com os módulos e legenda) num contexto 2D. */
export function drawQr(ctx: CanvasRenderingContext2D, drawing: QrDrawing, size = QR_IMAGE_SIZE): void {
  const colors = colorsOf(drawing);
  const matrix = buildMatrix(drawing.text);
  const title = drawing.title.trim();
  const caption = drawing.caption.trim();
  const layout = qrLayout(title.length > 0, caption.length > 0, size);

  ctx.fillStyle = colors.background;
  ctx.fillRect(0, 0, size, size);

  ctx.fillStyle = colors.text;
  ctx.textAlign = 'center';
  if (title) {
    ctx.font = `bold ${Math.round(size * 0.07)}px system-ui, sans-serif`;
    ctx.fillText(fit(ctx, title, size * 0.88), size / 2, layout.titleY);
  }
  if (caption) {
    ctx.font = `${Math.round(size * 0.045)}px system-ui, sans-serif`;
    ctx.fillText(fit(ctx, caption, size * 0.88), size / 2, layout.captionY);
  }

  // cartão claro com uma "zona de silêncio" de 4 módulos ao redor (exigência dos leitores de QR Code)
  const { x, y, size: cardSize } = layout.card;
  ctx.fillStyle = colors.card;
  ctx.fillRect(x, y, cardSize, cardSize);
  const quiet = 4;
  const module = Math.floor(cardSize / (matrix.size + 2 * quiet));
  const used = module * matrix.size;
  const offsetX = x + Math.round((cardSize - used) / 2);
  const offsetY = y + Math.round((cardSize - used) / 2);
  ctx.fillStyle = colors.modules;
  for (let row = 0; row < matrix.size; row++) {
    for (let column = 0; column < matrix.size; column++) {
      if (matrix.isDark(row, column)) {
        ctx.fillRect(offsetX + column * module, offsetY + row * module, module, module);
      }
    }
  }
}

/** Corta o texto com "…" até caber na largura. */
function fit(ctx: CanvasRenderingContext2D, text: string, maxWidth: number): string {
  if (ctx.measureText(text).width <= maxWidth) {
    return text;
  }
  let cut = text;
  while (cut.length > 1 && ctx.measureText(cut + '…').width > maxWidth) {
    cut = cut.slice(0, -1);
  }
  return cut + '…';
}

/** Nome do arquivo na biblioteca: "QR Code - <título ou endereço>.png". */
export function qrFileName(drawing: Pick<QrDrawing, 'text' | 'title'>): string {
  const base = (drawing.title.trim() || drawing.text.trim().replace(/^https?:\/\//i, '')).replace(/[\\/:*?"<>|]+/g, ' ').trim();
  return `QR Code - ${base.slice(0, 60) || tr('sem nome')}.png`;
}
