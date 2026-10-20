import { Layout, LayoutRequest, LayoutTemplate } from '../core/models';

export const MAX_ZONES = 6;
export const MIN_SIZE = 5;

/** Uma zona em edição. {@code key} identifica a linha na tela (zonas novas ainda não têm id). */
export interface ZoneDraft {
  key: number;
  name: string;
  x: number;
  y: number;
  width: number;
  height: number;
  main: boolean;
  playlistId: number | null;
}

type ZoneShape = { name: string; x: number; y: number; width: number; height: number; main: boolean; playlistId: number | null };

let nextKey = 1;

export function newKey(): number {
  return nextKey++;
}

function toDraft(zone: ZoneShape): ZoneDraft {
  return { key: newKey(), name: zone.name, x: zone.x, y: zone.y, width: zone.width, height: zone.height, main: zone.main, playlistId: zone.playlistId };
}

export function draftsFromLayout(layout: Pick<Layout, 'zones'>): ZoneDraft[] {
  return layout.zones.map(toDraft);
}

export function draftsFromTemplate(template: Pick<LayoutTemplate, 'zones'>): ZoneDraft[] {
  return template.zones.map(toDraft);
}

/** Um layout novo, do zero: uma zona principal ocupando a tela toda. */
export function blankDrafts(): ZoneDraft[] {
  return [{ key: newKey(), name: 'Principal', x: 0, y: 0, width: 100, height: 100, main: true, playlistId: null }];
}

export function overlaps(a: ZoneDraft, b: ZoneDraft): boolean {
  return a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height;
}

/**
 * Confere o layout antes de enviar (as mesmas regras do servidor, que continua sendo quem decide). Devolve a
 * primeira mensagem de erro, ou null se está tudo certo.
 */
export function validateZones(zones: ZoneDraft[]): string | null {
  if (zones.length === 0) {
    return 'O layout precisa de pelo menos uma zona';
  }
  if (zones.length > MAX_ZONES) {
    return `O layout pode ter no máximo ${MAX_ZONES} zonas`;
  }
  if (zones.filter((zone) => zone.main).length !== 1) {
    return 'Escolha exatamente uma zona principal (a que exibe a playlist da TV)';
  }
  for (const zone of zones) {
    const label = `A zona "${zone.name.trim() || '(sem nome)'}"`;
    if (!zone.name.trim()) {
      return 'Dê um nome a cada zona';
    }
    const numbers = [zone.x, zone.y, zone.width, zone.height];
    if (numbers.some((value) => !Number.isFinite(value) || !Number.isInteger(value))) {
      return `${label} precisa de números inteiros na posição e no tamanho`;
    }
    if (zone.x < 0 || zone.y < 0) {
      return `${label} começa fora da tela`;
    }
    if (zone.width < MIN_SIZE || zone.height < MIN_SIZE) {
      return `${label} é pequena demais (mínimo ${MIN_SIZE}% de largura e de altura)`;
    }
    if (zone.x + zone.width > 100 || zone.y + zone.height > 100) {
      return `${label} passa do limite da tela (100%)`;
    }
  }
  for (let i = 0; i < zones.length; i++) {
    for (let j = i + 1; j < zones.length; j++) {
      if (overlaps(zones[i], zones[j])) {
        return `As zonas "${zones[i].name.trim()}" e "${zones[j].name.trim()}" se sobrepõem`;
      }
    }
  }
  const names = zones.map((zone) => zone.name.trim().toLowerCase());
  if (new Set(names).size !== names.length) {
    return 'Cada zona precisa de um nome diferente';
  }
  return null;
}

export function toLayoutRequest(name: string, description: string, zones: ZoneDraft[]): LayoutRequest {
  return {
    name: name.trim(),
    description: description.trim() || null,
    zones: zones.map((zone) => ({
      name: zone.name.trim(),
      x: zone.x,
      y: zone.y,
      width: zone.width,
      height: zone.height,
      main: zone.main,
      // a principal usa a playlist da TV: nunca leva playlist fixa
      playlistId: zone.main ? null : zone.playlistId,
    })),
  };
}

/** Marca uma zona como a principal e tira a marca das outras (a principal não tem playlist fixa). */
export function setMain(zones: ZoneDraft[], key: number): ZoneDraft[] {
  return zones.map((zone) => ({ ...zone, main: zone.key === key, playlistId: zone.key === key ? null : zone.playlistId }));
}

export function removeZone(zones: ZoneDraft[], key: number): ZoneDraft[] {
  const remaining = zones.filter((zone) => zone.key !== key);
  // se a principal foi removida, a primeira que sobrou assume
  if (remaining.length > 0 && !remaining.some((zone) => zone.main)) {
    remaining[0] = { ...remaining[0], main: true, playlistId: null };
  }
  return remaining;
}

/**
 * Acrescenta uma zona num espaço livre da tela (procura em passos de 5%, do tamanho 25% x 25% para baixo até 10% x 10%).
 * Devolve null se não houver lugar.
 */
export function addZone(zones: ZoneDraft[]): ZoneDraft[] | null {
  if (zones.length >= MAX_ZONES) {
    return null;
  }
  for (const size of [25, 20, 15, 10]) {
    for (let y = 0; y + size <= 100; y += 5) {
      for (let x = 0; x + size <= 100; x += 5) {
        const candidate: ZoneDraft = { key: newKey(), name: uniqueName(zones, 'Zona'), x, y, width: size, height: size, main: false, playlistId: null };
        if (!zones.some((zone) => overlaps(zone, candidate))) {
          return [...zones, candidate];
        }
      }
    }
  }
  return null;
}

function uniqueName(zones: ZoneDraft[], base: string): string {
  const taken = new Set(zones.map((zone) => zone.name.trim().toLowerCase()));
  for (let n = zones.length + 1; ; n++) {
    const name = `${base} ${n}`;
    if (!taken.has(name.toLowerCase())) {
      return name;
    }
  }
}

/** Cores das zonas na pré-visualização (a principal é sempre a primeira cor). */
export const ZONE_COLORS = ['#3563e9', '#19a66a', '#e89a32', '#9b59d0', '#e05263', '#17a2b8'];

export function zoneColor(zones: Pick<ZoneDraft, 'main'>[], index: number): string {
  if (zones[index]?.main) {
    return ZONE_COLORS[0];
  }
  const others = zones.slice(0, index).filter((zone) => !zone.main).length;
  return ZONE_COLORS[1 + (others % (ZONE_COLORS.length - 1))];
}
