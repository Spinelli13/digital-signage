# Vyzzu: relatório técnico-comercial para análise de precificação e viabilidade

Data do levantamento: 22/10/2026. Versão do Player em campo: 0.14.69; **a 0.14.87 foi publicada em 22/10/2026 depois do levantamento** e ainda não foi instalada em nenhuma TV. Levantamento **somente leitura**: consultas `SELECT` no Supabase de produção, leitura da configuração do serviço no Render e leitura do código-fonte. Nada foi alterado em produção, nem no código, nem nos dados. O relatório não traz senhas, chaves, tokens nem dados pessoais.

**Legenda:** ✅ pronto e funcionando · 🟡 parcial ou com limitações · ❌ não existe · "não verificado" = não consegui confirmar com o que tenho acesso.
**Fontes citadas entre colchetes:** `[SQL]` consulta no banco de produção (Supabase) · `[Render]` configuração do serviço no Render · `[Código]` arquivo do repositório · `[Manual]` manuais/documentos do projeto · `[Estimativa]` cálculo meu a partir de dados medidos, com as premissas escritas.

> **ATUALIZAÇÃO DE 22/10/2026, fim da noite (Etapas 1 e 2 concluídas):** o texto abaixo é o levantamento original e continua valendo como retrato do ponto de partida. Esta seção mostra o que mudou. Onde uma tabela do levantamento ficou desatualizada, ela leva a marca **[atualizado 22/10]** com o estado de agora.

---

## ATUALIZAÇÃO: ETAPAS 1 E 2 (situação em 22/10/2026, fim da noite)

As 13 entregas das Etapas 1 e 2 foram feitas, cada uma num pedido de alteração próprio (PR #1 a #13), com testes e CI verde, e estão **no ar em produção** (servidor e painel). Só o que depende de uma **versão nova do app da TV (Player)** está pronto e testado (inclusive no emulador de Android TV), e a versão **0.14.87 foi publicada em 22/10/2026**: a atualização do app pede um OK em cada TV, então o recurso só aparece em cada TV depois desse OK. Nada foi testado em TV física. A etapa 3 (anunciantes e cotas, cobrança automática, 2FA e auditoria, player web, contas de agência) **não foi iniciada**, como combinado.

**Legenda desta seção:** ✅ pronto e funcionando agora · 🟡 pronto no código mas falta uma ação sua ou o Player novo · ❌ não existe.

### Situação por item

| Item | Situação | O que está no ar | O que falta, e com quem |
|---|---|---|---|
| **1.1** Infraestrutura (plano, backup, monitor) | 🟡 | Plano de migração para Supabase Pro + Render Starter na mesma região (`docs/MIGRACAO_PRODUCAO.md`); scripts de backup e de restauração, testados no CI (copia, restaura e confere); rotina diária de backup e alarme de disponibilidade prontos | **Você:** contratar os planos (tem custo), criar as chaves do backup e um monitor externo. **Enquanto isso o backup ainda NÃO roda** e o servidor grátis continua dormindo |
| **1.2** Licenças e conformidade | 🟡 | Clima trocado para a fonte MET Norway (uso comercial permitido, com a atribuição); política de privacidade, termos, aviso com concordância, prazos por cliente e pedidos de titulares (LGPD) no ar; termos de cada fonte em `docs/FONTES_DE_DADOS.md` | **Você:** razão social/CNPJ e encarregado de dados; escolher quais portais de notícias ficam ligados (3 opções no documento); revisão da política por um advogado |
| **1.3** Registro de exibição menor | ✅ | Registro compactado e resumo diário por mídia, guardado um ano (V24); contas de capacidade em `docs/CAPACIDADE_BANCO.md` | |
| **1.4** Pareamento por QR Code | 🟡 | Servidor e painel (`/parear/<código>`) no ar | O **QR na tela da TV** só aparece depois que a TV instalar o Player 0.14.87 (publicado; falta o OK na TV) |
| **1.5** Diagnóstico da TV | 🟡 | Painel (pedir e ler o diagnóstico) e servidor no ar (V25) | A **tela de diagnóstico e o envio** pela TV exigem o Player 0.14.87 (publicado; falta o OK na TV) |
| **1.6** Inventário básico | 🟡 | Número de série, data de instalação e nota de comodato no painel, e relatório de inventário (V26) | **Modelo, versão do Android e memória** automáticos exigem o Player 0.14.87 (publicado; falta o OK na TV) |
| **1.7** Primeiros passos | ✅ | Lista de passos no dashboard, com visto, atalho e que some ao concluir | |
| **2.1** Planos | ✅ | 7 planos novos (Start, Pro, Business, Rede e os 3 de comodato) com preços, limites e recursos (V27, `docs/PLANOS_E_PRECOS.md`) | **TV extra a R$ 9: ❌ o modelo atual não comporta** (a fatura é o preço fixo do plano); a proposta de como fazer está no documento e aguarda seu OK. Desativar os planos antigos: você decide quando |
| **2.2** E-mail do sistema | 🟡 | Recuperar senha, boas-vindas, aviso de fatura e aviso de TV offline por e-mail, tudo pronto e no ar (V28); envio pela API do Brevo ou do Resend (o plano grátis do Render **bloqueia SMTP**) | **Você:** escolher o provedor e criar a conta (5 passos em `docs/EMAIL.md`; o Brevo não exige domínio próprio). **Nenhum e-mail sai antes disso** |
| **2.3** Alertar agora | 🟡 | Painel e servidor no ar (V29): aviso em tela cheia, por cima de tudo, para uma TV, um grupo ou todas, com prazo e botão de encerrar. Testado no emulador de Android TV | Chega a cada TV só depois que ela instalar o Player **0.14.87** (publicado; falta o OK na TV). Instalado, a TV percebe o aviso em até 30 s |
| **2.4** Validade por item da playlist | ✅ | Datas de início e fim e dias da semana por item, sem campanha (V30); decidido no servidor, então **funciona em todas as versões do Player**, inclusive as instaladas hoje | Limite: uma TV sem internet só vê a virada do dia quando reconectar |
| **2.5** Resultados do QR e dos cupons | ✅ | Tela **Resultados**: "o conteúdo rodou X vezes, Y cupons foram usados", por dia, por TV (QR Code por TV), por promoção, taxa de uso e CSV (V31) | "Escaneado" = abertura da página da promoção (não prova que pegou o cupom) |
| **2.6** Modelos por segmento | ✅ barbearia · 🟡 demais | **Modelos prontos**: barbearia, restaurante, clínica, academia e loja criam avisos, relógio, clima, tabela de preços, playlists e layout com os textos da pessoa | Os textos e preços de exemplo são genéricos e precisam de conferência; **só a barbearia** foi desenhada com mais cuidado, e ainda **sem validação com um cliente real** |

### O que depende de você, em ordem de impacto
1. **Apertar OK em cada TV** para instalar o Player 0.14.87 (QR de pareamento na TV, diagnóstico, inventário automático e "Alertar agora"); já está publicado.
2. **Escolher o provedor de e-mail** (docs/EMAIL.md): sem isso, recuperar senha, boas-vindas, fatura e aviso por e-mail **não saem**.
3. **Infraestrutura paga e backup** (docs/MIGRACAO_PRODUCAO.md): hoje o banco **não tem backup nosso** e o servidor dorme.
4. **CNPJ, razão social e encarregado**, e **a decisão dos portais de notícias** (conformidade).
5. **TV extra a R$ 9**: responder à proposta; e **desativar os planos antigos** quando só a oferta nova valer.

### O que foi verificado e como
- **Testes automáticos:** 655 do servidor, 821 do painel e 256 do Player, todos verdes; o CI (4 jobs) passou em cada pedido antes de entrar.
- **Produção:** cada migração (V24 a V31) foi conferida nos registros do Render ("now at version vN") e o servidor respondeu saudável depois de cada publicação; o painel foi publicado a cada entrega visível.
- **Na tela, de verdade:** o aviso em tela cheia e a validade por item foram vistos num emulador de Android TV conectado a um servidor local; a tela de Resultados, de Alertar agora e de Modelos prontos foram vistas no navegador.
- **Não verificado:** nada disso foi testado numa TV física (TCL ou Fire Stick); o envio real de e-mail (não há provedor); o backup em produção (não está ativo).

---

## RESUMO EXECUTIVO

1. O Vyzzu é uma plataforma de mídia indoor multi-cliente: painel web, servidor e app Android que roda nas TVs e sticks. Tem playlists, campanhas agendadas, layouts em zonas, widgets (clima, notícias, cotação, preços, fila de senhas), comprovação de exibição e planos com limite de TVs, GB e usuários.
2. **O tema "hospedagem Hostinger" não se confirma:** não há nenhuma referência a Hostinger no código nem na configuração. A produção está em **Render (servidor, plano grátis), Supabase (banco e arquivos, plano grátis) e Cloudflare Pages (painel)**. Hoje o custo recorrente verificado é **R$ 0**; é infraestrutura de teste, não de operação comercial.
3. **Uso real hoje:** 2 telas ativas (ambas no Player 0.14.69), 15 mídias (8 arquivos, 7,7 MB no total), banco de 14 MB, 1.548 registros de exibição em ~3 h de operação.
4. **O primeiro gargalo de escala é o banco, não o armazenamento:** o registro de exibição cresce ~0,6 KB por exibição; uma tela com itens curtos gera ~16 mil por dia (~10 MB/dia). Em 14 dias, uma tela 24 h ocupa ~135 MB. O limite do plano grátis do banco é 500 MB (valor público do provedor, não conferido hoje).
5. **Modelo 3 (SaaS por GB)** é o mais próximo de vender: multi-cliente, planos e bloqueio por limite existem. Falta cadastro self-service, e-mail (recuperação de senha, verificação) e cobrança automática.
6. **Modelo 1 (comodato)** roda hoje, mas a operação ainda é manual: cobrança por fatura registrada à mão, sem inventário de equipamento/contrato, sem captura de tela nem reinício remoto, alertas só por webhook.
7. **Modelo 2 (anúncios de terceiros)** é o mais distante: **não existe** cadastro de anunciante, pacote, cota de exibição nem controle de "fatia" do loop. Existem os ingredientes (campanhas, zonas de layout, comprovação por mídia), mas não o produto.
8. Riscos que geram custo ou reclamação: servidor grátis que "dorme" (partida a frio de ~100 s), **sem backup do banco configurado por nós**, sem recuperação de senha e sem 2FA, licença gratuita do Open-Meteo (clima) que é para uso não comercial, e o Android pedir confirmação na TV a cada atualização do app.
9. Correções ao que você escreveu: o app roda em **Android (TV, sticks, Fire TV)**, **não** em Samsung/LG nem no navegador; as TVs **baixam e guardam** os arquivos (não fazem streaming) e seguem tocando sem internet.
10. Prioridade comercial sugerida: infraestrutura paga com backup, cobrança recorrente com e-mail, e só depois o módulo de anunciantes (seção 10).

---

## 1. VISÃO GERAL

### 1.1 O que o sistema faz (5 linhas)
O dono da plataforma cadastra **clientes** (empresas). Cada cliente tem o próprio painel, onde sobe **imagens, vídeos, páginas web e widgets**, monta **playlists** e **campanhas** (com agenda e prioridade) e escolhe o que cada **TV** exibe. As TVs rodam um app Android que **baixa os arquivos, guarda no aparelho e continua tocando sem internet**. O servidor acompanha cada TV (online/offline, versão, o que está no ar) e registra cada exibição em milissegundos (**comprovação de exibição**). A cobrança é por **plano** (limite de TVs, GB e usuários) e é registrada manualmente.

### 1.2 Stack
| Camada | Tecnologia | Onde roda | Fonte |
|---|---|---|---|
| Painel (front-end) | Angular 21 (SPA, sem Zone.js), PT/EN, tema claro/escuro, responsivo para celular | Cloudflare Pages (`signage-painel.pages.dev`) | [Código] `frontend/` |
| Back-end | Java 21, Spring Boot 3.5.3, API REST, Flyway (**31 migrações** [atualizado 22/10]; eram 23) | Render, serviço Docker, **plano free**, 1 instância, região **oregon** | [Render] `get_service`; [Código] `backend/` |
| Banco | PostgreSQL 17 (Supabase), região us-east-1 | Supabase, **plano free** | [SQL] `get_organization` (plan=free); `get_project` |
| Armazenamento de mídia | Supabase Storage via API S3, bucket privado `media`, limite de 50 MB por arquivo | Supabase | [SQL] `storage.buckets` |
| Autenticação | **Própria** (e-mail e senha, JWT de 8 h, senha com BCrypt). **Não usa** o Supabase Auth | no back-end | [Código] `SecurityConfig`, `application.yml` |
| App da TV | Kotlin nativo para Android (ExoPlayer/Media3 para vídeo, WebView para widgets), mínimo Android 5.0 (API 21), APK de 5,9 MB | TVs, sticks, Fire TV | [Código] `player/`; `latest.json` do site de downloads |
| Downloads e manuais | Site estático (APK, PDFs, `latest.json`) | Cloudflare Pages (`signage-downloads.pages.dev`) | [Código] `scripts/release-player.mjs` |
| Build e testes | GitHub Actions: 3 jobs (backend, painel, Player); **655 testes de backend, 256 do Player, 821 do painel** [atualizado 22/10]; eram ~563, ~235 e 733 | GitHub | [Código] `.github/`, contagem de `@Test` |

**Domínios próprios, e-mail e CDN pagos:** não há nenhum configurado. O painel usa o endereço `*.pages.dev`, o servidor `*.onrender.com` e o e-mail de suporte da plataforma está em branco [SQL `platform_settings`: não consultado por conter e-mail; o código não envia e-mail].

### 1.3 Arquitetura: como o conteúdo sai do painel e chega na tela
```
 Painel (Cloudflare)  ──HTTPS/JWT──►  API Spring (Render, Oregon) ──SQL──► PostgreSQL (Supabase, us-east-1)
        │                                   │
        │ upload (multipart)                │ guarda o arquivo ──► Supabase Storage (bucket "media")
        ▼                                   ▼
   arquivo + metadados            TV (app Android) a cada 30 s:  POST /player/heartbeat
                                          │   resposta traz "versão desejada da configuração"
                                          │   se mudou: GET /player/config  (playlists, zonas, campanha efetiva, plano offline de 7 dias)
                                          │   para cada mídia que falta no cache: GET /media/{id}/file
                                          │        → servidor responde 302 para URL assinada do Supabase Storage
                                          │        → a TV baixa direto do Storage (o arquivo NÃO passa pelo Render)
                                          │   valida SHA-256, grava no disco e só então troca a playlist
                                          ▼
                              Exibição a partir do disco; cada exibição entra num diário com
                              corrente de hashes e é enviada ao servidor depois de cada heartbeat
```
Fontes: [Código] `PlayerEngine.kt` (heartbeat 30 s), `ConfigSync.kt`, `MediaCache.kt`, `MediaController.download` (302 para URL assinada), `PlayLog.kt`.

---

## 2. MÓDULOS E FUNCIONALIDADES

### 2.1 Painel do dono da plataforma ("Plataforma")
| Item | Status | Observação / fonte |
|---|---|---|
| Visão geral (clientes por situação, TVs, receita mensal recorrente, faturas abertas/vencidas) | ✅ | `PlatformBillingController.overview` |
| Clientes (criar, trocar plano, suspender, **entrar como o cliente** para dar suporte) | ✅ | `PlatformTenantController` |
| Planos (preço, limites de TVs/GB/usuários/tamanho de arquivo, recursos incluídos) | ✅ | [SQL] `plans`; tela Planos |
| Faturas (emissão automática por período, baixa manual, cancelamento) | 🟡 | emite e controla vencimento; pagamento é registrado à mão |
| Cupons de assinatura (desconto, dias grátis, plano liberado por tempo) | ✅ | `PlatformCouponController` |
| Gestores (usuários de operação da plataforma) | ✅ | `PlatformManagerController` |
| Configurações da plataforma (nome, instruções de pagamento, suporte, dias de teste/tolerância, dados de LGPD) | ✅ | tela Configurações |

### 2.2 Painel do cliente (o que a empresa faz sozinha)
Papéis dentro da empresa: **Visualizador** (só olha), **Editor** (conteúdo e o que cada TV exibe), **Administrador** (tudo, inclusive equipe, cobrança e privacidade).

| Item | Status | Observação |
|---|---|---|
| Dashboard (saúde da rede, TVs que precisam de atenção) | ✅ | |
| Dispositivos, Locais, Grupos | ✅ | ver 2.3 e 2.6 |
| Biblioteca (imagens, vídeos, páginas web, QR Code, widgets) | ✅ | ver 2.4 |
| Playlists, Campanhas, Layouts | ✅ | ver 2.5 |
| Preços dinâmicos (tabelas com regra por horário) | ✅ | exige o plano incluir o recurso |
| Atendimento (fila de senhas, guichês, painel de chamadas na TV, senha pelo celular) | ✅ | |
| Cupons para os clientes da loja (QR Code na TV, cupom único, baixa no caixa) | ✅ | |
| Alertas, Relatórios | ✅ | ver 2.8 e 2.9 |
| Equipe (usuários e papéis) e Plano e cobrança (uso, faturas, resgate de cupom) | ✅ | |
| Privacidade e dados (LGPD) | ✅ | feito em 21/10/2026, com pendências jurídicas (seção 7) |
| Downloads e manuais (APK, PDFs) | ✅ | |

### 2.3 Cadastro e gestão de telas
| Item | Status | Observação |
|---|---|---|
| Pareamento por **código de ativação** exibido na TV (o cliente digita no painel) | ✅ | |
| Nome, local, grupo, playlist, layout, orientação da tela (horizontal/vertical, 4 rotações) | ✅ | |
| Status online/offline/nunca conectou, último sinal, versão do app, o que está tocando agora | ✅ | [SQL] `devices.last_seen_at`, `player_version` |
| Ações em várias TVs ao mesmo tempo (aplicar playlist/layout, mover de local, ativar/desativar, atualizar app) | ✅ | `BulkAction` |
| Histórico por TV (ativada, offline, playlist trocada, erros do Player) | ✅ | `device_events` |
| Inventário do equipamento (modelo, número de série, data de instalação, contrato) | 🟡 **[atualizado 22/10]** | Série, data de instalação e nota de comodato no painel e no relatório de inventário (V26). Modelo, Android e memória chegam sozinhos **só com o Player novo** (0.14.87, publicado em 22/10; falta o OK em cada TV). Contrato com vigência e valor ainda não existe |

### 2.4 Upload de mídia
| Item | Status | Observação |
|---|---|---|
| Imagens JPG, PNG, WEBP | ✅ | [Código] `MediaKind` |
| Vídeos MP4, MOV (iPhone) e M4V | ✅ | |
| Tamanho máximo | 🟡 | **50 MB por arquivo**, imposto pelo bucket [SQL `storage.buckets.file_size_limit = 52428800`]. O servidor aceita até 300 MB, mas o Storage recusa acima de 50 MB. O limite por plano existe no cadastro, mas está **vazio em todos os planos** [SQL `plans.max_upload_mb`] |
| Conversão/compressão automática | 🟡 | **no navegador** (WebCodecs): converte MOV/HEVC/4K/HDR/arquivo >50 MB para MP4 H.264 até 1080p, 30 fps, 8 Mbps. **Não existe conversão no servidor.** Testada com arquivos sintéticos; **não verificado com um vídeo real de iPhone** |
| Página da web como conteúdo (cardápio online, painel de vendas) | ✅ | exige internet na TV |
| Pré-visualização do que vai para a TV | ✅ | |

### 2.5 Playlists, loop e agendamento
| Item | Status | Observação |
|---|---|---|
| Playlist com ordem e tempo de cada item | ✅ | |
| Campanha: data de início/fim, **dias da semana**, **janela de horário**, fuso, **prioridade** | ✅ | `CampaignService`, `CampaignSchedule` |
| Alvo da campanha: todas as TVs, um local, um grupo ou uma TV | ✅ | `TargetType` |
| Desempate quando duas campanhas valem ao mesmo tempo | ✅ | maior prioridade, depois alvo mais específico, depois a mais nova |
| "Simular antes de publicar" (ver o que cada TV exibiria num horário) | ✅ | |
| **Mistura de campanhas no mesmo loop** (duas campanhas alternando) | ❌ | **uma campanha vence e a TV exibe só a playlist dela** naquele momento |
| Agenda por item dentro da playlist (cada mídia com a sua validade) | ❌ | o item só tem posição e duração |
| Layout em zonas (várias áreas na mesma tela, cada uma com a sua playlist), versão para tela em pé | ✅ | `layout_zones` |

### 2.6 Divisão do loop entre conteúdo próprio e anúncios de terceiros
**Não existe controle de "espaços" (slots), cota, peso ou frequência.** Não há anunciante, pacote ou contagem de inserções contratadas. [Código: nenhuma ocorrência de `weight`, `slot`, `frequency`, `advertiser` na lógica de exibição.]

O que dá para fazer hoje, **de forma manual e improvisada**:
- Colocar os anúncios **dentro da mesma playlist**, repetindo itens para "pesar" mais; ou
- Usar um **layout com duas zonas** (uma com o conteúdo da empresa, outra com a playlist de anúncios) 🟡; ou
- Uma campanha agendada que troca a playlist inteira por um horário.

Limitação estrutural: **cada TV pertence a um único cliente (tenant)**. Anúncio de terceiro e conteúdo da empresa precisam morar no mesmo cliente; na prática, o dono da plataforma opera tudo "entrando como o cliente".

### 2.7 Agrupamento de telas
| Item | Status | Observação |
|---|---|---|
| Por cliente | ✅ | cada cliente é isolado |
| Por local ("Pontos & Locais") | ✅ | nome e descrição |
| Por grupo (conjuntos manuais de TVs) | ✅ | |
| Por região ou categoria de local (barbearia, academia, clínica) | ❌ | local não tem categoria nem etiqueta |

### 2.8 Conteúdos automáticos (widgets)
| Widget | Status | Observação |
|---|---|---|
| Relógio e data | ✅ | hora do servidor, não depende do relógio da TV |
| Previsão do tempo | 🟡 | usa **Open-Meteo**, cuja versão gratuita é **para uso não comercial**: trocar por plano comercial antes de vender [Manual `DECISIONS` parte 15] |
| Notícias (RSS: G1, Agência Brasil, BBC, CNN, UOL ou feed próprio) | 🟡 | direitos de uso de cada fonte: não verificado |
| Cotações (BCE via Frankfurter) | ✅ | |
| Recado, contagem regressiva | ✅ | |
| Tabela de preços (com regra por horário) e painel de chamadas da fila | ✅ | |
| Cores personalizáveis (fundo, texto, destaque) | ✅ | |
| Redes sociais (Instagram, TikTok, YouTube) | ❌ | só dá via página web, se o site permitir |

### 2.9 Relatórios e monitoramento
| Item | Status | Observação |
|---|---|---|
| **Comprovação de exibição** (cada exibição em milissegundos, TV, mídia, zona, campanha, concluída ou não, corrente de hashes SHA-256, "verificar integridade", CSV) | ✅ | [SQL] 1.548 registros de 2 telas |
| Quantas vezes cada mídia rodou, por TV, por dia (resumo diário guardado por 1 ano) | ✅ | `media_plays`, `play_log_daily` |
| Disponibilidade (tempo online de cada TV no período) | ✅ | `/reports/uptime` |
| Inventário, Eventos, Exibições | ✅ | |
| Relatório em PDF, relatório por anunciante, envio agendado por e-mail | ❌ | só tela e CSV |
| Alerta de TV offline / nunca conectou / erros do Player | 🟡 | alerta na tela e **por webhook** (Slack/Discord). **Sem e-mail, SMS ou WhatsApp** |
| Último sinal de cada TV | ✅ | |
| **Captura de tela remota** | ❌ | |
| Reiniciar ou comandar a TV remotamente | ❌ | só "pedir atualização do app" |
| Assinatura criptográfica por TV na comprovação | ❌ | a corrente de hashes detecta alteração, mas **não prova qual aparelho gerou** |

### 2.10 Outras funções que existem e você não listou
Fila de atendimento (senha pelo celular por QR Code, painel de chamadas com bip); cupons para o consumidor da loja; modo offline inteligente (a TV segue a agenda por até 7 dias); atualização remota do app; escala para TV de baixa memória; tela vertical; QR Code gerado no painel; privacidade/LGPD; cupons de assinatura; tema claro/escuro e PT/EN.

---

## 3. MULTI-CLIENTE

| Pergunta | Resposta | Status | Fonte |
|---|---|---|---|
| É multi-tenant? | Sim. Cada tabela de dados tem `tenant_id` e o Hibernate filtra toda consulta pelo cliente da sessão (`@TenantId`); consultas SQL diretas levam o filtro explícito. Há testes automáticos de isolamento entre clientes | ✅ | [Código] `TenantContext`, testes |
| **RLS do Supabase** configurado? | **Não é a barreira usada.** O RLS está ligado nas 34 tabelas, mas há **0 políticas**; o servidor conecta como dono do banco e o isolamento é **só no código da aplicação**. Risco: uma consulta nova sem o filtro de cliente vaza dados | 🟡 | [SQL] `pg_policies` = 0, `rowsecurity` = 34/34 |
| Cadastro self-service (o cliente cria a conta) | **Não.** Só existe login, troca de senha e "quem sou eu". Cliente e usuário são criados pela operação | ❌ | [Código] `AuthController` |
| Planos, limites e cotas | Sim: TVs, **GB de mídia**, usuários, tamanho de arquivo (campo existe, vazio) e recursos por plano. **Planos hoje:** Grátis R$ 0 (2 TVs, 200 MB, 2 usuários), Básico R$ 49 (5 TVs, 2 GB, 5 usuários), Profissional R$ 149 (25 TVs, 10 GB, 20 usuários), Interno (sem limites) | ✅ | [SQL] `plans` |
| Bloqueio ao atingir o limite | Sim: ativar TV acima do limite, subir arquivo que passa dos GB e criar usuário acima do limite são **recusados com mensagem**. Recursos fora do plano são barrados no servidor | ✅ | [Código] `PlanLimits` |
| Como mede o armazenamento por cliente | `SUM(media.file_size)` do cliente, calculado na hora. Confere com o Storage (8.081.523 bytes nas duas fontes) | ✅ | [Código] `UsageService`; [SQL] `media` x `storage.objects` |
| Mede o **tráfego** (banda) por cliente | **Não.** Só armazenamento, TVs e usuários | ❌ | [Código] |
| Usuários e permissões na mesma empresa | Sim: Visualizador, Editor, Administrador, com limite por plano | ✅ | [Código] `SecurityConfig` |
| Situação da assinatura | Teste, Ativo, Em atraso, **Suspenso** (painel bloqueado; **as TVs seguem exibindo**), Cancelado | ✅ | [Código] `TenantStatus` |

---

## 4. PLAYER / APP DA TELA

| Pergunta | Resposta | Status | Fonte |
|---|---|---|---|
| Baixa e guarda no aparelho ou faz streaming a cada exibição? | **Baixa e guarda em disco.** O arquivo baixa uma vez, tem o SHA-256 conferido (vai para `.part` e só vira arquivo final se bater) e toca **sempre do disco**. Streaming por exibição não existe | ✅ | [Código] `MediaCache.kt`, `ConfigSync.kt` |
| Internet caiu: continua? | Sim, com o que já está no cache. Depois de 75 s sem contato, segue a **agenda de 7 dias** que o servidor mandou (campanhas que começam e terminam na hora certa) e usa a hora do servidor + relógio monotônico (não confia no relógio da TV). **Testado em emulador** com o servidor derrubado. Em TV real a continuidade durante uma queda: **não verificado** (os registros de exibição em produção só começam hoje, 22/10 00:11 UTC) | ✅ / não verificado em TV real | [Código] `OfflinePlanner`; [SQL] `play_logs` |
| Como sabe que o conteúdo mudou | **Polling.** A cada **30 s** a TV chama o servidor (heartbeat); a resposta traz a "versão desejada" da configuração; se diferente, busca a configuração nova. **Não há push nem tempo real**: mudança leva até ~30 s mais o tempo de download. (Só o painel de chamadas da fila atualiza a cada 3 s) | 🟡 | [Código] `Timing.heartbeatMs = 30_000` |
| Baixa só o que mudou? | **Só o que falta.** O nome do arquivo no cache leva o checksum; mídia já guardada não baixa de novo. A playlist antiga continua tocando até a nova ficar completa; depois as mídias que ninguém mais usa são apagadas. Verifica espaço livre antes de baixar. **Sem retomada de download** interrompido (recomeça o arquivo) | ✅ / 🟡 | [Código] `ConfigSync.kt` (`missing`, `freeBytes`) |
| Em quais aparelhos funciona | **Android 5.0 ou mais novo com interface de TV (leanback):** Android TV, Google TV, Fire TV/Fire Stick, sticks e boxes Android. **Não existe** app para Samsung (Tizen), LG (webOS), navegador/Chromecast nativo ou Raspberry Pi | ✅ Android / ❌ demais | [Código] manifesto, `minSdk = 21` |
| Quais foram testados | **Emuladores** Android 14 TV e Android 5.1 TV (todos os fluxos, 4 rotações, offline, atualização). **TV real:** uma **TCL Android TV** (informado por você em 19–20/10). Fire Stick: planejado, **não verificado**. O modelo das 2 TVs em produção **não é guardado** pelo sistema: **não verificado** | 🟡 | [Manual] memória do projeto; [SQL] `devices` |
| Inicia sozinho quando a TV liga? | **Sim**, por receptor de boot (`BOOT_COMPLETED`/`QUICKBOOT_POWERON`) e permissão "exibir sobre outros apps"; há vigia que reabre o app se ele cair (testado em emulador). **Não é modo kiosk travado** (isso exigiria Device Owner/MDM). Em Android 10+ a permissão pode ter de ser liberada manualmente na TV (está no manual) | ✅ / 🟡 | [Código] `BootReceiver`, `Restarter`; [Manual] |
| Atualização do app | **Semiautomática.** A TV consulta o `latest.json` do site de downloads a cada 30 min (janela de horário configurável) e o painel tem "Atualizar app" (uma ou todas). Baixa e confere o SHA-256, mas o **Android sempre pede um OK na TV** para instalar, e exige liberar "instalar apps desconhecidos" para o app. Foi exatamente o que aconteceu hoje. **Sem MDM, não há atualização 100% silenciosa** | 🟡 | [Código] `UpdateManager.kt`; [SQL] `device_events` |
| Requisitos mínimos | **Android 5.0 (API 21)**; APK de **5,9 MB** [`latest.json`]; **RAM:** funciona em aparelhos de ~1 GB com um **perfil de pouca memória** (imagens reduzidas a 1280×720, um vídeo por vez, libera decodificador ao sair de cena); armazenamento: o suficiente para as mídias das playlists da TV (o app confere espaço livre antes de baixar); vídeo recomendado **H.264 até 1080p** (HEVC/4K depende do aparelho; o app avisa quando não toca) | ✅ | [Código] `MemoryProfile.kt` |

---

## 5. COBRANÇA E COMERCIAL

| Pergunta | Resposta | Status | Fonte |
|---|---|---|---|
| Pagamento recorrente integrado (Asaas, Mercado Pago, Stripe...) | **Não existe nenhuma integração.** O sistema emite a fatura, mostra as **instruções de pagamento** (texto livre, por exemplo chave Pix) e o operador **registra a baixa à mão** (método e observação) | ❌ | [Código] `BillingService`, `docs/INTEGRACOES.md` §3.8 |
| Gestão de contratos | **Não há** cadastro de contrato, vigência, comodato nem equipamento. Existe **assinatura por plano** com período e fatura | ❌ | [Código] |
| **[atualizado 22/10]** Inventário de comodato | 🟡 Existe o controle básico do equipamento (série, instalação, nota de comodato); **continua sem** contrato com vigência, valor e renovação | 🟡 | [Código] V26 |
| Vencimentos e bloqueio por inadimplência | Sim: emissão da fatura N dias antes do fim do período, marca "em atraso" no vencimento, **suspende o painel** depois dos dias de tolerância (configuráveis), roda de hora em hora. **As TVs continuam exibindo** (não dá para "cortar" a tela do inadimplente) | ✅ / 🟡 | [Código] `BillingJob`, `TenantStatus` |
| Nota fiscal, boleto, recibo em PDF | ❌ | ❌ | [Código] |
| Cadastro de anunciantes, campanhas de anunciantes e pacotes de anúncio | **Não existe.** Há **campanhas** (agenda e prioridade), mas sem anunciante, cota de inserções, preço ou pacote | ❌ | [Código] |
| Relatório para enviar ao anunciante | 🟡 **Os dados existem** (comprovação por mídia, TV, campanha e período, com CSV e verificação de integridade). **Não existe** relatório formatado/PDF, por anunciante ou com marca | 🟡 | [Código] `/reports/proof` |
| Cupons de desconto na assinatura | ✅ | ✅ | `PlatformCouponController` |

---

## 6. CUSTOS E USO REAL (números atuais, 22/10/2026)

### 6.1 Planos e custos de infraestrutura
| Serviço | Plano contratado | Fonte | O que inclui / observação |
|---|---|---|---|
| **Supabase** | **Free** (organização `plan = free`) | [SQL] `get_organization` | Banco PostgreSQL, Storage, Auth (não usado) e MAU (não se aplica: a autenticação é própria). Limites do plano grátis (**valores públicos do provedor, não conferidos hoje**): ~500 MB de banco, ~1 GB de arquivos, ~5 GB de tráfego/mês, pausa por inatividade. O limite de 50 MB por arquivo configurado no bucket coincide com o teto do plano grátis |
| **Render** | **Free**, 1 instância, Docker, região **oregon** | [Render] `get_service` | ~512 MB de RAM, **dorme após ~15 min sem requisição** (partida a frio de **~100 s** medida nos logs de hoje) e tem horas mensais limitadas (valores públicos, não conferidos). Enquanto as TVs fazem o heartbeat de 30 s o servidor não dorme; se todas ficarem offline, dorme |
| **Cloudflare Pages** | Painel e site de downloads | não verificado (plano da conta) | Hospedagem estática; normalmente sem custo |
| **Hostinger** | **Não há referência alguma** | [Código] busca no repositório e nas configurações | **não verificado**: se você paga Hostinger por domínio, e-mail ou site, isso **não está ligado** ao sistema |
| Domínio, e-mail, CDN, APIs pagas | Nenhum configurado no código | [Código] | Open-Meteo (clima) no plano gratuito, **não comercial** |
| **Custo recorrente verificado** | **R$ 0** | | |

### 6.2 Uso atual
| Medida | Valor | Fonte |
|---|---|---|
| Clientes | 2 linhas em `tenants` (1 ativo + 1 de sistema); 2 usuários | [SQL] |
| **Telas ativas** | **2** (ambas no Player 0.14.69; último sinal há poucos minutos) | [SQL] `devices` |
| Mídias cadastradas | 15: 4 imagens, 4 vídeos, 1 página web, 6 widgets | [SQL] `media` |
| **GB armazenados** | **8.081.523 bytes ≈ 7,7 MB** em 8 arquivos (0,8% de 1 GB) | [SQL] `storage.objects`, conferido com `media` |
| **Tamanho médio de um vídeo** | **1,53 MB** (4 vídeos, 6,12 MB no total; o maior tem 3,8 MB) | [SQL] |
| Banco de dados | **14 MB**. A maior tabela é `play_logs` (942 KB, 1.548 linhas) | [SQL] `pg_database_size`, `pg_stat_user_tables` |
| Registros de exibição | 1.548 (2 telas, desde 22/10 00:11 UTC); média de 13,7 s por exibição; 97,6% concluídas | [SQL] `play_logs` |
| Ritmo de exibição da tela "Call 01" | **343 a 665 exibições por hora** (itens de ~6 a 9 s) | [SQL] `play_logs` por hora |
| **Tráfego mensal medido** | **não verificado**: não há medição de banda por cliente no sistema. Exige o painel de uso do Supabase/Render, ao qual não tenho acesso | — |

### 6.3 Tráfego estimado de UMA tela (a CALL) por mês
**[Estimativa]**, a partir do ritmo medido e do código. Premissas: tela ligada 24 h; item médio de 6–9 s; heartbeat a cada 30 s; JSON e cabeçalhos HTTPS de 1,5–2,5 KB por heartbeat; ~0,4 KB por exibição enviada ao servidor.

| Parcela | Conta | Por mês |
|---|---|---|
| Heartbeats | 2.880/dia × 1,5–2,5 KB | ~130–220 MB |
| Envio da comprovação | ~16 mil exibições/dia × 0,4 KB ≈ 6,4 MB/dia | ~190 MB (a ~6,6 mil exibições/dia: ~80 MB) |
| **Total de API (passa pelo Render)** | | **~0,2 a 0,4 GB por tela por mês** |
| Download de mídia (Supabase Storage, **não** passa pelo Render) | uma vez por mídia nova ou trocada, por TV: a CALL hoje carrega poucos KB de imagens (~51 KB, itens distintos) e a outra tela 3,8 MB de vídeo | **desprezível hoje**; para uma TV com 100 MB de vídeos que mudam 1×/mês, ~100 MB/mês |
Conclusão: **a banda de mídia só pesa quando há muitos vídeos grandes trocados com frequência em muitas TVs**; o tráfego fixo por tela é pequeno. O que cresce com o número de telas é o **banco** (seção 6.4).

### 6.4 Até onde a infraestrutura atual aguenta
| Recurso | Medida de hoje | Limite do plano grátis (valor público, não conferido) | Aguenta aproximadamente |
|---|---|---|---|
| Armazenamento de mídia | 7,7 MB | ~1 GB | ~130x o uso atual; com vídeos de 1,5 MB, ~600 vídeos. O teto de 50 MB por arquivo é do bucket |
| **Banco** | 14 MB; **~0,6 KB por exibição** (942.080 B / 1.548 linhas) | ~500 MB | **Gargalo.** Uma tela como a "Call 01" gera ~16 mil linhas/dia ≈ **9,7 MB/dia** ≈ **136 MB em 14 dias** (retenção padrão). **~3 telas assim ocupariam o banco inteiro.** Reduzir a retenção (mínimo de 7 dias), agregar ou migrar de plano resolve |
| Servidor (Render free) | 1 instância, 512 MB, pool de 3 conexões com o banco | — | **Não testado em carga.** 1.000 telas = ~33 requisições/s só de heartbeat: **acima do que um serviço grátis aguenta**. Para dezenas de telas deve servir; centenas exigem plano pago |
| Latência | servidor em **oregon** e banco em **us-east-1** (outro lado do país) | — | Cada consulta atravessa o país. O `render.yaml` do projeto pedia a mesma região do banco; **o serviço criado está em oregon** [Render]. Efeito não medido |
| **Próximo degrau** | Supabase Pro e Render pago (valores públicos aproximados: ~US$ 25 e ~US$ 7+ por mês, **conferir hoje**) | | Resolve banco maior, backup e servidor que não dorme |

---

## 7. SEGURANÇA E CONFORMIDADE

| Item | Situação | Status | Fonte |
|---|---|---|---|
| Login | E-mail e senha; senha com BCrypt; sessão (JWT) de 8 h; senha provisória com troca obrigatória no primeiro acesso | ✅ | [Código] |
| **Recuperação de senha** ("esqueci a senha") **[atualizado 22/10]** | **Existe** (link de uso único, vale 1 hora; resposta igual exista ou não a conta) e há e-mail de boas-vindas. **Falta o provedor de e-mail:** sem ele nenhum e-mail sai | 🟡 | [Código] V28, `docs/EMAIL.md` |
| **2FA** | **Não existe** | ❌ | [Código] |
| Registro de auditoria (quem alterou o quê) | Não existe (só o histórico das TVs) | ❌ | [Código] |
| **Backups** | **Não há rotina de backup do banco no projeto.** Dependem do plano do Supabase: no plano gratuito **não verificado** (o que sei é que backups automáticos normalmente são do plano pago). Os **arquivos do Storage** também não têm cópia nossa. A chave de assinatura do app (guardada na sua máquina) **precisa de backup seu**: sem ela as TVs deixam de atualizar | ❌ / não verificado | [Código] busca por backup; [Manual] |
| **Backups [atualizado 22/10]** | Scripts de backup e de **restauração testada** (no CI), rotina diária e plano de migração para o Supabase Pro estão prontos (Etapa 1.1). **Ainda não estão ativos em produção:** falta você contratar o plano e criar as chaves. Enquanto isso o risco descrito acima continua | 🟡 | `docs/MIGRACAO_PRODUCAO.md` |
| Isolamento entre clientes | Em código, com testes (seção 3) | 🟡 | |
| Limites contra abuso nas páginas públicas | Sim (por aparelho e por rede) | ✅ | |
| **LGPD** | Política de Privacidade e Termos de Uso publicados (`/privacidade`, `/termos`, texto-base); aviso e **concordância** na coleta do nome (fila e cupom); prazos de guarda por cliente com limpeza horária; tela para **buscar, copiar e apagar** dados de uma pessoa, com registro; mapa dos dados em `docs/LGPD.md`. **Pendências:** revisão por advogado, razão social/CNPJ/encarregado ainda **em branco na produção**, contratos com clientes e com os provedores, plano de incidentes | 🟡 | [Código], [Manual] |
| **Dados pessoais armazenados** | Equipe (nome, e-mail, papel); nome opcional na fila e no cupom; código aleatório do aparelho; e-mail de quem dá baixa no cupom. Dados de cobrança da empresa. **Transferência para os EUA** (Supabase us-east-1, Render oregon) | 🟡 | `docs/LGPD.md` |
| **Moderação de conteúdo** | **Nenhuma.** Só validação de tipo e tamanho de arquivo. Não há aprovação, varredura de vírus, lista de bloqueio nem denúncia. O cliente responde pelo que sobe (isso consta nos Termos) | ❌ | [Código] |

---

## 8. PRONTIDÃO POR MODELO DE NEGÓCIO

### Modelo 1: mensalidade com equipamento em comodato
**Pode vender amanhã:** instalar o app na TV/stick (APK, ~10 min por aparelho), parear, subir conteúdo, agendar, acompanhar online/offline com alerta por webhook, comprovação de exibição, monitorar TVs de vários locais, suspender o painel por atraso. O plano "Profissional" (R$ 149, 25 TVs) ou um plano novo por TV existe no cadastro. **Em uso real hoje** (CALL).

| O que falta | Esforço |
|---|---|
| Cobrança recorrente automática (Pix/boleto/cartão com baixa automática) e e-mail de fatura | **Grande** (semanas) com gateway; **médio** se só Pix com conciliação |
| Cadastro de equipamento e contrato (modelo, série, data de instalação, valor do comodato, vigência, devolução) | **Médio** (dias) |
| Alerta de TV offline por **e-mail/WhatsApp** (hoje só webhook) | **Médio** (dias; exige provedor de e-mail/WhatsApp) |
| Reinício e **captura de tela remotos** (reduz visita técnica) | **Médio a grande**: exige novo comando no Player e teste em vários aparelhos |
| Infraestrutura paga + backup + monitor externo do servidor | **Pequeno** (horas) de trabalho, mais custo mensal |
| Kit de instalação padronizado (sticks pré-configurados com permissões de boot e "apps desconhecidos") | **Pequeno** (horas, é processo) |
| Nota fiscal/recibo | **Grande** (depende do seu contador/emissor) |

**Riscos:** servidor grátis dorme e leva ~100 s para voltar (as TVs seguem tocando, mas o painel parece "fora do ar"); **sem backup**; o Android pede confirmação em cada atualização do app (alguém precisa apertar OK na TV ou usar MDM); variedade de aparelhos Android baratos (memória, HEVC) com **poucos modelos testados**; clima gratuito não comercial.

### Modelo 2: venda de espaço publicitário
**Pode vender amanhã (de forma artesanal):** rodar anúncios de terceiros dentro do conteúdo da empresa **colocando as mídias na playlist ou numa zona do layout**, e entregar o **CSV de comprovação de exibição** como prova ao anunciante. Operação 100% manual.

| O que falta | Esforço |
|---|---|
| Cadastro de **anunciante**, **pacote** (nº de inserções, período, preço) e vínculo mídia ↔ anunciante | **Médio** (dias) |
| **Controle de fatia do loop**: cota de inserções por hora/dia, frequência, ordem de rotação entre próprio e anúncio (hoje uma campanha vence e as demais param) | **Grande** (semanas) |
| Relatório do anunciante (PDF ou página com marca, por período, TV e mídia) | **Médio** |
| Acompanhamento de entrega do pacote ("já rodou 80% das inserções contratadas") e alerta de cota | **Médio** |
| Fluxo de aprovação do anúncio antes de ir ao ar (moderação) | **Médio** |
| Comprovação assinada por TV (auditoria de terceiros) | **Médio a grande** |
| Faturamento por pacote | **Médio** (depende da cobrança do modelo 1) |
| Cobertura dos dois conteúdos no mesmo cliente (TV pertence a um cliente só): definir o processo (o dono opera "entrando como o cliente") | **Pequeno** (processo) |

**Riscos:** prometer ao anunciante número exato de inserções **sem o controle de cota** gera reclamação; a comprovação detecta alteração dos registros, mas **não prova qual aparelho exibiu**; TV offline por horas = inserções não entregues (não há "compensação" automática).

### Modelo 3: assinatura SaaS por GB (cliente usa os próprios aparelhos)
**Pode vender amanhã (com operação manual):** criar o cliente, escolher o plano (TVs, **GB**, usuários), entregar o link do painel e o manual do app; o sistema **mede** o armazenamento, **bloqueia** upload acima do GB e TV acima do limite, mostra o uso ao cliente e suspende por atraso. Planos de exemplo já cadastrados [SQL].

| O que falta | Esforço |
|---|---|
| **Cadastro self-service** (criar conta, escolher plano, período de teste) | **Médio** (dias), mais **verificação de e-mail** |
| **E-mail transacional** (verificação, recuperação de senha, boas-vindas, fatura) | **Médio** (provedor + modelos) |
| **Recuperação de senha** | **Pequeno a médio**, depende do e-mail |
| **Cobrança recorrente automática** com webhook de confirmação | **Grande** |
| Medição e limite de **tráfego** por cliente (hoje só GB armazenados) | **Médio** |
| Infraestrutura paga, **backup** e acompanhamento de disponibilidade (SLA) | **Pequeno** de esforço, mais custo mensal |
| Redução do crescimento do registro de exibição (agregação, retenção menor ou particionamento) | **Médio** (antes de escalar) |
| Aceite dos Termos no cadastro e contratos de tratamento de dados | **Jurídico** (pequeno de código) |
| Chaves de API por cliente (integração com sistemas do cliente) | **Médio** |
| Suporte a outros aparelhos (Samsung/LG, navegador) para "usar os próprios aparelhos" de verdade | **Grande** (nova plataforma de Player) |

**Riscos:** o cliente com Smart TV Samsung/LG **não consegue usar** (só Android); limite de GB sem limite de banda permite o cliente trocar vídeos pesados com frequência e gerar tráfego que o plano não mede; sem recuperação de senha todo esquecimento vira chamado de suporte; isolamento só no código (uma consulta nova sem filtro vaza dados entre clientes: precisa de revisão e testes a cada mudança).

---

## 9. DIFERENCIAIS E LIMITAÇÕES

**Aviso:** não tenho acesso a 4YouSee, Musardos, Yodeck nem Attractive Mídia Indoor hoje. A comparação abaixo é **sobre o que o Vyzzu tem**, com base no que conheço dessas plataformas em geral; **recursos e preços deles: não verificado**. Confira antes de usar em proposta.

### 9.1 O que o Vyzzu tem de diferente (verificado no código)
- **Comprovação de exibição com corrente de hashes** em milissegundos, verificável e exportável (mais detalhada que contagem diária).
- **Offline inteligente:** a TV segue a agenda por até 7 dias e usa a hora do servidor, não a da TV.
- **Preços dinâmicos por horário** (cardápio/serviços com regra de happy hour) e **fila de senhas com painel de chamadas** e **cupons por QR Code**, tudo no mesmo produto.
- **Perfil de pouca memória** para TVs de 1 GB; tela vertical com 4 rotações.
- **Multi-cliente com plano por GB/TVs/usuários**, gestor de plataforma que "entra como o cliente", cupons de assinatura.
- Painel em **português e inglês**, responsivo para celular, tema escuro, manuais completos.
- **Custo de operação zero hoje** (camadas gratuitas) e código sob seu controle.

### 9.2 Limitações conhecidas
| Limitação | Efeito |
|---|---|
| Só Android (sem Samsung/LG/navegador) | Reduz o mercado de "use seus aparelhos" |
| Infra em camadas gratuitas (servidor dorme, banco de 500 MB, sem backup nosso) | Não serve para vender com SLA |
| Sem cota/slot de anúncio, sem anunciante e sem relatório de anunciante | Modelo 2 incompleto |
| Cobrança manual, sem e-mail, sem recuperação de senha, sem 2FA, sem auditoria | Operação e segurança abaixo do esperado em SaaS |
| Atualização do app exige OK na TV | Visita ou orientação ao cliente a cada versão |
| Detecção de mudança por polling de 30 s e sem retomada de download | Latência e consumo em redes ruins |
| Registro de exibição cresce rápido (~10 MB/dia por tela intensa) | Estoura o banco grátis com poucas telas |
| Conversão de vídeo só no navegador e testada com arquivos sintéticos | Vídeos reais de iPhone **não verificados** |
| Clima gratuito não comercial; direitos dos feeds de notícia | Risco de licença |
| Hardware real pouco testado (uma TCL; modelo das TVs em produção não registrado) | Surpresa em aparelhos baratos |
| Isolamento entre clientes só no código (RLS sem políticas) | Exige disciplina de revisão |
| Um teste automático de fila (`PublicTicketTest`) falhou uma vez em execução conjunta e passou nas demais (3 isoladas e no CI); causa provável: corrida de milissegundos na ordem de senhas, **não confirmada** | Instabilidade de teste, sem efeito conhecido em produção |

### 9.3 Inconsistências que encontrei durante o levantamento
- A Política de Privacidade e o `docs/LGPD.md` que escrevi dizem que o servidor fica "na Virgínia" (baseei-me no `render.yaml`). **O serviço real está em Oregon** [Render]. Continua sendo transferência para os EUA, mas o texto precisa ser corrigido. **Não corrigi agora por causa da regra de somente leitura.**
- `render.yaml` pede o servidor na mesma região do banco; o serviço criado não segue isso.

---

## 10. ROADMAP SUGERIDO (impacto comercial, em ordem)

| # | Melhoria | Justificativa | Esforço |
|---|---|---|---|
| 1 | **Infraestrutura de produção:** Render pago (não dorme), Supabase Pro (banco maior e backup), mesma região, monitor externo de disponibilidade, backup do Storage | Nenhum dos 3 modelos se sustenta com servidor que dorme e sem backup; é a venda mais fácil de defender | Pequeno (horas) + custo mensal |
| 2 | **E-mail transacional** + **recuperação de senha** + alerta de TV offline por e-mail | Desbloqueia SaaS, reduz suporte e dá o alerta que o comodato precisa | Médio |
| 3 | **Cobrança recorrente** (Pix/boleto/cartão com baixa automática) | Hoje a receita depende de baixa manual; é o item mais ligado a escalar | Grande |
| 4 | **Controle do crescimento do registro de exibição** (agregação e retenção menor por padrão) | Sem isso 3 telas intensas enchem o banco; é o primeiro limite real | Médio |
| 5 | **Anunciantes, pacotes e cota de inserções** com **relatório em PDF** para o anunciante | É o que transforma comprovação de exibição em produto vendável (modelo 2) | Grande |
| 6 | **Controle de fatia do loop** (rotação entre conteúdo próprio e anúncios, frequência) | Sem isso a promessa de "x inserções por dia" não se cumpre | Grande |
| 7 | **Cadastro self-service** com período de teste, verificação e aceite dos Termos | Escala o modelo 3 sem trabalho manual | Médio |
| 8 | **Operação de comodato:** cadastro de equipamento/contrato, captura de tela e reinício remotos | Reduz visita técnica e dá controle do ativo emprestado | Médio a grande |
| 9 | **Matriz de aparelhos testados** (Fire Stick, Google TV, TCL, sticks baratos), download retomável e meta de compatibilidade documentada | Reduz o risco de reclamação por aparelho | Médio |
| 10 | **Moderação/aprovação** de conteúdo e medição de banda por cliente | Protege a plataforma (conteúdo de terceiros) e fecha o preço por uso | Médio |


**Situação do roadmap após as Etapas 1 e 2 [atualizado 22/10]:**

| # | Melhoria | Situação |
|---|---|---|
| 1 | Infraestrutura de produção, backup e monitor | 🟡 Pronto no código e documentado; **falta você contratar e ativar** |
| 2 | E-mail transacional, recuperação de senha e alerta por e-mail | 🟡 Pronto e no ar; **falta o provedor** (Brevo ou Resend) |
| 3 | Cobrança recorrente | ❌ Etapa 3 (não iniciada) |
| 4 | Controle do crescimento do registro de exibição | ✅ Feito (V24) |
| 5 | Anunciantes, pacotes e relatório em PDF | ❌ Etapa 3 |
| 6 | Controle de fatia do loop | ❌ Etapa 3 |
| 7 | Cadastro self-service | ❌ Etapa 3 |
| 8 | Operação de comodato (inventário, diagnóstico remoto) | 🟡 Inventário e diagnóstico prontos; parte depende de a TV instalar o Player 0.14.87 (já publicado); captura de tela e reinício remoto não existem |
| 9 | Matriz de aparelhos testados | ❌ Não feito (depende de hardware) |
| 10 | Moderação e medição de banda | ❌ Não feito |

---

## ANEXO: o que NÃO consegui verificar
- Uso real de tráfego (egress) do Supabase e do Render, e as faturas desses serviços: exigem os painéis deles.
- Limites **atuais** dos planos grátis e preços dos planos pagos (usei valores públicos que conheço; **conferir hoje**).
- Plano e custo do Cloudflare; Hostinger (sem relação com o sistema).
- Modelo, versão do Android e RAM das 2 TVs em produção.
- Comportamento em TV real durante queda de internet, e Fire Stick.
- Se algum log do Render contém dado pessoal.
- Teste de carga do servidor.
- Funcionalidades atuais das plataformas concorrentes.
