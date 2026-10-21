-- Cupons para os clientes da loja: o consumidor escaneia um QR Code na TV, recebe um código único e o caixa o resgata no painel.
-- (Diferente dos cupons de assinatura da plataforma, V20.) Recurso opcional do plano: COUPONS.
CREATE TABLE promotions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    name VARCHAR(120) NOT NULL,
    -- regras que o consumidor lê (ex.: "válido para compras acima de R$ 50")
    description VARCHAR(500),
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    -- quantos dias o cupom vale depois de pego (nulo = até o fim da promoção)
    claim_days INTEGER CHECK (claim_days IS NULL OR claim_days >= 1),
    max_claims INTEGER CHECK (max_claims IS NULL OR max_claims >= 1),
    claimed_count INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    -- o segredo que vai no QR Code
    public_token VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_promotions_tenant ON promotions (tenant_id);
CREATE UNIQUE INDEX uq_promotions_token ON promotions (public_token);

-- Um cupom pego por um consumidor. O nome é opcional (dado pessoal): é apagado depois de alguns dias.
CREATE TABLE promotion_coupons (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    promotion_id BIGINT NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    code VARCHAR(9) NOT NULL,
    -- ISSUED (pego, ainda não usado) ou REDEEMED (resgatado no caixa); "vencido" se deduz da data
    status VARCHAR(10) NOT NULL DEFAULT 'ISSUED',
    claimant_name VARCHAR(60),
    -- o aparelho que pegou (código aleatório guardado pela página): o mesmo aparelho recebe sempre o mesmo cupom
    client_id VARCHAR(64),
    public_token VARCHAR(32) NOT NULL,
    claimed_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    redeemed_at TIMESTAMPTZ,
    redeemed_by VARCHAR(255)
);
CREATE UNIQUE INDEX uq_promotion_coupons_code ON promotion_coupons (tenant_id, code);
CREATE UNIQUE INDEX uq_promotion_coupons_token ON promotion_coupons (public_token);
CREATE UNIQUE INDEX uq_promotion_coupons_device ON promotion_coupons (promotion_id, client_id) WHERE client_id IS NOT NULL;
CREATE INDEX idx_promotion_coupons_promotion ON promotion_coupons (promotion_id, claimed_at DESC);

-- O recurso novo entra nos planos que já existem (nada muda para quem já usa); o gestor ajusta no painel.
ALTER TABLE plans ALTER COLUMN features SET DEFAULT 'CAMPAIGNS,COUPONS,LAYOUTS,WIDGETS,PRICES,QUEUE,PROOF_OF_PLAY';
UPDATE plans SET features = features || ',COUPONS' WHERE features NOT LIKE '%COUPONS%';
