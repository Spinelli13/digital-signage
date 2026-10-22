-- Etapa 2.4: validade por item da playlist. Um item pode valer só entre duas datas e/ou só em certos dias da semana (por exemplo,
-- "promoção de terça" ou "até 31/12"), sem precisar criar uma campanha. Fora da validade a TV simplesmente não exibe o item.
-- Só acrescenta colunas nulas: nulo = vale sempre, como todo item existente continua valendo. Nenhum dado muda.
--
-- Para desfazer à mão (o Flyway Community não desfaz):
--   ALTER TABLE playlist_items DROP CONSTRAINT chk_playlist_items_period;
--   ALTER TABLE playlist_items DROP COLUMN valid_from, DROP COLUMN valid_until, DROP COLUMN days_of_week;
--   DELETE FROM flyway_schema_history WHERE version = '30';

-- Primeiro e último dia em que o item vale (inclusive), no fuso de São Paulo (o mesmo das campanhas). Nulo = sem limite daquele lado.
ALTER TABLE playlist_items ADD COLUMN valid_from DATE;
ALTER TABLE playlist_items ADD COLUMN valid_until DATE;
-- Dias da semana em que vale, de 1 (segunda) a 7 (domingo), separados por vírgula, como nas campanhas ("2" = só terça). Nulo = todos.
ALTER TABLE playlist_items ADD COLUMN days_of_week VARCHAR(13);

ALTER TABLE playlist_items ADD CONSTRAINT chk_playlist_items_period CHECK (valid_from IS NULL OR valid_until IS NULL OR valid_from <= valid_until);
