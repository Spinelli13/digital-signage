-- Senha provisória: quem recebe uma senha definida por outra pessoa (ex.: um gestor novo) é obrigado a trocá-la no primeiro acesso.
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
