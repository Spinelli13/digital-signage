-- Tabelas de preços: o que as TVs mostram nos painéis de preço (cardápio, tabela de serviços, preços do dia).
-- Os itens e as regras de preço por horário ("happy hour", "almoço executivo") ficam juntos, em JSON, porque sempre são lidos
-- e gravados como um conjunto só.
CREATE TABLE price_lists (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    name VARCHAR(80) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'BRL',
    timezone VARCHAR(60) NOT NULL DEFAULT 'America/Sao_Paulo',
    items JSONB NOT NULL DEFAULT '[]'::jsonb,
    rules JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_price_lists_tenant ON price_lists (tenant_id);
CREATE UNIQUE INDEX uq_price_lists_name ON price_lists (tenant_id, lower(name));
