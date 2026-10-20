import { HttpErrorResponse } from '@angular/common/http';

import { deviceState, errorMessage, formatBytes, needsAttention, timeAgo } from './format';
import { Device } from './models';

const device = (overrides: Partial<Device>): Device => ({
  id: 1,
  name: 'TV',
  location: null,
  status: 'ACTIVE',
  online: false,
  lastSeenAt: null,
  playerVersion: null,
  currentPlaylistId: null,
  configVersion: 0,
  createdAt: '',
  updatedAt: '',
  ...overrides,
});

describe('deviceState', () => {
  it('shows online for an active device that is reporting', () => {
    expect(deviceState(device({ online: true, lastSeenAt: 'x' }))).toEqual({ label: 'Online', tone: 'on' });
  });

  it('shows offline for an active device that stopped reporting', () => {
    expect(deviceState(device({ online: false, lastSeenAt: 'x' })).tone).toBe('off');
  });

  it('warns when an active device never connected', () => {
    expect(deviceState(device({})).label).toBe('Nunca conectou');
  });

  it('shows pending and disabled devices distinctly', () => {
    expect(deviceState(device({ status: 'PENDING' })).label).toBe('Aguardando ativação');
    expect(deviceState(device({ status: 'DISABLED' })).label).toBe('Desativado');
  });
});

describe('needsAttention', () => {
  it('flags offline and pending devices but not online or deliberately disabled ones', () => {
    expect(needsAttention(device({ online: true, lastSeenAt: 'x' }))).toBe(false);
    expect(needsAttention(device({ online: false, lastSeenAt: 'x' }))).toBe(true);
    expect(needsAttention(device({ status: 'PENDING' }))).toBe(true);
    expect(needsAttention(device({ status: 'DISABLED' }))).toBe(false);
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
