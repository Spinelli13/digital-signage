import { HttpErrorResponse } from '@angular/common/http';

import {
  connectionOf,
  daysAgo,
  deviceState,
  errorMessage,
  formatBytes,
  formatDateTime,
  formatPercent,
  humanSeconds,
  isoDate,
  needsAttention,
  stateOfConnection,
  timeAgo,
} from './format';
import { Device } from './models';

const device = (overrides: Partial<Device>): Device => ({
  id: 1,
  name: 'TV',
  location: null,
  locationId: null,
  status: 'ACTIVE',
  connection: 'NEVER',
  groupIds: [],
  online: false,
  lastSeenAt: null,
  playerVersion: null,
  currentPlaylistId: null,
  configVersion: 0,
  effectivePlaylistId: null,
  campaignId: null,
  campaignName: null,
  createdAt: '',
  updatedAt: '',
  ...overrides,
});

describe('deviceState', () => {
  it('shows online for an active device that is reporting', () => {
    expect(deviceState(device({ connection: 'ONLINE', online: true, lastSeenAt: 'x' }))).toEqual({ label: 'Online', tone: 'on' });
  });

  it('shows offline for an active device that stopped reporting', () => {
    expect(deviceState(device({ connection: 'OFFLINE', lastSeenAt: 'x' })).tone).toBe('off');
  });

  it('warns when an active device never connected', () => {
    expect(deviceState(device({ connection: 'NEVER' })).label).toBe('Nunca conectou');
  });

  it('shows pending and disabled devices distinctly', () => {
    expect(deviceState(device({ status: 'PENDING', connection: 'PENDING' })).label).toBe('Aguardando ativação');
    expect(deviceState(device({ status: 'DISABLED', connection: 'DISABLED' })).label).toBe('Desativado');
  });

  it('has a label for every situation the server can report', () => {
    for (const connection of ['ONLINE', 'OFFLINE', 'NEVER', 'PENDING', 'DISABLED'] as const) {
      expect(stateOfConnection(connection).label.length).toBeGreaterThan(0);
    }
  });
});

/** Respostas de um servidor antigo (sem "connection") ainda precisam mostrar o estado certo. */
describe('connectionOf', () => {
  const legacy = (overrides: Partial<Device>) => device({ connection: undefined as never, ...overrides });

  it('trusts the server when it reports the connection', () => {
    expect(connectionOf(device({ connection: 'OFFLINE' }))).toBe('OFFLINE');
  });

  it('derives the situation from status and last contact when the server does not send it', () => {
    expect(connectionOf(legacy({ online: true, lastSeenAt: 'x' }))).toBe('ONLINE');
    expect(connectionOf(legacy({ online: false, lastSeenAt: 'x' }))).toBe('OFFLINE');
    expect(connectionOf(legacy({}))).toBe('NEVER');
    expect(connectionOf(legacy({ status: 'PENDING' }))).toBe('PENDING');
    expect(connectionOf(legacy({ status: 'DISABLED' }))).toBe('DISABLED');
  });
});

describe('needsAttention', () => {
  it('flags offline and pending devices but not online or deliberately disabled ones', () => {
    expect(needsAttention(device({ connection: 'ONLINE', online: true, lastSeenAt: 'x' }))).toBe(false);
    expect(needsAttention(device({ connection: 'OFFLINE', lastSeenAt: 'x' }))).toBe(true);
    expect(needsAttention(device({ status: 'PENDING', connection: 'PENDING' }))).toBe(true);
    expect(needsAttention(device({ status: 'DISABLED', connection: 'DISABLED' }))).toBe(false);
  });
});

describe('timeAgo', () => {
  const now = new Date('2026-10-19T12:00:00Z').getTime();

  it('formats relative times', () => {
    expect(timeAgo(null, now)).toBe('—');
    expect(timeAgo('2026-10-19T11:59:40Z', now)).toBe('agora');
    expect(timeAgo('2026-10-19T11:30:00Z', now)).toBe('30 min atrás');
    expect(timeAgo('2026-10-19T09:00:00Z', now)).toBe('3 h atrás');
    expect(timeAgo('2026-10-17T12:00:00Z', now)).toBe('2 d atrás');
  });
});

describe('formatBytes', () => {
  it('uses the right unit', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(2048)).toBe('2.0 KB');
    expect(formatBytes(5 * 1024 * 1024)).toBe('5.0 MB');
  });
});

describe('errorMessage', () => {
  it('uses the message from the API body', () => {
    const error = new HttpErrorResponse({ status: 409, error: { error: 'Não é possível remover' } });
    expect(errorMessage(error)).toBe('Não é possível remover');
  });

  it('explains connection failures', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toContain('conectar');
  });

  it('falls back for unknown errors', () => {
    expect(errorMessage(new Error('x'), 'padrão')).toBe('padrão');
  });
});

describe('humanSeconds', () => {
  it('writes durations the same way the server does', () => {
    expect(humanSeconds(0)).toBe('0 min');
    expect(humanSeconds(20)).toBe('menos de 1 min');
    expect(humanSeconds(300)).toBe('5 min');
    expect(humanSeconds(3600)).toBe('1 h');
    expect(humanSeconds(7320)).toBe('2 h 2 min');
    expect(humanSeconds(86_400)).toBe('1 d');
    expect(humanSeconds(86_400 + 4 * 3600)).toBe('1 d 4 h');
  });
});

describe('formatPercent', () => {
  it('uses a decimal comma and a dash when there is no data yet', () => {
    expect(formatPercent(99.5)).toBe('99,5%');
    expect(formatPercent(100)).toBe('100,0%');
    expect(formatPercent(null)).toBe('—');
  });
});

describe('dates', () => {
  it('formats the local calendar day, not the UTC one', () => {
    expect(isoDate(new Date(2026, 10, 5, 23, 30))).toBe('2026-11-05');
    expect(isoDate(new Date(2026, 0, 2, 0, 5))).toBe('2026-01-02');
  });

  it('counts days back across month and year boundaries', () => {
    const now = new Date(2026, 0, 3, 15, 0);
    expect(daysAgo(0, now)).toBe('2026-01-03');
    expect(daysAgo(6, now)).toBe('2025-12-28');
    expect(daysAgo(29, now)).toBe('2025-12-05');
  });

  it('shows a dash when there is no date', () => {
    expect(formatDateTime(null)).toBe('—');
    expect(formatDateTime('2026-11-05T12:00:00Z')).toMatch(/^\d{2}\/\d{2}\/\d{4}/);
  });
});
