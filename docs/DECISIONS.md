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
