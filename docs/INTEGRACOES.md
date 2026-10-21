# Integrações do Smartplay: o que vale construir, em que ordem e por quê

Documento de análise (item 8 do pedido da madrugada de 21/10/2026). Não promete nada de terceiros: **preços, limites e termos de serviço de outras empresas mudam e precisam ser conferidos na hora de construir** (marcados como "a confirmar"). O que está dito sobre o Smartplay foi conferido no código.

## 1. Onde estamos hoje (o que já é integração)

| Peça | O que faz | Observação |
|---|---|---|
| **API REST** (`/api/v1/...`) | Tudo que o painel faz passa por ela: TVs, mídias, playlists, campanhas, layouts, preços, relatórios. | Autenticação por **login de usuário (JWT)**, com papéis (Visualizador, Editor, Administrador). Não há ainda **chave de API** para máquinas. |
| **Alertas por webhook** (`WebhookNotifier`) | Avisa Slack, Discord e compatíveis quando uma TV cai ou o Player erra. | Só `https` e só endereços públicos (`WebhookUrlPolicy`), sem seguir redirecionamentos (proteção contra SSRF). A carga leva `text` e `content` mais os dados do evento. |
| **Widgets de dados** | Clima (Open-Meteo), notícias (RSS), câmbio (Frankfurter), recado, relógio, contagem regressiva. | O servidor busca e guarda em cache; o Player só desenha. Já tem proteção contra SSRF e XXE e um conector HTTP trocável nos testes (`WidgetHttp`). **Open-Meteo gratuito é para uso não comercial**: contratar o plano comercial antes de vender. |
| **Preços por planilha** | Importar e exportar CSV (`;` ou `,`, Excel brasileiro, Windows-1252). | É a ponte para ERP/PDV hoje: exporta-se do sistema da loja e importa-se no painel, à mão. |
| **Comprovação de exibição** | Registro por TV, em milissegundos, com corrente de hashes (SHA-256) e relatório auditável (CSV). | Quem audita confere a corrente; **ainda não é assinada por TV** (ver 3.5). |
| **Páginas da web e QR Code** | Uma TV pode abrir qualquer página `https` (Power BI, painel de vendas, cardápio online). | Já resolve muita integração "visual" sem código: o outro sistema só precisa publicar uma página. |

## 2. Como decidimos o que construir

Cada candidata foi pontuada em quatro coisas: **valor** para o dono de loja ou escritório (o cliente que paga), **esforço** de construir e manter, **risco** (segurança, termos de uso, dependência de terceiros) e **custo recorrente** para nós. Regras que seguimos:

1. **Sem custo fixo novo** enquanto der: prefira URLs públicas e arquivos (planilha publicada, calendário ICS, RSS) a serviços pagos.
2. **Sem pedir senha de outro sistema** ao cliente: quando houver OAuth, ele autoriza na tela do próprio sistema; quando houver chave, fica guardada só no servidor.
3. **O Player continua burro e offline-first**: integração é sempre do servidor. A TV nunca fala com o sistema de terceiros (exceto páginas web que a própria pessoa escolheu).
4. **Tudo que entra por URL passa pela mesma barreira contra SSRF** dos widgets e dos webhooks.

## 3. Recomendação, em ordem

### 3.1 Chaves de API por cliente (base de todo o resto), esforço M, risco baixo

Hoje só dá para chamar a API com o login de uma pessoa (JWT que expira). Um sistema de loja (PDV, ERP, planilha com script) precisa de uma **chave** própria:

- Criada pelo Administrador no painel (**Equipe e acesso > Chaves de API**), mostrada **uma vez**, guardada só como hash, com **nome**, **papel** (Visualizador, Editor) e **data de uso**; revogável.
- Passa pelo mesmo controle de papéis e de cliente (multi-inquilino) que um usuário comum: a chave **é** um usuário de serviço do cliente.
- Limite de chamadas por chave (já existe limite por plano para TVs e armazenamento; falta o de requisições).
- Entrega: `Authorization: Bearer sk_...`, documentação em `docs/API.md` com exemplos `curl` (trocar um preço, trocar a playlist de uma TV, ler a comprovação do dia).

### 3.2 Preços vindos de planilha online e de ERP/PDV, esforço M, valor alto

O pedido de "preços dinâmicos" já foi entregue por planilha CSV. Os próximos degraus:

1. **Planilha publicada na web** (Google Sheets "Publicar na web" como CSV; qualquer URL `https` que devolva CSV): o cliente edita a planilha que já usa e a tabela de preços **se atualiza sozinha** (verificação a cada N minutos pelo servidor, com a barreira contra SSRF, tamanho máximo e mesmas regras de importação de hoje). **Sem OAuth, sem custo.** É o melhor custo/benefício da lista.
2. **Endpoint de atualização** com chave de API (`PUT /api/v1/price-lists/{id}/items`) para PDV e ERP empurrarem preços ("o sistema da loja manda, a TV mostra").
3. **Conectores prontos** (Bling, Tiny/Olist, Omie, Conta Azul, Consumer, Linx...) só depois de haver demanda real de clientes pagantes: cada um é um projeto próprio, com termos e credenciais de parceiro (**a confirmar** em cada caso). A planilha publicada e o endpoint cobrem a maior parte sem isso.

Ainda em preços: estoque em tempo real ("esgotado" automático a partir do PDV) e **preços por loja/grupo de TVs** (mesma tabela, valores diferentes), citados como "não feito" na parte 16 do `DECISIONS.md`.

### 3.3 Agenda e calendário na TV (ICS), esforço P-M, valor médio-alto para escritórios, clínicas e salas

Um widget **Agenda** que lê um calendário **ICS** público ou privado por URL (Google Agenda, Outlook, Apple e outros exportam um endereço secreto ICS): "Sala de reuniões: ocupada até 15:30", "Hoje: consultas", "Próximos eventos". Mesmo desenho do widget de notícias (o servidor busca e guarda em cache; a TV desenha e guarda o último dado para continuar offline). Sem OAuth. Fuso e eventos recorrentes são a parte trabalhosa (biblioteca de ICS e testes de recorrência).

### 3.4 Webhooks de saída mais ricos e assinados, esforço P, valor médio

O aviso de alerta já existe. Evoluir para:

- **Formatos prontos** para Slack, Microsoft Teams, Discord, Telegram e e-mail (hoje a carga serve a Slack/Discord).
- **Mais eventos** (TV voltou, conteúdo trocado, comprovação do dia fechada, vídeo sem som...).
- **Assinatura** de cada envio (cabeçalho `X-Smartplay-Signature`, HMAC-SHA256 com um segredo por cliente), para quem recebe ter certeza de que veio de nós, e **reenvio** com espera crescente. Os testes já cobrem a política de URL; a assinatura é um complemento.

### 3.5 Comprovação com assinatura por TV, esforço M, valor alto para quem vende espaço de anúncio

Hoje a corrente de hashes mostra se **algum registro foi apagado ou alterado depois**; não prova **qual aparelho** gerou. Evolução: **cada TV gera um par de chaves** (chave privada no aparelho, pública cadastrada no servidor na ativação) e **assina o fecho de cada lote**; o relatório traz a assinatura e um verificador independente (script ou página) confere sem confiar no servidor. É o passo que torna o relatório aceitável para um anunciante desconfiado. Os cuidados: guardar a chave no armazenamento protegido do Android (Keystore), e o que fazer quando a TV é reinstalada (nova chave, histórico da antiga mantido).

### 3.6 Disparo de conteúdo por evento, esforço M, valor médio (varejo, atendimento)

`POST /api/v1/triggers` (com chave de API) para um sistema avisar "venda de R$ 10 mil", "senha 045 chamada", "estoque acabou": o servidor aplica uma **campanha temporária** a TVs ou grupos por N minutos (a regra de campanha e a agenda offline já existem). Casa com o item 11 (fila de atendimento).

### 3.7 Entrar com Google ou Microsoft no painel (SSO), esforço M, valor médio

Para clientes que já usam Google Workspace ou Microsoft 365. OAuth/OpenID Connect, sem senha nova. Vale quando houver cliente pedindo ou plano "empresarial"; antes disso, aumenta superfície de ataque e manutenção sem ganho claro para a loja pequena.

### 3.8 Cobrança automática (Pix e cartão), esforço M-G, valor alto para o negócio (não para o cliente final)

Hoje o pagamento é **registrado à mão** (fatura e baixa manual). Para escalar a venda, ligar um gateway com **Pix** e boleto/cartão (opções comuns no Brasil: Asaas, Mercado Pago, Stripe; **a confirmar** taxas e termos) com webhook de confirmação e baixa automática. É decisão comercial do dono: qual gateway, quem emite nota fiscal. Fica fora do escopo técnico até essa decisão.

## 4. O que NÃO construir agora (e por quê)

| Ideia | Por que esperar |
|---|---|
| **Instagram/Facebook, TikTok, YouTube ao vivo** como conteúdo | As APIs oficiais exigem aprovação de app e mudam com frequência; o que funciona hoje é **página web** (YouTube/Vimeo incorporado, "pagina sem cabeçalho") e **QR Code**. Termos de uso e direitos do conteúdo são do cliente. |
| **iFood, Rappi, Uber Eats** (cardápio) | APIs só para parceiros homologados (**a confirmar**). Preço por planilha/endpoint resolve o mesmo problema. |
| **Plugins por tipo de sistema** (WordPress, Shopify...) | Cada um vira um produto para manter. A API com chave cobre quem tem desenvolvedor. |
| **Controle por voz, câmeras de contagem de público** | Dados pessoais (LGPD), hardware e modelo próprio. Só com demanda e parceiro. |
| **Smart TV nativa** (Samsung Tizen, LG webOS) | É nova plataforma de Player, não integração; entra se o cliente pedir em volume. |
| **WhatsApp** para avisar "sua senha foi chamada" | Exige a API oficial do WhatsApp Business (cobrança por conversa, modelos aprovados; **a confirmar**). Pode entrar junto do item 11 como opção paga. |

## 5. Cuidados que valem para toda integração

- **LGPD:** a fila de atendimento (item 11), ao guardar nome ou telefone, passa a tratar dado pessoal: finalidade clara, prazo de retenção curto e exclusão automática, como já fazemos na comprovação (14 dias por padrão).
- **Segredos:** chaves de API e de terceiros **só no servidor**, nunca na TV nem no navegador; rotação e revogação visíveis no painel.
- **Falha de terceiro não derruba a TV:** toda busca tem tempo limite, guarda o **último dado bom** e mostra um estado vazio claro (como os widgets fazem).
- **Limites por plano:** cada integração que gera tráfego ou armazenamento entra nos limites do plano (já existem para TVs e disco).
- **Termos de serviço:** conferir licença de cada fonte de dados antes de vender (exemplo já em aberto: Open-Meteo).

## 6. Ordem sugerida e decisões que são do dono

1. **Chaves de API** (3.1), pré-requisito de 3.2, 3.6 e de qualquer cliente com sistema próprio.
2. **Planilha publicada na web para preços** (3.2.1): ganho imediato, sem custo.
3. **Agenda ICS** (3.3) e **webhooks assinados e formatos** (3.4).
4. **Comprovação assinada por TV** (3.5), quando o primeiro cliente de mídia paga pedir auditoria.
5. **Gateway de pagamento** (3.8), quando o dono decidir como cobrar e emitir nota.

Perguntas que só o dono responde: qual perfil de cliente vai pagar primeiro (varejo, escritório, clínica, bar)? Há ERP/PDV que **vários** clientes usam (para valer um conector)? Quem emite nota fiscal, e qual gateway de pagamento? Haverá plano "empresarial" com SSO e suporte?
