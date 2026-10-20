import { Media, PlaylistItem } from '../core/models';
import { addMedia, formatDuration, moveItem, removeAt, setDuration, toRequest, totalSeconds } from './playlist-editor';

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
