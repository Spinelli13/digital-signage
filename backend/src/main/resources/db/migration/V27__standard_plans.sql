-- Etapa 2.1: os planos da oferta comercial (Start, Pro, Business, Rede e os três de comodato) e o limite de tamanho de arquivo.
-- Só ACRESCENTA planos: nenhum cliente existente muda de plano, e os planos antigos (Grátis, Básico, Profissional) ficam como
-- estão e ativos (a operação desativa quando quiser, em Plataforma > Planos). Preços em centavos de real; a operação ajusta tudo
-- no painel.
--
-- Para desfazer à mão (o Flyway Community não desfaz); só depois de conferir que nenhum cliente usa os planos novos:
--   DELETE FROM plans WHERE code IN ('start','pro','business','rede','comodato-basico','comodato-completo','comodato-premium');
--   UPDATE plans SET max_upload_mb = NULL WHERE code IN ('interno','gratis','basico','profissional');
--   DELETE FROM flyway_schema_history WHERE version = '27';

INSERT INTO plans (code, name, description, price_cents, max_devices, max_storage_mb, max_users, sort_order, features, max_upload_mb) VALUES
    ('start', 'Start', 'Para começar: até 2 TVs, com conteúdo e widgets.', 3900, 2, 3072, 2, 4, 'WIDGETS', 50),
    ('pro', 'Pro', 'Pequenos negócios: até 5 TVs, com fila de senhas, cupons com QR Code e tabelas de preços.', 8900, 5, 10240, 5, 5,
     'WIDGETS,PRICES,QUEUE,COUPONS', 50),
    ('business', 'Business', 'Várias lojas: até 15 TVs, com todos os recursos.', 17900, 15, 30720, 15, 6,
     'CAMPAIGNS,LAYOUTS,WIDGETS,PRICES,QUEUE,PROOF_OF_PLAY,COUPONS', 50),
    ('rede', 'Rede', 'Redes e franquias: até 40 TVs, com todos os recursos.', 34900, 40, 81920, 40, 7,
     'CAMPAIGNS,LAYOUTS,WIDGETS,PRICES,QUEUE,PROOF_OF_PLAY,COUPONS', 50),
    ('comodato-basico', 'Comodato Básico', 'Uma TV por contrato, a TV é do cliente: com os recursos do Pro.', 7900, 1, 3072, 2, 8,
     'WIDGETS,PRICES,QUEUE,COUPONS', 50),
    ('comodato-completo', 'Comodato Completo', 'Uma TV por contrato, com TV e stick em comodato: com os recursos do Pro.', 12900, 1, 3072, 2, 9,
     'WIDGETS,PRICES,QUEUE,COUPONS', 50),
    ('comodato-premium', 'Comodato Premium', 'Uma TV por contrato, com TV nova de 32 polegadas em comodato: com os recursos do Pro.', 18900, 1, 3072, 2, 10,
     'WIDGETS,PRICES,QUEUE,COUPONS', 50)
ON CONFLICT (code) DO NOTHING;

-- O armazenamento de mídias (o bucket) recusa arquivo acima de 50 MB. Com o limite do plano vazio o painel prometia "sem limite" e
-- o envio falhava no servidor de arquivos; agora o painel avisa antes, com a mesma conta. Nenhum arquivo que já coubesse é recusado.
UPDATE plans SET max_upload_mb = 50 WHERE max_upload_mb IS NULL;
