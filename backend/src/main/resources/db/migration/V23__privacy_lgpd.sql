-- LGPD: dados da plataforma (quem responde pelo tratamento), configurações de privacidade de cada cliente, registro de
-- pedidos de titulares atendidos e prova do aviso de privacidade mostrado a quem informa o nome nas páginas públicas.

-- A operação da plataforma: razão social, CNPJ e o canal do encarregado (aparecem na Política de Privacidade).
ALTER TABLE platform_settings ADD COLUMN legal_name VARCHAR(160);
ALTER TABLE platform_settings ADD COLUMN legal_document VARCHAR(30);
ALTER TABLE platform_settings ADD COLUMN privacy_contact_name VARCHAR(120);
ALTER TABLE platform_settings ADD COLUMN privacy_contact_email VARCHAR(255);

-- Por cliente (a loja, que é a controladora dos dados dos clientes dela): canal de contato e prazos de guarda dos nomes.
CREATE TABLE privacy_settings (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    contact_name VARCHAR(120),
    contact_email VARCHAR(255),
    -- as senhas da fila (com o nome de quem esperou) somem depois disto
    ticket_retention_days INTEGER NOT NULL DEFAULT 30 CHECK (ticket_retention_days BETWEEN 1 AND 365),
    -- o nome de quem pegou um cupom some depois disto (o cupom em si fica para os números)
    coupon_name_retention_days INTEGER NOT NULL DEFAULT 90 CHECK (coupon_name_retention_days BETWEEN 1 AND 365),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_privacy_settings_tenant ON privacy_settings (tenant_id);

-- Cada vez que o gestor exporta ou apaga os dados de uma pessoa fica um registro (sem o termo buscado: ele é dado pessoal).
CREATE TABLE privacy_requests (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    -- EXPORT (cópia dos dados) ou ERASE (dados apagados)
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('EXPORT', 'ERASE')),
    tickets INTEGER NOT NULL DEFAULT 0,
    coupons INTEGER NOT NULL DEFAULT 0,
    users INTEGER NOT NULL DEFAULT 0,
    note VARCHAR(200),
    performed_by VARCHAR(255) NOT NULL,
    performed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_privacy_requests_tenant ON privacy_requests (tenant_id, performed_at DESC);

-- Quem informa o nome na página pública concorda com o aviso: guardamos quando e qual versão do aviso foi mostrada.
ALTER TABLE tickets ADD COLUMN consent_at TIMESTAMPTZ;
ALTER TABLE tickets ADD COLUMN consent_notice VARCHAR(20);
ALTER TABLE promotion_coupons ADD COLUMN consent_at TIMESTAMPTZ;
ALTER TABLE promotion_coupons ADD COLUMN consent_notice VARCHAR(20);
