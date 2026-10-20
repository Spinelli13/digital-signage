# Player

Plataforma: **Android TV / Google TV / Fire TV**. App Kotlin single-module (`app`), tela cheia, tudo que importa roda sem depender do servidor estar no ar.

## Como funciona

```text
abre → gera token secreto (só o hash vai ao servidor) → registra → mostra CÓDIGO de 6 caracteres
     → admin digita o código no painel → TV vira "ativa"
     → heartbeat a cada 30 s (informa versão sincronizada, recebe playlist+versão desejadas)
     → mudou? baixa a config + mídias que faltam → valida SHA-256 → só então troca a playlist
     → toca em loop: imagem pela duração, vídeo até o fim
```

Pontos de projeto:

- **Offline‑first**: ao abrir, toca o que está em cache sem esperar rede. Falha de rede ou de download nunca interrompe a playlist em exibição.
- **Troca atômica**: a nova playlist só entra depois que *todas* as mídias foram baixadas e conferidas (checksum + tamanho); download corrompido é descartado.
- **Identidade de conteúdo = playlist + versão** (versões podem coincidir entre playlists diferentes).
- **Token nunca vai para o armazenamento de mídia**: redirecionamentos (URL assinada S3) são seguidos à mão, sem reenviar `X-Device-Token`.
- Dispositivo excluído/desativado no painel → a TV esquece o conteúdo e volta a pedir ativação (ou mostra "desativado").

## Stack

```text
Gradle 8.14.5 · AGP 8.13.2 · Kotlin 2.1.21 · compileSdk/targetSdk 36 · minSdk 21
OkHttp 4.12 · Media3 ExoPlayer 1.4.1 · Coroutines 1.9
```

Versões fixadas deliberadamente na última linha estável 8.x — ver `docs/DECISIONS.md`.

## Build

```bash
cd player
./gradlew assembleDebug -PserverUrl=http://192.168.0.10:8080
./gradlew testDebugUnitTest        # testes JVM (não precisam de TV nem emulador)
```

`-PserverUrl` (ou `signage.serverUrl=...` em `player/local.properties`) define o endereço do backend embutido no APK. O APK **debug** aceita `http://` (teste em rede local); o **release** exige `https://`.

## Instalar e configurar

```bash
adb connect IP_DA_TV:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.digitalsignage.player/.MainActivity
```

Sem recompilar, trocando de servidor ou voltando ao estado de fábrica:

```bash
adb shell am start -n com.digitalsignage.player/.MainActivity --es server_url https://meu-servidor
adb shell am start -n com.digitalsignage.player/.MainActivity --ez reset true
```

Na tela de ativação / erro, **OK** no controle abre um diálogo para digitar o endereço do servidor.

Início automático em Android 10+: `adb shell appops set com.digitalsignage.player SYSTEM_ALERT_WINDOW allow`.

Passo a passo completo (TCL, Fire Stick, nuvem): [docs/DEPLOY.md](../docs/DEPLOY.md).

## Status

- [x] Ativação por código, token do dispositivo, heartbeat.
- [x] Download/cache com validação de checksum; limpeza de mídias antigas.
- [x] Reprodução de imagens (decodificação reduzida p/ pouca RAM) e vídeos MP4 (ExoPlayer), loop, item corrompido é pulado.
- [x] Funciona offline e retoma sozinho quando a rede volta.
- [x] 39 testes JVM (cliente HTTP com MockWebServer, cache, sincronização, máquina de estados).
- [ ] **Validado em hardware real** — nada disso foi rodado numa TV ainda (a máquina de desenvolvimento não tem emulador Android). Reprodução na tela, tela cheia e início automático só se confirmam na TCL/Fire Stick.
