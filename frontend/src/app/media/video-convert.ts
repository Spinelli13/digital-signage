import { InjectionToken } from '@angular/core';

import { VideoInfo } from './video-probe';

/**
 * Converte, no próprio navegador e ANTES de enviar, o vídeo para MP4 (H.264) em até Full HD. O que o iPhone grava
 * costuma ser MOV com HEVC em 4K e HDR: pesado para enviar (o limite do servidor é de 50 MB) e para TVs e caixas
 * mais simples tocarem. O servidor não converte nada (a hospedagem gratuita não aguenta), então quem trabalha é o
 * aparelho de quem envia, com os codecs do próprio navegador (WebCodecs, aceleração por hardware).
 *
 * As regras (quando converter, em que tamanho, com que taxa de bits) são funções puras, testadas à parte; a conversão
 * em si só roda num navegador de verdade e carrega a biblioteca sob demanda (não pesa em quem nunca envia vídeo).
 */

/** O limite do servidor para um arquivo (ver docs/DEPLOY.md: bucket de mídia). */
export const MAX_UPLOAD_BYTES = 50 * 1024 * 1024;
/** Mira bem abaixo do limite: o codificador passa até ~17% da taxa pedida em imagens muito agitadas (medido no Edge com ruído). */
const TARGET_BYTES = 40 * 1024 * 1024;
const FULL_HD_LONG = 1920;
const FULL_HD_SHORT = 1080;
/** A taxa de bits de um vídeo Full HD bom e leve para TV (H.264 High). Resoluções menores usam proporcionalmente menos. */
const FULL_HD_BITRATE = 8_000_000;
const MIN_BITRATE = 1_500_000;
/** Abaixo disto a imagem já fica ruim demais: prefira avisar a entregar um vídeo borrado. */
const WORST_ACCEPTABLE_BITRATE = 2_500_000;
const AUDIO_ALLOWANCE = 0.93;

/** Por que o arquivo precisa ser convertido (para explicar à pessoa e para os testes). */
export type ConvertReason = 'container' | 'codec' | 'resolution' | 'hdr' | 'bytes';

export interface ConversionPlan {
  reasons: ConvertReason[];
  width: number;
  height: number;
  /** Bits por segundo do vídeo convertido. */
  bitrate: number;
  /** false = nem na menor qualidade aceitável o vídeo cabe no limite de 50 MB. */
  fits: boolean;
  /** Quantos bytes o arquivo convertido deve ter, aproximadamente (nulo sem a duração). */
  estimatedBytes: number | null;
}

const H264 = /^avc[13]$/;

/** O arquivo é um MOV (o contêiner do iPhone), mesmo quando o navegador não informa o tipo? */
export function isQuickTime(file: { name: string; type: string }): boolean {
  return file.type === 'video/quicktime' || /\.mov$/i.test(file.name);
}

/**
 * Precisa converter? Nulo = enviar como está. MP4 em H.264 até Full HD e dentro do limite de tamanho não é mexido
 * (converter de novo só perderia qualidade).
 */
export function planConversion(file: { name: string; type: string; size: number }, info: VideoInfo): ConversionPlan | null {
  const reasons: ConvertReason[] = [];
  if (isQuickTime(file)) {
    reasons.push('container');
  }
  if (!H264.test(info.codec)) {
    reasons.push('codec');
  }
  if (Math.max(info.width, info.height) > FULL_HD_LONG || Math.min(info.width, info.height) > FULL_HD_SHORT) {
    reasons.push('resolution');
  }
  if (info.hdr) {
    reasons.push('hdr');
  }
  if (file.size > MAX_UPLOAD_BYTES) {
    reasons.push('bytes');
  }
  if (reasons.length === 0) {
    return null;
  }

  const { width, height } = targetSize(info);
  const wanted = Math.max(MIN_BITRATE, Math.round((FULL_HD_BITRATE * width * height) / (FULL_HD_LONG * FULL_HD_SHORT)));
  const seconds = info.durationSeconds && info.durationSeconds > 0 ? info.durationSeconds : null;
  if (seconds === null) {
    return { reasons, width, height, bitrate: wanted, fits: true, estimatedBytes: null };
  }
  // Cabe o que o limite deixa; se o vídeo é longo, a qualidade cai até um piso (abaixo dele a conversão não compensa).
  const budget = Math.floor((TARGET_BYTES * 8 * AUDIO_ALLOWANCE) / seconds);
  const bitrate = Math.min(wanted, Math.max(budget, MIN_BITRATE));
  const estimatedBytes = Math.round((bitrate * seconds) / (8 * AUDIO_ALLOWANCE));
  return { reasons, width, height, bitrate, fits: budget >= Math.min(wanted, WORST_ACCEPTABLE_BITRATE), estimatedBytes };
}

/** O quadro cabe em 1920×1080 (ou 1080×1920 em pé) sem ampliar e com medidas pares (o H.264 exige). */
export function targetSize(info: Pick<VideoInfo, 'width' | 'height'>): { width: number; height: number } {
  const long = Math.max(info.width, info.height);
  const short = Math.min(info.width, info.height);
  const scale = Math.min(1, FULL_HD_LONG / long, FULL_HD_SHORT / short);
  const even = (value: number) => Math.max(2, Math.round((value * scale) / 2) * 2);
  return { width: even(info.width), height: even(info.height) };
}

/** O mesmo nome com a extensão trocada para .mp4. */
export function mp4Name(name: string): string {
  const base = name.replace(/\.[^./\\]+$/, '');
  return `${base || 'video'}.mp4`;
}

export interface ConvertedVideo {
  file: File;
  /** O que não deu para manter (por exemplo, o som, num navegador que não sabe regravar o áudio). */
  warnings: ConversionWarning[];
}

export type ConversionWarning = 'no-audio';

export interface VideoConverter {
  /** Este navegador sabe regravar vídeo em H.264? (Chrome, Edge e Safari recentes sim; o Firefox em geral não.) */
  canConvert(): boolean;
  /** Converte; rejeita se o navegador não consegue decodificar o vídeo de origem (HEVC no Chrome sem hardware, por exemplo). */
  convert(file: File, plan: ConversionPlan, info: VideoInfo, onProgress: (fraction: number) => void): Promise<ConvertedVideo>;
}

/** Trocável nos testes: a conversão de verdade só roda num navegador. */
export const VIDEO_CONVERTER = new InjectionToken<VideoConverter>('VIDEO_CONVERTER', {
  providedIn: 'root',
  factory: () => browserConverter,
});

const browserConverter: VideoConverter = {
  canConvert: () => typeof VideoEncoder === 'function' && typeof VideoDecoder === 'function',

  async convert(file, plan, info, onProgress) {
    const lib = await import('mediabunny');
    const input = new lib.Input({ source: new lib.BlobSource(file), formats: lib.ALL_FORMATS });
    try {
      const track = await input.getPrimaryVideoTrack();
      if (!track) {
        throw new Error('no-video-track');
      }
      // Passar de 30 quadros por segundo só pesa para a TV e para o tamanho do arquivo.
      let frameRate: number | undefined;
      try {
        const stats = await track.computePacketStats(120);
        if (stats.averagePacketRate > 31) {
          frameRate = 30;
        }
      } catch {
        frameRate = undefined;
      }

      const output = new lib.Output({ format: new lib.Mp4OutputFormat({ fastStart: 'in-memory' }), target: new lib.BufferTarget() });
      const conversion = await lib.Conversion.init({
        input,
        output,
        video: {
          codec: 'avc',
          width: plan.width,
          height: plan.height,
          fit: 'fill',
          quality: new lib.Quality({ bitrate: plan.bitrate }),
          ...(frameRate ? { frameRate } : {}),
          // HDR: passar pela tela de desenho converte para as cores de sempre (SDR); sem isso o H.264 sairia desbotado.
          ...(info.hdr ? { crop: { left: 0, top: 0, width: info.width, height: info.height } } : {}),
        },
        audio: { codec: 'aac' },
      });
      if (!conversion.isValid) {
        throw new Error('invalid-conversion');
      }
      const warnings: ConversionWarning[] = conversion.discardedTracks.some((discarded) => discarded.track.isAudioTrack()) ? ['no-audio'] : [];
      conversion.onProgress = (fraction) => onProgress(Math.min(1, Math.max(0, fraction)));
      await conversion.execute();
      const buffer = output.target.buffer;
      if (!buffer) {
        throw new Error('empty-output');
      }
      return {
        file: new File([buffer], mp4Name(file.name), { type: 'video/mp4', lastModified: file.lastModified }),
        warnings,
      };
    } finally {
      input.dispose();
    }
  },
};
