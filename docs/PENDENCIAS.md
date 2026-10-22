# Pendências do dono

Coisas que dependem de decisão, conta ou pagamento do dono e **não bloqueiam** o desenvolvimento. O desenvolvimento deixa tudo pronto para ligar; quando o dono concluir o passo, a configuração é feita em seguida. Registrado em 2026-10-22.

| # | Pendência | Quem resolve | Já está pronto do lado do sistema | Como fechar |
|---|---|---|---|---|
| 1 | **Infraestrutura paga**: Supabase Pro + Render Starter, servidor e banco na mesma região | Dono (conta e cartão) | Plano de migração, scripts de backup, teste de restauração e monitor (Etapa 1.1) | Lista de passos do item 1.1 (`docs/MIGRACAO_PRODUCAO.md`) |
| 2 | **Provedor de e-mail transacional** (recuperar senha, boas-vindas, fatura, TV offline) | Dono escolhe o provedor e cria a conta | A integração (Etapa 2.2) funciona sem provedor: grava no log em vez de enviar | Criar a conta, validar o domínio de envio e passar a chave ao servidor (variável de ambiente, nunca em chat ou arquivo) |
| 3 | **Razão social, CNPJ e encarregado de dados (nome e e-mail)** | Dono fornece os dados | Os campos existem em **Plataforma → Configurações** e alimentam a política de privacidade e os termos | Preencher os quatro campos; a política passa a mostrar "controlador" e canal do encarregado com os dados reais |
| 4 | **Notícias: quais portais manter ligados** | Dono decide | Lista com os termos de cada feed em `docs/FONTES_DE_DADOS.md` | Escolher a opção 1, 2 ou 3 do documento; a opção 2 (só Agência Senado e feed próprio por padrão) é a recomendada antes de vender |
| 5 | **Domínio próprio** (opcional, para o endereço do painel e do servidor) | Dono | O MET Norway pede um contato no `User-Agent`; hoje vale o endereço do painel | Se comprar domínio: avisar para trocar o endereço em `JdkWidgetHttp`, a política de privacidade e o `render.yaml` |

## Decisões já tomadas (registro)

- **Clima:** usar fonte gratuita liberada para uso comercial (MET Norway); o Open-Meteo foi retirado.
- **Equipamento:** **não se vende TV nem stick, só se aluga** (comodato). O "Kit Stick R$ 249" da análise comercial está fora. O controle de equipamento em comodato (Etapa 1.6) passa a ter mais importância que a venda.
- **Fora da Etapa 1 e 2:** anunciantes e cotas, cobrança automática, 2FA e auditoria, player web e contas de agência (Etapa 3).
