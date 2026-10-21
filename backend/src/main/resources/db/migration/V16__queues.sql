-- Fila de atendimento: filas (com letra da senha e prioridade), guichês e as senhas emitidas.
-- As senhas guardam o nome que a pessoa informou (dado pessoal): são apagadas depois de alguns dias (ver TicketRetention).
CREATE TABLE service_queues (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    name VARCHAR(60) NOT NULL,
    prefix VARCHAR(3) NOT NULL,
    priority BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    timezone VARCHAR(60) NOT NULL DEFAULT 'America/Sao_Paulo',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_service_queues_tenant ON service_queues (tenant_id);
CREATE UNIQUE INDEX uq_service_queues_name ON service_queues (tenant_id, lower(name));
CREATE UNIQUE INDEX uq_service_queues_prefix ON service_queues (tenant_id, upper(prefix));

CREATE TABLE service_counters (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    name VARCHAR(40) NOT NULL,
    -- as filas que o guichê atende (vazio = todas)
    queue_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_service_counters_tenant ON service_counters (tenant_id);
CREATE UNIQUE INDEX uq_service_counters_name ON service_counters (tenant_id, lower(name));

CREATE TABLE tickets (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    queue_id BIGINT NOT NULL REFERENCES service_queues(id) ON DELETE CASCADE,
    ticket_day DATE NOT NULL,
    number INT NOT NULL,
    label VARCHAR(10) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'WAITING',
    counter_id BIGINT REFERENCES service_counters(id) ON DELETE SET NULL,
    customer_name VARCHAR(60),
    source VARCHAR(10) NOT NULL DEFAULT 'PANEL',
    issued_at TIMESTAMPTZ NOT NULL,
    first_called_at TIMESTAMPTZ,
    called_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    call_count INT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_tickets_number ON tickets (queue_id, ticket_day, number);
CREATE INDEX idx_tickets_tenant_status ON tickets (tenant_id, status);
CREATE INDEX idx_tickets_tenant_day ON tickets (tenant_id, ticket_day);
