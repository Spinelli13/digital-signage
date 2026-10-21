-- O produto passou a se chamar Smartplay. Só troca o nome padrão: quem já personalizou o nome da empresa em Plataforma > Configurações não é alterado.
UPDATE platform_settings SET company_name = 'Smartplay' WHERE company_name = 'Digital Signage';
ALTER TABLE platform_settings ALTER COLUMN company_name SET DEFAULT 'Smartplay';
