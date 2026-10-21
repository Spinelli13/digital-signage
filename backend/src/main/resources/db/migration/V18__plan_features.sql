-- Permissões por plano: quais recursos cada plano inclui, e o tamanho máximo de cada arquivo enviado.
-- Os planos que já existem continuam com tudo (nada muda para quem já usa); a operação ajusta no painel.
ALTER TABLE plans ADD COLUMN features VARCHAR(500) NOT NULL DEFAULT 'CAMPAIGNS,LAYOUTS,WIDGETS,PRICES,QUEUE,PROOF_OF_PLAY';
-- Limite nulo = sem limite (como os demais).
ALTER TABLE plans ADD COLUMN max_upload_mb INTEGER CHECK (max_upload_mb IS NULL OR max_upload_mb >= 1);
