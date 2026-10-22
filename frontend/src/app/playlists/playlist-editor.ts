import { ALL_DAYS, describeDays } from '../campaigns/campaign-form';
import { formatDate, tr } from '../core/i18n';
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
    items: items.map((item) => ({
      mediaId: item.mediaId,
      durationSeconds: item.durationSeconds,
      // só vai o que foi escolhido: um item sem validade segue com o corpo de sempre
      ...(item.validFrom ? { validFrom: item.validFrom } : {}),
      ...(item.validUntil ? { validUntil: item.validUntil } : {}),
      ...(item.days && item.days.length > 0 && item.days.length < ALL_DAYS.length ? { days: [...item.days].sort((a, b) => a - b) } : {}),
    })),
  };
}

// ---------- validade por item (datas e dias da semana) ----------

/** O que muda na validade de um item (o que não vier fica como está). */
export interface ValidityPatch {
  validFrom?: string | null;
  validUntil?: string | null;
  days?: number[] | null;
}

/** O item tem alguma regra de validade? */
export function hasValidity(item: PlaylistItem): boolean {
  return !!item.validFrom || !!item.validUntil || (!!item.days && item.days.length > 0 && item.days.length < ALL_DAYS.length);
}

/** Hoje em São Paulo ("2026-11-03"): o dia da validade é sempre o de lá, como no servidor. */
export function saoPauloToday(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
}

/** O item vale neste dia (AAAA-MM-DD)? A mesma conta do servidor: datas inclusivas e dia da semana de 1 (segunda) a 7 (domingo). */
export function isValidOn(item: PlaylistItem, day: string): boolean {
  if (item.validFrom && day < item.validFrom) {
    return false;
  }
  if (item.validUntil && day > item.validUntil) {
    return false;
  }
  if (item.days && item.days.length > 0 && item.days.length < ALL_DAYS.length) {
    const weekday = new Date(`${day}T12:00:00Z`).getUTCDay(); // 0 = domingo
    return item.days.includes(weekday === 0 ? 7 : weekday);
  }
  return true;
}

/** Troca a validade de um item; todos os dias marcados (ou nenhum) = sem restrição de dia. */
export function setValidity(items: PlaylistItem[], index: number, patch: ValidityPatch): PlaylistItem[] {
  return items.map((item, i) => {
    if (i !== index) {
      return item;
    }
    const next = { ...item, ...patch };
    const days = next.days && next.days.length > 0 && next.days.length < ALL_DAYS.length ? [...next.days].sort((a, b) => a - b) : null;
    return { ...next, validFrom: next.validFrom || null, validUntil: next.validUntil || null, days };
  });
}

/** Marca ou desmarca um dia da semana (a partir de "todos os dias", marcar um dia restringe a ele). */
export function toggleDay(items: PlaylistItem[], index: number, day: number): PlaylistItem[] {
  const current = items[index]?.days && items[index].days!.length > 0 ? items[index].days! : [...ALL_DAYS];
  const next = current.includes(day) ? current.filter((d) => d !== day) : [...current, day];
  // desmarcar o último dia deixaria o item sem dia nenhum: volta a valer todos os dias em vez de sumir sem aviso
  return setValidity(items, index, { days: next.length === 0 ? null : next });
}

/** Tira toda a validade do item (volta a valer sempre). */
export function clearValidity(items: PlaylistItem[], index: number): PlaylistItem[] {
  return setValidity(items, index, { validFrom: null, validUntil: null, days: null });
}

/** "Ter · de 05/11/2026 a 06/11/2026", ou "Sempre" quando não há regra. */
export function describeValidity(item: PlaylistItem): string {
  if (!hasValidity(item)) {
    return tr('Sempre');
  }
  const parts: string[] = [];
  if (item.days && item.days.length > 0 && item.days.length < ALL_DAYS.length) {
    parts.push(describeDays(item.days));
  }
  if (item.validFrom && item.validUntil) {
    parts.push(tr('de {from} a {to}', { from: formatDate(item.validFrom), to: formatDate(item.validUntil) }));
  } else if (item.validFrom) {
    parts.push(tr('a partir de {date}', { date: formatDate(item.validFrom) }));
  } else if (item.validUntil) {
    parts.push(tr('até {date}', { date: formatDate(item.validUntil) }));
  }
  return parts.join(' · ');
}

/** O que impede de salvar a validade deste item (início depois do fim), ou nulo. */
export function validityProblem(item: PlaylistItem): string | null {
  return item.validFrom && item.validUntil && item.validFrom > item.validUntil
    ? tr('O dia de início não pode ser depois do dia de fim.')
    : null;
}

function renumber(items: PlaylistItem[]): PlaylistItem[] {
  return items.map((item, position) => ({ ...item, position }));
}
