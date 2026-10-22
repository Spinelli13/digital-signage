import { Media, PlaylistItem } from '../core/models';
import {
  addMedia,
  clearValidity,
  describeValidity,
  formatDuration,
  hasValidity,
  isValidOn,
  moveItem,
  removeAt,
  saoPauloToday,
  setDuration,
  setValidity,
  toRequest,
  toggleDay,
  totalSeconds,
  validityProblem,
} from './playlist-editor';

const image = (id: number, name = `img${id}`): Media => ({
  id,
  name,
  type: 'IMAGE',
  mimeType: 'image/png',
  url: '',
  fileSize: 1,
  durationSeconds: null,
  checksum: null,
  createdAt: '',
});

const video = (id: number, seconds: number): Media => ({ ...image(id), type: 'VIDEO', mimeType: 'video/mp4', durationSeconds: seconds });

function build(...medias: Media[]): PlaylistItem[] {
  return medias.reduce<PlaylistItem[]>((items, media) => addMedia(items, media), []);
}

describe('playlist editor', () => {
  it('uses 10s for images and the media duration for videos', () => {
    const items = build(image(1), video(2, 42));

    expect(items.map((i) => i.durationSeconds)).toEqual([10, 42]);
  });

  it('keeps positions sequential after adding, moving and removing', () => {
    let items = build(image(1), image(2), image(3));
    items = moveItem(items, 2, -1);
    items = removeAt(items, 0);

    expect(items.map((i) => i.mediaId)).toEqual([3, 2]);
    expect(items.map((i) => i.position)).toEqual([0, 1]);
  });

  it('does not move items outside the list bounds', () => {
    const items = build(image(1), image(2));

    expect(moveItem(items, 0, -1)).toBe(items);
    expect(moveItem(items, 1, 1)).toBe(items);
  });

  it('allows the same media twice in a playlist', () => {
    const items = build(image(1), image(1));

    expect(items).toHaveLength(2);
    expect(items[1].position).toBe(1);
  });

  it('clamps duration to at least 1s and ignores garbage', () => {
    const items = build(image(1));

    expect(setDuration(items, 0, 0)[0].durationSeconds).toBe(1);
    expect(setDuration(items, 0, -5)[0].durationSeconds).toBe(1);
    expect(setDuration(items, 0, 7.6)[0].durationSeconds).toBe(8);
    expect(setDuration(items, 0, NaN)[0].durationSeconds).toBe(10);
  });

  it('sums the cycle duration', () => {
    const items = build(image(1), video(2, 50));

    expect(totalSeconds(items)).toBe(60);
    expect(formatDuration(60)).toBe('1min 00s');
    expect(formatDuration(75)).toBe('1min 15s');
    expect(formatDuration(9)).toBe('9s');
  });

  it('builds the API request in order, trimming the name and nulling an empty description', () => {
    const items = setDuration(build(image(1), image(2)), 1, 25);

    expect(toRequest('  Loja  ', '   ', items)).toEqual({
      name: 'Loja',
      description: null,
      items: [
        { mediaId: 1, durationSeconds: 10 },
        { mediaId: 2, durationSeconds: 25 },
      ],
    });
  });
});

describe('validade por item (datas e dias da semana)', () => {
  const base = (): PlaylistItem[] => build(image(1), image(2));

  it('sends no validity fields for an item without rules, keeping the old body', () => {
    expect(toRequest('X', '', base()).items).toEqual([
      { mediaId: 1, durationSeconds: 10 },
      { mediaId: 2, durationSeconds: 10 },
    ]);
  });

  it('sends only the rules that were chosen, with the days sorted', () => {
    let items = setValidity(base(), 0, { validFrom: '2026-11-01', days: [5, 2] });
    items = setValidity(items, 1, { validUntil: '2026-12-31' });

    expect(toRequest('X', '', items).items).toEqual([
      { mediaId: 1, durationSeconds: 10, validFrom: '2026-11-01', days: [2, 5] },
      { mediaId: 2, durationSeconds: 10, validUntil: '2026-12-31' },
    ]);
  });

  it('treats all seven days (or none) and empty dates as no restriction', () => {
    const items = setValidity(base(), 0, { validFrom: '', validUntil: '', days: [1, 2, 3, 4, 5, 6, 7] });

    expect(hasValidity(items[0])).toBe(false);
    expect(items[0].days).toBeNull();
    expect(items[0].validFrom).toBeNull();
    expect(setValidity(base(), 0, { days: [] })[0].days).toBeNull();
    expect(describeValidity(items[0])).toBe('Sempre');
  });

  it('starting from every day, ticking a day off keeps the others, and the last tick falls back to every day', () => {
    let items = toggleDay(base(), 0, 3); // desmarca a quarta: sobram 6 dias
    expect(items[0].days).toEqual([1, 2, 4, 5, 6, 7]);
    items = toggleDay(items, 0, 3); // marca de novo: volta aos 7 = sem restrição
    expect(items[0].days).toBeNull();

    items = setValidity(base(), 0, { days: [2] });
    items = toggleDay(items, 0, 2); // tira o único dia: não pode ficar sem nenhum
    expect(items[0].days).toBeNull();
    expect(hasValidity(items[0])).toBe(false);
  });

  it('clears all the rules of an item and leaves the other items alone', () => {
    let items = setValidity(base(), 0, { validFrom: '2026-11-01', days: [2] });
    items = setValidity(items, 1, { validUntil: '2026-12-31' });

    items = clearValidity(items, 0);

    expect(hasValidity(items[0])).toBe(false);
    expect(hasValidity(items[1])).toBe(true);
  });

  it('knows whether an item is valid on a day, with inclusive dates and Monday = 1 to Sunday = 7', () => {
    const item = setValidity(base(), 0, { validFrom: '2026-11-05', validUntil: '2026-11-06' })[0];
    expect(isValidOn(item, '2026-11-04')).toBe(false);
    expect(isValidOn(item, '2026-11-05')).toBe(true);
    expect(isValidOn(item, '2026-11-06')).toBe(true);
    expect(isValidOn(item, '2026-11-07')).toBe(false);

    const tuesdays = setValidity(base(), 0, { days: [2] })[0];
    expect(isValidOn(tuesdays, '2026-11-03')).toBe(true); // terça
    expect(isValidOn(tuesdays, '2026-11-04')).toBe(false);
    const sundays = setValidity(base(), 0, { days: [7] })[0];
    expect(isValidOn(sundays, '2026-11-08')).toBe(true); // domingo
    expect(isValidOn(sundays, '2026-11-02')).toBe(false); // segunda
  });

  it('counts today in São Paulo, not in UTC', () => {
    expect(saoPauloToday(new Date('2026-11-04T02:59:59Z'))).toBe('2026-11-03');
    expect(saoPauloToday(new Date('2026-11-04T03:00:00Z'))).toBe('2026-11-04');
  });

  it('describes the rules in words', () => {
    expect(describeValidity(setValidity(base(), 0, { days: [2] })[0])).toBe('Ter');
    expect(describeValidity(setValidity(base(), 0, { days: [1, 2, 3, 4, 5] })[0])).toBe('Seg a Sex');
    expect(describeValidity(setValidity(base(), 0, { validUntil: '2026-12-31' })[0])).toContain('até');
    expect(describeValidity(setValidity(base(), 0, { validFrom: '2026-11-01', validUntil: '2026-11-30', days: [6, 7] })[0])).toMatch(/^Fim de semana · de /);
  });

  it('refuses a start day after the end day', () => {
    const items = setValidity(base(), 0, { validFrom: '2026-12-31', validUntil: '2026-12-01' });

    expect(validityProblem(items[0])).toContain('início');
    expect(validityProblem(setValidity(base(), 0, { validFrom: '2026-12-01', validUntil: '2026-12-01' })[0])).toBeNull();
  });
});
