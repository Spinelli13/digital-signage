-- Etapa 1.3: o registro de exibição passa a ocupar menos espaço no banco e cada dia ganha um resumo por mídia, guardado por um ano.
-- Nada é perdido: os hashes são convertidos sem alteração de conteúdo, e o resumo por mídia é calculado também sobre o que já existe.
--
-- Para desfazer à mão (o Flyway Community não desfaz), nesta ordem:
--   DROP TABLE play_log_daily_media;
--   ALTER TABLE play_logs ALTER COLUMN prev_hash TYPE CHAR(64) USING encode(prev_hash, 'hex'),
--                         ALTER COLUMN hash TYPE CHAR(64) USING encode(hash, 'hex');
--   CREATE INDEX idx_play_logs_media_time ON play_logs (media_id, started_at DESC);
--   ALTER TABLE monitoring_settings ALTER COLUMN play_log_retention_days SET DEFAULT 14;
--   DELETE FROM flyway_schema_history WHERE version = '24';

-- 1. Resumo diário por TV, por mídia e por campanha: "quantas vezes o anúncio X rodou em cada dia", por um ano, mesmo depois
-- de o detalhe (linha a linha) ser apagado no prazo de guarda. media_id e campaign_id valem 0 quando não há (chave sem nulos).
CREATE TABLE play_log_daily_media (
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    day DATE NOT NULL,
    media_id BIGINT NOT NULL DEFAULT 0,
    campaign_id BIGINT NOT NULL DEFAULT 0,
    media_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(10) NOT NULL,
    campaign_name VARCHAR(150),
    plays BIGINT NOT NULL DEFAULT 0,
    completed BIGINT NOT NULL DEFAULT 0,
    total_ms BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (device_id, day, media_id, campaign_id)
);
CREATE INDEX idx_play_log_daily_media_tenant_day ON play_log_daily_media (tenant_id, day);

-- O que já foi registrado entra no resumo (o dia segue o fuso dos relatórios, America/Sao_Paulo, o mesmo do resumo diário).
INSERT INTO play_log_daily_media (tenant_id, device_id, day, media_id, campaign_id, media_name, media_type, campaign_name, plays, completed, total_ms)
SELECT tenant_id, device_id, (started_at AT TIME ZONE 'America/Sao_Paulo')::date, COALESCE(media_id, 0), COALESCE(campaign_id, 0),
       (array_agg(media_name ORDER BY started_at DESC))[1], (array_agg(media_type ORDER BY started_at DESC))[1],
       (array_agg(campaign_name ORDER BY started_at DESC))[1], COUNT(*), COUNT(*) FILTER (WHERE completed), SUM(duration_ms)
FROM play_logs
GROUP BY tenant_id, device_id, (started_at AT TIME ZONE 'America/Sao_Paulo')::date, COALESCE(media_id, 0), COALESCE(campaign_id, 0);

-- 2. Os hashes (64 letras e números) viram 32 bytes: o mesmo conteúdo na metade do espaço. Medido com 200 mil registros: de 549
-- para 424 bytes por exibição, junto com o item 3.
ALTER TABLE play_logs
    ALTER COLUMN prev_hash TYPE BYTEA USING decode(prev_hash, 'hex'),
    ALTER COLUMN hash TYPE BYTEA USING decode(hash, 'hex');

-- 3. O índice por mídia só servia ao filtro "mídia" do relatório, que fica rápido sem ele (o detalhe guarda poucos dias).
DROP INDEX idx_play_logs_media_time;

-- 4. Novos clientes guardam o detalhe por 7 dias (antes, 14). Quem já tem um prazo salvo mantém o dele: nada é apagado por isto.
ALTER TABLE monitoring_settings ALTER COLUMN play_log_retention_days SET DEFAULT 7;
