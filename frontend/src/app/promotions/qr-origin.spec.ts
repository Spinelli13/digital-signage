import { MAX_ORIGIN, cleanOrigin, linkWithOrigin, tvOrigin } from './qr-origin';

describe('origem do QR Code', () => {
  it('cleans what the person types into a short label without accents or symbols', () => {
    expect(cleanOrigin('Balcão da Frente!')).toBe('balcao-da-frente');
    expect(cleanOrigin('  Panfleto 2026  ')).toBe('panfleto-2026');
    expect(cleanOrigin('tv-12')).toBe('tv-12');
    expect(cleanOrigin('!!!')).toBe('');
    expect(cleanOrigin('')).toBe('');
  });

  it('never goes past the size the server accepts and never ends with a hyphen', () => {
    const cleaned = cleanOrigin('a'.repeat(30));
    expect(cleaned).toHaveLength(MAX_ORIGIN);
    expect(cleanOrigin('palavra-longa-demais-aqui-agora x')).toBe('palavra-longa-demais-aqu');
    expect(cleanOrigin('abcdefghijklmnopqrstuvw x').endsWith('-')).toBe(false);
  });

  it('names a TV origin by its id and appends the origin to the address only when there is one', () => {
    expect(tvOrigin(12)).toBe('tv-12');
    expect(linkWithOrigin('https://painel/cupom/abc', '')).toBe('https://painel/cupom/abc');
    expect(linkWithOrigin('https://painel/cupom/abc', 'tv-12')).toBe('https://painel/cupom/abc?o=tv-12');
  });
});
