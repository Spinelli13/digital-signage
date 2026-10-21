import { QR_THEMES, QrTooLongError, buildMatrix, colorsOf, defaultQrColors, drawQr, qrFileName, qrLayout, qrReadProblem, qrTextContrastLow } from './qr';

/** Um contexto 2D "de mentira" que só anota o que foi desenhado (o jsdom não tem canvas). */
function fakeContext() {
  const rects: { x: number; y: number; w: number; h: number; fill: string }[] = [];
  const texts: { text: string; y: number }[] = [];
  const ctx = {
    fillStyle: '',
    font: '',
    textAlign: '',
    fillRect(x: number, y: number, w: number, h: number) {
      rects.push({ x, y, w, h, fill: this.fillStyle });
    },
    fillText(text: string, _x: number, y: number) {
      texts.push({ text, y });
    },
    measureText(text: string) {
      return { width: text.length * 20 };
    },
  };
  return { ctx: ctx as unknown as CanvasRenderingContext2D, rects, texts };
}

describe('buildMatrix', () => {
  it('encodes a text as a square grid of modules', () => {
    const matrix = buildMatrix('https://exemplo.com/promo');

    expect(matrix.size).toBeGreaterThanOrEqual(21);
    // todo QR Code tem o quadrado de localização no canto superior esquerdo (7x7 com a borda escura)
    expect(matrix.isDark(0, 0)).toBe(true);
    expect(matrix.isDark(0, 6)).toBe(true);
    expect(matrix.isDark(1, 1)).toBe(false);
    expect(matrix.isDark(3, 3)).toBe(true);
  });

  it('is deterministic and grows with the text', () => {
    const small = buildMatrix('abc');
    const again = buildMatrix('abc');
    const big = buildMatrix('https://exemplo.com/uma/pagina/com/um/endereco/bem/comprido?parametro=valor&outro=valor2&mais=outro');

    expect(again.size).toBe(small.size);
    expect(big.size).toBeGreaterThan(small.size);
  });

  it('accepts accents and emoji', () => {
    expect(() => buildMatrix('Promoção 🎉 de verão')).not.toThrow();
  });

  it('refuses an empty text and a text that is too long', () => {
    expect(() => buildMatrix('   ')).toThrow('Informe o endereço ou o texto do QR Code');
    expect(() => buildMatrix('x'.repeat(5000))).toThrow(QrTooLongError);
  });
});

describe('qrLayout', () => {
  it('uses almost the whole image when there is no title or caption', () => {
    const layout = qrLayout(false, false, 1000);

    expect(layout.card.size).toBe(880);
    expect(layout.card.x).toBe(60);
    expect(layout.card.y).toBe(60);
  });

  it('leaves room for the title above and the caption below, keeping the card centred and square', () => {
    const layout = qrLayout(true, true, 1000);

    expect(layout.card.size).toBeLessThan(880);
    expect(layout.card.x * 2 + layout.card.size).toBe(1000);
    expect(layout.titleY).toBeLessThan(layout.card.y);
    expect(layout.captionY).toBeGreaterThan(layout.card.y + layout.card.size);
    expect(layout.card.y + layout.card.size).toBeLessThan(1000);
  });
});

describe('drawQr', () => {
  it('paints the background, the card and one square per dark module in the theme colours', () => {
    const { ctx, rects } = fakeContext();
    const text = 'https://exemplo.com';
    const matrix = buildMatrix(text);
    let dark = 0;
    for (let r = 0; r < matrix.size; r++) for (let c = 0; c < matrix.size; c++) if (matrix.isDark(r, c)) dark++;

    drawQr(ctx, { text, title: '', caption: '', theme: 'escuro' }, 1000);

    expect(rects[0]).toMatchObject({ x: 0, y: 0, w: 1000, h: 1000, fill: QR_THEMES.escuro.background });
    expect(rects[1].fill).toBe(QR_THEMES.escuro.card);
    // (no tema escuro o fundo tem a mesma cor dos módulos: depois do fundo e do cartão vêm só os módulos)
    expect(rects.slice(2).filter((r) => r.fill === QR_THEMES.escuro.modules)).toHaveLength(dark);
    expect(rects).toHaveLength(2 + dark);
  });

  it('writes the title and the caption, shortening what does not fit', () => {
    const { ctx, texts } = fakeContext();

    drawQr(ctx, { text: 'abc', title: 'Promoção', caption: 'x'.repeat(100), theme: 'claro' }, 1000);

    expect(texts[0].text).toBe('Promoção');
    expect(texts[1].text.endsWith('…')).toBe(true);
    expect(texts[1].text.length).toBeLessThan(100);
  });

  it('keeps the code dark on a light card in every theme (phones need the contrast)', () => {
    for (const colors of Object.values(QR_THEMES)) {
      expect(colors.card).toBe('#ffffff');
      expect(colors.modules).toBe('#0f0a16');
    }
  });
});

describe('qrFileName', () => {
  it('names the file after the title, or after the address without the scheme', () => {
    expect(qrFileName({ text: 'https://exemplo.com/promo', title: '' })).toBe('QR Code - exemplo.com promo.png');
    expect(qrFileName({ text: 'https://exemplo.com', title: 'Cardápio' })).toBe('QR Code - Cardápio.png');
    expect(qrFileName({ text: '   ', title: '' })).toBe('QR Code - sem nome.png');
  });
});

describe('QR Code com cores personalizadas', () => {
  const custom = { background: '#1f0a3d', card: '#fdf4ff', modules: '#3b0764', text: '#f5d0fe' };

  it('desenha com as cores escolhidas no tema personalizado e ignora-as nos temas prontos', () => {
    const personalized = fakeContext();
    drawQr(personalized.ctx, { text: 'https://exemplo.com', title: 'Oi', caption: '', theme: 'personalizado', colors: custom });
    const fills = personalized.rects.map((rect) => rect.fill);
    expect(fills[0]).toBe(custom.background);
    expect(fills[1]).toBe(custom.card);
    expect(fills.slice(2).every((fill) => fill === custom.modules)).toBe(true);

    const preset = fakeContext();
    drawQr(preset.ctx, { text: 'https://exemplo.com', title: '', caption: '', theme: 'violeta', colors: custom });
    expect(preset.rects[0].fill).toBe(QR_THEMES.violeta.background);
    expect(colorsOf({ theme: 'claro', colors: custom })).toEqual(QR_THEMES.claro);
    expect(colorsOf({ theme: 'personalizado' })).toEqual(defaultQrColors());
  });

  it('só deixa salvar se o celular conseguir ler: código escuro sobre cartão claro, com contraste', () => {
    expect(qrReadProblem(custom)).toBeNull();
    expect(qrReadProblem(defaultQrColors())).toBeNull();
    expect(qrReadProblem({ ...custom, modules: '#fdf4ff', card: '#3b0764' })).toBe('inverted');
    expect(qrReadProblem({ ...custom, modules: '#cccccc', card: '#ffffff' })).toBe('low');
    for (const preset of Object.values(QR_THEMES)) {
      expect(qrReadProblem(preset), preset.label).toBeNull();
    }
  });

  it('avisa quando o texto fica difícil de ler sobre o fundo', () => {
    expect(qrTextContrastLow(custom)).toBe(false);
    expect(qrTextContrastLow({ ...custom, text: '#2a1050' })).toBe(true);
  });
});
