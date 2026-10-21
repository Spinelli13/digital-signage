/**
 * Monta, para os testes, um MP4/MOV mínimo só com as caixas que o leitor de vídeo do painel consulta (o mesmo desenho
 * que o iPhone grava). Não é um vídeo tocável.
 */
export const ascii = (text: string) => Array.from(text).map((c) => c.charCodeAt(0));
export const u16 = (n: number) => [(n >> 8) & 255, n & 255];
const u32 = (n: number) => [(n >>> 24) & 255, (n >>> 16) & 255, (n >>> 8) & 255, n & 255];
const fixed = (n: number) => u32(Math.round(n * 65536) >>> 0);

export function box(type: string, ...payload: number[][]): number[] {
  const body = payload.flat();
  return [...u32(8 + body.length), ...ascii(type), ...body];
}

export interface VideoOptions {
  codec?: string;
  width?: number;
  height?: number;
  rotation?: 0 | 90 | 180 | 270;
  timescale?: number;
  duration?: number;
  extras?: number[];
  handler?: string;
  moovFirst?: boolean;
}

export function buildVideo(o: VideoOptions = {}): Blob {
  const { codec = 'avc1', width = 1920, height = 1080, rotation = 0, timescale = 600, duration = 6000, extras = [], handler = 'vide', moovFirst = false } = o;
  const [a, b] = { 0: [1, 0], 90: [0, 1], 180: [-1, 0], 270: [0, -1] }[rotation];
  const matrix = [...fixed(a), ...fixed(b), ...u32(0), ...fixed(-b), ...fixed(a), ...u32(0), ...u32(0), ...u32(0), ...u32(0x40000000)];
  const tkhd = box('tkhd', u32(0x0000000f), u32(0), u32(0), u32(1), u32(0), u32(duration), u32(0), u32(0), u16(0), u16(0), u16(0), u16(0), matrix, fixed(width), fixed(height));
  const entry = box(codec, new Array(6).fill(0), u16(1), new Array(16).fill(0), u16(width), u16(height), new Array(50).fill(0), extras);
  const stsd = box('stsd', u32(0), u32(1), entry);
  const stbl = box('stbl', stsd);
  const minf = box('minf', stbl);
  const hdlr = box('hdlr', u32(0), u32(0), ascii(handler), new Array(12).fill(0), [0]);
  const mdia = box('mdia', hdlr, minf);
  const trak = box('trak', tkhd, mdia);
  const mvhd = box('mvhd', u32(0), u32(0), u32(0), u32(timescale), u32(duration), new Array(80).fill(0));
  const moov = box('moov', mvhd, trak);
  const ftyp = box('ftyp', ascii('qt  '), u32(0), ascii('qt  '));
  const mdat = box('mdat', new Array(4096).fill(7));
  return new Blob([new Uint8Array(moovFirst ? [...ftyp, ...moov, ...mdat] : [...ftyp, ...mdat, ...moov])]);
}

