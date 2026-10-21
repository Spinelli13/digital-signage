-- Até quando vale a agenda offline que a TV guardou (informado pelo Player a cada heartbeat). Vazio = a TV não tem agenda
-- (nenhuma campanha vale para ela) ou usa um Player mais antigo que não informa.
ALTER TABLE devices ADD COLUMN offline_plan_until TIMESTAMPTZ;
