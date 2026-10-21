-- Cupons de assinatura: o gestor cria, o administrador do cliente resgata em "Plano e cobrança".
-- Tipos: PERCENT_OFF (porcentagem nas próximas faturas), AMOUNT_OFF (valor fixo nas próximas faturas),
-- FREE_DAYS (dias grátis de teste/período) e PLAN_UPGRADE (um plano maior liberado por alguns dias).
CREATE TABLE coupons (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL,
    description VARCHAR(255),
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('PERCENT_OFF', 'AMOUNT_OFF', 'FREE_DAYS', 'PLAN_UPGRADE')),
    -- porcentagem (1 a 100), centavos de desconto ou dias, conforme o tipo
    value INTEGER NOT NULL CHECK (value >= 1),
    -- descontos: em quantas faturas vale
    duration_months INTEGER CHECK (duration_months IS NULL OR duration_months >= 1),
    -- PLAN_UPGRADE: o plano liberado
    upgrade_plan_id BIGINT REFERENCES plans(id),
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    -- nulo = sem limite de usos
    max_redemptions INTEGER CHECK (max_redemptions IS NULL OR max_redemptions >= 1),
    redeemed_count INTEGER NOT NULL DEFAULT 0 CHECK (redeemed_count >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    -- nome do lote, quando o cupom foi gerado em série
    batch VARCHAR(60),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_coupons_code ON coupons (upper(code));

-- Quem resgatou o quê. Um cliente usa o mesmo cupom uma vez só.
CREATE TABLE coupon_redemptions (
    id BIGSERIAL PRIMARY KEY,
    coupon_id BIGINT NOT NULL REFERENCES coupons(id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    redeemed_by VARCHAR(255),
    redeemed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- descontos: quantas faturas ainda serão descontadas
    invoices_left INTEGER CHECK (invoices_left IS NULL OR invoices_left >= 0),
    UNIQUE (coupon_id, tenant_id)
);
CREATE INDEX idx_coupon_redemptions_tenant ON coupon_redemptions (tenant_id);

-- Plano liberado por um cupom: vale até a data; o plano pago continua sendo o cobrado.
ALTER TABLE tenants ADD COLUMN bonus_plan_id BIGINT REFERENCES plans(id);
ALTER TABLE tenants ADD COLUMN bonus_plan_until TIMESTAMPTZ;
