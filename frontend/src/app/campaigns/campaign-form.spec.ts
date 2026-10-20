import { Campaign } from '../core/models';
import {
  CampaignForm,
  describeDays,
  describeSchedule,
  describeTargets,
  emptyForm,
  formFrom,
  stateBadge,
  toRequest,
  trimTime,
  validateForm,
} from './campaign-form';

const campaign = (overrides: Partial<Campaign> = {}): Campaign => ({
  id: 1,
  name: 'Happy hour',
  description: null,
  playlistId: 7,
  playlistName: 'Promo',
  priority: 80,
  enabled: true,
  startDate: null,
  endDate: null,
  startTime: null,
  endTime: null,
  daysOfWeek: [1, 2, 3, 4, 5, 6, 7],
  timezone: 'America/Sao_Paulo',
  targets: [{ type: 'ALL', id: null, name: null }],
  state: 'ACTIVE',
  deviceCount: 3,
  createdAt: '',
  updatedAt: '',
  ...overrides,
});

const valid = (overrides: Partial<CampaignForm> = {}): CampaignForm => ({ ...emptyForm(), name: 'Promo', playlistId: 7, ...overrides });

describe('validateForm', () => {
  it('accepts the default form once it has a name and a playlist', () => {
    expect(validateForm(valid())).toBeNull();
  });

  it('asks for a name and a playlist', () => {
    expect(validateForm(valid({ name: '  ' }))).toBe('Dê um nome à campanha.');
    expect(validateForm(valid({ playlistId: null }))).toBe('Escolha a playlist da campanha.');
  });

  it('checks the priority range', () => {
    expect(validateForm(valid({ priority: -1 }))).toContain('prioridade');
    expect(validateForm(valid({ priority: 1001 }))).toContain('prioridade');
    expect(validateForm(valid({ priority: 1.5 }))).toContain('prioridade');
    expect(validateForm(valid({ priority: 0 }))).toBeNull();
  });

  it('refuses a start date after the end date but allows open-ended periods and a single day', () => {
    expect(validateForm(valid({ startDate: '2026-12-01', endDate: '2026-11-01' }))).toBe('A data de início não pode ser depois da data de fim.');
    expect(validateForm(valid({ startDate: '2026-11-01' }))).toBeNull();
    expect(validateForm(valid({ startDate: '2026-11-05', endDate: '2026-11-05' }))).toBeNull();
  });

  it('validates the daily window only when it is not "all day"', () => {
    expect(validateForm(valid({ allDay: false, startTime: '18:00', endTime: '18:00' }))).toBe('O horário de início e o de fim não podem ser iguais.');
    expect(validateForm(valid({ allDay: false, startTime: '', endTime: '18:00' }))).toContain('Informe o horário');
    expect(validateForm(valid({ allDay: false, startTime: '22:00', endTime: '06:00' }))).toBeNull(); // atravessa a meia-noite
    expect(validateForm(valid({ allDay: true, startTime: '18:00', endTime: '18:00' }))).toBeNull();
  });

  it('needs at least one weekday and at least one target', () => {
    expect(validateForm(valid({ days: new Set() }))).toBe('Marque pelo menos um dia da semana.');
    expect(validateForm(valid({ allDevices: false }))).toContain('Escolha para quem é a campanha');
    expect(validateForm(valid({ allDevices: false, groupIds: new Set([3]) }))).toBeNull();
  });
});

describe('toRequest', () => {
  it('sends "all TVs" as a single target and leaves empty fields out', () => {
    expect(toRequest(valid())).toEqual({
      name: 'Promo',
      description: null,
      playlistId: 7,
      priority: 50,
      enabled: true,
      startDate: null,
      endDate: null,
      startTime: null,
      endTime: null,
      daysOfWeek: [1, 2, 3, 4, 5, 6, 7],
      timezone: 'America/Sao_Paulo',
      targets: [{ type: 'ALL', id: null }],
    });
  });

  it('sends the daily window only when it is not all day, and sorts the weekdays', () => {
    const request = toRequest(valid({ allDay: false, startTime: '18:00', endTime: '23:00', days: new Set([6, 5]), startDate: '2026-11-01', endDate: '2026-11-30' }));

    expect(request.startTime).toBe('18:00');
    expect(request.endTime).toBe('23:00');
    expect(request.daysOfWeek).toEqual([5, 6]);
    expect(request.startDate).toBe('2026-11-01');
    expect(request.endDate).toBe('2026-11-30');
  });

  it('lists each chosen location, group and TV as its own target', () => {
    const request = toRequest(valid({ allDevices: false, locationIds: new Set([1]), groupIds: new Set([2, 3]), deviceIds: new Set([9]) }));

    expect(request.targets).toEqual([
      { type: 'LOCATION', id: 1 },
      { type: 'GROUP', id: 2 },
      { type: 'GROUP', id: 3 },
      { type: 'DEVICE', id: 9 },
    ]);
  });

  it('ignores the chosen lists when "all TVs" is on', () => {
    expect(toRequest(valid({ allDevices: true, locationIds: new Set([1]) })).targets).toEqual([{ type: 'ALL', id: null }]);
  });

  it('trims the text fields', () => {
    const request = toRequest(valid({ name: '  Promo  ', description: '   ' }));

    expect(request.name).toBe('Promo');
    expect(request.description).toBeNull();
  });
});

describe('formFrom', () => {
  it('round-trips a campaign through the form', () => {
    const original = campaign({
      startDate: '2026-11-01',
      endDate: '2026-11-30',
      startTime: '18:00:00',
      endTime: '23:00:00',
      daysOfWeek: [5, 6],
      targets: [
        { type: 'LOCATION', id: 1, name: 'Centro' },
        { type: 'DEVICE', id: 9, name: 'TV' },
      ],
    });

    const form = formFrom(original);

    expect(form.allDay).toBe(false);
    expect(form.startTime).toBe('18:00');
    expect(form.allDevices).toBe(false);
    expect([...form.locationIds]).toEqual([1]);
    expect([...form.deviceIds]).toEqual([9]);
    expect(toRequest(form)).toMatchObject({
      startDate: '2026-11-01',
      endDate: '2026-11-30',
      startTime: '18:00',
      endTime: '23:00',
      daysOfWeek: [5, 6],
      targets: [
        { type: 'LOCATION', id: 1 },
        { type: 'DEVICE', id: 9 },
      ],
    });
  });

  it('shows a campaign without hours as "all day" and with "all TVs"', () => {
    const form = formFrom(campaign());

    expect(form.allDay).toBe(true);
    expect(form.allDevices).toBe(true);
  });
});

describe('descriptions', () => {
  it('trims the seconds of a time', () => {
    expect(trimTime('18:00:00')).toBe('18:00');
    expect(trimTime(null)).toBe('');
  });

  it('names the common day sets', () => {
    expect(describeDays([1, 2, 3, 4, 5, 6, 7])).toBe('Todos os dias');
    expect(describeDays([5, 4, 3, 2, 1])).toBe('Seg a Sex');
    expect(describeDays([7, 6])).toBe('Fim de semana');
    expect(describeDays([1, 3, 5])).toBe('Seg, Qua, Sex');
  });

  it('writes the whole schedule in one sentence', () => {
    expect(describeSchedule(campaign())).toBe('Todos os dias, o dia todo');
    expect(
      describeSchedule(campaign({ daysOfWeek: [1, 2, 3, 4, 5], startTime: '18:00:00', endTime: '23:00:00', startDate: '2026-11-01', endDate: '2026-11-30' })),
    ).toBe('Seg a Sex, 18:00–23:00, de 01/11/2026 a 30/11/2026');
    expect(describeSchedule(campaign({ startTime: '08:00:00', startDate: '2026-11-01' }))).toBe('Todos os dias, a partir das 08:00, a partir de 01/11/2026');
    expect(describeSchedule(campaign({ endDate: '2026-12-31' }))).toBe('Todos os dias, o dia todo, até 31/12/2026');
  });

  it('summarises the targets', () => {
    expect(describeTargets([{ type: 'ALL', id: null, name: null }])).toBe('Todas as TVs');
    expect(
      describeTargets([
        { type: 'LOCATION', id: 1, name: 'A' },
        { type: 'LOCATION', id: 2, name: 'B' },
        { type: 'GROUP', id: 3, name: 'C' },
        { type: 'DEVICE', id: 4, name: 'D' },
      ]),
    ).toBe('2 locais, 1 grupo, 1 TV');
    expect(describeTargets([])).toContain('Sem alvo');
  });

  it('has a badge for every state', () => {
    expect(stateBadge('ACTIVE').tone).toBe('on');
    for (const state of ['ACTIVE', 'WAITING', 'SCHEDULED', 'ENDED', 'PAUSED'] as const) {
      expect(stateBadge(state).label.length).toBeGreaterThan(0);
      expect(stateBadge(state).hint.length).toBeGreaterThan(0);
    }
  });
});
