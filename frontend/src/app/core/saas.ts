import { currentLocale, tr, trServer } from './i18n';
import { InvoiceStatus, PlanInfo, Role, TenantStatus, Usage } from './models';

/** Os textos abaixo são as chaves em português; use roleLabel() etc. para obter o texto no idioma atual. */
export const ROLE_LABELS: Record<Role, string> = {
  VIEWER: 'Visualizador',
  EDITOR: 'Editor',
  ADMIN: 'Administrador',
  PLATFORM_ADMIN: 'Operador da plataforma',
};

/** O que cada papel pode (para o formulário de equipe e o manual). */
export const ROLE_HELP: Record<Role, string> = {
  VIEWER: 'Só consulta: vê TVs, conteúdo, alertas e relatórios.',
  EDITOR: 'Cuida do conteúdo (mídias, playlists, campanhas, layouts) e escolhe o que cada TV exibe.',
  ADMIN: 'Administra o cliente: TVs, locais, grupos, alertas, equipe, plano e cobrança.',
  PLATFORM_ADMIN: 'Opera a plataforma (clientes, planos e faturas).',
};

/** Os papéis que um Administrador de cliente pode dar à equipe. */
export const TEAM_ROLES: Role[] = ['VIEWER', 'EDITOR', 'ADMIN'];

export const TENANT_STATUS_LABELS: Record<TenantStatus, string> = {
  SYSTEM: 'Reserva',
  TRIAL: 'Em teste',
  ACTIVE: 'Em dia',
  PAST_DUE: 'Em atraso',
  SUSPENDED: 'Suspenso',
  CANCELED: 'Encerrado',
};

/** Cor de cada situação (usa as mesmas bolinhas das TVs). */
export const TENANT_STATUS_TONES: Record<TenantStatus, 'on' | 'warn' | 'off'> = {
  SYSTEM: 'off',
  TRIAL: 'warn',
  ACTIVE: 'on',
  PAST_DUE: 'warn',
  SUSPENDED: 'off',
  CANCELED: 'off',
};

export const INVOICE_STATUS_LABELS: Record<InvoiceStatus, string> = { OPEN: 'A pagar', PAID: 'Paga', VOID: 'Cancelada' };

export const roleLabel = (role: Role): string => tr(ROLE_LABELS[role]);
export const roleHelp = (role: Role): string => tr(ROLE_HELP[role]);
export const tenantStatusLabel = (status: TenantStatus): string => tr(TENANT_STATUS_LABELS[status]);
export const invoiceStatusLabel = (status: InvoiceStatus): string => tr(INVOICE_STATUS_LABELS[status]);

/** Nome e descrição dos planos de fábrica vêm do servidor em português: os conhecidos aparecem traduzidos. */
export const planText = (text: string | null | undefined): string => (text ? trServer(text) : '');

/** Dias inteiros até a data (negativo = já passou). */
export function daysUntil(iso: string, now: number = Date.now()): number {
  return Math.ceil((new Date(iso).getTime() - now) / 86_400_000);
}

/** "R$ 49,00" (português) ou "R$49.00" (inglês): a moeda do plano continua a mesma. */
export function formatMoney(cents: number, currency = 'BRL'): string {
  return (cents / 100).toLocaleString(currentLocale(), { style: 'currency', currency });
}

/** Limite de um plano: o número ou "ilimitado". */
export function formatLimit(value: number | null, unit = ''): string {
  return value === null ? tr('ilimitado') : `${value}${unit}`;
}

/** Espaço em MB ou GB com uma casa decimal quando pequeno. */
export function formatStorage(bytes: number): string {
  const mb = bytes / (1024 * 1024);
  const one = (value: number) => value.toLocaleString(currentLocale(), { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  if (mb >= 1024) {
    return `${one(mb / 1024)} GB`;
  }
  return `${mb < 10 ? one(mb) : Math.round(mb)} MB`;
}

/** Quanto do limite já foi usado (0–100); sem limite = 0 (não há o que encher). */
export function usagePercent(used: number, limit: number | null): number {
  if (limit === null || limit <= 0) {
    return 0;
  }
  return Math.min(100, Math.round((used * 100) / limit));
}

/** Os três usos de um cliente em linhas prontas para mostrar (nome, "usado de limite", percentual). */
export function usageRows(plan: PlanInfo, usage: Usage): { label: string; text: string; percent: number }[] {
  const storageLimit = plan.maxStorageMb === null ? null : plan.maxStorageMb * 1024 * 1024;
  return [
    {
      label: tr('TVs'),
      text: tr('{used} de {limit}', { used: usage.devices, limit: formatLimit(plan.maxDevices) }),
      percent: usagePercent(usage.devices, plan.maxDevices),
    },
    {
      label: tr('Espaço de mídias'),
      text: tr('{used} de {limit}', {
        used: formatStorage(usage.storageBytes),
        limit: storageLimit === null ? tr('ilimitado') : formatStorage(storageLimit),
      }),
      percent: usagePercent(usage.storageBytes, storageLimit),
    },
    {
      label: tr('Usuários'),
      text: tr('{used} de {limit}', { used: usage.users, limit: formatLimit(plan.maxUsers) }),
      percent: usagePercent(usage.users, plan.maxUsers),
    },
  ];
}

/** "49,90" ou "49.90" (ou "1.249,90") em centavos; texto vazio ou inválido vira null. */
export function parseMoney(text: string): number | null {
  const clean = text.trim().replace(/[R$\s]/g, '');
  if (!clean) {
    return null;
  }
  // O último separador é o decimal: "1.249,90" (pt) e "1,249.90" (en) valem o mesmo; "49,90" e "49.90" também.
  const normalized =
    clean.lastIndexOf(',') > clean.lastIndexOf('.') ? clean.replace(/\./g, '').replace(',', '.') : clean.replace(/,/g, '');
  const value = Number(normalized);
  return Number.isFinite(value) && value >= 0 ? Math.round(value * 100) : null;
}

/** Centavos no campo de valor ("49,00" em português, "49.00" em inglês). */
export function moneyInput(cents: number): string {
  const text = (cents / 100).toFixed(2);
  return currentLocale() === 'pt-BR' ? text.replace('.', ',') : text;
}

/** Instante ISO no campo de data (AAAA-MM-DD, no fuso do navegador). */
export function dateInput(iso: string | null): string {
  if (!iso) {
    return '';
  }
  const date = new Date(iso);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** Data do campo (AAAA-MM-DD) como instante: fim do dia no fuso do navegador. */
export function endOfDayIso(date: string): string {
  return new Date(`${date}T23:59:59`).toISOString();
}
