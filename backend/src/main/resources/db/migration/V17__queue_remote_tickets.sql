-- Senha pelo celular: a fila pode ser aberta a quem escaneia um QR Code (sem entrar no painel).
--  * public_token da fila: o segredo que vai no QR Code (quem tem o endereço pode pegar senha);
--  * public_token da senha: o segredo que a pessoa guarda para acompanhar a própria senha pelo celular.
ALTER TABLE service_queues ADD COLUMN remote_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE service_queues ADD COLUMN public_token VARCHAR(32);
UPDATE service_queues SET public_token = replace(gen_random_uuid()::text, '-', '') WHERE public_token IS NULL;
ALTER TABLE service_queues ALTER COLUMN public_token SET NOT NULL;
CREATE UNIQUE INDEX uq_service_queues_token ON service_queues (public_token);

ALTER TABLE tickets ADD COLUMN public_token VARCHAR(32);
CREATE UNIQUE INDEX uq_tickets_token ON tickets (public_token) WHERE public_token IS NOT NULL;
