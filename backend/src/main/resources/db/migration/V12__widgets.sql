-- Widgets: conteúdo dinâmico (relógio, clima, notícias, cotações, aviso, contagem regressiva) como um tipo de mídia.
-- Não há arquivo para guardar: só o tipo do widget e as escolhas feitas no painel (cidade, fonte das notícias, textos...).
ALTER TABLE media ADD COLUMN widget_kind VARCHAR(20);
ALTER TABLE media ADD COLUMN widget_config JSONB;
