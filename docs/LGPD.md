# LGPD no Vyzzu: registro das operações de tratamento e como operar

Este documento é o **mapa dos dados pessoais** do Vyzzu (o "registro das operações de tratamento", LGPD art. 37), o procedimento para atender pedidos de titulares e a lista do que ainda depende de decisão ou de revisão jurídica. Foi escrito a partir do código e da configuração de hospedagem em 2026-10-21 (versão do aviso `2026-10-21`).

> **Não é parecer jurídico.** A Política de Privacidade e os Termos de Uso publicados no painel (`/privacidade` e `/termos`) são texto-base e **precisam de revisão de um advogado** antes de valer como documento jurídico. As bases legais abaixo são sugestões técnicas para essa revisão.

## 1. O que o sistema faz hoje (resumo)

| Tema | Como está |
|---|---|
| Política de Privacidade e Termos de Uso | Páginas abertas `/privacidade` e `/termos`, com link no login e no aviso das páginas públicas. Quem responde pelo tratamento (razão social, CNPJ, encarregado) vem de **Plataforma → Configurações → Privacidade (LGPD)**. |
| Aviso e concordância | As páginas públicas da fila e do cupom mostram o aviso (para que o nome serve, quem é a loja responsável, por quantos dias fica, contato e link da política). Ao digitar um nome aparece a caixa **"Concordo que meu nome seja usado assim"**; **sem concordância o servidor recusa o nome**. Fica gravado quando e com qual versão do aviso a pessoa concordou. Sem nome, nada é coletado. |
| Prazos de guarda por cliente | Tela **Privacidade e dados** (Administrador): senhas da fila (padrão 30 dias), nome nos cupons (90), histórico das TVs (90), exibições detalhadas (14). Uma limpeza de hora em hora apaga o que passou do prazo, **mesmo que ninguém emita senha ou cupom**. |
| Direitos do titular (art. 18) | A mesma tela: **buscar** por nome, e-mail ou código do cupom; **baixar a cópia** (JSON); **apagar** (nome, aparelho e concordância somem; a senha e o cupom ficam, sem identificar ninguém). Cada pedido atendido fica registrado (tipo, quantidades, observação, quem atendeu; **nunca o nome buscado**). Usuário do painel: entra na cópia; a exclusão é feita em **Equipe**. |
| Isolamento | Cada cliente só enxerga e apaga os próprios dados (testado). A tela de privacidade é só do Administrador, inclusive para consultar. |

## 2. Papéis

| Dado | Controlador | Operador |
|---|---|---|
| Equipe do painel (nome, e-mail, acesso) | Vyzzu (a plataforma) | Provedores de infraestrutura |
| Nome de quem pega senha de fila ou cupom numa loja | **A loja (o cliente)**, que decide a finalidade | Vyzzu |
| Cobrança e cadastro do cliente (empresa) | Vyzzu | Provedores de infraestrutura |
| Conteúdo enviado pelo cliente (imagens, vídeos, textos) | O cliente | Vyzzu |

Consequência: o Vyzzu precisa de um **contrato de tratamento de dados com cada cliente** (como operador) e de contratos com os provedores. Veja a seção 8.

## 3. Inventário de dados pessoais

| Dado | Titular | Onde fica | Finalidade | Base legal sugerida | Prazo | Quem acessa |
|---|---|---|---|---|---|---|
| Nome, e-mail, papel, situação, data de criação | Equipe do cliente | `users` | Acesso ao painel | Contrato (art. 7º, V); legítimo interesse na segurança (IX) | Enquanto a conta existir | Administrador do cliente; operação da plataforma |
| Senha | Equipe do cliente | `users.password_hash` (BCrypt, irreversível) | Autenticação | Contrato | Enquanto a conta existir | Ninguém lê a senha |
| Nome (opcional, até 60 caracteres) + concordância (`consent_at`, `consent_notice`) | Cliente da loja que pegou a senha | `tickets` | Chamar a senha | Consentimento (art. 7º, I) | **30 dias** por padrão (1 a 365, por cliente); a senha inteira é apagada | Administrador e Editor do cliente (Atendimento) |
| Nome digitado no balcão pelo atendente | Cliente da loja | `tickets.customer_name` (`source = PANEL`) | Chamar a senha | Responsabilidade da loja (sem concordância gravada) | Idem | Idem |
| Nome (opcional) + concordância | Consumidor que pegou o cupom | `promotion_coupons` | Achar o cupom no caixa | Consentimento (art. 7º, I) | **90 dias** por padrão (1 a 365); o nome some e o cupom fica | Editor e Administrador do cliente |
| Código aleatório do aparelho | Consumidor | `promotion_coupons.client_id`; cabeçalho `X-Client-Id` (só em memória nas senhas) | Devolver o mesmo cupom/senha; limitar abusos | Legítimo interesse (art. 7º, IX) | Enquanto o cupom existir; apagado num pedido de exclusão | Ninguém na tela; só o banco |
| E-mail de quem deu baixa no cupom | Equipe do cliente | `promotion_coupons.redeemed_by` | Auditoria da baixa | Legítimo interesse | Enquanto o cupom existir | Administrador e Editor do cliente |
| Endereço IP | Quem abre páginas públicas | **Só na memória do servidor** (limite de pedidos por hora) | Segurança contra abuso | Legítimo interesse | Janela de 1 hora; **não é gravado no banco** | Ninguém |
| Empresa, plano, faturas, e-mail de suporte | Cliente (empresa) | `tenants`, `invoices`, `platform_settings` | Cobrança e suporte | Contrato; obrigação legal | Prazo fiscal e contábil | Operação da plataforma |
| Histórico das TVs, alertas, exibições | (não identificam pessoas) | `device_events`, `alerts`, `play_logs`, `media_plays` | Operação e comprovação | — | 90 dias (7–730); detalhe de exibição 14 dias (7–365); resumo diário 1 ano | Cliente |
| Arquivos enviados (imagens, vídeos) | Quem aparece neles (a cargo do cliente) | Armazenamento de arquivos (Supabase) | Exibir nas TVs | A cargo do cliente | Até o cliente apagar | Cliente |

**Armazenamento no navegador:** `localStorage` só para o que o serviço precisa (sessão do painel, tema, idioma, código do aparelho `smartplay.client`, senha/cupom já pegos). Sem cookies de publicidade nem rastreadores; por isso não há faixa de cookies.

**Dados sensíveis e crianças:** não são pedidos. O aviso orienta a não digitar dado sensível no nome; menores de 12 anos devem ter acompanhamento (art. 14).

## 4. Operadores, regiões e transferência internacional

| Provedor | Para quê | Região |
|---|---|---|
| Supabase | Banco de dados (PostgreSQL) e arquivos | `us-east-1` (EUA) |
| Render | Servidor da aplicação (backend) | Virgínia (EUA) |
| Cloudflare Pages | Painel (site estático) e site de downloads | Global (rede de distribuição) |

A transferência para os EUA é necessária para executar o serviço contratado (LGPD, art. 33, IX, combinado com o art. 7º, V). As fontes públicas dos widgets (previsão do tempo, cotações, notícias) são consultadas pelo servidor sem enviar dado de pessoas.

## 5. Atender um pedido de titular (passo a passo)

Quando uma pessoa pedir acesso, correção ou exclusão dos dados dela:

1. **Confirme quem pede** (por exemplo, o titular mostra a senha ou o cupom que pegou, ou escreve do e-mail cadastrado). O sistema não faz essa verificação por você.
2. Entre no painel como **Administrador** → **Privacidade e dados** → **Pedido de um titular**.
3. **Busque** pelo nome, pelo e-mail ou pelo código do cupom. Confira os resultados (senhas da fila, cupons e equipe).
4. **Acesso/portabilidade:** preencha a observação (o protocolo) e clique em **Baixar cópia dos dados (JSON)**. Entregue ao titular por um canal seguro.
5. **Eliminação:** **Apagar dados pessoais encontrados** (pede confirmação; não tem volta). O nome, o aparelho e a concordância somem; a senha e o cupom permanecem, sem identificar ninguém. Se a pessoa for da equipe, exclua o usuário em **Equipe**.
6. **Correção:** como o nome é só o texto que a pessoa digitou, o caminho é apagar e a pessoa pegar uma nova senha ou cupom com o nome certo.
7. **Responda dentro de 15 dias** (art. 19, II). O pedido já ficou registrado na lista **Pedidos atendidos**.

Quem pede à **plataforma** (e não à loja) sobre dados da loja: a plataforma é operadora; encaminhe à loja (controladora) e registre o encaminhamento.

## 6. Retenção e eliminação (como funciona por dentro)

- **Limpeza horária** (`MonitoringJob` → `PrivacyService.purgeExpired`), por cliente: apaga senhas mais antigas que o prazo e limpa o nome (e a concordância) dos cupons mais antigos que o prazo. A limpeza ao emitir senha ou pegar cupom continua, mas a guarda **não depende mais de uso**.
- Os prazos de histórico e de exibição detalhada são os mesmos da tela de alertas (a tela Privacidade e dados mostra e grava os dois lados).
- Excluir um cliente (empresa) apaga as configurações e os pedidos de privacidade dele junto (`ON DELETE CASCADE`).

## 7. Segurança (o que já existe)

HTTPS; senhas com BCrypt; JWT; papéis (visualizador, editor, administrador, operação) conferidos no servidor; isolamento por cliente na camada de dados; segredos nos endereços dos QR Codes; limites de pedidos por hora e por aparelho; troca obrigatória da senha provisória; CORS restrito; a tela de privacidade só para o Administrador. Os testes de API cobrem o isolamento entre clientes (`PrivacyTest`).

## 8. Pendências (precisam de você ou do jurídico)

Nada disto está feito e **nenhum item foi tratado como concluído**:

1. **Revisão jurídica** da Política de Privacidade e dos Termos de Uso (textos-base em `frontend/src/app/legal/legal-content.ts`; mudou o texto, suba `PrivacyNotice.VERSION` no backend).
2. **Preencher em Plataforma → Configurações → Privacidade (LGPD):** razão social, CNPJ e o **encarregado de dados (DPO)** com e-mail. Sem isso a política mostra só o nome da plataforma e o e-mail de suporte. A indicação formal do encarregado (art. 41) é decisão da empresa.
3. **Contrato de tratamento de dados com os clientes** (o Vyzzu como operador) e **aceite dos Termos**: hoje os termos ficam publicados e linkados, mas **não há aceite explícito no cadastro** (clientes são criados pela operação; o aceite deve estar no contrato).
4. **Contratos de tratamento de dados com Supabase, Render e Cloudflare** (aceitar/guardar os DPAs deles) e conferir a retenção de backups e de logs de cada um. A política publicada diz apenas que os provedores podem manter cópias por tempo limitado.
5. **Relatório de impacto (RIPD)** e **plano de resposta a incidentes** formalizados (a regulamentação da ANPD sobre comunicação de incidentes fixa prazo curto, hoje 3 dias úteis; confirme a versão vigente com o jurídico). O texto da política promete comunicar em "prazo razoável".
6. **Registros de acesso (Marco Civil da Internet, art. 15):** o sistema não grava IP no banco. Se o jurídico entender que a plataforma é provedora de aplicação sujeita à guarda de registros de acesso por 6 meses, será preciso decidir onde guardar.
7. **Logs de aplicação do Render:** não verifiquei se algum log contém dado pessoal (não há log intencional de nome ou e-mail). Conferir.
8. **Treinar a equipe** que opera o serviço (quem atende pedidos, quem acessa o banco).
9. **Conteúdo dos clientes** (fotos de pessoas nas mídias): responsabilidade do cliente; vale um aviso no contrato.

## 9. Ao mudar o sistema

Criou um novo dado pessoal, uma nova coleta ou um novo prazo? Atualize, **na mesma mudança**: este documento (inventário), a Política de Privacidade (`legal-content.ts`), o aviso das páginas públicas se for o caso, `PrivacyNotice.VERSION` (para saber qual texto cada pessoa viu), a busca/cópia/exclusão em `PrivacyService` e o teste `PrivacyTest`. O teste `legal.page.spec.ts` confere que a política cita os dados e prazos principais.
