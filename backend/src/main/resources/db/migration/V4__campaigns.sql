-- Fase 3 (campanhas): uma playlist que só vale em certas datas/horas/dias, para certas TVs, com prioridade.
--
-- Cada TV continua tendo a sua playlist "base" (devices.current_playlist_id). Uma campanha ativa AGORA e que
-- alcança a TV tem prioridade sobre a base; sem campanha ativa, a TV exibe a base.

CREATE TABLE campaigns (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    -- RESTRICT: não dá para apagar uma playlist que ainda é usada por uma campanha (a API explica o motivo)
    playlist_id BIGINT NOT NULL REFERENCES playlists(id) ON DELETE RESTRICT,
    -- maior número vence quando duas campanhas alcançam a mesma TV ao mesmo tempo
    priority INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    -- período (datas inclusivas, no fuso da campanha); vazio = sem limite
    start_date DATE,
    end_date DATE,
    -- janela diária no fuso da campanha; vazio = o dia todo. Se o fim é menor que o início, atravessa a meia-noite.
    start_time TIME,
    end_time TIME,
    -- dias da semana em que vale, padrão ISO (1 = segunda ... 7 = domingo)
    days_of_week VARCHAR(20) NOT NULL DEFAULT '1,2,3,4,5,6,7',
    timezone VARCHAR(64) NOT NULL DEFAULT 'America/Sao_Paulo',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT campaigns_period_order CHECK (start_date IS NULL OR end_date IS NULL OR start_date <= end_date),
    CONSTRAINT campaigns_time_differs CHECK (start_time IS NULL OR end_time IS NULL OR start_time <> end_time)
);
CREATE INDEX idx_campaigns_playlist_id ON campaigns (playlist_id);

-- Quem recebe a campanha: todas as TVs, uma TV, um local ou um grupo (várias linhas = soma dos alvos).
-- Colunas separadas com chave estrangeira própria: apagar a TV/local/grupo remove o alvo sozinho.
CREATE TABLE campaign_targets (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    all_devices BOOLEAN NOT NULL DEFAULT FALSE,
    device_id BIGINT REFERENCES devices(id) ON DELETE CASCADE,
    location_id BIGINT REFERENCES locations(id) ON DELETE CASCADE,
    group_id BIGINT REFERENCES device_groups(id) ON DELETE CASCADE,
    -- exatamente um tipo de alvo por linha
    CONSTRAINT campaign_targets_one_kind CHECK (
        (CASE WHEN all_devices THEN 1 ELSE 0 END)
        + (CASE WHEN device_id IS NOT NULL THEN 1 ELSE 0 END)
        + (CASE WHEN location_id IS NOT NULL THEN 1 ELSE 0 END)
        + (CASE WHEN group_id IS NOT NULL THEN 1 ELSE 0 END) = 1)
);
CREATE INDEX idx_campaign_targets_campaign ON campaign_targets (campaign_id);
CREATE INDEX idx_campaign_targets_device ON campaign_targets (device_id);
CREATE INDEX idx_campaign_targets_location ON campaign_targets (location_id);
CREATE INDEX idx_campaign_targets_group ON campaign_targets (group_id);
