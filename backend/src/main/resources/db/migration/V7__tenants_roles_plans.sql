-- Fase 6 (SaaS): clientes (tenants), planos, papéis de usuário e isolamento dos dados por cliente.

-- ---------- planos ----------
-- Limites nulos = sem limite. Os preços são um ponto de partida: a operação da plataforma edita tudo no painel.
CREATE TABLE plans (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    price_cents INTEGER NOT NULL DEFAULT 0 CHECK (price_cents >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'BRL',
    max_devices INTEGER CHECK (max_devices IS NULL OR max_devices >= 0),
    max_storage_mb INTEGER CHECK (max_storage_mb IS NULL OR max_storage_mb >= 0),
    max_users INTEGER CHECK (max_users IS NULL OR max_users >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO plans (code, name, description, price_cents, max_devices, max_storage_mb, max_users, sort_order) VALUES
    ('interno', 'Interno (sem limites)', 'Uso próprio da operação: sem limites e sem cobrança.', 0, NULL, NULL, NULL, 0),
    ('gratis', 'Grátis', 'Para conhecer: 2 TVs.', 0, 2, 200, 2, 1),
    ('basico', 'Básico', 'Pequenos negócios: até 5 TVs.', 4900, 5, 2048, 5, 2),
    ('profissional', 'Profissional', 'Várias lojas: até 25 TVs.', 14900, 25, 10240, 20, 3);

-- ---------- clientes ----------
-- status: SYSTEM (a reserva das TVs ainda sem cliente), TRIAL, ACTIVE, PAST_DUE (fatura vencida, ainda funciona),
-- SUSPENDED (painel bloqueado; as TVs seguem exibindo o que já têm) e CANCELED.
CREATE TABLE tenants (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(60) NOT NULL UNIQUE,
    plan_id BIGINT NOT NULL REFERENCES plans(id),
    status VARCHAR(20) NOT NULL DEFAULT 'TRIAL',
    trial_ends_at TIMESTAMPTZ,
    current_period_end TIMESTAMPTZ,
    billing_email VARCHAR(255),
    document VARCHAR(30),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 1 = a reserva onde ficam as TVs que se registraram mas ainda não foram ativadas por nenhum cliente;
-- 2 = o cliente onde está tudo que já existia antes desta versão.
INSERT INTO tenants (id, name, slug, plan_id, status) VALUES
    (1, 'Sem cliente (aguardando ativação)', 'pool', (SELECT id FROM plans WHERE code = 'interno'), 'SYSTEM'),
    (2, 'Minha empresa', 'minha-empresa', (SELECT id FROM plans WHERE code = 'interno'), 'ACTIVE');
SELECT setval(pg_get_serial_sequence('tenants', 'id'), 2);

-- ---------- usuários ----------
-- Papéis: PLATFORM_ADMIN (opera a plataforma e entra em qualquer cliente), ADMIN, EDITOR, VIEWER (de um cliente).
ALTER TABLE users ADD COLUMN tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'ADMIN';
ALTER TABLE users ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
-- Quem já administrava esta instalação continua com poder total e passa a operar também a plataforma.
UPDATE users SET tenant_id = 2, role = 'PLATFORM_ADMIN';
CREATE INDEX idx_users_tenant ON users (tenant_id);

-- ---------- isolamento dos dados ----------
-- Toda tabela de negócio ganha o cliente dono da linha. Tudo que existia vai para o cliente 2; as TVs que
-- ainda aguardam ativação ficam na reserva (1) até um cliente digitar o código.
ALTER TABLE devices ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 2 REFERENCES tenants(id);
UPDATE devices SET tenant_id = 1 WHERE status = 'PENDING';
ALTER TABLE media ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 2 REFERENCES tenants(id);
ALTER TABLE playlists ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 2 REFERENCES tenants(id);
ALTER TABLE locations ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 2 REFERENCES tenants(id);
ALTER TABLE device_groups ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 2 REFERENCES tenants(id);
ALTER TABLE campaigns ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 2 REFERENCES tenants(id);
ALTER TABLE layouts ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 2 REFERENCES tenants(id);
ALTER TABLE device_events ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 2 REFERENCES tenants(id);
ALTER TABLE alerts ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 2 REFERENCES tenants(id);

-- Sem valor padrão daqui para frente: uma linha sem cliente é um erro, não um vazamento silencioso.
ALTER TABLE devices ALTER COLUMN tenant_id DROP DEFAULT;
ALTER TABLE media ALTER COLUMN tenant_id DROP DEFAULT;
ALTER TABLE playlists ALTER COLUMN tenant_id DROP DEFAULT;
ALTER TABLE locations ALTER COLUMN tenant_id DROP DEFAULT;
ALTER TABLE device_groups ALTER COLUMN tenant_id DROP DEFAULT;
ALTER TABLE campaigns ALTER COLUMN tenant_id DROP DEFAULT;
ALTER TABLE layouts ALTER COLUMN tenant_id DROP DEFAULT;
ALTER TABLE device_events ALTER COLUMN tenant_id DROP DEFAULT;
ALTER TABLE alerts ALTER COLUMN tenant_id DROP DEFAULT;

CREATE INDEX idx_devices_tenant ON devices (tenant_id);
CREATE INDEX idx_media_tenant ON media (tenant_id);
CREATE INDEX idx_playlists_tenant ON playlists (tenant_id);
CREATE INDEX idx_locations_tenant ON locations (tenant_id);
CREATE INDEX idx_device_groups_tenant ON device_groups (tenant_id);
CREATE INDEX idx_campaigns_tenant ON campaigns (tenant_id);
CREATE INDEX idx_layouts_tenant ON layouts (tenant_id);
CREATE INDEX idx_device_events_tenant ON device_events (tenant_id);
CREATE INDEX idx_alerts_tenant ON alerts (tenant_id);

-- Nomes únicos passam a valer por cliente (dois clientes podem ter um local "Loja Centro").
DROP INDEX idx_locations_name_lower;
CREATE UNIQUE INDEX idx_locations_name_lower ON locations (tenant_id, lower(name));
DROP INDEX idx_device_groups_name_lower;
CREATE UNIQUE INDEX idx_device_groups_name_lower ON device_groups (tenant_id, lower(name));
DROP INDEX uq_layouts_name;
CREATE UNIQUE INDEX uq_layouts_name ON layouts (tenant_id, lower(name));

-- Configurações de monitoramento: uma linha por cliente (antes era uma linha só, id = 1).
ALTER TABLE monitoring_settings DROP CONSTRAINT monitoring_settings_id_check;
ALTER TABLE monitoring_settings DROP CONSTRAINT monitoring_settings_pkey;
ALTER TABLE monitoring_settings ALTER COLUMN id TYPE BIGINT;
CREATE SEQUENCE monitoring_settings_id_seq START 2 OWNED BY monitoring_settings.id;
ALTER TABLE monitoring_settings ALTER COLUMN id SET DEFAULT nextval('monitoring_settings_id_seq');
ALTER TABLE monitoring_settings ADD PRIMARY KEY (id);
ALTER TABLE monitoring_settings ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 2 REFERENCES tenants(id);
ALTER TABLE monitoring_settings ALTER COLUMN tenant_id DROP DEFAULT;
CREATE UNIQUE INDEX uq_monitoring_settings_tenant ON monitoring_settings (tenant_id);
