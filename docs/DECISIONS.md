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
