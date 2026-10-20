import { Me, PlanInfo, Role, TenantStatus, TenantSummary } from '../core/models';

/** Dados de teste da Fase 6 (conta, plano, cliente): só para os specs. */
export const PLAN: PlanInfo = {
  id: 3,
  code: 'basico',
  name: 'Básico',
  priceCents: 4900,
  currency: 'BRL',
  maxDevices: 5,
  maxStorageMb: 2048,
  maxUsers: 5,
};

export function tenantFixture(overrides: Partial<TenantSummary> = {}): TenantSummary {
  return {
    id: 2,
    name: 'Minha Empresa',
    slug: 'minha-empresa',
    status: 'ACTIVE' as TenantStatus,
    plan: PLAN,
    usage: { devices: 2, storageBytes: 300 * 1024 * 1024, users: 2 },
    trialEndsAt: null,
    currentPeriodEnd: null,
    billingEmail: null,
    ...overrides,
  };
}

export function meFixture(role: Role = 'ADMIN', overrides: Partial<Me> = {}): Me {
  return {
    id: 10,
    name: 'Ana',
    email: 'ana@empresa.com',
    role,
    platformAdmin: role === 'PLATFORM_ADMIN',
    tenant: tenantFixture(),
    ...overrides,
  };
}
