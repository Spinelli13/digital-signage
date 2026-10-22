# Colocando no ar e testando em uma TV

Passo a passo para ligar **uma tela** ao sistema, do mais simples ao mais "de verdade". Cada fase só depende da anterior ter funcionado.

| Fase | O que é | Custo | Precisa de cartão? | Serve para |
|---|---|---|---|---|
| **A** | Backend e painel no seu PC, TV na mesma rede Wi‑Fi | R$ 0 | Não | Validar o Player na TCL (ativação, reprodução, offline, boot) |
| **B** | Mesmo PC, mas a TV acessa pela internet via túnel | R$ 0 | Não | Provar que funciona fora da sua rede, sem hospedar nada |
| **C** | Nuvem gratuita 24h (Render + Supabase + Cloudflare Pages) | R$ 0 | Supabase e Cloudflare: não. **Render pode pedir** (verificação antifraude, ~US$ 1 estornado) | Deixar a TV rodando sem depender do seu PC |
| **C‑alt** | Servidor próprio gratuito (Oracle Cloud) | R$ 0 | **Sim** | Mais robusto que a C, mas depende de disponibilidade |

> Recomendação: faça **A → C**. A fase B é um atalho útil se quiser testar fora de casa antes de criar contas.
>
> Preços e limites de planos gratuitos mudam com frequência. Os números abaixo foram conferidos em **out/2026** (fontes no fim); confirme na tela de cadastro de cada serviço antes de contar com eles.

---

## Antes de tudo: o que já foi e o que não foi validado

Validado automaticamente (rodou, passou):

- Backend: 66 testes de API com Postgres real (login, mídia, playlists, ativação por código, token do aparelho, heartbeat, redirecionamento para armazenamento S3, status HTTP reais).
- Painel: 23 testes e build de produção. Fluxo de login → ativar dispositivo pela UI conferido no navegador contra o backend real.
- Player: 39 testes JVM (cliente HTTP, cache com checksum, sincronização, máquina de estados offline/ativação) e APK gerado.
- Ciclo completo simulado via HTTP: registro → código → ativação no painel → upload → playlist → config → download → heartbeat.

**Ainda NÃO validado — só dá para saber na TV:**

- Reprodução de vídeo (ExoPlayer) e imagens na tela real, tela cheia, enquadramento.
- Início automático após ligar a TV (depende da versão do Android/Fire OS — ver "Reinício" abaixo).
- Upload real para S3/Supabase/R2 (a assinatura de URL é testada, o envio real só no deploy).
- Memória do backend no Render com 512 MB (medi ~270 MB residentes com heap de 300 MB no Windows; no Render não foi medido).

---

## Fase A0 — Emulador de Android TV no próprio PC

Já está instalado nesta máquina: AVD `signage_tv` (Android 14 TV, x86, aceleração WHPX; imagem em `Sdk\system-images\android-34\android-tv\x86`, ~8 GB em disco). É o jeito mais rápido de iterar: o emulador enxerga o seu PC em **`10.0.2.2`**.

```bash
# 1) ligar o emulador (abre uma janela; ~40 s até estar pronto)
"$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd signage_tv -no-snapshot-save

# 2) compilar o Player apontando para o backend do PC e instalar
cd player
./gradlew assembleDebug -PserverUrl=http://10.0.2.2:8080
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.digitalsignage.player/.MainActivity

# 3) ver a tela da TV como imagem (útil para conferir sem olhar a janela)
adb -s emulator-5554 exec-out screencap -p > tv.png
```

O backend pode ser o do Docker (`docker compose up -d`, Postgres 16 real) ou o `DevApplication`. Depois é o mesmo fluxo do passo 7 da Fase A: a TV mostra o código, você ativa no painel (`http://localhost:4200`), envia mídias e atribui uma playlist.

Para testar o início automático no emulador (o reboot não persiste a permissão sozinho, então force a gravação):

```bash
adb -s emulator-5554 shell appops set com.digitalsignage.player SYSTEM_ALERT_WINDOW allow
adb -s emulator-5554 shell cmd appops write-settings   # só necessário no emulador
adb -s emulator-5554 reboot
```

Dica: `screenrecord` só grava quando a tela muda — para gerar um MP4 de teste com movimento, navegue num menu enquanto grava.

---

## Fase A — TV TCL na sua rede local

### 1. Subir o backend no PC

Opção 1 — **sem Docker** (Postgres embutido, dados em `backend/data/dev-pg`):

```bash
cd backend
mvn -q test-compile exec:java -Dexec.mainClass=com.digitalsignage.backend.DevApplication -Dexec.classpathScope=test
```

Opção 2 — com Docker (abra o Docker Desktop antes):

```bash
cp .env.example .env
docker compose up --build
```

Confirme: `http://localhost:8080/api/v1/health` deve responder `{"status":"ok"}`.

### 2. Liberar a porta 8080 no Windows

Em um PowerShell **como administrador** (a rede precisa estar como "Particular"):

```powershell
New-NetFirewallRule -DisplayName "Vyzzu 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private
```

Descubra o IP do PC com `ipconfig` (campo "Endereço IPv4"; hoje é `192.168.0.117`, mas pode mudar — se mudar, refaça o passo 4).

Teste: no navegador **do celular** (no mesmo Wi‑Fi) abra `http://IP_DO_PC:8080/api/v1/health`. Se responder, a TV também alcança.

### 3. Subir o painel

```bash
cd frontend
npm install
npm start
```

Abra `http://localhost:4200` e entre com o administrador do `.env` (padrão `admin@digitalsignage.local` / `changeme`).

### 4. Gerar o APK apontando para o seu PC

```bash
cd player
./gradlew assembleDebug -PserverUrl=http://IP_DO_PC:8080
```

O APK sai em `player/app/build/outputs/apk/debug/app-debug.apk`. Use o APK **debug** neste teste: só ele aceita `http://` (o release exige `https://`).

> Para não repetir o `-P`, crie `player/local.properties` (já é ignorado pelo git) com a linha `signage.serverUrl=http://IP_DO_PC:8080`.

### 5. Habilitar depuração na TV TCL (Android TV / Google TV)

1. **Configurações → Preferências do dispositivo → Sobre** → clique 7 vezes em **Compilação** (build). Aparece "Você agora é um desenvolvedor".
2. Volte e abra **Opções do desenvolvedor** → ative **Depuração USB** e, se existir, **Depuração de rede / Depuração ADB**.
3. Veja o IP da TV em **Configurações → Rede**.

### 6. Instalar o Player na TV

```bash
adb connect IP_DA_TV:5555
adb install -r player/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.digitalsignage.player/.MainActivity
```

(`adb` fica em `C:\Users\mathe\AppData\Local\Android\Sdk\platform-tools\`.) A TV vai pedir para autorizar o computador — aceite ("sempre permitir"). Em versões mais novas do Google TV a depuração sem fio usa `adb pair IP:PORTA` com o código mostrado na TV; siga o que a tela de opções do desenvolvedor indicar.

Se o `adb connect` não funcionar, instale pelo pendrive/app "Send Files to TV", ou pelo app **Downloader** hospedando o APK em um link.

### 7. Ativar e exibir conteúdo

1. A TV mostra **"Código de ativação"** com 6 caracteres. Se mostrar "Sem conexão com o servidor", o endereço está errado: aperte **OK** no controle e corrija, ou rode `adb shell am start -n com.digitalsignage.player/.MainActivity --es server_url http://IP_DO_PC:8080`.
2. No painel: **Dispositivos → Adicionar dispositivo** → digite o código, dê um nome → **Ativar**.
3. **Biblioteca** → envie 2 ou 3 imagens e um vídeo MP4 curto.
4. **Playlists → Nova playlist** → adicione as mídias, ajuste a duração → **Publicar alterações**.
5. **Dispositivos** → escolha a playlist na coluna "Playlist".
6. Em até ~30 s a TV baixa as mídias e começa a exibir. O painel mostra o dispositivo **Online**.

### 8. Roteiro de validação na TCL

| Teste | Como | Esperado |
|---|---|---|
| Tela cheia | Abrir o Player | Sem barras do sistema |
| Tela não apaga | Deixar 10+ min | Continua ligada |
| Imagem + vídeo | Playlist mista | Alterna; vídeo toca até o fim |
| Troca de playlist | Editar e publicar | TV atualiza em até ~30 s, sem tela preta longa |
| **Sem internet** | Desligue o Wi‑Fi da TV (ou pare o backend) | **Continua tocando** o que já baixou |
| Volta da internet | Religue | Painel volta a mostrar Online |
| **Reinício** | Reinicie a TV pelo menu | Player volta sozinho (veja abaixo) |
| Excluir dispositivo | Excluir no painel | TV volta a pedir um novo código |

#### Reinício (início automático)

O Player abre sozinho ao ligar a TV, mas **no Android 10 ou mais novo isso exige uma permissão** ("exibir sobre outros apps"). Conceda uma vez, com a TV já com o app aberto pelo menos uma vez:

```bash
adb shell appops set com.digitalsignage.player SYSTEM_ALERT_WINDOW allow
```

O que foi verificado no emulador de Android TV 14 (Fase A0), lendo o log do sistema:

| Situação | Resultado |
|---|---|
| Sem a permissão | `BAL_BLOCK`: o receiver roda, mas o Android bloqueia abrir a tela — o app **não** abre |
| Com a permissão, abrindo a tela direto | `BAL_BLOCK` do mesmo jeito: a permissão sozinha **não basta** |
| Com a permissão + overlay invisível de 1 px (o que o `BootReceiver` faz) | `BAL_ALLOW_VISIBLE_WINDOW`: o Player abre e volta a tocar |

Se na sua TCL o app continuar sem abrir, **me diga o modelo e a versão do Android da TV**. A alternativa mais robusta é registrar o Player como tela inicial (launcher) da TV, que o sistema abre sempre no boot — troca o comportamento do botão Home, por isso só faço se for preciso. No Fire Stick (Fire OS baseado em Android 9/11, conforme o modelo) o início automático é medido à parte.

---

## Fase B — Teste pela internet usando túnel (opcional)

Expõe o backend do seu PC com HTTPS temporário, sem conta e sem hospedar nada. O PC precisa ficar ligado.

```powershell
winget install Cloudflare.cloudflared
cloudflared tunnel --url http://localhost:8080
```

Ele imprime um endereço `https://alguma-coisa.trycloudflare.com`. Aponte a TV para ele **sem recompilar**:

```bash
adb shell am start -n com.digitalsignage.player/.MainActivity --es server_url https://alguma-coisa.trycloudflare.com
```

O painel continua em `http://localhost:4200` falando com `localhost:8080`. O endereço do túnel muda toda vez que você reinicia o `cloudflared`.

Para voltar a um Player "de fábrica" (novo código de ativação): `adb shell am start -n com.digitalsignage.player/.MainActivity --ez reset true`.

---

## Fase C — Nuvem gratuita 24h

Arquitetura:

```text
TV (Player) ──HTTPS──> Render (backend Java) ──> Supabase Postgres
        │                      │
        │                      └─> Supabase Storage (mídias, protocolo S3)
        └─ baixa a mídia direto da URL assinada (não passa pelo backend)

Navegador (painel) ──> Cloudflare Pages (arquivos estáticos) ──> API no Render
```

Por que essas peças (limites conferidos em out/2026):

- **Render (backend)** — plano gratuito: 512 MB de RAM, 0,1 vCPU, 750 h/mês (um serviço 24h cabe: 744 h), dorme após 15 min sem requisições e acorda em ~1 min. O Player manda um heartbeat a cada 30 s, o que o mantém acordado enquanto houver TV ligada. **Sem disco persistente** — por isso as mídias vão para um armazenamento externo. 5 GB de banda/mês: como a mídia sai direto do armazenamento, o backend só trafega JSON.
- **Supabase (banco + mídias)** — Postgres 500 MB e Storage com protocolo S3. O projeto só é pausado após ~7 dias *sem atividade*; o heartbeat evita isso. **Limite de 50 MB por arquivo** no plano gratuito.
  - Por que não Neon para o banco: o plano gratuito tem 100 horas de computação/mês e o compute "dorme" após 5 min ociosos. Com heartbeat a cada 30 s ele nunca dorme, e uma conta simples (0,25 CU × 24 h × 30 d ≈ 180 h) estoura a cota na metade do mês. Para um dispositivo que fala o tempo todo, o Supabase se adequa melhor.
  - Por que não o Postgres do Render: expira em 30 dias.
- **Cloudflare Pages (painel)** — hospedagem estática com banda ilimitada, sem cartão.

### C.1 Repositório no GitHub

Render e Cloudflare Pages implantam a partir do GitHub. O repositório já existe em `Spinelli13/digital-signage`: faça `git push` com tudo que está aqui. Os testes rodam sozinhos a cada push (aba **Actions**).

### C.2 Supabase — banco e armazenamento

1. Crie uma conta e um **New project** (região mais próxima — por exemplo São Paulo). Guarde a senha do banco.
2. **Banco:** clique em **Connect** e escolha a aba **Session pooler**. Anote *host*, *porta (5432)* e *usuário* (no formato `postgres.<ref>`).
   - Use o **Session pooler**, não a conexão direta: a direta é IPv6 e o Render não alcança; o pooler tem IPv4 gratuito.
   - Não use a porta 6543 (transaction pooler): quebra *prepared statements*.
3. **Mídias:** em **Storage → New bucket**, crie `media` (privado). **Tipos permitidos (MIME):** se restringir os tipos do bucket, inclua `image/jpeg`, `image/png`, `image/webp`, `video/mp4` **e `video/quicktime`** (o `.MOV` do iPhone; sem ele o envio falha com "Internal Server Error": o log mostra `mime type video/quicktime is not supported`). Também `video/x-m4v` e `video/m4v` (`.m4v`). Limite de tamanho do plano gratuito: 50 MB por arquivo. Em **Storage → S3 Configuration**:
   - copie o **Endpoint** (algo como `https://<ref>.storage.supabase.co/storage/v1/s3`) e a **Region**;
   - **New access key** → copie *Access key ID* e *Secret* (o secret só aparece uma vez).

### C.3 Render — backend

**Caminho rápido (recomendado):** em render.com, **New → Blueprint** → escolha o repositório. O arquivo [`render.yaml`](../render.yaml) já define o serviço (Docker, plano Free, health check, `JWT_SECRET` gerado automaticamente) e a tela pede só os valores secretos da tabela abaixo.

**Caminho manual:** **New → Web Service** → conecte o repositório e configure:

- **Language:** Docker · **Dockerfile Path:** `./backend/Dockerfile` · **Docker Build Context Directory:** `./backend`
- **Instance type:** Free · **Health Check Path:** `/api/v1/health`

Variáveis de ambiente (no Blueprint, só as que não têm valor fixo no arquivo são pedidas):

| Variável | Valor |
|---|---|
| `DB_URL` | `jdbc:postgresql://<host-do-pooler>:5432/postgres?sslmode=require` |
| `POSTGRES_USER` | `postgres.<ref>` |
| `POSTGRES_PASSWORD` | senha do banco |
| `DB_POOL_SIZE` | `3` |
| `JWT_SECRET` | texto aleatório longo — gere com `openssl rand -hex 32` |
| `ADMIN_NAME` / `ADMIN_EMAIL` | seus dados |
| `ADMIN_PASSWORD` | **senha forte** (não use `changeme`) |
| `CORS_ALLOWED_ORIGINS` | `https://<seu-projeto>.pages.dev` (preencha depois da C.4) |
| `STORAGE_TYPE` | `s3` |
| `S3_BUCKET` | `media` |
| `S3_ENDPOINT` | endpoint copiado do Supabase |
| `S3_REGION` | região copiada do Supabase |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | chaves do Supabase |
| `PANEL_URL` | (opcional) endereço do painel, sem barra no final; vai nos links dos e-mails e no QR Code da TV. Vazio = a primeira origem de `CORS_ALLOWED_ORIGINS` |
| `MAIL_FROM` | (e-mail, opcional) remetente, por exemplo `Vyzzu <avisos@seudominio.com>`, validado no provedor. Sem ele não sai e-mail (o sistema funciona do mesmo jeito) |
| `MAIL_API` / `MAIL_API_KEY` | (e-mail) `resend` ou `brevo` e a chave da API do provedor. **É a forma que funciona no plano gratuito do Render**, que bloqueia as portas de SMTP. Passo a passo em [`EMAIL.md`](EMAIL.md) |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | (e-mail por SMTP, só em hospedagem paga) servidor, porta (587 com STARTTLS), usuário e senha do provedor |

4. **Create Web Service**. O primeiro build leva alguns minutos; o primeiro start pode levar mais de 1 minuto (0,1 vCPU). Teste `https://<servico>.onrender.com/api/v1/health`.

Observações:

- O administrador só é criado se ainda não existir: se errar `ADMIN_PASSWORD` na primeira subida, mudar a variável depois **não** altera a senha — apague o usuário na tabela `users` (SQL Editor do Supabase) e reinicie.
- Se o serviço cair com "Out of memory"/código 143, a JVM não coube nos 512 MB: ajuste a variável `JAVA_OPTS` (por exemplo `-XX:MaxRAMPercentage=50 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k`) ou considere uma instância paga.

### C.4 Cloudflare Pages — painel

1. Em dash.cloudflare.com: **Workers & Pages → Create → Pages → Connect to Git** (se a tela só mostrar Workers, procure o link "Looking to deploy Pages? Get started") → autorize o GitHub e escolha o repositório, branch `master`. O repositório é privado: autorize o acesso a ele.
2. Configure:
   - **Root directory:** `frontend`
   - **Build command:** `npm ci && npm run build`
   - **Build output directory:** `dist/painel/browser`
   - **Variável `NODE_VERSION`** = `24` (o Angular 21 exige Node 20.19+/22.12+/24; assim o build não depende da versão padrão do Cloudflare)
3. Em **Settings → Variables and secrets** (ou na criação do projeto), defina `API_BASE_URL` = `https://<servico>.onrender.com`. O build grava essa URL em `config.json` sozinho (`frontend/scripts/write-config.mjs`); não precisa de commit. Depois de definir a variável, faça um novo deploy.
4. Volte ao Render e ajuste `CORS_ALLOWED_ORIGINS` para `https://<seu-projeto>.pages.dev` (sem barra no final).

Abra o painel, entre, e envie uma imagem para conferir se o upload para o Supabase funciona (se falhar aqui, veja "Problemas comuns").

### C.5 Player apontando para a nuvem

```bash
cd player
./gradlew assembleDebug -PserverUrl=https://<servico>.onrender.com
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

(Ou, sem recompilar: `adb shell am start -n com.digitalsignage.player/.MainActivity --es server_url https://<servico>.onrender.com`.)

Depois repita o **passo 7 e o roteiro da Fase A**.

### Problemas comuns

| Sintoma | Causa provável |
|---|---|
| Painel: "Não foi possível conectar ao servidor" | Backend dormindo (aguarde ~1 min) ou `apiBaseUrl` errado em `config.json` |
| Painel carrega mas chamadas falham no console (CORS) | `CORS_ALLOWED_ORIGINS` diferente da URL real do painel (precisa ser idêntica, sem `/` final) |
| Backend não sobe, erro de conexão com `[...]` (IPv6) | Está usando a conexão direta do Supabase; use o Session pooler |
| Backend cai com `Found non-empty schema(s) "public" but no schema history table` | O `public` do Supabase já tem objetos (função do RLS automático). Defina `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` e `SPRING_FLYWAY_BASELINE_VERSION=0` (já estão no `render.yaml`) |
| Backend cai com `password authentication failed` | `POSTGRES_PASSWORD` ausente ou errada (ausente = usa a senha padrão de desenvolvimento). Resete a senha no Supabase e cole no Render |
| Backend tenta `localhost:5432` | `DB_URL` não chegou ao serviço: variáveis marcadas `sync: false` no Blueprint ficam em branco se não forem preenchidas |
| Upload retorna 500 | Credenciais/endpoint/região S3 errados; arquivo acima de 50 MB (limite do plano gratuito do Supabase) |
| TV mostra "Sem conexão" | URL do servidor errada ou backend dormindo; aperte OK no controle para corrigir |
| TV baixa mas não toca vídeo | Formato não suportado — use MP4 H.264 |

---

## Fase C‑alt — Oracle Cloud "Always Free" (servidor próprio)

Dá uma máquina virtual de verdade, onde sobe tudo com `docker compose` (inclusive Postgres e mídias em disco, sem Supabase). Pontos de atenção:

- **Exige cartão de crédito** no cadastro (para verificação).
- Reportagens de meados de 2026 indicam que a cota gratuita das instâncias ARM caiu de 4 vCPU/24 GB para **2 vCPU/12 GB** (fontes divergem sobre contas pagas). Em regiões lotadas é comum o erro "Out of capacity" por semanas.
- Instâncias com uso baixo de CPU/rede/memória por 7 dias podem ser **recolhidas** pela Oracle.
- Para HTTPS você precisa de um domínio (um subdomínio gratuito do DuckDNS serve) e um proxy como o Caddy, que emite o certificado sozinho. Sem HTTPS só funciona com APK debug.

Eu consigo preparar o `docker-compose` com Caddy se você preferir esse caminho.

---

## Depois de no ar: manutenção

### Segredos obrigatórios na nuvem

Com `REQUIRE_STRONG_SECRETS=true` (já no `render.yaml`) o backend **recusa subir** se `JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` ou `POSTGRES_PASSWORD` ainda estiverem nos valores padrão de desenvolvimento, e o log do Render diz exatamente quais variáveis corrigir (sem mostrar os valores). Em um serviço que já existe, adicione essa variável em *Environment*.

### Deploy automático do painel (GitHub Actions)

O workflow [`deploy-painel.yml`](../.github/workflows/deploy-painel.yml) publica o painel no Cloudflare Pages a cada push na `master` que mude `frontend/`: roda os testes, compila com a URL do backend e confere que o `config.json` aponta para o lugar certo antes de publicar. As variáveis não secretas (`API_BASE_URL`, `CLOUDFLARE_ACCOUNT_ID`) já estão no repositório. **Falta só um segredo**, que você cria uma vez:

1. No Cloudflare: **My Profile → API Tokens → Create Token → Create Custom Token**.
2. Permissão: **Account → Cloudflare Pages → Edit**. Recursos da conta: a conta do projeto. Crie e copie o token (aparece uma vez).
3. No terminal, dentro da pasta do projeto (o valor é digitado no prompt e **não passa por mais ninguém**):

```bash
gh secret set CLOUDFLARE_API_TOKEN
```

Enquanto o segredo não existir, o job termina como "pulado" (aviso, não erro). Para testar depois: *Actions → Deploy do painel → Run workflow*.

### Publicar o Player (APK, lista de downloads e manuais)

O site de downloads (`https://signage-downloads.pages.dev`) é um projeto separado do painel, de propósito: um deploy do painel nunca apaga os APKs. Ele guarda o **APK mais recente** (e as 4 versões anteriores), o `latest.json` que as TVs consultam, o atalho curto `/d` e os **manuais em PDF** (instalação na TV e uso do painel). O painel mostra tudo isso na página **Downloads e manuais**.

**Versão automática.** O `versionCode` é o número de commits do repositório (sempre cresce) e o `versionName` é `player/VERSION` + esse número (ex.: `0.4.71`). Não há mais nada para editar à mão; para uma mudança "grande", suba o `player/VERSION` (ex.: de `0.4` para `0.5`).

**Pelo seu computador:**

```bash
node scripts/release-player.mjs --notes "O que mudou nesta versão"   # APK novo + site + manuais
node scripts/release-player.mjs --site-only                          # só site e manuais (sem versão nova)
node scripts/release-player.mjs --dry-run                            # monta release-site/ sem publicar
```

O script compila o APK de release, assina, **confere que a chave é a mesma das versões já publicadas** (se não for, recusa: as TVs rejeitariam a atualização), baixa as versões anteriores do próprio site, gera os manuais em PDF/HTML, publica e **confere o que ficou no ar** (baixa o APK e compara o SHA-256, confere os PDFs e o CORS do `releases.json`).

**Automático (GitHub Actions):** o fluxo [`publicar-player.yml`](../.github/workflows/publicar-player.yml) faz o mesmo a cada push na `master` que mude `player/**` (APK novo) ou os manuais (só site). Antes de publicar ele roda os testes do Player: se algum falhar, nada é publicado. Ele só funciona depois de você criar os segredos **uma vez** (até lá termina como "pulado", sem erro):

1. Crie o token da Cloudflare (o mesmo da seção anterior) e deixe-o numa variável do seu terminal **apenas durante o comando** (PowerShell: `$env:CLOUDFLARE_API_TOKEN = "..."`).
2. Com o `gh auth login` feito, rode na raiz do projeto:

```bash
node scripts/configurar-ci.mjs
```

Ele grava como segredos do repositório a chave de assinatura (`SIGNAGE_KEYSTORE_BASE64`, `SIGNAGE_KEYSTORE_PASSWORD`, `SIGNAGE_KEY_ALIAS`, `SIGNAGE_KEY_PASSWORD`) e o `CLOUDFLARE_API_TOKEN`, **sem imprimir nenhum valor**. Atenção: isso entrega a chave de assinatura ao GitHub Actions (só os fluxos do repositório a leem). Se não quiser, não rode e publique pelo seu computador.

**Como as TVs ficam em dia.** Cada TV consulta o `latest.json` a cada 30 minutos; você pode adiantar no painel (**Dispositivos → Atualizar app** ou **Atualizar todas as TVs**: o backend marca o pedido e a TV percebe no próximo heartbeat, em até 30 s). O Android **sempre pede confirmação na TV** antes de instalar (não há atualização silenciosa sem MDM/root); sem confirmação em 10 minutos a TV cancela e volta a exibir. As TVs desatualizadas aparecem marcadas no painel.

**Chave de assinatura:** criada uma única vez com `node scripts/create-release-key.mjs` e guardada em `~/.signage` (a senha fica ao lado, nunca no repositório). **Faça backup dessa pasta.** Sem a mesma chave não há atualização por cima das TVs já instaladas (só desinstalando e reinstalando cada uma).

**Compatibilidade:** um único APK (Android 5.0 ou mais novo, qualquer processador) serve a todos os aparelhos; a tabela está no anexo do [INSTALACAO_TV.md](./INSTALACAO_TV.md).

**Manuais:** [INSTALACAO_TV.md](./INSTALACAO_TV.md) e [MANUAL_PAINEL.md](./MANUAL_PAINEL.md) viram PDF ilustrado (`scripts/build-manuals.mjs`, usa o Edge ou o Chrome do computador/runner). As capturas de tela ficam em `docs/manual/img/` (reais, do painel e do emulador de Android TV) e os esquemas são gerados por `scripts/manual-illustrations.mjs`. Para atualizar uma captura, rode o painel/emulador, tire a imagem nova e substitua o arquivo.

---

## Instalando no Fire Stick (depois da TCL)

> O passo a passo completo e detalhado (TCL e Fire TV Stick, com o app Downloader ou ADB) está em [INSTALACAO_TV.md](./INSTALACAO_TV.md). O resumo abaixo é só uma referência rápida.

O mesmo APK serve — o Fire OS é baseado em Android.

1. **Configurações → Meu Fire TV → Opções do desenvolvedor** (se não aparecer: **Sobre** → clique 7× no nome do aparelho).
2. Ative **Depuração ADB** e **Instalar apps desconhecidos**.
3. Veja o IP em **Sobre → Rede** e instale como na TCL: `adb connect IP:5555` + `adb install -r ...`.
4. O app aparece em **Configurações → Aplicativos → Gerenciar aplicativos instalados**, não na tela inicial.

Cuidados específicos:

- Algumas versões do Fire OS matam apps sideloaded em segundo plano ou ignoram o início automático. Meça isso no seu Fire Stick; se necessário, trato com uma solução específica.
- O Fire Stick tem pouca memória: prefira imagens até Full HD e vídeos MP4 H.264 de até 1080p.

---

## Segurança antes de deixar no ar

- Troque `ADMIN_PASSWORD` e `JWT_SECRET` (nunca use os valores do `.env.example`).
- O token do aparelho nunca vai para o servidor de mídia: o Player segue o redirecionamento sem reenviar a credencial (há teste automatizado para isso).
- O registro de aparelhos é público (qualquer TV pode pedir um código), mas só vira dispositivo ativo quando **você** digita o código no painel. Registros não ativados somem em 24 h e há teto de 100 pendentes.
- Chaves do Supabase S3 dão acesso total ao armazenamento: coloque apenas no Render, nunca no painel ou no app.

---

## Fontes (conferidas em out/2026)

- Render — [mudanças de plano de 1º/ago/2026 e limites do plano gratuito](https://jwatte.com/blog/render-com-platform-review/), [comparativo de planos gratuitos](https://flaviocopes.com/hosting-free-tiers/)
- Supabase — [pausa de projetos gratuitos](https://supabase.com/docs/guides/platform/free-project-pausing), [limite de arquivos no Storage](https://supabase.com/docs/guides/storage/uploads/file-limits), [S3 do Storage](https://supabase.com/docs/guides/storage/s3), [IPv4/IPv6 e pooler](https://supabase.com/docs/guides/troubleshooting/supabase--your-network-ipv4-and-ipv6-compatibility-cHe3BP)
- Neon — [plano gratuito](https://neon.com/faqs/managed-postgres-databases-free-tier)
- Oracle Cloud — [recursos Always Free](https://docs.oracle.com/en-us/iaas/Content/FreeTier/resourceref.htm), [redução de cota (InfoQ)](https://infoq.com/news/2026/07/oracle-cloud-free-tier-limits/)
- Fly.io e Koyeb — [levantamento de planos gratuitos](https://flaviocopes.com/hosting-free-tiers/) (Fly.io não tem mais plano gratuito contínuo para contas novas; Koyeb exige cartão)
