import { ascii, box, buildVideo as video, u16 } from '../testing/mp4';
import { exceedsFullHd, looksLikeVideo, probeVideo } from './video-probe';

describe('probeVideo', () => {
  it('lê codec, tamanho e duração de um vídeo do iPhone (moov no fim do arquivo)', async () => {
    const info = await probeVideo(video({ codec: 'hvc1', width: 1920, height: 1080, duration: 6000 }));

    expect(info).toMatchObject({ codec: 'hvc1', codecName: 'HEVC (H.265)', width: 1920, height: 1080, rotation: 0, durationSeconds: 10, hdr: false });
  });

  it('acha a moov também quando ela vem antes dos dados (arquivo "fast start")', async () => {
    const info = await probeVideo(video({ moovFirst: true, codec: 'avc1' }));

    expect(info?.codecName).toBe('H.264');
  });

  it('vídeo gravado em pé (giro de 90°) é 1080×1920 para a pessoa', async () => {
    const info = await probeVideo(video({ width: 1920, height: 1080, rotation: 90 }));

    expect(info).toMatchObject({ width: 1080, height: 1920, rotation: 90 });
    expect(exceedsFullHd(info!)).toBe(false);
  });

  it.each([180, 270] as const)('reconhece o giro de %s°', async (rotation) => {
    expect((await probeVideo(video({ rotation })))?.rotation).toBe(rotation);
  });

  it('Dolby Vision e transferência HDR (PQ/HLG) contam como HDR', async () => {
    const dolby = await probeVideo(video({ codec: 'hvc1', extras: box('dvcC', [1, 0, 0, 0]) }));
    const hlg = await probeVideo(video({ codec: 'hvc1', extras: box('colr', ascii('nclx'), u16(9), u16(18), u16(9), [0]) }));
    const sdr = await probeVideo(video({ codec: 'hvc1', extras: box('colr', ascii('nclx'), u16(1), u16(1), u16(1), [0]) }));

    expect(dolby?.hdr).toBe(true);
    expect(hlg?.hdr).toBe(true);
    expect(sdr?.hdr).toBe(false);
  });

  it('usa a duração do arquivo mesmo quando o navegador não toca o codec', async () => {
    expect((await probeVideo(video({ timescale: 1000, duration: 45_500 })))?.durationSeconds).toBe(45.5);
  });

  it('quem não é vídeo (ou não é MP4/MOV) volta nulo, sem erro', async () => {
    expect(await probeVideo(new Blob(['isto não é um vídeo']))).toBeNull();
    expect(await probeVideo(new Blob([]))).toBeNull();
    expect(await probeVideo(video({ handler: 'soun' }))).toBeNull();
  });
});

describe('limite de 1080p', () => {
  it('Full HD deitado ou em pé cabe; acima disso não', () => {
    expect(exceedsFullHd({ width: 1920, height: 1080 })).toBe(false);
    expect(exceedsFullHd({ width: 1080, height: 1920 })).toBe(false);
    expect(exceedsFullHd({ width: 1280, height: 720 })).toBe(false);
    expect(exceedsFullHd({ width: 3840, height: 2160 })).toBe(true);
    expect(exceedsFullHd({ width: 2160, height: 3840 })).toBe(true);
    expect(exceedsFullHd({ width: 2560, height: 1440 })).toBe(true);
  });
});

describe('looksLikeVideo', () => {
  it('vale o tipo declarado ou, se vazio, a extensão (alguns celulares não informam o tipo do .mov)', () => {
    expect(looksLikeVideo({ name: 'a.bin', type: 'video/quicktime' })).toBe(true);
    expect(looksLikeVideo({ name: 'IMG_1.MOV', type: '' })).toBe(true);
    expect(looksLikeVideo({ name: 'clipe.m4v', type: '' })).toBe(true);
    expect(looksLikeVideo({ name: 'foto.jpg', type: 'image/jpeg' })).toBe(false);
  });
});
