/**
 * Lê, no próprio navegador, o que o arquivo de vídeo diz sobre si mesmo (container MP4/QuickTime, o que o iPhone grava):
 * codec, tamanho do quadro, duração, giro e HDR. Nada é enviado nem decodificado: só se leem as "caixas" (boxes) do
 * arquivo, saltando o conteúdo (mdat) pelo tamanho que cada caixa declara. Serve para avisar ANTES de enviar (vídeo
 * acima de 1080p, HEVC em TV antiga, HDR) e para preencher a duração mesmo quando o navegador não decodifica o vídeo
 * (o Chrome do Windows costuma não tocar HEVC).
 */
export interface VideoInfo {
  /** Os quatro caracteres do codec (avc1, hvc1, hev1, dvh1, av01, vp09…). */
  codec: string;
  /** Nome para a pessoa: H.264, HEVC (H.265), AV1… */
  codecName: string;
  /** Tamanho do quadro como a pessoa vê (já com o giro aplicado: um vídeo de celular em pé é 1080×1920). */
  width: number;
  height: number;
  /** 0, 90, 180 ou 270. */
  rotation: number;
  durationSeconds: number | null;
  hdr: boolean;
}

const MAX_MOOV_BYTES = 64 * 1024 * 1024;

const CODEC_NAMES: Record<string, string> = {
  avc1: 'H.264',
  avc3: 'H.264',
  hvc1: 'HEVC (H.265)',
  hev1: 'HEVC (H.265)',
  dvh1: 'HEVC (H.265) com Dolby Vision',
  dvhe: 'HEVC (H.265) com Dolby Vision',
  av01: 'AV1',
  vp09: 'VP9',
  mp4v: 'MPEG-4',
};

interface Box {
  type: string;
  /** Onde começa o conteúdo (depois do cabeçalho) e onde termina a caixa. */
  start: number;
  end: number;
}

/** null quando não é um MP4/MOV legível (o envio segue assim mesmo; só não há o que avisar). */
export async function probeVideo(file: Blob): Promise<VideoInfo | null> {
  try {
    const moov = await findMoov(file);
    if (!moov) {
      return null;
    }
    const data = await readBytes(file, moov.start, moov.end);
    return parseMoov(new DataView(data));
  } catch {
    return null;
  }
}

async function readBytes(file: Blob, start: number, end: number): Promise<ArrayBuffer> {
  const slice = file.slice(start, end);
  if (typeof slice.arrayBuffer === 'function') {
    return slice.arrayBuffer();
  }
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as ArrayBuffer);
    reader.onerror = () => reject(reader.error);
    reader.readAsArrayBuffer(slice);
  });
}

/** Anda pelas caixas do topo do arquivo (cada uma diz o próprio tamanho) até achar a "moov". */
async function findMoov(file: Blob): Promise<Box | null> {
  let offset = 0;
  while (offset + 8 <= file.size) {
    const header = new DataView(await readBytes(file, offset, Math.min(offset + 16, file.size)));
    let size = header.getUint32(0);
    const type = fourCC(header, 4);
    let headerSize = 8;
    if (size === 1 && header.byteLength >= 16) {
      size = Number(header.getBigUint64(8));
      headerSize = 16;
    } else if (size === 0) {
      size = file.size - offset;
    }
    if (size < headerSize) {
      return null;
    }
    if (type === 'moov') {
      const end = Math.min(offset + size, file.size);
      return end - (offset + headerSize) > MAX_MOOV_BYTES ? null : { type, start: offset + headerSize, end };
    }
    offset += size;
  }
  return null;
}

function parseMoov(view: DataView): VideoInfo | null {
  let timescale = 0;
  let movieDuration = 0;
  for (const box of children(view, 0, view.byteLength)) {
    if (box.type === 'mvhd') {
      const version = view.getUint8(box.start);
      timescale = view.getUint32(box.start + (version === 1 ? 20 : 12));
      movieDuration = version === 1 ? Number(view.getBigUint64(box.start + 24)) : view.getUint32(box.start + 16);
    }
  }

  for (const trak of children(view, 0, view.byteLength).filter((box) => box.type === 'trak')) {
    const video = parseTrack(view, trak);
    if (video) {
      const swap = video.rotation === 90 || video.rotation === 270;
      return {
        codec: video.codec,
        codecName: CODEC_NAMES[video.codec] ?? video.codec,
        width: swap ? video.height : video.width,
        height: swap ? video.width : video.height,
        rotation: video.rotation,
        durationSeconds: timescale > 0 && movieDuration > 0 ? Math.round((movieDuration / timescale) * 10) / 10 : null,
        hdr: video.hdr,
      };
    }
  }
  return null;
}

function parseTrack(view: DataView, trak: Box) {
  let rotation = 0;
  let isVideo = false;
  let codec = '';
  let width = 0;
  let height = 0;
  let hdr = false;

  for (const box of children(view, trak.start, trak.end)) {
    if (box.type === 'tkhd') {
      rotation = rotationOf(view, box);
    }
    if (box.type === 'mdia') {
      for (const inner of children(view, box.start, box.end)) {
        if (inner.type === 'hdlr') {
          isVideo = fourCC(view, inner.start + 8) === 'vide';
        }
        if (inner.type === 'minf') {
          const stbl = children(view, inner.start, inner.end).find((b) => b.type === 'stbl');
          const stsd = stbl && children(view, stbl.start, stbl.end).find((b) => b.type === 'stsd');
          if (stsd) {
            // stsd: versão e flags (4) + número de entradas (4) + a primeira entrada de amostra
            const entryStart = stsd.start + 8;
            const entry = children(view, entryStart, stsd.end)[0];
            if (entry) {
              codec = entry.type;
              // entrada visual: o cabeçalho (8) + 6 reservados + 2 de índice + 16 de uso interno e então largura e altura
              width = view.getUint16(entryStart + 32);
              height = view.getUint16(entryStart + 34);
              hdr = isHdr(view, entry, entryStart + 86);
            }
          }
        }
      }
    }
  }
  return isVideo && codec ? { codec, width, height, rotation, hdr } : null;
}

/** Dolby Vision (dvcC/dvvC) ou transferência PQ/HLG na caixa de cor (colr com nclx). */
function isHdr(view: DataView, entry: Box, extrasStart: number): boolean {
  if (entry.type === 'dvh1' || entry.type === 'dvhe') {
    return true;
  }
  for (const box of children(view, extrasStart, entry.end)) {
    if (box.type === 'dvcC' || box.type === 'dvvC') {
      return true;
    }
    if (box.type === 'colr' && fourCC(view, box.start) === 'nclx') {
      const transfer = view.getUint16(box.start + 6);
      return transfer === 16 || transfer === 18;
    }
  }
  return false;
}

/** O giro vem da matriz da trilha: [a b …] com a e b em ponto fixo 16.16. */
function rotationOf(view: DataView, tkhd: Box): number {
  const version = view.getUint8(tkhd.start);
  const matrix = tkhd.start + (version === 1 ? 52 : 40);
  const a = view.getInt32(matrix) / 65536;
  const b = view.getInt32(matrix + 4) / 65536;
  const degrees = Math.round((Math.atan2(b, a) * 180) / Math.PI);
  return ((degrees % 360) + 360) % 360;
}

function children(view: DataView, start: number, end: number): Box[] {
  const boxes: Box[] = [];
  let offset = start;
  while (offset + 8 <= end) {
    let size = view.getUint32(offset);
    const type = fourCC(view, offset + 4);
    let headerSize = 8;
    if (size === 1 && offset + 16 <= end) {
      size = Number(view.getBigUint64(offset + 8));
      headerSize = 16;
    } else if (size === 0) {
      size = end - offset;
    }
    if (size < headerSize || offset + size > end) {
      break;
    }
    boxes.push({ type, start: offset + headerSize, end: offset + size });
    offset += size;
  }
  return boxes;
}

function fourCC(view: DataView, offset: number): string {
  return String.fromCharCode(view.getUint8(offset), view.getUint8(offset + 1), view.getUint8(offset + 2), view.getUint8(offset + 3));
}

/** Acima de Full HD (1920×1080, em pé ou deitado)? */
export function exceedsFullHd(info: Pick<VideoInfo, 'width' | 'height'>): boolean {
  return Math.max(info.width, info.height) > 1920 || Math.min(info.width, info.height) > 1080;
}

/** O arquivo parece ser um vídeo? Alguns celulares/navegadores deixam o tipo vazio em .mov. */
export function looksLikeVideo(file: { name: string; type: string }): boolean {
  return file.type.startsWith('video/') || /\.(mp4|mov|m4v)$/i.test(file.name);
}
