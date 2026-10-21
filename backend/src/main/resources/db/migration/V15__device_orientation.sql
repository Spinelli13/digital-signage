-- Orientação da tela de cada TV: como ela foi instalada. AUTO = o que a própria TV informar (como sempre foi);
-- as demais obrigam o Player a girar o conteúdo (TV na vertical presa na parede, saída de vídeo sempre horizontal).
ALTER TABLE devices ADD COLUMN orientation VARCHAR(24) NOT NULL DEFAULT 'AUTO';
