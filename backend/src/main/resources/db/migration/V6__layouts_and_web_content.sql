-- Fase 5 (recursos avançados): conteúdo web e layouts com zonas.

-- Conteúdo web: uma "mídia" que é um endereço (https) exibido pelo Player, não um arquivo guardado.
ALTER TABLE media ALTER COLUMN storage_key DROP NOT NULL;
ALTER TABLE media ADD COLUMN web_url TEXT;

-- Layout: como a tela é dividida. Cada zona é um retângulo em % da tela (0-100) com a sua própria lista de mídias.
-- config_version sobe a cada mudança nas zonas; o Player usa isso (junto com as playlists) para saber que precisa baixar de novo.
CREATE TABLE layouts (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    config_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_layouts_name ON layouts (lower(name));

CREATE TABLE layout_zones (
    id BIGSERIAL PRIMARY KEY,
    layout_id BIGINT NOT NULL REFERENCES layouts(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    name VARCHAR(50) NOT NULL,
    x INTEGER NOT NULL CHECK (x BETWEEN 0 AND 95),
    y INTEGER NOT NULL CHECK (y BETWEEN 0 AND 95),
    width INTEGER NOT NULL CHECK (width BETWEEN 5 AND 100),
    height INTEGER NOT NULL CHECK (height BETWEEN 5 AND 100),
    CHECK (x + width <= 100 AND y + height <= 100),
    -- a zona principal exibe a playlist da TV (e das campanhas); as demais têm a playlist fixa do layout
    is_main BOOLEAN NOT NULL DEFAULT FALSE,
    playlist_id BIGINT REFERENCES playlists(id) ON DELETE RESTRICT
);
CREATE INDEX idx_layout_zones_layout ON layout_zones (layout_id, position);
-- (a regra "uma única zona principal por layout" é validada na aplicação: um índice único parcial quebraria ao trocar as zonas,
-- porque o Hibernate insere as novas antes de apagar as antigas)

-- Layout da TV (vazio = tela cheia, como sempre foi). Apagar um layout em uso é recusado.
ALTER TABLE devices ADD COLUMN layout_id BIGINT REFERENCES layouts(id) ON DELETE RESTRICT;
