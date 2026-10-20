import { Media, PlaylistItem, PlaylistRequest } from '../core/models';

export const DEFAULT_IMAGE_SECONDS = 10;

export function addMedia(items: PlaylistItem[], media: Media): PlaylistItem[] {
  const durationSeconds = media.durationSeconds ?? DEFAULT_IMAGE_SECONDS;
  return renumber([
    ...items,
    { mediaId: media.id, mediaName: media.name, mediaType: media.type, position: items.length, durationSeconds },
  ]);
}

export function removeAt(items: PlaylistItem[], index: number): PlaylistItem[] {
  return renumber(items.filter((_, i) => i !== index));
}

/** Move o item uma posição para cima (delta -1) ou para baixo (delta +1); fora dos limites não faz nada. */
export function moveItem(items: PlaylistItem[], index: number, delta: -1 | 1): PlaylistItem[] {
  const target = index + delta;
  if (target < 0 || target >= items.length) {
    return items;
  }
  const copy = [...items];
  [copy[index], copy[target]] = [copy[target], copy[index]];
  return renumber(copy);
}

export function setDuration(items: PlaylistItem[], index: number, seconds: number): PlaylistItem[] {
  const safe = Number.isFinite(seconds) ? Math.max(1, Math.round(seconds)) : DEFAULT_IMAGE_SECONDS;
  return items.map((item, i) => (i === index ? { ...item, durationSeconds: safe } : item));
}

export function totalSeconds(items: PlaylistItem[]): number {
  return items.reduce((sum, item) => sum + item.durationSeconds, 0);
}

export function formatDuration(totalSecs: number): string {
  const minutes = Math.floor(totalSecs / 60);
  const seconds = totalSecs % 60;
  return minutes > 0 ? `${minutes}min ${String(seconds).padStart(2, '0')}s` : `${seconds}s`;
}

export function toRequest(name: string, description: string, items: PlaylistItem[]): PlaylistRequest {
  return {
    name: name.trim(),
    description: description.trim() || null,
    items: items.map((item) => ({ mediaId: item.mediaId, durationSeconds: item.durationSeconds })),
  };
}

function renumber(items: PlaylistItem[]): PlaylistItem[] {
  return items.map((item, position) => ({ ...item, position }));
}
