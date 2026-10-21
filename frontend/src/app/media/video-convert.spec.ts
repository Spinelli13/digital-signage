import { MAX_UPLOAD_BYTES, isQuickTime, mp4Name, planConversion, targetSize } from './video-convert';
import { VideoInfo } from './video-probe';

const info = (overrides: Partial<VideoInfo> = {}): VideoInfo => ({
  codec: 'avc1',
  codecName: 'H.264',
  width: 1920,
  height: 1080,
  rotation: 0,
  durationSeconds: 10,
  hdr: false,
  ...overrides,
});
const file = (name: string, size = 10 * 1024 * 1024, type = 'video/mp4') => ({ name, type, size });

describe('isQuickTime', () => {
  it('reconhece o MOV pelo tipo ou pela extensão (o Safari às vezes não informa o tipo)', () => {
    expect(isQuickTime(file('a.mov', 1, 'video/quicktime'))).toBe(true);
    expect(isQuickTime(file('IMG_0042.MOV', 1, ''))).toBe(true);
    expect(isQuickTime(file('a.mp4', 1, 'video/mp4'))).toBe(false);
  });
});

describe('planConversion', () => {
  it('não mexe num MP4 H.264 em Full HD dentro do limite: converter de novo só perderia qualidade', () => {
    expect(planConversion(file('promo.mp4'), info())).toBeNull();
    expect(planConversion(file('em-pe.mp4'), info({ width: 1080, height: 1920 }))).toBeNull();
    expect(planConversion(file('hd.mp4'), info({ width: 1280, height: 720 }))).toBeNull();
  });

  it('converte o MOV do iPhone (HEVC, 4K, HDR) e diz por que', () => {
    const plan = planConversion(file('IMG_0042.MOV', 60 * 1024 * 1024, 'video/quicktime'), info({ codec: 'hvc1', width: 3840, height: 2160, hdr: true }))!;

    expect(plan.reasons).toEqual(['container', 'codec', 'resolution', 'hdr', 'bytes']);
    expect(plan.width).toBe(1920);
    expect(plan.height).toBe(1080);
    expect(plan.fits).toBe(true);
  });

  it('converte um MOV mesmo em H.264 Full HD (o contêiner do iPhone) e um MP4 em HEVC', () => {
    expect(planConversion(file('a.mov', 1, 'video/quicktime'), info())!.reasons).toEqual(['container']);
    expect(planConversion(file('b.mp4'), info({ codec: 'hev1' }))!.reasons).toEqual(['codec']);
    expect(planConversion(file('c.mp4'), info({ codec: 'av01' }))!.reasons).toEqual(['codec']);
  });

  it('um MP4 H.264 Full HD maior que o limite de 50 MB é convertido só para caber', () => {
    const plan = planConversion(file('grande.mp4', 80 * 1024 * 1024), info({ durationSeconds: 120 }))!;

    expect(plan.reasons).toEqual(['bytes']);
    expect(plan.estimatedBytes!).toBeLessThanOrEqual(MAX_UPLOAD_BYTES);
  });

  it('usa 8 Mbps em Full HD quando o vídeo é curto e baixa a taxa para caber no limite quando é longo', () => {
    const short = planConversion(file('a.mov', 1, 'video/quicktime'), info({ durationSeconds: 10 }))!;
    const long = planConversion(file('b.mov', 1, 'video/quicktime'), info({ durationSeconds: 60 }))!;

    expect(short.bitrate).toBe(8_000_000);
    expect(long.bitrate).toBeLessThan(8_000_000);
    expect(long.bitrate).toBeGreaterThan(4_500_000);
    expect(long.estimatedBytes!).toBeLessThanOrEqual(MAX_UPLOAD_BYTES);
    expect(long.fits).toBe(true);
  });

  it('resolução menor usa proporcionalmente menos taxa e nunca amplia o quadro', () => {
    const plan = planConversion(file('a.mov', 1, 'video/quicktime'), info({ width: 1280, height: 720, durationSeconds: 10 }))!;

    expect(plan.width).toBe(1280);
    expect(plan.height).toBe(720);
    expect(plan.bitrate).toBeCloseTo(3_555_556, -3);
  });

  it('um vídeo longo demais não cabe nem na menor qualidade aceitável', () => {
    const plan = planConversion(file('palestra.mov', 90 * 1024 * 1024, 'video/quicktime'), info({ codec: 'hvc1', durationSeconds: 600 }))!;

    expect(plan.fits).toBe(false);
  });

  it('sem a duração, converte com a taxa padrão e não estima o tamanho', () => {
    const plan = planConversion(file('a.mov', 1, 'video/quicktime'), info({ durationSeconds: null }))!;

    expect(plan.estimatedBytes).toBeNull();
    expect(plan.fits).toBe(true);
    expect(plan.bitrate).toBe(8_000_000);
  });
});

describe('targetSize', () => {
  it('reduz 4K deitado e em pé para o Full HD correspondente, mantendo a proporção', () => {
    expect(targetSize({ width: 3840, height: 2160 })).toEqual({ width: 1920, height: 1080 });
    expect(targetSize({ width: 2160, height: 3840 })).toEqual({ width: 1080, height: 1920 });
    expect(targetSize({ width: 2880, height: 2880 })).toEqual({ width: 1080, height: 1080 });
  });

  it('só devolve medidas pares (o H.264 exige) e nunca passa de Full HD', () => {
    const odd = targetSize({ width: 1919, height: 1079 });
    expect(odd.width % 2).toBe(0);
    expect(odd.height % 2).toBe(0);
    const weird = targetSize({ width: 4001, height: 2251 });
    expect(weird.width % 2).toBe(0);
    expect(weird.height % 2).toBe(0);
    expect(Math.max(weird.width, weird.height)).toBeLessThanOrEqual(1920);
    expect(Math.min(weird.width, weird.height)).toBeLessThanOrEqual(1080);
  });
});

describe('mp4Name', () => {
  it('troca a extensão para .mp4 sem mexer no resto do nome', () => {
    expect(mp4Name('IMG_0042.MOV')).toBe('IMG_0042.mp4');
    expect(mp4Name('festa de fim de ano.final.mov')).toBe('festa de fim de ano.final.mp4');
    expect(mp4Name('sem-extensao')).toBe('sem-extensao.mp4');
    expect(mp4Name('.mov')).toBe('video.mp4');
  });
});
