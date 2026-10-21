import { tr } from './i18n';
import { AlertType, EventLevel, EventType } from './models';

export interface EventLabel {
  icon: string;
  label: string;
}

/** Como cada tipo de evento aparece na linha do tempo. */
export const EVENT_LABELS: Record<EventType, EventLabel> = {
  ACTIVATED: { icon: '✅', label: 'Ativada' },
  ONLINE: { icon: '🟢', label: 'Conectou' },
  OFFLINE: { icon: '🔴', label: 'Sem contato' },
  DISABLED: { icon: '⏸', label: 'Desativada' },
  ENABLED: { icon: '▶', label: 'Reativada' },
  PLAYLIST_CHANGED: { icon: '🎞', label: 'Conteúdo' },
  LAYOUT_CHANGED: { icon: '🧩', label: 'Layout da tela' },
  UPDATE_REQUESTED: { icon: '⬆', label: 'Atualização pedida' },
  PLAYER_UPDATED: { icon: '📦', label: 'Player atualizado' },
  PLAYER_LOG: { icon: '📝', label: 'Registro do Player' },
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
