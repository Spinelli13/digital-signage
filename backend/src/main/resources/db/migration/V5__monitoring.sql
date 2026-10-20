-- Fase 4 (monitoramento): histórico de eventos, alertas, o que a TV exibiu e configurações de alerta.

-- Linha do tempo de cada TV: ficou online/offline, mudou de playlist, o Player foi atualizado, erros que o
-- próprio Player relatou... Apagar a TV apaga o histórico dela.
CREATE TABLE device_events (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL,
    level VARCHAR(10) NOT NULL DEFAULT 'INFO',
    message TEXT NOT NULL,
    -- quando aconteceu (para erros do Player é o horário informado por ele; para os demais, o do servidor)
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_device_events_device_time ON device_events (device_id, occurred_at DESC);
CREATE INDEX idx_device_events_time ON device_events (occurred_at DESC);
CREATE INDEX idx_device_events_type_time ON device_events (type, occurred_at DESC);

-- Estado que o servidor já registrou como evento (para só gravar a TRANSIÇÃO online -> offline, não repetir)
-- e o que o Player diz estar exibindo agora.
ALTER TABLE devices ADD COLUMN recorded_connection VARCHAR(12);
ALTER TABLE devices ADD COLUMN activated_at TIMESTAMPTZ;
ALTER TABLE devices ADD COLUMN current_media_id BIGINT;
ALTER TABLE devices ADD COLUMN last_playlist_id BIGINT;
ALTER TABLE devices ADD COLUMN last_campaign_id BIGINT;
-- TVs que já estavam ativas antes desta versão: a melhor aproximação da hora de ativação
UPDATE devices SET activated_at = updated_at WHERE status <> 'PENDING';

-- Alertas: uma linha por problema; fica aberto até o problema passar (resolved_at).
CREATE TABLE alerts (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL,
    message TEXT NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    -- quando o aviso foi enviado ao webhook (vazio = ainda não enviado ou sem webhook)
    notified_at TIMESTAMPTZ,
    resolution_notified_at TIMESTAMPTZ
);
-- no máximo UM alerta aberto por TV e tipo
CREATE UNIQUE INDEX uq_alerts_open ON alerts (device_id, type) WHERE resolved_at IS NULL;
CREATE INDEX idx_alerts_opened_at ON alerts (opened_at DESC);

-- Configurações (uma única linha).
CREATE TABLE monitoring_settings (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    offline_after_minutes INTEGER NOT NULL DEFAULT 10 CHECK (offline_after_minutes BETWEEN 1 AND 1440),
    never_connected_after_hours INTEGER NOT NULL DEFAULT 24 CHECK (never_connected_after_hours BETWEEN 1 AND 720),
    -- erros do Player nos últimos 10 minutos que abrem o alerta "erros repetidos"
    error_burst_count INTEGER NOT NULL DEFAULT 5 CHECK (error_burst_count BETWEEN 1 AND 100),
    webhook_url TEXT,
    notify_on_resolve BOOLEAN NOT NULL DEFAULT TRUE,
    events_retention_days INTEGER NOT NULL DEFAULT 90 CHECK (events_retention_days BETWEEN 7 AND 730),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO monitoring_settings (id) VALUES (1);

-- O que cada TV exibiu, por mídia e por dia (relatório de exibição). Apagar a TV ou a mídia apaga a linha.
CREATE TABLE media_plays (
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    media_id BIGINT NOT NULL REFERENCES media(id) ON DELETE CASCADE,
    day DATE NOT NULL,
    plays INTEGER NOT NULL DEFAULT 0,
    seconds BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (device_id, media_id, day)
);
CREATE INDEX idx_media_plays_day ON media_plays (day);
