import { tr, trServer } from '../core/i18n';
import { Layout, LayoutRequest, LayoutTemplate, LayoutZoneRequest } from '../core/models';

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
  /** Como a mídia ocupa a zona: cabe inteira (CONTAIN) ou preenche, cortando as bordas (COVER). */
  fit: 'CONTAIN' | 'COVER';
}

type ZoneShape = { name: string; x: number; y: number; width: number; height: number; main: boolean; playlistId: number | null; fit?: 'CONTAIN' | 'COVER' };

let nextKey = 1;

export function newKey(): number {
  return nextKey++;
}

function toDraft(zone: ZoneShape): ZoneDraft {
  return { key: newKey(), name: zone.name, x: zone.x, y: zone.y, width: zone.width, height: zone.height, main: zone.main, playlistId: zone.playlistId, fit: zone.fit === 'COVER' ? 'COVER' : 'CONTAIN' };
}

/** A versão para TV em pé de um layout (nula se ele não tem uma). */
export function portraitDraftsFromLayout(layout: Pick<Layout, 'portraitZones'>): ZoneDraft[] | null {
  return layout.portraitZones && layout.portraitZones.length > 0 ? layout.portraitZones.map(toDraft) : null;
}

/** A sugestão para TV em pé de um modelo (nula se o modelo serve como está). */
export function portraitDraftsFromTemplate(template: Pick<LayoutTemplate, 'portraitZones'>): ZoneDraft[] | null {
  return template.portraitZones && template.portraitZones.length > 0 ? template.portraitZones.map(toDraft) : null;
}

/**
 * Um ponto de partida para a versão em pé: a horizontal "deitada" (x vira y, largura vira altura). Uma faixa lateral vira uma
 * faixa embaixo, as duas metades viram uma em cima da outra. É só o começo: a pessoa ajusta.
 */
export function rotateZones(zones: ZoneDraft[]): ZoneDraft[] {
  return zones.map((zone) => ({ ...zone, key: newKey(), x: zone.y, y: zone.x, width: zone.height, height: zone.width }));
}

export function draftsFromLayout(layout: Pick<Layout, 'zones'>): ZoneDraft[] {
  return layout.zones.map(toDraft);
}

export function draftsFromTemplate(template: Pick<LayoutTemplate, 'zones'>): ZoneDraft[] {
  return template.zones.map(toDraft);
}

/** Um layout novo, do zero: uma zona principal ocupando a tela toda. */
export function blankDrafts(): ZoneDraft[] {
  return [{ key: newKey(), name: tr('Principal'), x: 0, y: 0, width: 100, height: 100, main: true, playlistId: null, fit: 'CONTAIN' }];
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
    return tr('O layout precisa de pelo menos uma zona');
  }
  if (zones.length > MAX_ZONES) {
    return tr('O layout pode ter no máximo {n} zonas', { n: MAX_ZONES });
  }
  if (zones.filter((zone) => zone.main).length !== 1) {
    return tr('Escolha exatamente uma zona principal (a que exibe a playlist da TV)');
  }
  for (const zone of zones) {
    const label = tr('A zona "{name}"', { name: zone.name.trim() || tr('(sem nome)') });
    if (!zone.name.trim()) {
      return tr('Dê um nome a cada zona');
    }
    const numbers = [zone.x, zone.y, zone.width, zone.height];
    if (numbers.some((value) => !Number.isFinite(value) || !Number.isInteger(value))) {
      return tr('{label} precisa de números inteiros na posição e no tamanho', { label });
    }
    if (zone.x < 0 || zone.y < 0) {
      return tr('{label} começa fora da tela', { label });
    }
    if (zone.width < MIN_SIZE || zone.height < MIN_SIZE) {
      return tr('{label} é pequena demais (mínimo {min}% de largura e de altura)', { label, min: MIN_SIZE });
    }
    if (zone.x + zone.width > 100 || zone.y + zone.height > 100) {
      return tr('{label} passa do limite da tela (100%)', { label });
    }
  }
  for (let i = 0; i < zones.length; i++) {
    for (let j = i + 1; j < zones.length; j++) {
      if (overlaps(zones[i], zones[j])) {
        return tr('As zonas "{a}" e "{b}" se sobrepõem', { a: zones[i].name.trim(), b: zones[j].name.trim() });
      }
    }
  }
  const names = zones.map((zone) => zone.name.trim().toLowerCase());
  if (new Set(names).size !== names.length) {
    return tr('Cada zona precisa de um nome diferente');
  }
  return null;
}

function toZoneRequest(zone: ZoneDraft): LayoutZoneRequest {
  return {
    name: zone.name.trim(),
    x: zone.x,
    y: zone.y,
    width: zone.width,
    height: zone.height,
    main: zone.main,
    // a principal usa a playlist da TV: nunca leva playlist fixa
    playlistId: zone.main ? null : zone.playlistId,
    fit: zone.fit,
  };
}

export function toLayoutRequest(name: string, description: string, zones: ZoneDraft[], portraitZones: ZoneDraft[] | null = null): LayoutRequest {
  return {
    name: name.trim(),
    description: description.trim() || null,
    zones: zones.map(toZoneRequest),
    portraitZones: portraitZones ? portraitZones.map(toZoneRequest) : [],
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
        const candidate: ZoneDraft = { key: newKey(), name: uniqueName(zones, tr('Zona')), x, y, width: size, height: size, main: false, playlistId: null, fit: 'CONTAIN' };
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
export const ZONE_COLORS = ['#8b5cf6', '#a1a1aa', '#e89a32', '#d946ef', '#e05263', '#c4b5fd'];

/** Nome e descrição dos modelos vêm do servidor (em português): as conhecidas aparecem traduzidas. */
export const templateText = (text: string): string => trServer(text);

export function zoneColor(zones: Pick<ZoneDraft, 'main'>[], index: number): string {
  if (zones[index]?.main) {
    return ZONE_COLORS[0];
  }
  const others = zones.slice(0, index).filter((zone) => !zone.main).length;
  return ZONE_COLORS[1 + (others % (ZONE_COLORS.length - 1))];
}
