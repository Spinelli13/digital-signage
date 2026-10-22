-- Etapa 2.2: e-mail do sistema. Link de redefinição de senha (também usado nas boas-vindas) e o e-mail de aviso de TV offline.
-- Só acrescenta uma tabela e uma coluna nula: nada existente muda.
--
-- Para desfazer à mão (o Flyway Community não desfaz):
--   DROP TABLE password_resets;
--   ALTER TABLE monitoring_settings DROP COLUMN alert_email;
--   DELETE FROM flyway_schema_history WHERE version = '28';

-- O link leva um segredo aleatório; só o SHA-256 dele fica guardado (quem lê o banco não consegue usar o link).
CREATE TABLE password_resets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    -- RESET: "esqueci a senha" (vale 1 hora); WELCOME: boas-vindas de uma conta nova (vale 3 dias)
    purpose VARCHAR(10) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_resets_user ON password_resets (user_id, created_at DESC);

-- Para onde vão os avisos de TV offline, além do webhook (um ou mais endereços separados por vírgula). Vazio = só o webhook.
ALTER TABLE monitoring_settings ADD COLUMN alert_email VARCHAR(300);
