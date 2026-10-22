-- Etapa 1.5: diagnóstico da TV. O Player monta um relatório curto (internet, servidor, versão, espaço, última sincronização, o que
-- falta baixar, permissões) e o envia; o painel mostra o último e deixa pedir um novo sem ninguém estar na frente da TV.
--
-- Para desfazer à mão (o Flyway Community não desfaz):
--   DROP TABLE device_diagnostics;
--   ALTER TABLE devices DROP COLUMN diagnostic_requested_at;
--   DELETE FROM flyway_schema_history WHERE version = '25';

-- Quando o painel pediu o diagnóstico; a TV percebe no próximo heartbeat (o mesmo desenho do pedido de atualização do Player).
ALTER TABLE devices ADD COLUMN diagnostic_requested_at TIMESTAMPTZ;

-- Só o último relatório de cada TV (o texto fica fora da tabela devices: o painel lista as TVs a cada 10 segundos).
CREATE TABLE device_diagnostics (
    device_id BIGINT PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    report TEXT NOT NULL,
    -- quantos itens do relatório estão com problema (0 = tudo certo)
    issues SMALLINT NOT NULL DEFAULT 0,
    received_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_device_diagnostics_tenant ON device_diagnostics (tenant_id);
