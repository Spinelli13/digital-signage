-- O produto passou a se chamar Vyzzu. Só troca o nome padrão: quem já personalizou o nome da empresa em Plataforma > Configurações não é alterado.
UPDATE platform_settings SET company_name = 'Vyzzu' WHERE company_name IN ('Smartplay', 'Digital Signage');
ALTER TABLE platform_settings ALTER COLUMN company_name SET DEFAULT 'Vyzzu';
