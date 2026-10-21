-- Comprovação de exibição (proof of play): cada vez que uma mídia foi ao ar numa TV, com a hora e a duração exatas
-- em milissegundos, medidas pelo próprio Player e encadeadas por hash (cada registro leva o hash do anterior).

CREATE TABLE play_logs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    -- número de ordem de cada registro da TV (1, 2, 3...): quem confere vê se faltou algum
    seq BIGINT NOT NULL,
    -- sem chave estrangeira de propósito: a prova sobrevive à exclusão da mídia, da playlist e da campanha
    media_id BIGINT,
    media_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(10) NOT NULL,
    playlist_id BIGINT,
    campaign_id BIGINT,
    campaign_name VARCHAR(150),
    zone_name VARCHAR(100),
    -- o horário em que começou, já corrigido pelo relógio do servidor; abaixo, o que o relógio da TV marcou e a correção
    started_at TIMESTAMPTZ NOT NULL,
    device_started_ms BIGINT NOT NULL,
    clock_offset_ms BIGINT NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL CHECK (duration_ms >= 0),
    planned_ms BIGINT,
    -- true = foi até o fim; false = interrompida (troca de playlist, TV fechada, erro)
    completed BOOLEAN NOT NULL,
    -- a corrente de hashes: o servidor confere ao receber e de novo a qualquer momento (verificação)
    prev_hash CHAR(64) NOT NULL,
    hash CHAR(64) NOT NULL,
    hash_valid BOOLEAN NOT NULL,
    continuous BOOLEAN NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (device_id, seq)
);
CREATE INDEX idx_play_logs_tenant_time ON play_logs (tenant_id, started_at DESC);
CREATE INDEX idx_play_logs_device_time ON play_logs (device_id, started_at DESC);
CREATE INDEX idx_play_logs_media_time ON play_logs (media_id, started_at DESC);

-- Um resumo por TV e por dia, que fica muito mais tempo que o detalhe (o detalhe é apagado no prazo de guarda): quantas
-- exibições, quanto tempo e o último hash da corrente naquele dia.
CREATE TABLE play_log_daily (
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    day DATE NOT NULL,
    plays BIGINT NOT NULL DEFAULT 0,
    completed BIGINT NOT NULL DEFAULT 0,
    total_ms BIGINT NOT NULL DEFAULT 0,
    first_seq BIGINT NOT NULL,
    last_seq BIGINT NOT NULL,
    last_hash CHAR(64) NOT NULL,
    PRIMARY KEY (device_id, day)
);
CREATE INDEX idx_play_log_daily_tenant_day ON play_log_daily (tenant_id, day);

-- Quantos dias o detalhe (linha a linha) fica guardado. Padrão curto: cada TV gera milhares de linhas por dia e o banco
-- gratuito é pequeno; o resumo diário fica por mais tempo (um ano).
ALTER TABLE monitoring_settings ADD COLUMN play_log_retention_days INTEGER NOT NULL DEFAULT 14
    CHECK (play_log_retention_days BETWEEN 7 AND 365);
