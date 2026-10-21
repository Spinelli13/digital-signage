import type { IconName } from '../shared/icons';
import type { Tone } from '../shared/ui';
import { tr } from './i18n';
import { AlertType, EventLevel, EventType } from './models';

export interface EventLabel {
  icon: IconName;
  tone: Tone;
  label: string;
}

/** Como cada tipo de evento aparece na linha do tempo (ícone + cor; o texto sempre acompanha). */
export const EVENT_LABELS: Record<EventType, EventLabel> = {
  ACTIVATED: { icon: 'badge-check', tone: 'ok', label: 'Ativada' },
  ONLINE: { icon: 'monitor-check', tone: 'ok', label: 'Conectou' },
  OFFLINE: { icon: 'wifi-off', tone: 'err', label: 'Sem contato' },
  DISABLED: { icon: 'circle-pause', tone: 'neutral', label: 'Desativada' },
  ENABLED: { icon: 'play', tone: 'ok', label: 'Reativada' },
  PLAYLIST_CHANGED: { icon: 'list-video', tone: 'brand', label: 'Conteúdo' },
  LAYOUT_CHANGED: { icon: 'layout-template', tone: 'brand', label: 'Layout da tela' },
  UPDATE_REQUESTED: { icon: 'download', tone: 'info', label: 'Atualização pedida' },
  PLAYER_UPDATED: { icon: 'package', tone: 'info', label: 'Player atualizado' },
  PLAYER_LOG: { icon: 'file-text', tone: 'neutral', label: 'Registro do Player' },
};

export const EVENT_TYPES = Object.keys(EVENT_LABELS) as EventType[];

export const LEVEL_LABELS: Record<EventLevel, string> = { INFO: 'Informação', WARN: 'Aviso', ERROR: 'Erro' };

export const ALERT_LABELS: Record<AlertType, string> = {
  OFFLINE: 'TV sem contato',
  NEVER_CONNECTED: 'TV nunca conectou',
  PLAYER_ERRORS: 'Erros no Player',
};

/** O texto no idioma atual (as tabelas acima guardam as chaves em português). */
export const eventLabel = (type: EventType): string => tr(EVENT_LABELS[type].label);
export const levelLabel = (level: EventLevel): string => tr(LEVEL_LABELS[level]);
export const alertLabel = (type: AlertType): string => tr(ALERT_LABELS[type]);

/** Cor do evento: erro e aviso sobrepõem a cor do tipo, para o problema saltar à vista. */
export const eventTone = (type: EventType, level: EventLevel): Tone =>
  level === 'ERROR' ? 'err' : level === 'WARN' ? 'warn' : EVENT_LABELS[type].tone;
