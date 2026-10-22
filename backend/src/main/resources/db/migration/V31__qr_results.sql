-- Etapa 2.5: resultados do QR Code e dos cupons. Quantos escanearam a promoção por dia e de onde (qual TV), e de que origem veio cada
-- cupom pego. Só acrescenta uma tabela de contadores e uma coluna nula: nada existente muda e nenhum dado é apagado.
--
-- Para desfazer à mão (o Flyway Community não desfaz):
--   DROP TABLE promotion_scans;
--   ALTER TABLE promotion_coupons DROP COLUMN origin;
--   DELETE FROM flyway_schema_history WHERE version = '31';

-- Quantas vezes a página da promoção foi aberta (o QR Code escaneado), por dia e por origem. Só um número por dia: nenhum dado de pessoa.
-- Origem = de onde veio o QR Code: "tv-12" (a TV de id 12), ou um rótulo livre ("balcao", "panfleto"); vazia = o QR geral, sem origem.
CREATE TABLE promotion_scans (
    promotion_id BIGINT NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    day DATE NOT NULL,
    origin VARCHAR(24) NOT NULL DEFAULT '',
    scans INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (promotion_id, day, origin)
);
CREATE INDEX idx_promotion_scans_tenant_day ON promotion_scans (tenant_id, day);

-- De que QR Code veio cada cupom (a mesma origem dos escaneamentos); nulo = sem origem (todo cupom que já existe).
ALTER TABLE promotion_coupons ADD COLUMN origin VARCHAR(24);
