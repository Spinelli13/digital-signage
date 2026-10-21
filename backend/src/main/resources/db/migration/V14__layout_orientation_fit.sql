-- Layouts responsivos:
--  * portrait: a zona pertence à VERSÃO DO LAYOUT PARA TV EM PÉ (as zonas com portrait = false são a versão horizontal, de sempre);
--  * fit: como a mídia ocupa a zona quando a proporção é diferente: CONTAIN (cabe inteira, com faixas) ou COVER (preenche, cortando).
ALTER TABLE layout_zones ADD COLUMN portrait BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE layout_zones ADD COLUMN fit VARCHAR(10) NOT NULL DEFAULT 'CONTAIN';
