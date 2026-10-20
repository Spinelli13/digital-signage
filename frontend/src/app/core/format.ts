import { HttpErrorResponse } from '@angular/common/http';

import { Connection, Device } from './models';

export interface DeviceState {
  label: string;
  tone: 'on' | 'off' | 'warn';
}

/** Situação vinda do servidor; se faltar (resposta antiga), deduz do status e do último contato. */
export function connectionOf(device: Device): Connection {
  if (device.connection) {
    return device.connection;
  }
  if (device.status === 'DISABLED' || device.status === 'PENDING') {
    return device.status;
  }
  if (device.online) {
    return 'ONLINE';
  }
  return device.lastSeenAt ? 'OFFLINE' : 'NEVER';
}

const STATES: Record<Connection, DeviceState> = {
  ONLINE: { label: 'Online', tone: 'on' },
  OFFLINE: { label: 'Offline', tone: 'off' },
  NEVER: { label: 'Nunca conectou', tone: 'warn' },
  PENDING: { label: 'Aguardando ativação', tone: 'warn' },
  DISABLED: { label: 'Desativado', tone: 'off' },
};

export function stateOfConnection(connection: Connection): DeviceState {
  return STATES[connection];
}

export function deviceState(device: Device): DeviceState {
  return STATES[connectionOf(device)];
}

/** True quando o dispositivo precisa de atenção do administrador (offline, pendente ou nunca conectado). */
export function needsAttention(device: Device): boolean {
  const state = deviceState(device);
  return state.tone !== 'on' && device.status !== 'DISABLED';
}

export function timeAgo(iso: string | null, now: number = Date.now()): string {
  if (!iso) {
    return '—';
  }
  const seconds = Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000));
  if (seconds < 60) {
    return 'agora';
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes} min atrás`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `${hours} h atrás`;
  }
  return `${Math.floor(hours / 24)} d atrás`;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  if (bytes < 1024 * 1024 * 1024) {
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

/** Extrai a mensagem {"error": "..."} devolvida pela API, com fallback legível. */
export function errorMessage(error: unknown, fallback = 'Algo deu errado. Tente novamente.'): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Não foi possível conectar ao servidor.';
    }
    const body = error.error as { error?: string } | null;
    if (body && typeof body.error === 'string') {
      return body.error;
    }
  }
  return fallback;
}

/** "05/11/2026 09:00", no fuso do navegador. */
export function formatDateTime(iso: string | null): string {
  if (!iso) {
    return '—';
  }
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** "5 min", "2 h 10 min", "3 d 4 h" — a mesma escrita do servidor nos alertas e no CSV. */
export function humanSeconds(totalSeconds: number): string {
  const minutes = Math.max(0, Math.floor(totalSeconds / 60));
  if (minutes < 1) {
    return totalSeconds > 0 ? 'menos de 1 min' : '0 min';
  }
  if (minutes < 60) {
    return `${minutes} min`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return minutes % 60 === 0 ? `${hours} h` : `${hours} h ${minutes % 60} min`;
  }
  const days = Math.floor(hours / 24);
  return hours % 24 === 0 ? `${days} d` : `${days} d ${hours % 24} h`;
}

/** "99,5%"; sem dados ainda, um traço (nunca "0%", que pareceria uma TV sempre fora do ar). */
export function formatPercent(value: number | null): string {
  return value === null ? '—' : `${value.toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}%`;
}

/** AAAA-MM-DD do dia local de {@code date} (toISOString usaria UTC e viraria o dia à noite). */
export function isoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** O dia de {@code count} dias antes de {@code now} (0 = hoje). */
export function daysAgo(count: number, now: Date = new Date()): string {
  const day = new Date(now.getFullYear(), now.getMonth(), now.getDate() - count);
  return isoDate(day);
}
