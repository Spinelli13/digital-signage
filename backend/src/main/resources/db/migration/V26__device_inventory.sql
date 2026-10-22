-- Etapa 1.6: inventário básico de equipamento. O Player informa modelo, versão do Android e memória no heartbeat; o painel
-- guarda, por TV, o número de série, a data de instalação e uma observação de comodato (o que é do cliente, o que é nosso).
-- Só acrescenta colunas vazias: nenhuma TV e nenhum dado existente muda.
--
-- Para desfazer à mão (o Flyway Community não desfaz):
--   ALTER TABLE devices DROP COLUMN hardware_model, DROP COLUMN android_version, DROP COLUMN ram_mb,
--                       DROP COLUMN serial_number, DROP COLUMN installed_on, DROP COLUMN comodato_note;
--   DELETE FROM flyway_schema_history WHERE version = '26';

ALTER TABLE devices
    -- informados pelo próprio Player (nulos até o Player novo contar)
    ADD COLUMN hardware_model VARCHAR(80),
    ADD COLUMN android_version VARCHAR(30),
    ADD COLUMN ram_mb INTEGER CHECK (ram_mb IS NULL OR ram_mb >= 0),
    -- preenchidos à mão no painel (só o Administrador vê e muda)
    ADD COLUMN serial_number VARCHAR(80),
    ADD COLUMN installed_on DATE,
    ADD COLUMN comodato_note VARCHAR(500);
