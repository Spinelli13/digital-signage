import { Campaign, CampaignRequest, CampaignState, TargetType } from '../core/models';

/** Estado do formulário de campanha (os campos como a pessoa os preenche). */
export interface CampaignForm {
  name: string;
  description: string;
  playlistId: number | null;
  priority: number;
  enabled: boolean;
  startDate: string;
  endDate: string;
  allDay: boolean;
  startTime: string;
  endTime: string;
  days: ReadonlySet<number>;
  timezone: string;
  allDevices: boolean;
  locationIds: ReadonlySet<number>;
  groupIds: ReadonlySet<number>;
  deviceIds: ReadonlySet<number>;
}

export const ALL_DAYS: readonly number[] = [1, 2, 3, 4, 5, 6, 7];
export const DAY_LABELS: Record<number, string> = { 1: 'Seg', 2: 'Ter', 3: 'Qua', 4: 'Qui', 5: 'Sex', 6: 'Sáb', 7: 'Dom' };

export const TIMEZONES: { value: string; label: string }[] = [
  { value: 'America/Sao_Paulo', label: 'Brasília (São Paulo, Rio, Belo Horizonte…)' },
  { value: 'America/Manaus', label: 'Manaus (UTC−4)' },
  { value: 'America/Cuiaba', label: 'Cuiabá (UTC−4)' },
  { value: 'America/Fortaleza', label: 'Fortaleza, Recife, Salvador (UTC−3, sem horário de verão)' },
  { value: 'America/Rio_Branco', label: 'Rio Branco (UTC−5)' },
  { value: 'America/Noronha', label: 'Fernando de Noronha (UTC−2)' },
  { value: 'UTC', label: 'UTC' },
];

export function emptyForm(): CampaignForm {
  return {
    name: '',
    description: '',
    playlistId: null,
    priority: 50,
    enabled: true,
    startDate: '',
    endDate: '',
    allDay: true,
    startTime: '18:00',
    endTime: '23:00',
    days: new Set(ALL_DAYS),
    timezone: 'America/Sao_Paulo',
    allDevices: true,
    locationIds: new Set(),
    groupIds: new Set(),
    deviceIds: new Set(),
  };
}

/** "18:00:00" → "18:00" (o campo de hora do navegador não aceita os segundos). */
export function trimTime(time: string | null): string {
  return time ? time.slice(0, 5) : '';
}

export function formFrom(campaign: Campaign): CampaignForm {
  const idsOf = (type: TargetType) => new Set(campaign.targets.filter((t) => t.type === type && t.id !== null).map((t) => t.id as number));
  const allDay = !campaign.startTime && !campaign.endTime;
  return {
    name: campaign.name,
    description: campaign.description ?? '',
    playlistId: campaign.playlistId,
    priority: campaign.priority,
    enabled: campaign.enabled,
    startDate: campaign.startDate ?? '',
    endDate: campaign.endDate ?? '',
    allDay,
    startTime: trimTime(campaign.startTime) || '00:00',
    endTime: trimTime(campaign.endTime) || '23:59',
    days: new Set(campaign.daysOfWeek),
    timezone: campaign.timezone,
    allDevices: campaign.targets.some((t) => t.type === 'ALL'),
    locationIds: idsOf('LOCATION'),
    groupIds: idsOf('GROUP'),
    deviceIds: idsOf('DEVICE'),
  };
}

/** Primeiro problema do formulário, em português, ou null se está tudo certo (o servidor confere de novo). */
export function validateForm(form: CampaignForm): string | null {
  if (!form.name.trim()) {
    return 'Dê um nome à campanha.';
  }
  if (form.playlistId === null) {
    return 'Escolha a playlist da campanha.';
  }
  if (!Number.isInteger(form.priority) || form.priority < 0 || form.priority > 1000) {
    return 'A prioridade precisa ser um número de 0 a 1000.';
  }
  if (form.startDate && form.endDate && form.startDate > form.endDate) {
    return 'A data de início não pode ser depois da data de fim.';
  }
  if (!form.allDay) {
    if (!form.startTime || !form.endTime) {
      return 'Informe o horário de início e o de fim (ou marque "o dia todo").';
    }
    if (form.startTime === form.endTime) {
      return 'O horário de início e o de fim não podem ser iguais.';
    }
  }
  if (form.days.size === 0) {
    return 'Marque pelo menos um dia da semana.';
  }
  if (!form.allDevices && form.locationIds.size + form.groupIds.size + form.deviceIds.size === 0) {
    return 'Escolha para quem é a campanha: todas as TVs ou pelo menos um local, grupo ou TV.';
  }
  return null;
}

export function toRequest(form: CampaignForm): CampaignRequest {
  const targets: CampaignRequest['targets'] = form.allDevices
    ? [{ type: 'ALL', id: null }]
    : [
        ...[...form.locationIds].map((id) => ({ type: 'LOCATION' as const, id })),
        ...[...form.groupIds].map((id) => ({ type: 'GROUP' as const, id })),
        ...[...form.deviceIds].map((id) => ({ type: 'DEVICE' as const, id })),
      ];
  return {
    name: form.name.trim(),
    description: form.description.trim() || null,
    playlistId: form.playlistId as number,
    priority: form.priority,
    enabled: form.enabled,
    startDate: form.startDate || null,
    endDate: form.endDate || null,
    startTime: form.allDay ? null : form.startTime,
    endTime: form.allDay ? null : form.endTime,
    daysOfWeek: [...form.days].sort((a, b) => a - b),
    timezone: form.timezone,
    targets,
  };
}

/** "Todos os dias", "Seg a Sex", "Fim de semana" ou a lista ("Seg, Qua, Sex"). */
export function describeDays(days: readonly number[]): string {
  const sorted = [...days].sort((a, b) => a - b);
  const key = sorted.join(',');
  if (key === ALL_DAYS.join(',')) {
    return 'Todos os dias';
  }
  if (key === '1,2,3,4,5') {
    return 'Seg a Sex';
  }
  if (key === '6,7') {
    return 'Fim de semana';
  }
  return sorted.map((d) => DAY_LABELS[d]).join(', ');
}

function brDate(iso: string): string {
  const [year, month, day] = iso.split('-');
  return `${day}/${month}/${year}`;
}

/** Uma frase com a agenda: "Seg a Sex, 18:00–23:00, de 01/11/2026 a 30/11/2026". */
export function describeSchedule(campaign: Pick<Campaign, 'daysOfWeek' | 'startTime' | 'endTime' | 'startDate' | 'endDate'>): string {
  const parts = [describeDays(campaign.daysOfWeek)];
  if (campaign.startTime || campaign.endTime) {
    const start = trimTime(campaign.startTime) || '00:00';
    const end = trimTime(campaign.endTime);
    parts.push(end ? `${start}–${end}` : `a partir das ${start}`);
  } else {
    parts.push('o dia todo');
  }
  if (campaign.startDate && campaign.endDate) {
    parts.push(`de ${brDate(campaign.startDate)} a ${brDate(campaign.endDate)}`);
  } else if (campaign.startDate) {
    parts.push(`a partir de ${brDate(campaign.startDate)}`);
  } else if (campaign.endDate) {
    parts.push(`até ${brDate(campaign.endDate)}`);
  }
  return parts.join(', ');
}

/** "Em 3 locais, 1 grupo e 2 TVs" / "Todas as TVs". */
export function describeTargets(targets: Campaign['targets']): string {
  if (targets.length === 0) {
    return 'Sem alvo (não alcança nenhuma TV)';
  }
  if (targets.some((t) => t.type === 'ALL')) {
    return 'Todas as TVs';
  }
  const count = (type: TargetType) => targets.filter((t) => t.type === type).length;
  const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;
  return [
    count('LOCATION') ? plural(count('LOCATION'), 'local', 'locais') : '',
    count('GROUP') ? plural(count('GROUP'), 'grupo', 'grupos') : '',
    count('DEVICE') ? plural(count('DEVICE'), 'TV', 'TVs') : '',
  ]
    .filter(Boolean)
    .join(', ');
}

export interface StateBadge {
  label: string;
  tone: 'on' | 'off' | 'warn';
  hint: string;
}

const BADGES: Record<CampaignState, StateBadge> = {
  ACTIVE: { label: 'Ativa agora', tone: 'on', hint: 'Está valendo neste momento nas TVs alcançadas.' },
  WAITING: { label: 'Aguardando horário', tone: 'warn', hint: 'Está no período, mas hoje ainda não é o dia ou a hora dela.' },
  SCHEDULED: { label: 'Agendada', tone: 'warn', hint: 'O período ainda não começou.' },
  ENDED: { label: 'Encerrada', tone: 'off', hint: 'O período já terminou.' },
  PAUSED: { label: 'Pausada', tone: 'off', hint: 'Pausada: não é exibida até você reativá-la.' },
};

export function stateBadge(state: CampaignState): StateBadge {
  return BADGES[state];
}
