import { Connection } from '../core/models';
import { IconName } from './icons';
import { Tone } from './ui';

/** Ícone e cor de cada situação de uma TV (a cor nunca vai sozinha: sempre com ícone e texto). */
export const SITUATION: Record<Connection, { icon: IconName; tone: Tone }> = {
  ONLINE: { icon: 'monitor-check', tone: 'ok' },
  OFFLINE: { icon: 'wifi-off', tone: 'err' },
  NEVER: { icon: 'plug-zap', tone: 'warn' },
  PENDING: { icon: 'hourglass', tone: 'info' },
  DISABLED: { icon: 'monitor-off', tone: 'neutral' },
};
