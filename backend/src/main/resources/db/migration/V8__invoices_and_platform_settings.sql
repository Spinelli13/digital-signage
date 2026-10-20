-- Fase 6 (SaaS): cobrança. Faturas por cliente e as configurações da operação da plataforma.

-- Uma fatura é a cobrança de um período de assinatura (ou um valor avulso). O pagamento é registrado pela operação
-- da plataforma (PIX, boleto, cartão fora do sistema...): o sistema controla vencimento, carência e suspensão.
CREATE SEQUENCE invoice_number_seq START 1;

CREATE TABLE invoices (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    number VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL,
    period_start TIMESTAMPTZ,
    period_end TIMESTAMPTZ,
    amount_cents INTEGER NOT NULL CHECK (amount_cents >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'BRL',
    -- OPEN (a pagar), PAID (paga) ou VOID (cancelada)
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'PAID', 'VOID')),
    due_date DATE NOT NULL,
    paid_at TIMESTAMPTZ,
    payment_method VARCHAR(20),
    payment_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_invoices_tenant ON invoices (tenant_id, created_at DESC);
CREATE INDEX idx_invoices_open_due ON invoices (due_date) WHERE status = 'OPEN';

-- Configurações da plataforma (uma linha): como o cliente paga e como falar com a operação.
CREATE TABLE platform_settings (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    company_name VARCHAR(150) NOT NULL DEFAULT 'Digital Signage',
    payment_instructions TEXT,
    support_email VARCHAR(255),
    trial_days INTEGER NOT NULL DEFAULT 14 CHECK (trial_days BETWEEN 0 AND 90),
    -- dias de uso depois do vencimento antes de suspender o painel
    grace_days INTEGER NOT NULL DEFAULT 7 CHECK (grace_days BETWEEN 0 AND 60),
    -- quantos dias antes do fim do período a próxima fatura é emitida
    invoice_lead_days INTEGER NOT NULL DEFAULT 5 CHECK (invoice_lead_days BETWEEN 0 AND 30),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO platform_settings (id, payment_instructions) VALUES (1, 'Pague por PIX ou boleto e envie o comprovante para o suporte. Assim que o pagamento for confirmado a fatura é baixada.');
