-- Etapa 2.3: "Alertar agora". Um aviso em tela cheia, por cima de tudo, para uma TV, um grupo ou todas as TVs do cliente, com prazo
-- e botão de encerrar. A TV percebe no próximo heartbeat (até 30 s) e some sozinha quando o prazo acaba.
-- Só acrescenta uma tabela nova: nada existente muda e nenhum dado é apagado.
--
-- Para desfazer à mão (o Flyway Community não desfaz):
--   DROP TABLE screen_alerts;
--   DELETE FROM flyway_schema_history WHERE version = '29';

CREATE TABLE screen_alerts (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    -- título curto opcional ("ATENÇÃO") e o texto grande que a TV mostra
    title VARCHAR(60),
    message VARCHAR(300) NOT NULL,
    -- URGENT (vermelho), WARNING (âmbar) ou INFO (violeta da marca)
    tone VARCHAR(10) NOT NULL,
    -- ALL (todas as TVs do cliente), GROUP ou DEVICE; target_id é o grupo ou a TV (nulo em ALL). Sem chave estrangeira de propósito:
    -- o histórico do aviso continua mesmo que o grupo ou a TV seja excluído depois.
    target_type VARCHAR(10) NOT NULL,
    target_id BIGINT,
    created_by VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL,
    -- todo aviso tem prazo (no máximo 24 h): um aviso esquecido não fica na TV para sempre
    expires_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    ended_by VARCHAR(120)
);
CREATE INDEX idx_screen_alerts_tenant ON screen_alerts (tenant_id, created_at DESC);
