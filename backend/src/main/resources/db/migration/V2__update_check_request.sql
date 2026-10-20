-- Quando o administrador pede, no painel, que a TV verifique agora se há versão nova do Player.
-- A TV recebe este instante no heartbeat e, ao ver um valor que ainda não tratou, consulta o site de downloads.
ALTER TABLE devices ADD COLUMN update_requested_at TIMESTAMPTZ;
