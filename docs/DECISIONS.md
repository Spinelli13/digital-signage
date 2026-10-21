# Decisões do projeto

Registro de decisões que complementam o [documento de contexto](../PROJETO_DIGITAL_SIGNAGE_CONTEXTO_CLAUDE.md).

## 2026-09-29

- Plataforma de Player para a primeira prova técnica: **Android TV / Google TV Box** (hardware já disponível para testes).
- Encontrados na pasta do projeto: `apresentacao_digital_signage.html` (pitch conceitual) e `prototipo_painel_digital_signage.html` (protótipo clicável do painel — usar como referência visual do frontend, menu já bate com a seção 14 do documento de contexto).
- **Correção de stack**: a apresentação (slide 12) definia Java + Spring Boot + Angular, enquanto o documento de contexto original definia Go + React. O documento de contexto foi corrigido para refletir a stack válida, confirmada pelo usuário: **backend Java + Spring Boot, frontend Angular + TypeScript**. PostgreSQL, Docker e a estratégia de sincronização (REST + polling no MVP, WebSocket depois) permanecem como estavam.
- Início da implementação seguiu a ordem recomendada do documento: estrutura do repositório + backend Spring Boot mínimo com `GET /api/v1/health` (validado localmente com Maven — Java 21).
- **Spring Boot 4.1.1 → 3.5.3**: a versão mais nova (4.1.1) reorganizou a autoconfiguração em módulos separados e não trouxe suporte automático a Flyway (nenhuma `FlywayAutoConfiguration` é registrada, mesmo com as dependências corretas no `pom.xml`), causando falha de schema na subida da aplicação. Por ser uma major muito recente com integração ainda instável, optei por fixar em **Spring Boot 3.5.3** (LTS-like, Flyway funcionando nativamente, mesma API de configuração). Continua Java 21 + Spring Boot — não muda a stack aprovada, só a versão exata.
- PostgreSQL, migrations (Flyway), autenticação (JWT) e CRUD de dispositivos foram implementados e validados de ponta a ponta com um Postgres real (login, criação, listagem, atualização, remoção, tratamento de 401/404/validação).
- **Biblioteca de mídia**: upload (multipart) de JPG/PNG/WEBP/MP4 com armazenamento em disco local (`app.storage.local-path`, volume Docker dedicado `media_data`), checksum SHA-256 calculado no servidor (será usado pelo Player para validar o cache), endpoint de download (`GET /media/{id}/file`) e remoção (apaga registro + arquivo físico). Validado de ponta a ponta: upload, rejeição de tipo não suportado (400), download com integridade confirmada por diff, remoção e 404 pós-remoção. `duration_seconds` de vídeo é opcional no upload (sem parsing de metadata no servidor — se necessário, extrair no frontend antes de enviar).
- **Playlists**: CRUD completo com itens ordenados (`POST`/`PUT` recebem a lista completa de itens e substituem tudo de uma vez — mesmo padrão do protótipo de UI, que salva a playlist inteira ao clicar "Publicar alterações"). Duração de cada item: se não informada, usa a duração da própria mídia (vídeos) ou 10s por padrão (imagens). `config_version` incrementa a cada alteração de itens — é o que o Player vai comparar no passo 10 (sincronização). Associar playlist a um dispositivo fica para o passo 10, junto com o endpoint de configuração do Player (não faz sentido isolado ainda).
  - **Bug real encontrado e corrigido**: `GET /playlists` e `GET /playlists/{id}` quebravam com `LazyInitializationException` ao montar a resposta (a coleção `items` é `LAZY` e `open-in-view: false` já fecha a sessão do Hibernate antes da serialização). `POST`/`PUT` não sofriam porque já tinham `@Transactional`. Corrigido adicionando `@Transactional(readOnly = true)` aos métodos de leitura. Vale lembrar desse padrão em qualquer novo endpoint que leia uma relação `LAZY` fora de um método já transacional.
  - Restrição de integridade confirmada: apagar uma mídia referenciada por uma playlist retorna 409 (FK `ON DELETE RESTRICT`); apagar a playlist primeiro libera a mídia.
- **Endpoint de configuração do Player + associação de playlist**: `GET /devices/{id}/config` retorna a playlist atual do dispositivo (`config_version`, itens com URL/checksum/duração). `PUT /devices/{id}` ganhou o campo opcional `currentPlaylistId` para associar (mesmo padrão de "só altera se enviado" já usado em `status`).
  - **Decisão de design importante**: o `config_version` retornado pelo endpoint é sempre lido **direto da playlist** (`playlist.getConfigVersion()`), nunca de `devices.config_version`. Testei e confirmei: editar a playlist depois de associada reflete a nova versão no `/config` do dispositivo automaticamente, sem precisar reassociar — é exatamente o que o Player vai precisar para decidir quando sincronizar. A coluna `devices.config_version` (do schema original) fica reservada para o passo 15 (heartbeat), onde vai registrar o que o Player *de fato* reportou ter sincronizado — não é a mesma coisa que a versão "atual" da playlist.
  - Dispositivo sem playlist associada retorna `config_version: 0` e `items: []` (estado válido, não erro).
  - **Limitação temporária conhecida**: `/devices/{id}/config` ainda exige o JWT de admin, porque a autenticação por `device_token` só é criada no passo 12 (Ativação). Vai precisar revisar o `SecurityConfig` quando isso for implementado, para o Player conseguir chamar esse endpoint sem logar como admin.

## 2026-09-29 (continuação) — Player Android

- Ambiente verificado: Android Studio e SDK já instalados (`C:\Users\mathe\AppData\Local\Android\Sdk`), plataformas 36/36.1, build-tools até 37.0.0, `adb`/`fastboot` presentes. Sem `ANDROID_HOME` configurado — resolvido criando `player/local.properties` (gitignorado) apontando pro SDK.
- **Versões escolhidas deliberadamente não são as mais novas**: o SDK do Google já está em AGP 9.x e Gradle 9.8.0 (lançado dias atrás). Depois do problema real que tivemos com Spring Boot 4.1.1 (major novíssima quebrando o Flyway), decidi não repetir o padrão aqui: fixei **Gradle 8.14.5 + AGP 8.13.2 + Kotlin 2.1.21** — as últimas versões maduras da série 8.x, bem estabelecidas, evitando a série 9.x recém-lançada até que esteja mais validada pelo ecossistema.
- Gerado o Gradle Wrapper oficial (`gradlew`/`gradlew.bat`) baixando a distribuição real do Gradle e rodando a task `wrapper` nela — não escrevi os scripts à mão, pra garantir que o `gradle-wrapper.jar` seja o binário oficial.
- **Player mínimo criado** (módulo `app`, package `com.digitalsignage.player`): Activity única em tela cheia (immersive sticky), fundo com a paleta de marca da apresentação, `FLAG_KEEP_SCREEN_ON` (essencial pra um player de sinalização nunca apagar a tela), banner e ícone da TV (vetores simples, sem arte final ainda), e um `BroadcastReceiver` para `BOOT_COMPLETED`/`QUICKBOOT_POWERON` que relança o app sozinho — cobre "tela cheia" e o esqueleto de "inicialização automática" da lista de validação técnica (seção 4).
- **Compilação validada de verdade**: `./gradlew assembleDebug` rodou com sucesso e gerou `app/build/outputs/apk/debug/app-debug.apk`.
- **Ainda não validado**: não havia nenhum dispositivo Android conectado via `adb` no momento (o Android TV Box físico não estava plugado nesta máquina), então só confirmei que compila — falta instalar de fato no hardware e confirmar que o boot automático e a tela cheia funcionam na prática. Isso é o próximo passo antes de avançar para ativação/download/reprodução (passos 12-14).
- Escopo deliberadamente **não** incluído ainda (fica pros próximos passos): nenhuma biblioteca de rede (Retrofit/OkHttp) nem de mídia (ExoPlayer/Media3) — isso entra nos passos 12-14 quando o Player realmente precisar falar com o backend e reproduzir conteúdo.

## 2026-10-19 — Testes de regressão, passos 12–16, painel e preparação de deploy

### Testes de regressão (antes de seguir adiante)

- **Infra de testes**: Postgres real embutido (`io.zonky.test:embedded-postgres`, binários 16.x — mesma major do `postgres:16-alpine`) roda as migrations Flyway de produção sem precisar de Docker. Mesmo contexto Spring e mesmo banco para todas as classes; o banco é truncado antes de cada teste (o admin semeado sobrevive). Rodam também no GitHub Actions (`.github/workflows/ci.yml`).
- Os bugs corrigidos antes (e as decisões de design) viraram testes: `LazyInitializationException` em `GET /playlists`, 409 ao apagar mídia usada, `config_version` vindo da playlist e não do dispositivo, versão incrementando a cada troca de itens, duração padrão 10 s, ordem dos itens.
- **Bugs novos que os testes acharam e foram corrigidos:**
  - **Requisição sem token respondia 403, não 401** (Spring Security 6 sem `authenticationEntryPoint`). O painel depende do 401 para voltar ao login. Corrigido com `HttpStatusEntryPoint(UNAUTHORIZED)`.
  - **Em servidor real, todo 400/403/500 chegava ao cliente como 401.** O Tomcat reenvia respostas de erro para `/error`, e esse despacho voltava pela segurança como anônimo. Efeito: JSON inválido enviado pelo painel deslogava o usuário; "proibido" virava "não autenticado". Corrigido com `dispatcherTypeMatchers(ERROR).permitAll()`. **MockMvc não reproduz isso** (não faz despacho de erro) — por isso `RealServerTest` sobe o Tomcat de verdade. Confirmei que o teste falha sem a correção (3 falhas).
  - **Heartbeat só devolvia a versão**, e duas playlists diferentes podem estar ambas na versão 1: trocar a playlist do dispositivo por outra de mesma versão não seria percebido. O heartbeat passou a devolver `playlistId` + `configVersion`, e o Player compara os dois.

### Backend

- **Ativação (passo 12)**: o Player gera um token aleatório de 256 bits, envia em `POST /player/register` (público) e o servidor guarda só o SHA-256. A TV recebe um **código de 6 caracteres** (alfabeto sem `0/O/1/I`) e o exibe; o admin o digita no painel (`POST /devices/activate`). O mesmo token autentica todas as chamadas seguintes (`X-Device-Token`) — não há troca de segredo na ativação. Registro é idempotente (mesmo token → mesmo estado).
  - Papéis: `ROLE_DEVICE` (ativo), `ROLE_DEVICE_PENDING` (só `/player/status`), desativado não autentica (401, o Player para). Token de aparelho não abre a API do painel e vice‑versa (403).
  - Abuso: registros pendentes com mais de 24 h são apagados a cada novo registro; teto de 100 pendentes (429).
  - Isso resolve a "limitação temporária" do passo 10: o Player não precisa mais de JWT de admin.
- **Heartbeat (passo 15)**: `POST /player/heartbeat` registra presença e a versão que o Player *de fato sincronizou* em `devices.config_version`; responde com a playlist/versão desejadas. `online` = último contato há ≤ 2 min (heartbeat a cada 30 s).
- **Armazenamento de mídia plugável**: interface `MediaStorage` com `LocalMediaStorage` (disco) e `S3MediaStorage` (R2, B2, Supabase Storage, MinIO). Motivo: hosts gratuitos têm disco efêmero e banda limitada. Com S3, `GET /media/{id}/file` responde **302 para uma URL assinada** e o arquivo não passa pelo backend. AWS SDK v2 só com `url-connection-client` (sem apache/netty) por causa dos 512 MB. `requestChecksumCalculation=WHEN_REQUIRED` porque o CRC32 em trailer que o SDK novo liga por padrão quebra uploads em vários provedores S3-compatíveis.
  - **Não testado contra um S3 real** (sem Docker/MinIO aqui): a assinatura de URL e o 302 são testados; o `putObject` só será exercitado no deploy.
- Configuração para hosts gratuitos: `PORT`, `DB_URL` completo (Supabase/Neon), pool de conexões pequeno (`DB_POOL_SIZE=5`), `CORS_ALLOWED_ORIGINS`. Dockerfile com JVM ajustada para 512 MB (`MaxRAMPercentage=60`, SerialGC). Medição local: ~270 MB residentes com heap de 300 MB após upload de 40 MB — cabe, com folga curta; **não medido no Render**.
- `DevApplication` (em `src/test`): sobe o backend completo com Postgres embutido persistente, para desenvolver sem Docker.

### Painel (frontend)

- Angular 21, componentes standalone, signals, **zoneless** (campos comuns não disparam renderização — usar signals). URL da API lida de `public/config.json` em tempo de execução (deploy sem recompilar). JWT só é enviado para a URL da API.
- Escopo do MVP: login, dashboard, dispositivos (ativar por código), biblioteca (upload com progresso; duração de vídeo lida no navegador, conforme decisão anterior), editor de playlist. Campanhas/Locais/Relatórios/Configurações do protótipo ficam fora.
- Limitação conhecida: não dá para *remover* a playlist de um dispositivo (a API trata `currentPlaylistId` ausente como "não mudar"). Dá para trocar por outra.
- Conferido no navegador contra o backend real: login (CORS ok), dados em todas as telas, ativação de dispositivo pelo modal.

### Player

- Lógica concentrada em classes testáveis na JVM (`PlayerEngine`, `ConfigSync`, `MediaCache`, `HttpPlayerApi`); `MainActivity` e `Slideshow` ficam finos. 39 testes, incluindo MockWebServer e tempo virtual de corrotinas.
- Regras que viraram testes: troca atômica (download falho/corrompido não derruba a playlist atual); cache nomeado por checksum e validado por SHA‑256 + tamanho; **offline‑first** (toca o cache imediatamente e continua tocando sem rede); dispositivo excluído → esquece conteúdo e volta à ativação; **token nunca enviado ao host de mídia** em redirecionamento (redirects seguidos à mão).
- Servidor configurável sem recompilar: extra `server_url` via `adb`, diálogo com OK no controle, ou `-PserverUrl` no build. APK debug permite `http://` (rede local); release exige `https://`.
- **Não validado em hardware** (sem emulador nesta máquina): reprodução real, tela cheia, boot. Em Android 10+ o início automático pode exigir a permissão "exibir sobre outros apps" (declarada no manifest).

### Deploy (passo 19 — guia, não execução)

- [docs/DEPLOY.md](./DEPLOY.md): Fase A (TCL na rede local), B (túnel Cloudflare), C (Render + Supabase + Cloudflare Pages, tudo gratuito), C‑alt (Oracle), Fire Stick.
- Pesquisa de planos (out/2026) que moldou a escolha: Fly.io sem plano gratuito para contas novas; Koyeb exige cartão; Render gratuito dorme em 15 min (o heartbeat mantém acordado), 512 MB, sem disco persistente, 5 GB de banda; Neon gratuito estouraria as 100 h/mês de compute com heartbeat constante (conta nossa: 0,25 CU × 24 h × 30 d ≈ 180 h); Supabase gratuito tem 50 MB por arquivo; o banco deve ser acessado pelo *session pooler* (IPv4) porque o Render não alcança IPv6.

## 2026-10-20 — Primeira execução do Player (emulador Android TV) e achados

- **Emulador instalado**: AVD `signage_tv` (Android 14 TV, x86, WHPX). A imagem ocupou **~8,1 GB** em disco (a estimativa inicial de ~3 GB estava errada); o disco da máquina ficou com ~8 GB livres. As ferramentas de linha de comando do SDK foram instaladas manualmente (`cmdline-tools/latest`, SHA‑1 conferido); o `sdkmanager` agora é só um atalho para a nova "Android CLI" (`android sdk install system-images/android-34/android-tv/x86`).
- **Backend em Docker pela primeira vez**: a imagem construiu e subiu (Postgres 16.15 real, ~217 MB de RAM no contêiner). Até aqui o Dockerfile nunca tinha sido construído.
- **Verificado no emulador, contra o backend em Docker**: registro e exibição do código; ativação; sincronização de playlist em ≤ 45 s; imagens JPEG Full HD em tela cheia, em rotação; vídeo MP4 H.264 (ExoPlayer) tocando até o fim e voltando ao loop; troca para a v2 da playlist; **app morto e reaberto com o backend parado → toca o cache na hora**; reboot da TV → Player volta sozinho.
- **Bug real achado pelo emulador — início automático**: o `BootReceiver` era acionado, mas o Android 14 bloqueava abrir a Activity (`BAL_BLOCK`), **mesmo com `SYSTEM_ALERT_WINDOW` concedida**. A permissão só vale se o app tiver uma janela de overlay *visível*; o receiver agora cria um overlay invisível de 1 px, abre a Activity e remove o overlay (`BAL_ALLOW_VISIBLE_WINDOW` no log). Não há teste automatizado possível (depende do sistema); o procedimento de verificação está em docs/DEPLOY.md (Fase A0).
- Pegadinha do emulador: o `appops` não é persistido num `adb reboot` sem `cmd appops write-settings`. Em TV real isso não deve ser necessário.
- Deploy: `render.yaml` (Blueprint) e `API_BASE_URL` lida no build do painel (`frontend/scripts/write-config.mjs`), para a URL do backend não precisar de commit.

## 2026-10-20 (parte 3) — Nuvem no ar, endurecimento, logo, distribuição do APK e atualização automática

### Nuvem
- Backend no Render (Docker, plano gratuito, Oregon), banco e mídias no Supabase (projeto numa organização própria, plano Free), painel no Cloudflare Pages — tudo conectado e verificado de ponta a ponta com o emulador.
- **Região do Render (Oregon) não muda**: mudar exige recriar o serviço (URL nova, segredos de novo). Custo da distância ≈ 70 ms por consulta ao banco; irrelevante para uma TV que faz um heartbeat a cada 30 s. Decidido **não** recriar.
- Três falhas reais do primeiro deploy (todas na tabela "Problemas comuns" do DEPLOY.md): `DB_URL` não chegou (valores `sync: false` do Blueprint ficam em branco se não preenchidos → o app cai no padrão `localhost:5432`), senha do banco ausente (usa o padrão `changeme`), e o Flyway recusando um schema `public` não vazio (a opção "RLS automático" do Supabase cria uma função ali) — resolvido com `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` + `BASELINE_VERSION=0`, reproduzido e validado em Postgres 16 real antes de aplicar.
- Pegadinha da `wrangler` nova (4.151): ao falhar, ela **altera arquivos do projeto onde roda** (`package.json`, `package-lock.json`, `.gitignore`, cria `wrangler.jsonc`) e reconstrói a pasta de saída sem as variáveis, o que publicou um painel apontando para `localhost`. Passou-se a rodá-la de uma pasta fora do repositório e a conferir o `config.json` de `dist` antes de cada publicação. Projetos do Pages precisam de `--force` na criação (a CLI tenta delegar ao Workers).

### Endurecimento do backend
- **`REQUIRE_STRONG_SECRETS=true`**: o backend recusa subir se `JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` ou `POSTGRES_PASSWORD` estiverem nos padrões de desenvolvimento (só os *nomes* aparecem na mensagem). Testado sem Spring completo (`ApplicationContextRunner`). Desligado por padrão para não atrapalhar o desenvolvimento local.
- **`DELETE /devices/{id}/playlist`**: tirar a playlist de uma TV (o `PUT` com `currentPlaylistId` ausente significa "não mudar"). O Player vai para "Aguardando conteúdo" e libera o cache (teste de regressão no motor).

### Identidade visual
- Logo único (TV com "play"), usado como favicon, na barra lateral e no login do painel, como ícone do app e como banner da tela inicial do Android TV (verificado no launcher do emulador).

### Distribuição e atualização do APK (sem Google Play)
- **Site de downloads separado do painel** (`signage-downloads`): um deploy do painel nunca apaga os APKs. Contém a lista de versões, `latest.json` (o que o Player consulta), atalho curto `/d` e os manuais em HTML. `scripts/release-player.mjs` compila, assina, **recusa publicar se a chave de assinatura mudou**, reconstrói o site a partir do próprio site (confere o SHA‑256 das versões antigas) e confere o que ficou no ar.
- **Chave de assinatura** em `~/.signage` (senha ao lado, nunca no git). Perdê-la impede atualizar por cima das TVs já instaladas.
- **Atualização automática verificada no emulador (Android 14)**: o Player consulta `latest.json`, baixa, confere SHA‑256 e versionCode, instala pelo PackageInstaller e **volta sozinho** (receiver de `MY_PACKAGE_REPLACED`). Medido:
  - **O Android sempre pede confirmação** ("Deseja atualizar este app?", foco inicial em *Cancelar*), mesmo depois de o Player virar o instalador do app. Testei a hipótese de que ficar em segundo plano dispensaria o OK: **falso** (o modo foi implementado, medido e removido).
  - Para a TV nunca ficar presa na janela do instalador: sem confirmação em 10 min o Player **cancela a instalação e volta a exibir** (verificado no emulador). Janela de horário opcional (`update_window`).
  - Atualização **realmente silenciosa** só com aparelho gerenciado (proprietário do dispositivo/MDM) ou root.
- Sem a permissão "instalar apps desconhecidos" o Player não baixa nada e registra o motivo (aparece na tela de aviso).
- O Player lê o `latest.json` sem enviar nenhuma credencial do aparelho.

### Descoberta no emulador
- `MY_PACKAGE_REPLACED` reabre o Player depois da atualização; o truque do overlay de 1 px (já usado no boot) foi extraído para `LaunchHelper` e reaproveitado.

## 2026-10-20 (parte 4) — Erros tratados, downloads e manuais no painel, atualização remota e compatibilidade

### Erros tratados nos dois clientes
- **Painel:** todo erro (HTTP, timeout de 120 s, exceção não tratada) vira uma mensagem em português ("Ação: o que aconteceu") numa faixa que **fica até clicar em Fechar**. Mensagens idênticas não se empilham, e depois de fechada a mesma não volta por 60 s. Pegadinha do RxJS: o `timeout` do interceptor nunca disparava porque o `HttpClient` emite o evento `Sent` primeiro; filtra-se só a resposta (achado pelos testes).
- **Player:** os erros são traduzidos (`ErrorMessages`) e aparecem numa faixa vermelha por **10 segundos** (`ErrorReporter`), sem interromper a exibição. A mesma mensagem não repete por 5 min (com a internet fora, o Player tenta a cada poucos segundos e a faixa não pode ficar fixa). Quedas inesperadas são contadas na abertura seguinte. Verificado no emulador (sumiço da faixa medido por amostragem de pixels).

### Downloads e manuais sempre atualizados
- O painel tem a página **Downloads e manuais** (lê `releases.json` do site de downloads, que agora envia `Access-Control-Allow-Origin: *`) e destaca na lista de dispositivos as TVs com versão anterior à mais recente.
- **Manuais em PDF ilustrados** (instalação na TV e uso do painel) gerados no próprio fluxo de publicação: Markdown → HTML (`marked`) → PDF pelo Edge/Chrome em modo headless. Ilustrações: **capturas reais** (painel via Edge headless com dados de exemplo; TV via `adb screencap` do emulador, incluindo as telas do Android) e **esquemas em SVG** gerados por script para o que não dá para fotografar (menus de TCL/Fire TV, controle remoto, fluxos). Os esquemas são rotulados como ilustrativos: só a linha destacada traz o nome real da opção, o resto são barras cinzas, para não inventar telas que não foram vistas. Nenhuma captura de Fire TV foi simulada.
- Versão automática (`versionCode` = contagem de commits, `versionName` = `player/VERSION`.código) e publicação automática por GitHub Actions, condicionada aos testes e à existência dos segredos (o fluxo pula sem erro enquanto faltarem). Decisão consciente: **não** enviei a chave de assinatura ao GitHub por conta própria; o script `configurar-ci.mjs` faz isso quando o dono rodar.

### Atualização remota
- `POST /devices/{id}/update-check` e `POST /devices/update-check` gravam o instante do pedido (`update_requested_at`, migração V2); o heartbeat devolve `updateCheckToken`. A TV só reage a valores novos (guarda o último tratado), ignora a janela de horário nesse caso e consulta o site na hora. Campo opcional: Players antigos e backends antigos continuam compatíveis. Verificado no emulador: o pedido chegou, o Player baixou a versão 0.4.5, o Android pediu confirmação, e depois do OK o Player voltou sozinho já na versão nova (o painel mostrou `0.4.5`).
- O intervalo de verificação caiu de 6 h para 30 min. Enquanto uma instalação espera confirmação (até 10 min) o Player não inicia outra.
- **Achado:** no Android 14 o diálogo "não é permitido instalar apps desconhecidos desta fonte" pode aparecer mesmo com a permissão concedida por `appops` via adb; ele se corrige ao abrir a tela de permissão (que também serve de ilustração no manual). Com a permissão dada pelos menus da TV isso não deve ocorrer; fica como ponto de atenção na TV real.
- **Descartado:** modo "proprietário do dispositivo" para atualização silenciosa. Exige aparelho sem contas Google/de fábrica (TVs TCL e Fire TV configuradas já têm), então não é viável para o caso de uso.

### Compatibilidade (versões do APK para todos os Androids)
- **Um único APK universal** (minSdk 21, sem código nativo: nenhuma pasta `lib/` no APK) atende Android 5.0 ao 14 e qualquer processador. Lint de release sem erros e sem avisos de API (`NewApi`). Não há motivo para vários APKs; o que muda entre aparelhos é comportamento do sistema, documentado no anexo do manual.
- **Achado real (testado em emulador Android 5.1 TV):** o site de downloads usa certificados Let's Encrypt (cadeia terminando na *ISRG Root X1*), que o Android anterior ao 7.1.1 não conhece; o Player antigo falhava com "Trust anchor for certification path not found" e **nunca conseguiria se atualizar** em Fire OS 5 / TV boxes antigos. Correção: `TlsTrust` acrescenta a ISRG Root X1 (impressão digital conferida) às raízes do sistema, sem aceitar mais nada (testes: aceita cadeia que termina só na raiz extra; recusa autoridade desconhecida e certificado autoassinado). Depois da correção o mesmo emulador baixou a versão do site e abriu o instalador. O backend do Render usa Google Trust Services (cadeia cruzada com a GlobalSign) e já funcionava. Ainda não verificado: o app **Downloader** em Android < 7.1.1 (usa o repositório de certificados do aparelho; o manual manda usar o método ADB se der erro).
- Verificado no emulador Android 5.1: ativação, download e exibição da playlist, diálogo do instalador. Emulador instalado: AVD `signage_tv22` (imagem de ~1,4 GB).

## 2026-10-20 (parte 5) — Fase 2: multi-dispositivo (locais, grupos, status, ações em massa)

### Modelo
- **Local** (`locations`): ponto físico; cada TV tem no máximo um (`devices.location_id`, `ON DELETE SET NULL`: apagar o local **nunca** apaga a TV). Nome único sem diferenciar maiúsculas/minúsculas (índice em `lower(name)`; a API responde 409 com mensagem legível, não o erro genérico de integridade).
- **Grupo** (`device_groups` + `device_group_members`): conjunto livre, uma TV pode estar em vários; `ON DELETE CASCADE` nas duas pontas, de modo que apagar grupo ou TV só desfaz o vínculo. A associação é mapeada em `Device.groupIds` (`@ElementCollection` com `@BatchSize`), o que evita N+1 na lista.
- **Decisão: grupo e local não "herdam" playlist.** A playlist continua sendo da TV (`current_playlist_id`), e "aplicar a todas as TVs do grupo/local" grava a playlist em cada TV. Motivo: o Player e o heartbeat não mudam (nenhum risco para as TVs já em campo) e o comportamento é previsível. A herança/regra por segmento fica para a fase 3 (campanhas, segmentação).

### Migração V3 com dados reais
- Antes, o local era texto livre em `devices.location`. A V3 cria um local para cada nome distinto (sem diferenciar maiúsculas/minúsculas e espaços nas pontas; vale a grafia da TV mais antiga), aponta as TVs e só então apaga a coluna. Testada de duas formas: teste automatizado que cria o banco **no estado da V2**, insere TVs com grafias diferentes, aplica a V3 e confere (nenhuma TV se perde; espaços em branco não viram local), e uma execução sobre o banco de desenvolvimento que já tinha TVs de testes anteriores.
- **Compatibilidade de API:** a resposta da TV continua trazendo `location` (o nome) e a criação/edição aceita o local por nome (acha o existente ou cria), então o fluxo de ativação ("local Loja Centro") e o painel antigo continuam funcionando durante o deploy. Na edição, campo de local ausente = "não mudar" (antes limpava), e `location: ""` = sem local.

### Situação das TVs
- Uma única regra no servidor (`DeviceConnection`): ONLINE (ativa, contato nos últimos 2 min), OFFLINE, NEVER (ativa que nunca falou), PENDING, DISABLED. O painel exibe o que o servidor diz (com um fallback para respostas antigas) e o filtro de situação roda no banco.

### API
- `GET /devices` com filtros combináveis (`connection`, `locationId`/`noLocation`, `groupId`, `q`); a busca trata `%` e `_` como letras comuns (teste de regressão).
- `POST /devices/bulk`: uma ação (playlist, remover playlist, ativar/desativar, pedir atualização, mover/limpar local, entrar/sair de grupo) sobre **exatamente um alvo** (lista de TVs, grupo ou local). Uma TV inexistente na lista cancela a ação inteira (nada fica pela metade, testado). Cada ação só alcança as TVs a que se aplica e devolve `affected`.
- `GET /summary`: contagens por situação, TVs por local e lista de atenção (offline → nunca conectou → aguardando ativação; desativadas não alertam), no servidor.

### Painel
- Novas páginas **Pontos & Locais** e **Grupos**; **Dispositivos** ganhou busca, filtros (também por link vindo do Dashboard/Locais/Grupos), seleção e barra de ações em massa; o **Dashboard** passou a ser alimentado por `/summary`.
- Achado pelas capturas de tela reais (não pelos testes): o CSS global fazia todo `input`/`select` ocupar a linha inteira, o que empilhou os filtros e jogou as caixas de marcar acima do texto; corrigido (caixas de marcar com largura automática, rótulo ao lado).

## 2026-10-20 (parte 6) — Fase 3: campanhas (agendamento, período, horários, prioridade, segmentação)

### Decisão central: a agenda é resolvida **no servidor**, a cada heartbeat
- Uma campanha é uma playlist + agenda + prioridade + alvos. Cada TV continua tendo a sua playlist base; **uma campanha ativa agora e que alcança a TV substitui a base**, e sem campanha a TV exibe a base. `PlayerConfigService` passa a devolver a playlist efetiva, e o heartbeat (que já devolvia `playlistId` + `configVersion`) faz o Player trocar sozinho. **O Player não muda** (nenhuma versão nova do APK e nenhum risco para TVs já instaladas).
- Custo assumido e documentado: a troca depende de a TV estar online no horário (ela descobre em até 30 s e baixa a playlist); sem internet, continua exibindo o que tem e troca ao reconectar. A alternativa (o Player receber a agenda inteira, pré-baixar as mídias e trocar pelo relógio do aparelho) é mais robusta offline, mas exige mudança grande no Player, no cache e no protocolo, e só se justifica quando houver TVs reais com internet instável; fica anotada como evolução possível, não como pendência.

### Regras de agenda (`CampaignSchedule`, puras e testadas com horas fixas)
- Período com as duas datas **inclusivas**; dias da semana ISO (1 = segunda); janela diária com início inclusivo e fim **exclusivo**; fuso **por campanha** (padrão America/Sao_Paulo), nunca UTC (teste de regressão de fuso).
- **Janela que atravessa a meia-noite** (fim < início) conta pelo dia em que **começa**: sexta 22:00–06:00 vale de sexta 22:00 a sábado 06:00; uma campanha cujo último dia é sexta ainda termina a janela de sexta no sábado de madrugada, e a janela de sábado não começa. Início = fim é recusado (ambíguo); para o dia todo, deixam-se os dois vazios.
- **Desempate** (nunca ambíguo): maior prioridade; depois alvo mais específico (TV > grupo > local > todas); depois a campanha mais nova. A prioridade vence a especificidade de propósito (o administrador decide).
- O relógio é um bean (`Clock`) para os testes congelarem o tempo.

### Modelo e integridade
- `campaigns` + `campaign_targets` (V4). Alvos em colunas separadas com chave estrangeira própria (todas / TV / local / grupo, `CHECK` de exatamente um por linha): apagar a TV, o local ou o grupo remove o alvo sozinho e a campanha continua existindo (alcançando menos TVs). `playlist_id` é `RESTRICT`: apagar uma playlist usada por campanha é recusado com mensagem que diz qual campanha a usa.
- API: CRUD, `GET /campaigns/preview?deviceId=&at=` (o que a TV exibiria num momento: CAMPAIGN/BASE/NONE), e as respostas de TV trazem `effectivePlaylistId` e a campanha vencedora. O resumo do dashboard conta campanhas ativas agora.
- **Bug achado pelo teste de integração do heartbeat:** com `open-in-view` desligado, ler os grupos da TV fora de transação falhava (`LazyInitializationException`); os grupos passaram a ser carregados junto com a TV.

### Painel
- Página **Campanhas** (lista com situação — ativa agora, aguardando horário, agendada, encerrada, pausada —, editor com dias/horário/fuso/alvos e **simulador**), rótulo "Agora: campanha X" em cada TV e o contador no dashboard. Validação no formulário (mensagens em português) e de novo no servidor.


## 2026-10-20 (parte 7) — Fase 4: monitoramento (histórico, logs, relatórios e alertas)

### Decisão central: o servidor conta a história; o Player só relata o que só ele sabe
- Tudo que o **servidor** percebe sozinho vira evento: ativação, TV que volta a se conectar, TV que **parou de falar**, desativar/reativar, troca de playlist/campanha, versão nova do Player, pedido de atualização. O Player só relata o que o servidor não tem como saber: **erros e avisos** (mídia que não abre, download que falha, reinício após falha), a **mídia que está na tela** e **quantas vezes cada mídia foi exibida**. Isso vai **dentro do heartbeat que já existia** (campos novos, todos opcionais): nenhuma rota nova no Player e Players antigos (0.4.x) continuam funcionando, só sem esses dados.
- **Queda detectada pelo servidor, não pelo Player** (uma TV sem energia/internet não consegue avisar): a cada minuto o servidor percebe quem passou de 2 min sem falar e grava `OFFLINE` **datado em último contato + 2 min** (e não "quando percebi"). Se o servidor ainda não tinha percebido quando a TV volta (ex.: o próprio servidor estava parado), o heartbeat grava a queda de forma retroativa, para a linha do tempo ficar completa. Cada TV guarda o último estado gravado (`recorded_connection`) para nunca repetir evento.
- **Desativada não conta como queda**: o tempo desativada não entra na disponibilidade (nem como online, nem como offline) e fecha os alertas abertos.

### Modelo (V5)
- `device_events` (história), `alerts` (índice único parcial: **no máximo um alerta aberto por TV e tipo**), `monitoring_settings` (linha única, com `CHECK` nos limites) e `media_plays` (exibições por TV, mídia e **dia no fuso do relatório**, acumuladas com `ON CONFLICT DO UPDATE`). Apagar a TV apaga a história dela (cascata); apagar uma mídia não derruba o heartbeat que a cita (a contagem dessa mídia é descartada).
- Guarda do histórico configurável (padrão 90 dias, 7–730) e limpeza de hora em hora; alertas resolvidos saem junto.

### Alertas
- Três tipos: **TV sem contato** (padrão: 10 min), **TV ativada que nunca se conectou** (24 h) e **erros repetidos no Player** (5 erros em 10 min). Abre quando a condição vale, **fecha sozinho** quando deixa de valer; uma nova queda abre um alerta novo. Os limites são configuráveis no painel.
- **Webhook** (Slack/Discord/Teams/Zapier): `POST` com `text` (Slack) e `content` (Discord) iguais, mais `event` e os dados do alerta. Regras de segurança:
  - **só `https://`** e **nunca endereços internos** (localhost, 10.x, 192.168.x, 172.16–31.x, 169.254.x/metadados de nuvem, 100.64.x, IPv6 local): o servidor não pode ser usado para varrer a rede interna (SSRF); a regra vale também para o nome do host (resolvido) e **não segue redirecionamentos** (o nome é resolvido ao salvar e de novo ao enviar; um DNS malicioso que trocasse a resposta entre os dois momentos não é coberto, risco aceito porque só o administrador configura o endereço);
  - o endereço é um segredo (a URL do Slack dá acesso a postar): **nunca volta do servidor** — o painel recebe só o host e os 4 últimos caracteres;
  - falha de entrega é repetida a cada minuto e **abandonada depois de 1 h** (um webhook quebrado não acumula tentativas para sempre); botão "Enviar teste" no painel.
  - Em testes o servidor aceita `http` local (`app.monitoring.allow-insecure-webhooks`); em produção a opção não existe.

### Relatórios (todos com CSV)
- **Disponibilidade** (online ÷ observado por TV e da frota, quedas), **exibições** (por mídia, TV ou dia), **inventário** das TVs e **eventos**. Datas no fuso do relatório (America/Sao_Paulo), `to` inclusivo, período máximo de 366 dias.
- Disponibilidade só conta o tempo **observado** (a partir do primeiro evento da TV): uma TV nova não nasce com 0% por "não ter existido antes", e a TV sem nenhum dado mostra "sem dados", nunca "0%".
- CSV pensado para o Excel brasileiro: BOM UTF-8, separador `;`, vírgula decimal (fixa em pt-BR, não depende da configuração do servidor), e **proteção contra injeção de fórmula** (célula que começa com `=`, `+`, `-`, `@` ganha um apóstrofo; as mensagens dos erros vêm da TV e não são confiáveis).

### Player 0.5
- Fila de ocorrências em memória (`Telemetry`): mesma mensagem não se repete em 5 min (internet fora gera erro a cada tentativa), máximo de 100 pendentes (as mais antigas saem) e **entrega em duas etapas** (copia → servidor confirma → só então tira da fila; o que chegou no meio do caminho fica). Todo erro mostrado na tela (`ErrorReporter`) também vai para o histórico da TV.
- **Bug evitado por projeto:** o servidor recusa o heartbeat inteiro (HTTP 400) se o relato passar dos limites. Sem cuidado, um relato recusado seria reenviado para sempre e a TV pareceria offline. O Player trunca mensagens em 500 caracteres, respeita 30 ocorrências/100 mídias por heartbeat e **descarta o relato que o servidor recusar**.
- Exibição só é contada quando a mídia **termina** (parar no meio não conta); vídeo único em repetição conta cada volta. As contagens ficam na memória: se a TV reiniciar entre dois contatos, perde-se no máximo ~30 s de contagem.
- Verificado no emulador Android TV 14 contra o backend novo: ocorrências, mídia na tela e exibições chegaram; uma queda de 3 min virou `OFFLINE` + `ONLINE ("voltou depois de 3 min sem contato")` e o erro registrado durante a queda foi entregue depois.
- Os horários dos eventos do Player vêm do **relógio da TV**; o servidor os troca pelo horário dele quando passam de 5 min no futuro ou de 7 dias no passado (TV com relógio errado não bagunça o histórico). Não há correção fina de pequenos desvios (anotado como evolução possível).

### Painel
- **Alertas** (lista com abertos/resolvidos + limites + webhook com teste), **Relatórios** (4 abas, filtros e CSV baixado com o login do painel), **Histórico** por TV (linha do tempo com filtro de tipo e nível e "carregar mais"), "Na tela: …" em cada TV e o contador de alertas abertos no dashboard.


## 2026-10-20 (parte 8) — Fase 5: layouts com zonas, modelos, QR Code e conteúdo web

### Layout = divisão da tela em zonas, cada uma com a sua lista
- Uma **zona** é um retângulo em **% da tela** (inteiros, 0–100), com nome e, se não for a principal, uma **playlist fixa**. A **zona principal** exibe a playlist da TV — e é só ela que as **campanhas** substituem. Assim a Fase 3 (campanhas) e a Fase 5 não se atropelam: campanha = "o que passa na zona principal"; o resto do layout é permanente.
- Regras no servidor (`LayoutValidator`, pura e testada): 1 a 6 zonas, **exatamente uma principal**, dentro da tela, mínimo de 5% por lado, **sem sobreposição** (bordas que só se tocam valem), nomes diferentes, principal sem playlist fixa. O painel repete as regras para avisar na hora; o servidor continua sendo quem decide. A regra "uma só principal" **não** é um índice único parcial: o Hibernate insere as zonas novas antes de apagar as antigas ao regravar o layout, e o índice quebraria a edição (achado pelos testes).
- **Seis modelos prontos** (tela cheia, principal + lateral, principal + rodapé, principal + lateral + rodapé, duas metades, quatro quadros) em código (`LayoutTemplates`): "templates" da fase 5 sem tabela nova; `POST /layouts/from-template` cria o layout e o painel abre o editor para o ajuste fino.
- `devices.layout_id` com `ON DELETE RESTRICT`: excluir um layout em uso é recusado com mensagem (como playlist em campanha). Sem layout a TV é tela cheia, **exatamente como antes** (nenhuma TV existente muda de comportamento).

### Protocolo: compatível com Players antigos
- `GET /player/config` ganha `zones[]` (cada uma com `items`); o `items` de cima **continua sendo a zona principal**, então um Player 0.5 ou anterior mostra a principal em tela cheia em vez de quebrar. O heartbeat não muda de formato.
- **Versão do conteúdo:** o Player compara `playlistId + configVersion` por igualdade. Sem layout é a versão da playlist, como sempre. Com layout o conteúdo depende da playlist principal, do layout e das playlists das outras zonas, então `configVersion` é uma **combinação determinística** (hash de 53 bits, cabe em JavaScript) que muda se qualquer parte mudar. Achado por teste: duas playlists recém-criadas têm a mesma versão (1); sem o id da principal na conta, uma campanha trocando uma pela outra **não** mudaria a versão. O mesmo cálculo (`versionFor`) alimenta o heartbeat, o `GET config` e a lista de TVs, e um teste garante que os três concordam. O painel passou a mostrar "sincronizando…" quando `configVersion ≠ desiredConfigVersion` (antes comparava com `<`, que não serve para um hash).
- Mudança de layout gera evento `LAYOUT_CHANGED` no histórico da TV (só quando muda de fato).

### Conteúdo web
- Mídia do tipo `WEB`: guarda só o **endereço** (`media.web_url`; `storage_key` passou a aceitar nulo). Só **https** (o Player de produção não abre http), sem usuário/senha na URL, até 2000 caracteres, de 5 s a 1 h. `GET /media/{id}/file` de uma página responde 400 claro; o `url` do item na configuração é a própria página, que o Player abre direto (sem baixar nem validar checksum).
- Player: `WebView` (JavaScript e DOM storage ligados — é a página do próprio administrador —, **sem acesso a arquivos do aparelho**, sem zoom, sem foco do controle remoto). A página que não abre em 30 s, ou responde erro, é **pulada** com mensagem que vai para o histórico da TV. Sem internet a TV não exibe páginas (nada é guardado em cache): documentado no manual.
- **Bug achado no emulador:** com o cache já pronto, as zonas começavam a tocar antes de o layout da tela medir o tamanho delas; uma página aberta num quadro de 1 pixel guardava a escala errada e aparecia ampliada. Agora as zonas só começam **depois de dimensionadas**.

### QR Code: gerado no navegador, sem mudar servidor nem Player
- O QR Code é uma **imagem comum** (PNG 1080×1080: título, cartão claro com os módulos escuros e legenda) desenhada num canvas no painel (biblioteca `qrcode-generator`, MIT, sem dependências) e enviada como qualquer upload. Motivos: o servidor (Render, imagem enxuta) pode não ter fontes para desenhar texto com Java2D; o Player não precisa conhecer um tipo novo; e a pessoa vê a prévia antes de salvar. Sempre **escuro sobre claro** (contraste exigido pelos leitores) com zona de silêncio de 4 módulos. Verificado: o QR Code **capturado da tela do emulador** foi decodificado por uma biblioteca independente (jsQR) e devolveu exatamente o endereço digitado.

### Player 0.6
- `ZonePlayer` (a lista de uma zona: imagem, vídeo, página) e `Slideshow` (orquestra as zonas): sem layout (ou com uma zona em tela cheia) usa as views da própria tela, como antes; com layout monta uma área por zona (`ZoneGeometry` converte % em pixels arredondando **cada borda** — zonas vizinhas se encostam sem fresta nem sobreposição em qualquer resolução, testado em 1366×768, 1280×720, 3840×2160…).
- Só a **zona principal** diz ao servidor "o que está na tela" e toca **áudio**; todas as zonas contam exibições. Cache e sincronização cobrem as mídias de **todas** as zonas (a mesma mídia em duas zonas baixa uma vez) e limpam as que nenhuma zona usa mais. Config sem zonas (servidor antigo) vira uma zona principal em tela cheia.
- Limite conhecido, documentado: dois **vídeos** ao mesmo tempo dependem do hardware (decodificadores). Recomendação no manual: vídeo numa zona só.
- Verificado no emulador Android TV 14: principal 70% + lateral 30% (playlist fixa com QR Code e página web), troca de itens por zona, telemetria (mídia na tela, exibições, evento de layout, versão do Player).

## 2026-10-20 (parte 9) — Fase 6: SaaS (clientes, papéis, planos e cobrança)

### Multi-tenant: um banco, uma coluna `tenant_id`, isolamento automático
- Cada empresa cliente é um **tenant**. As nove tabelas de negócio (TVs, mídias, playlists, locais, grupos, campanhas, layouts, eventos e alertas, e as configurações de monitoramento) ganharam `tenant_id` e o Hibernate 6.6 filtra tudo sozinho com **`@TenantId`**: uma consulta esquecida de filtrar **não vaza** dados de outro cliente. O cliente "da vez" vem de um `TenantContext` (ThreadLocal) preenchido pelo filtro de autenticação; sem contexto, o padrão é o tenant 1 (o "reservatório"), que não tem dado nenhum de ninguém.
- **Onde o SQL nativo é intencional:** achar a TV pelo hash do token (a TV ainda não sabe a que cliente pertence), a ativação (a TV nasce no reservatório e **muda para o cliente de quem digita o código**), contagem de uso e limites, faturas e relatórios/expurgo (sempre com `tenant_id` explícito na consulta). `TenantIsolationTest` prova o isolamento nos dois sentidos (A não vê, não altera e não apaga o que é de B) em todas as áreas, inclusive alertas e monitoramento.
- **Migração V7:** cria tenants, papéis e planos; o tenant 1 é o reservatório e o tenant 2 ("Minha empresa", plano interno sem limites) recebe **todos os dados que já existiam**; os usuários existentes viram **PLATFORM_ADMIN do tenant 2**. Em produção isso significa que o administrador atual passa a ser o operador da plataforma e nada muda no que ele já via. **V8:** faturas e configurações da plataforma. Migrações já aplicadas não são editadas (checksum do Flyway).

### Papéis: hierarquia e quem decide
- `VIEWER < EDITOR < ADMIN < PLATFORM_ADMIN`: cada papel tem as autoridades dos menores. A matriz de acesso fica no `SecurityConfig` (consultar = Visualizador; mídias, playlists, campanhas, layouts e escolher playlist/layout das TVs = Editor; o resto = Administrador; `/platform/**` = operador). **O papel e o cliente são lidos do banco a cada requisição** (o JWT só leva o e-mail): rebaixar, desativar ou suspender vale na hora, sem esperar o token expirar.
- O que a matriz de URLs não enxerga (o corpo da requisição) é conferido no serviço: o **Editor** pode escolher playlist e layout de uma TV, mas nome, local, grupos e situação são do Administrador (`ForbiddenException` com mensagem clara), e nas ações em massa só as de playlist e layout. O painel esconde os botões que o papel não pode usar, mas **isso é conforto, não segurança**: quem decide é sempre o servidor.
- **Equipe:** o Administrador gerencia usuários do próprio cliente (papéis Visualizador, Editor e Administrador); não exclui nem desativa a si mesmo e o cliente **sempre mantém ao menos um administrador ativo**. O papel de operador só é dado e alterado pelo próprio operador. Qualquer pessoa troca a própria senha (`PUT /auth/password`, exige a atual).
- **Operador atuando num cliente:** o cabeçalho `X-Tenant-Id` só tem efeito para `PLATFORM_ADMIN` (para os demais é ignorado) e o painel o guarda **na aba** (`sessionStorage`): outra aba abre de volta no cliente do operador, e há um aviso azul permanente "Você está atuando no cliente …".

### Planos e limites
- Plano = preço mensal + limites de **TVs ativadas, espaço de mídias (MB) e usuários ativos**; limite nulo = ilimitado. Sementes: interno (sem limites; usado pelo tenant 2), grátis (2 TVs, 200 MB, 2 usuários), básico (R$ 49: 5 TVs, 2 GB, 5 usuários) e profissional (R$ 149: 25 TVs, 10 GB, 20 usuários). O operador cria, edita, desativa e (sem clientes) exclui planos.
- Passar de um limite responde **402 com a explicação e a saída** ("exclua uma TV ou peça um plano maior"). Limites só barram o **acréscimo**: nada que já existe é apagado ou desligado por causa de um limite (rebaixar de plano não derruba TV nenhuma).

### Cobrança: faturas e baixa manual, sem gateway de pagamento
- Estados do cliente: `TRIAL` (teste, 14 dias por padrão) → `ACTIVE` → `PAST_DUE` (fatura vencida, continua usando) → `SUSPENDED` (passou a tolerância, 7 dias por padrão) → `CANCELED`; `SYSTEM` é o reservatório. Faturas: `OPEN`/`PAID`/`VOID`, numeração por sequência (`2026-000001`).
- **`BillingJob` roda a cada hora** (e `POST /platform/billing/run` roda na hora). Regras (`BillingService`, idempotente): plano grátis nunca gera fatura; no fim do teste emite a primeira; com o cliente em dia emite a do próximo período `invoiceLeadDays` (5) antes do fim; fatura vencida → `PAST_DUE`; vencida além da tolerância → `SUSPENDED`; pagamento baixa a fatura, estende o período e, sem outra vencida, volta a `ACTIVE`. Achado por teste: quem está em atraso **sem nunca ter pago** ganhava uma fatura nova a cada ciclo; agora tem só a primeira para pagar.
- **Suspensão derruba o painel, não as TVs:** o cliente suspenso/encerrado recebe **402** em tudo (menos `/auth/me` e `GET /billing`, para ele ver e regularizar); as TVs continuam exibindo o que já têm, exceto quando a conta é **encerrada**. O operador nunca é bloqueado (precisa entrar para dar suporte).
- **Não há integração com meio de pagamento** (cartão, boleto, PIX automático). O pagamento é **registrado à mão** pelo operador (forma + observação) e o texto "Como pagar" (chave PIX etc.) é configurado em **Plataforma → Configurações**. O ponto de extensão para um gateway (Stripe, Asaas, Mercado Pago…) é `BillingService.registerPayment`: um webhook do gateway chamaria exatamente isso. **Nenhum e-mail é enviado** (avisos e lembretes aparecem só no painel); também fica como evolução.

### Painel
- `AuthService` guarda a conta (`/auth/me`: usuário, papel, cliente, plano, uso) e responde `can('content' | 'admin' | 'platform')`; enquanto a conta não chega, tudo é permitido menos a plataforma (nada some por engano; o servidor decide). Páginas só abrem depois que a conta chegou, e o cliente suspenso só abre "Plano e cobrança" (evita uma salva de erros 402 ao entrar). Um 402 recarrega a conta, o que faz a tela reagir a uma suspensão que acabou de acontecer.
- Telas novas: **Equipe**, **Plano e cobrança** (uso x limites, faturas, como pagar, planos para comparar com "Pedir este plano" por e-mail, dados da conta) e a área **Plataforma** (visão geral, clientes com "Entrar", planos, faturas com registro de pagamento, configurações). Manual: capítulos 11 a 13.

### Testes e o que NÃO foi feito
- Backend 350 testes (`TenantIsolationTest`, `PermissionsTest`, `TeamAndLimitsTest`, `PlatformTest`, `BillingTest` e os anteriores adaptados a tenants) e painel 345, todos verdes. O Player **não mudou** (continua 0.6.x): a TV se identifica pelo token e o servidor descobre o cliente pela linha da TV.
- **Não verificado/não feito:** cobrança real por gateway; envio de e-mails; autoatendimento de troca de plano (o cliente pede por e-mail e o operador troca); autoatendimento de cadastro de cliente novo (o operador cadastra); recuperação de senha por e-mail (a senha é redefinida por um administrador); domínio próprio por cliente.

## 2026-10-20 (parte 10) — Nova marca (verde/prata/preto), tema escuro, uso no celular e idioma (PT/EN)

### Marca e tema
- A cor principal deixou de ser azul: agora é **verde escuro** (`#1a6a49`, `#2a8a5c` no escuro), com **prata** (bordas, textos do menu, ícones) e **preto** (menu, texto e fundo do tema escuro). O logo (`favicon.svg`, `.ico`, `apple-touch-icon`), o ícone e o banner do Player, as ilustrações dos manuais e o tema do PDF seguem a mesma paleta.
- **Tokens, não cores soltas:** `styles.css` define as variáveis (`--primary`, `--panel`, `--line`, `--ok-*`, `--err-*`, `--warn-*`, `--sidebar*`…) para o tema claro (`:root`) e o escuro (`:root[data-theme='dark']`). Nenhum componente usa cor fixa (a única exceção são as cores das zonas na pré-visualização de layouts, que são dado de desenho, e o QR Code, que precisa de escuro sobre claro). Trocar a marca de novo é mudar um bloco de variáveis.
- **Modo escuro** (`ThemeService`): Automático (segue `prefers-color-scheme`, inclusive quando muda com o painel aberto), Claro ou Escuro; a escolha fica no `localStorage` do aparelho. Um script no `index.html` aplica o tema **antes** do Angular carregar (sem piscar branco) e troca a cor da barra do navegador do celular (`theme-color`).

### Celular primeiro
- **Estrutura:** até 820 px o menu lateral vira uma gaveta que desliza (botão ☰, cortina, tecla Esc, fecha ao navegar), com **barra no topo** (marca, tema, idioma) e **atalhos fixos na base** (Dashboard, Dispositivos, Alertas, Biblioteca, Menu); respeita as áreas seguras (`env(safe-area-inset-*)`) de iPhones com entalhe.
- **Tabelas viram cartões** com CSS puro: `table.stack` + `data-label` em cada célula (o rótulo vem do cabeçalho e é traduzido); `td.first` é o título do cartão, `td.row-actions` os botões. Sem tabela horizontalmente rolável no celular. Janelas viram "folha" colada na base; campos têm 16 px (o iOS não dá zoom ao tocar) e 44 px de altura; filtros, abas dos relatórios e o editor de playlist e de layout foram refeitos para a largura de um telefone.
- **Verificação:** capturas reais em 390×844 (claro/escuro, PT/EN) de todas as telas e janelas. Achados no caminho: a barra inferior herdava `flex-direction: column` do menu; o ponto de status quebrava de linha do rótulo (`.st`, `nowrap`); botões do cabeçalho caíam para a linha de baixo (o título agora cede espaço).

### Idioma: português nativo + inglês
- **Português é a chave** (como no gettext): o texto em português aparece no código e no template (`{{ 'Salvar' | t }}`, `tr('Local "{name}" salvo.', { name })`) e `core/i18n-en/` guarda o inglês. Se faltar uma tradução, a tela mostra o português (nunca uma chave técnica). `{valores}` entram por parâmetros; o pipe `t` é impuro de propósito (acompanha a troca na hora, sem recarregar). A escolha fica no `localStorage`; o padrão é o português, sem adivinhar pelo navegador.
- **Datas, números e moeda** seguem o idioma (`Intl`): `20/10/2026` e `R$ 49,00` em PT; `10/20/2026` e `R$49.00` em EN. Um dia sem hora (`2026-11-05`) é lido como dia do calendário (não vira o dia anterior no fuso do Brasil). Campos de valor aceitam `49,90`, `49.90`, `1.249,90` e `1,249.90`.
- **Mensagens do SERVIDOR** (erros de validação, limites do plano, histórico das TVs, alertas, planos e modelos de fábrica) saem em português da API: o painel as traduz **no cliente** com modelos (`trServer`): a chave `Já existe um local chamado "{name}"` vira uma expressão regular e reaproveita o valor. Só as frases do servidor entram nesse casamento (`SERVER_TEXTS`): achado por teste — modelos de tela como `{used} de {limit}` casariam com qualquer texto. O que a PESSOA digitou (nome de TV, playlist, local…) nunca é traduzido. Os PDFs dos manuais, os CSV dos relatórios e as mensagens técnicas do Player continuam em português (o Player ganhou `values-en`: as telas da TV seguem o idioma do aparelho).
- **Nenhuma tradução esquecida:** `i18n-coverage.spec.ts` lê o código-fonte (AST dos templates com o compilador do Angular + `tr()`), exige inglês para todo texto, **reprova texto fixo solto num template**, confere as tabelas de rótulos (papéis, situações, eventos, dias, fusos…), que a tradução tenha os mesmos `{valores}` da chave e que o dicionário não guarde sobras.
- Não foi feito: tradução dos manuais (PDF), do CSV, das mensagens técnicas do Player e de um terceiro idioma (basta uma nova seção em `i18n-en` e um item em `LANGUAGES`).

### Testes
- Painel: 412 testes (eram 345): motor de idioma (tr, trServer, datas, números), tema, barra de preferências, menu do celular, telas inteiras em inglês e a cobertura de tradução. Backend e Player sem mudança de código (só recursos do Player: cores, ícone, banner e `values-en`).

## 2026-10-21 (parte 11) — Revisão de UX/UI do painel (visual novo, ícones gratuitos, sem emoji)

Pedido: olhar de UX/UI sênior + tech lead de frontend, pesquisar e criar um layout mais intuitivo, bonito e chamativo, **sem emoji, só ícones gratuitos**. O diagnóstico, os princípios, o sistema de design e o antes/depois estão em [DESIGN_REVIEW.md](./DESIGN_REVIEW.md); aqui ficam as decisões e o porquê.

### Decisões
- **Ícones: Lucide (ISC), embutidos como SVG por um gerador** (`frontend/scripts/generate-icons.mjs` → `shared/icons.ts`, tipo `IconName`). Alternativas descartadas: fonte de ícones (peso, flash de texto antes de carregar, sem como saber quais são usados), pacote de componentes pronto (dependência grande para ~100 ícones) e SVGs soltos copiados à mão (sem tipo, sem licença rastreável). O custo é rodar o gerador ao acrescentar um ícone. Licenças em `frontend/THIRD_PARTY_NOTICES.md`.
- **Proibição de emoji e símbolos decorativos garantida por teste** (`icon-policy.spec.ts` varre o código, menos comentários), junto com a checagem de que todo ícone citado existe e de que o registro só tem desenho de traço sem script. O Player e os manuais já seguem a marca; os ícones só mudam o painel.
- **Tipografia Inter variável hospedada junto do painel** (`@fontsource-variable/inter`, OFL): mesma letra em qualquer aparelho, números tabulares (`.num`) onde há comparação. Sem pedido a Google Fonts (privacidade e funcionar offline).
- **Estado = ícone + texto + cor (nunca só cor):** `app-pill` e as tabelas `SITUATION` (TV), `LEGACY_TONE` (tons antigos das faturas e dos clientes), `EVENT_LABELS` (eventos, agora com ícone e tom em vez de emoji; `eventTone` faz o erro e o aviso saltarem à vista) e `STATE_LOOK` (campanhas).
- **Uma ação principal por linha + menu "mais ações":** Editar (ou Entrar, Baixar…) fica à vista; Excluir, Desativar, Atualizar app, Nova fatura vão para o menu, o destrutivo separado e em vermelho. Menos ruído, menos exclusão por engano e a mesma linha cabe no celular.
- **Estados vazios que ensinam e esqueleto de carregamento** em vez de "Carregando…" (`app-empty-state`, `app-skeleton`); o dashboard ganhou o **assistente de primeiros passos** (4 passos) enquanto não há TV, e a **saúde da rede** (anel + "N de M TVs online") como primeira informação.
- **Alertas visíveis no menu:** `AttentionService` consulta o resumo a cada 30 s, **em silêncio** (`SILENT_ERRORS`: se falhar, some o número, sem aviso de erro) e o menu mostra o contador (barra lateral) e o ponto vermelho (barra inferior do celular). Para somar zero requisições novas por tela, só o menu consulta.
- **Menu da pessoa** (`app-user-menu`) junta tema, idioma, Minha conta e Sair (antes espalhados). O painel do menu lateral é `position: fixed` porque o menu rola e cortaria um painel mais largo que a coluna.
- **Contraste medido, não presumido:** o degradê do botão primário começava em 3,8:1 (claro) e 3,0:1 (escuro) com texto branco; passou a ≥ 4,5:1 nos dois temas e o hover escurece.
- **Largura:** a tabela de Dispositivos (a mais densa) passava de 1280 px; menu lateral de 240 px abaixo de 1400 px, "Editar" só com o lápis abaixo de 1500 px (rótulo mantido para o leitor de tela) e selos com reticências (`app-pill` tem um `span.txt` com `text-overflow`). Mudar tamanho de coluna é sempre conferido a 1280 px.
- **Teste de cobertura de tradução agora lê todos os componentes de cada arquivo** (antes só o primeiro `template:` de cada arquivo; `shared/ui.ts` tem vários). Achado ao criar os componentes novos.

### Achados no caminho (todos corrigidos)
- Um `td { max-width }` não limita a largura mínima da tabela: selo `nowrap` precisa de `max-width` em pixels (percentual não entra no cálculo de largura intrínseca da tabela).
- `.menu-item.danger` herdava o estilo do botão `.danger` (centralizado, com borda): o item de menu precisa redefinir `justify-content`, `padding`, `border` e `background`.
- Conteúdo projetado (`ng-content`) não recebe os seletores com escopo do componente: estilo de `app-page-header` para os botões das telas é global em `styles.css`.
- A mensagem "desatualizado" virou um selo pequeno de 22 px para caber na coluna; o texto completo continua no `title`.

### Testes
- Painel: **441 testes** (eram 412): `ui.spec.ts` (selo, cabeçalho, estado vazio, esqueleto, menu, ícone), `user-menu.spec.ts`, `attention.service.spec.ts`, `icon-policy.spec.ts`, contador de alertas no menu, linha do tempo com ícones e as telas existentes adaptadas (os botões "+ Novo…" agora são "Novo…" com ícone; o menu "mais ações" é aberto pelo teste de papéis para conferir o que cada papel enxerga).
- Backend e Player: sem mudança.

### Não feito
- Teste visual automático (comparação de imagem), arrastar para reordenar, e modo "cartões" da tabela de Dispositivos em telas médias. Estão listados em `DESIGN_REVIEW.md`, seção 9.

## 2026-10-21 (parte 12) — Pedidos da madrugada, bloco 1: nome, servidor acordando, reabrir sozinho, pouca memória e vídeos do iPhone

### Nome: Smartplay
- "Digital Signage" já existe no mercado. O produto e o app passaram a se chamar **Smartplay** (o pedido veio como "Smarplay"; adotado "Smartplay", que é só trocar em `core/brand.ts` se for outro). **O identificador do aplicativo Android (`com.digitalsignage.player`) NÃO mudou, de propósito:** trocá-lo faria cada TV tratar a versão nova como OUTRO aplicativo (sem atualização automática e com dois apps instalados). O nome visível ficou num lugar só em cada parte: `BRAND` no painel, `strings.xml` (+ `values-en`) e o banner no Player, `scripts/build-manuals.mjs` e `release-player.mjs` nos manuais e no site de downloads. O site de downloads ainda estava azul (esquecido na troca de marca de 2026-10-20): ficou verde.
- Migração **V9** troca o nome padrão da empresa nas configurações da plataforma só se ainda for "Digital Signage" (quem personalizou não é tocado) e o padrão da coluna. O nome do repositório e o do arquivo de contexto não mudaram.

### Alerta de servidor acordando
- O plano gratuito do Render dorme sem uso e leva até ~1 min para responder; antes a pessoa via um botão "pendurado". `ServerStatusService` pergunta ao `/api/v1/health` ao abrir o painel (inclusive na tela de entrada) e, depois, sempre que uma chamada **à API** falha por falta de conexão (status 0, 502, 503, 504 ou tempo esgotado; falhas de outro site, como o de downloads, não contam). Mais de 3,5 s sem resposta já mostra o alerta amarelo "O servidor está acordando" (não espera o pedido terminar); repete a pergunta a cada 4 s; ao voltar mostra "Servidor pronto" por 5 s; depois de ~3 min vira vermelho ("O servidor não respondeu") com "Tentar de novo". A consulta de saúde não gera aviso de erro nem laço. O alerta fica embaixo (acima da barra de atalhos no celular) para não esconder o formulário.

### Ao trocar a playlist, a anterior é apagada?
- **Não antes de baixar a nova.** `ConfigSync.sync` baixa tudo que falta (a mesma mídia em duas playlists baixa uma vez), confere SHA-256 e tamanho de cada arquivo, grava a configuração nova e **só então** apaga o que nenhuma zona usa mais (`retainOnly`). Falha no meio: a playlist anterior continua inteira (teste `failed download keeps the previous config untouched`). **Consequência nova tratada:** os dois conjuntos coexistem por um instante, então antes de baixar o Player confere o espaço livre (mantendo 50 MB de folga), limpa restos de downloads interrompidos e, se mesmo assim não couber, **recusa** com a mensagem "Não há espaço na TV…" no histórico, mantendo o conteúdo atual.

### Reabrir sozinho
- Já existia: abrir no boot, no `QUICKBOOT_POWERON` e depois de atualizar o app (`BootReceiver` + `LaunchHelper`, com o overlay invisível que o Android 10+ exige). **Faltavam** a queda do app e o app fechado pelo sistema. Agora: (1) o tratador de falhas grava o motivo e agenda um alarme (`AlarmManager`, exato onde permitido; sobrevive à morte do processo) com espera 3 s → 10 s → 30 s → 1 min → 5 min conforme as quedas dos últimos 15 min (sem laço de quedas); (2) um **vigia** a cada 2 min reabre o Player se a tela não deu sinal de vida em 150 s; (3) **saída de propósito** (Voltar, Início via `onUserLeaveHint`) dá 10 min de folga ao vigia, para dar tempo de mexer nas configurações da TV; (4) o motivo da reabertura vira um aviso no histórico da TV. Escolhas: o alarme dispara um *broadcast* (não uma Activity) porque só assim o overlay invisível vale; sem a permissão "exibir sobre outros apps" o Android 10+ pode bloquear (igual ao boot). Testável no emulador com `adb shell am crash com.digitalsignage.player`.

### Pouca memória
- `MemoryProfile` (LOW quando `isLowRamDevice`, heap por app ≤ 96 MB ou RAM total ≤ 1,2 GB): imagens decodificadas em até 1280×720 e RGB_565 (metade da memória), **um vídeo por vez** (zonas secundárias pulam os vídeos, com aviso), ExoPlayer e WebView liberados assim que o item acaba, buffer do ExoPlayer pequeno (1,5–3 s, 4 MB); em todos os perfis `onTrimMemory` (a partir de RUNNING_LOW) solta o que não está à vista, a imagem anterior é reciclada na hora e o ExoPlayer usa `setEnableDecoderFallback` (tenta outros decodificadores, inclusive o de software, antes de desistir).

### Vídeos do iPhone
- O iPhone grava **.MOV (QuickTime) em HEVC** (padrão) ou H.264 ("Mais compatível"). O servidor agora aceita `video/mp4`, `video/quicktime`, `video/x-m4v`, e, quando o navegador manda tipo genérico (`application/octet-stream` ou vazio), decide pela extensão (`.mp4/.mov/.m4v`, `.jpg/.png/.webp`); o tipo guardado é sempre o canônico (`video/mp4` ou `video/quicktime`). O ExoPlayer toca `.mov` como MP4 (mesmo extrator).
- **O painel lê o vídeo antes de enviar** (`media/video-probe.ts`: caixas ISO-BMFF `moov`/`trak`/`stsd`/`tkhd`/`mvhd`, saltando o `mdat` pelo tamanho declarado, sem decodificar): codec (H.264, HEVC, AV1…), tamanho **já com o giro** (vídeo em pé = 1080×1920), duração (preenche sozinha, mesmo onde o navegador não toca HEVC), HDR (Dolby Vision, PQ/HLG). **Acima de 1080p é recusado** com instrução de como regravar (pedido: "até 1080p"); **HEVC e HDR avisam** mas enviam. Se o aparelho não decodificar, o Player diz "reenvie em H.264 de até 1080p" (`ErrorMessages.playbackFailure`). **Limite que continua valendo:** o armazenamento gratuito rejeita arquivos acima de 50 MB (cerca de 1 min de 1080p do iPhone); não foi mexido.
- **Não verificado em aparelho real:** reprodução de HEVC numa TCL/Fire Stick reais (depende do chip; o emulador usa decodificador próprio) e o envio de um .MOV real do iPhone (os testes usam arquivos montados com as mesmas caixas).

### Testes
- Painel 474 (eram 441): `ServerStatusService`, aviso, interceptor, leitor de vídeo (iPhone em pé, `moov` no fim ou no começo, HDR, giro), envio de vídeo. Backend: tipos aceitos e extensão (`MediaLibraryTest`). Player: `RestartPolicy`, `MemoryProfile`, espaço em disco, mensagens de vídeo.

## 2026-10-21 (parte 13) — Pedidos da madrugada, bloco 2: comprovação de exibição (proof of play)

### O que é e por que assim
- Pedido: "relatório auditável que registra exatamente os ms em cada mídia rodou". A **TV mede** (não o servidor): quando a mídia vai ao ar o Player guarda o início (relógio de parede, para o relatório) e mede a duração com o **relógio monotônico** (`SystemClock.elapsedRealtime`, que não pula quando alguém ajusta a hora da TV). Imagem e página web: a exibição vale do `showImage/showWeb` até o fim do tempo ou até ser cancelada; vídeo: do **primeiro quadro desenhado** (`onRenderedFirstFrame`), não do comando de tocar; vídeo em laço gera um registro por volta. Exibição **interrompida** (troca de playlist, tela fechada) também é registrada, com o tempo que de fato durou e `completed = false`.
- **Corrente de hashes** (o que torna "auditável"): cada TV numera seus registros (1, 2, 3…, número que nunca volta, guardado nas preferências) e cada registro leva o hash do anterior. O hash é o SHA-256 dos campos `prevHash, seq, deviceId, mediaId, mediaName, mediaType, playlistId, campaignId, zoneName, startedAtMs, durationMs, plannedMs, completed` separados por `0x1F` (nulo = vazio, completa = `1/0`). Player (Kotlin) e servidor (Java) calculam igual: **um vetor de teste idêntico nos dois lados** (`PlayLogTest` e `PlayProofTest`). Isto prova **integridade** (nada apagado, trocado ou reordenado depois que o servidor recebeu), **não** prova que a TV não foi adulterada antes de enviar: a TV é o ponto de confiança. Isso está dito na tela de ajuda do relatório.
- O servidor confere **ao receber** (marca `hash_valid` e `continuous` em cada linha, sem recusar: registro estranho vira linha marcada em vermelho, não some) e **de novo na verificação**, que também detecta números faltando e devolve o **resumo do período** (SHA-256 sobre os hashes, em ordem), para o cliente guardar com o relatório. Lote com campos inválidos é recusado (HTTP 400): o Player então **descarta o lote e avisa no histórico**, para um registro ruim nunca travar os seguintes.
- **Funciona sem internet:** o diário é um arquivo `playlog.jsonl` (acréscimo + `fsync`), com o estado (próximo número e último hash) nas preferências; se a energia cair entre as duas gravações, o arquivo manda. Linha cortada no fim é ignorada. Limite de 20.000 pendentes (descarta os mais antigos e avisa). A entrega ocorre **depois de cada heartbeat**, em lotes de 200 (até 10 lotes por ciclo); falha de rede deixa tudo para o próximo ciclo. Reenvio é seguro: `UNIQUE (device_id, seq)`, o servidor responde "confirmado até o número N".
- **Relógio da TV:** o Player manda a hora dele no lote; se a diferença para o servidor passar de **2 s**, o início é corrigido pela diferença (`clock_offset_ms` guarda o ajuste, e o horário original da TV também fica); abaixo disso o horário não é mexido (a latência da rede não pode "corrigir" o que está certo).
- **Campanha na prova:** a configuração e o heartbeat agora dizem qual campanha está no ar (`campaignId/campaignName`) e `ConfigSync` trata a troca de campanha como mudança de configuração (a mesma playlist pode vir de uma campanha ou da própria TV).
- A prova **não tem chave estrangeira** para mídia, playlist e campanha: apagar a mídia não apaga o histórico do que foi exibido (o nome fica copiado no registro).

### Espaço no banco gratuito
- Cada TV gera milhares de linhas por dia, e o Postgres gratuito é pequeno. Por isso: detalhe guardado **14 dias** por padrão (**7 a 365**, configurável em Alertas), apagado pelo `MonitoringJob`; e uma tabela de **resumo diário** (`play_log_daily`: exibições, tempo, primeiro/último número, último hash), mantida **366 dias**, que continua provando o fim da corrente de cada dia depois que o detalhe some. Consulta limitada a 93 dias e 500 linhas por página; CSV até 100.000 linhas; verificação até 300.000.

### Painel
- Nova aba **Comprovação** em Relatórios (`reports/proof-report.ts`): filtros por TV, mídia e campanha, totais, tabela com milissegundos, linha marcada quando a corrente tem problema, "Carregar mais", **Verificar integridade** (por TV, com o resumo SHA-256 para copiar), resumo por dia e CSV próprio (o botão do cabeçalho some nesta aba). Campo novo em Alertas: "Guardar cada exibição (comprovação) por (dias)".

### Testes
- Backend: `PlayProofTest` (22): recebimento em lotes e reenvio, números faltando, corrente quebrada, hash alterado, relógio da TV, relatório com filtros, CSV, resumo diário, prazo de guarda, outro cliente nunca vê, papéis, campanha na configuração e vetor do hash. Player: `PlayLogTest` (diário, reinício, linha cortada, limite, entrega em lotes, falha de rede, lote recusado, vetor do hash). Painel: `proof-report.spec.ts` (10) e ajustes em Relatórios e Alertas.

### Não verificado / ressalvas
- A medição dentro do `ZonePlayer` foi testada por unidade (diário e entrega) e no emulador na etapa de lançamento; **não** foi validada em TV real nem com vídeo longo em laço por horas.
- A TV não assina os registros (sem chave por aparelho): quem controla a TV pode, em tese, gerar uma corrente falsa **desde o início**. Assinatura por aparelho fica como evolução (listada em `docs/INTEGRACOES.md` quando for escrita).

## 2026-10-21 (parte 14) — Pedidos da madrugada, bloco 3: modo offline inteligente

### O que já existia e o que faltava
- Já existia o essencial do "offline-first": a TV guarda a última configuração e as mídias (validadas por SHA-256), reabre tocando o cache sem esperar a rede, e uma falha de rede nunca derruba a exibição. O que **faltava**: quem decide a campanha é o servidor (a cada heartbeat), então, sem internet, a TV ficava parada no que estava no ar quando a conexão caiu: a campanha das 18h não entrava e a das 23h não saía. As exibições também não eram guardadas (resolvido na parte 13, comprovação).

### Decisão: o servidor entrega a agenda já resolvida, a TV só olha a hora
- Alternativa descartada: levar as regras de campanha (prioridade, alvo mais específico, dias, janelas que passam da meia-noite, fuso) para o Player. Seriam duas implementações da mesma regra (Java e Kotlin, este último sem `java.time` no Android 5), com risco de divergência exatamente nos casos de borda.
- **Escolhida:** `OfflinePlanService` percorre os próximos **7 dias** e chama a MESMA regra do heartbeat (`CampaignService.effectiveFor`) em cada instante em que algo pode mudar: meia-noite, início e fim da janela de cada campanha, no fuso dela (calculados, não amostrados). Resultado: uma lista de **intervalos** `[fromMs, toMs)` com a playlist, a campanha e o `configVersion` que o servidor mandaria naquele instante, mais o conteúdo de cada playlist usada (uma vez cada). Intervalos vizinhos iguais são fundidos; teto de 300 intervalos (acima disso a agenda é cortada e `untilMs` recua). A agenda vai dentro de `GET /player/config` (`offlinePlan`); ausente quando nenhuma campanha vale para a TV.
- **`planVersion` no heartbeat:** hash (FNV, 53 bits) só das ENTRADAS (campanhas que alcançam a TV e não acabaram, prioridade, alvo, datas/horas/dias/fuso, playlists e versões, layout), **não do relógio**: não muda com a passagem do tempo, muda quando você cria, edita, pausa ou apaga uma campanha, troca a playlist ou o layout. A TV compara com a que guardou e baixa a configuração de novo se diferir (0 = sem agenda). O custo no heartbeat é uma consulta de playlists a mais (as campanhas ativas já eram carregadas; agora uma vez só por heartbeat).
- **Renovação:** a janela de 7 dias é renovada pela TV quando restam menos de 5 (a cada ~2 dias), no máximo uma vez por hora (o servidor com relógio estranho nunca vira laço), e as mídias da agenda que falharam são tentadas de novo em 5 min.

### A hora
- Relógio de TV de verdade erra. `TrustedClock`: a cada heartbeat a TV anota a hora do servidor (`serverTime`, que já vinha na resposta) e conta o tempo pelo **relógio monotônico** (`SystemClock.elapsedRealtime`, que não pula quando alguém mexe na hora); a diferença servidor−relógio também é guardada, para o caso de o Player reabrir (ou a TV religar) sem internet. Teste: TV com o relógio 40 minutos atrasado troca de campanha no minuto certo.
- Uma TV nunca conectada, com relógio errado e sem internet continua sem hora confiável: usa o relógio dela (limitação conhecida, igual a qualquer TV nova).

### O que a TV faz
- **Ao abrir** (inclusive sem internet): já escolhe o intervalo certo da agenda. **Com internet, quem manda é o servidor:** a agenda só entra depois de **75 s sem contato** (`Timing.offlineAfterMs`) e é conferida a cada 15 s. Ao reconectar, o motor compara com o servidor: se confirma o guardado, a tela volta ao conteúdo guardado; se manda outra coisa (campanha pausada), baixa e troca. A configuração montada localmente é **igual** à que o servidor mandaria (mesma playlist, versão, campanha, zonas; a agenda fica de fora da comparação da tela, `PlayerConfig.forScreen`), então a tela não reinicia à toa quando a conexão volta.
- **Mídias da agenda:** depois de o que está no ar estar pronto, baixa as das campanhas dos próximos dias, a mais próxima primeiro; **nunca** falha nem atrasa a troca do conteúdo atual. Sem espaço: guarda o que couber e avisa no Histórico. `retainOnly` passou a manter também o que a agenda usa (antes apagaria a mídia de uma campanha futura já baixada).
- **Layout:** só a zona principal troca de playlist (como no servidor); as zonas fixas permanecem.
- **Agenda vencida** (mais de 7 dias sem contato): fica o último conteúdo, nunca tela em branco.
- **Painel:** o heartbeat informa `planUntilMs`; o servidor guarda em `devices.offline_plan_until` (V11) e o **Histórico da TV** mostra o selo "agenda offline até …". Eventos no Histórico: sem contato, qual campanha entrou, contato restabelecido.

### Testes
- Backend `SmartOfflineTest` (13): sem campanha não há agenda; janela diária; janela que passa da meia-noite; prioridade na sobreposição; campanha que já acabou ou começa depois da semana; campanha que começa no meio da semana; alvo; pausada; versão estável com o tempo e mutável com edição/playlist/prioridade; primeiro intervalo = o que o heartbeat diz agora (com e sem layout, `configVersion` igual); `planUntilMs` no painel. Player `SmartOfflineTest` (+30): relógio confiável, planejador, leitura do JSON (incluindo hora ISO do servidor), pré-download (espaço, falhas, nova tentativa, renovação) e o motor sem internet (troca no minuto certo, abrir no intervalo certo, servidor manda quando volta, tela não reinicia, eventos). Painel: selo no Histórico.
- **Emulador:** com o servidor desligado, a TV (relógio do aparelho adiantado) trocou para a campanha de teste cerca de 14 s depois do início da janela, exibiu 40 vezes, voltou à base 14 s depois do fim, e entregou 332 registros de comprovação ao voltar (nenhum faltando, corrente íntegra). Não testado em TV real nem com agenda grande (dezenas de vídeos).

### Não feito
- **Conteúdo dinâmico offline** (widgets de clima/notícias) não existe ainda; quando existir terá de guardar o último valor.
- Agenda por **grupo/local** muda de alcance quando a TV muda de grupo: coberto pelo `planVersion`, mas só vale na próxima conexão.
