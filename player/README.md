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
- **Layouts e páginas da web (0.6+)**: a configuração pode trazer `zones` (retângulos em % da tela, cada um com a sua lista); `ZonePlayer` toca a lista de cada zona e `Slideshow` monta as áreas. Itens `WEB` abrem a página num WebView (só https, sem cache). Sem `zones` (servidor antigo ou TV sem layout) é uma zona principal em tela cheia, como antes.
- **Monitoramento (0.5+)**: o heartbeat também leva a mídia que está na tela, as ocorrências (erros/avisos/"Player iniciado") e quantas vezes cada mídia terminou de ser exibida (`telemetry/Telemetry.kt`). Fila em memória, entrega em duas etapas (só sai da fila depois que o servidor confirma) e um relato que o servidor recusa com HTTP 400 é descartado, para nunca travar os heartbeats. Servidores antigos ignoram os campos novos.

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

## Atualização automática (sem Google Play)

O Player consulta `https://signage-downloads.pages.dev/latest.json` a cada 30 minutos (e na hora, quando o painel pede em **Dispositivos → Atualizar app**). Havendo versão mais nova, baixa o APK, confere o SHA‑256 e a versão e o entrega ao instalador do Android; depois da atualização o app reabre sozinho. Detalhes e **limitações medidas** (o Android pede confirmação na TV) em [docs/INSTALACAO_TV.md](../docs/INSTALACAO_TV.md#6-atualizacao-automatica).

- Libere uma vez "instalar apps desconhecidos" para o Player: `adb shell appops set com.digitalsignage.player REQUEST_INSTALL_PACKAGES allow`.
- Se ninguém confirmar em 10 minutos, o Player cancela a instalação e volta a exibir.
- Ajustes sem recompilar (extras do `am start`): `--ez auto_update false`, `--es update_window "02:00-05:00"`, `--es update_url https://…/latest.json`.

### Gerar e publicar uma versão

```bash
node scripts/create-release-key.mjs        # uma única vez: cria a chave em ~/.signage (FAÇA BACKUP)
node scripts/release-player.mjs --notes "O que mudou"   # compila, assina e publica o APK + manuais (versão automática; o CI faz o mesmo a cada push em player/**)
```

Antes, aumente `versionCode`/`versionName` em `app/build.gradle.kts`. A chave **nunca** vai para o git (`player/keystore.properties` está ignorado).

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
- [x] Verificado no **emulador de Android TV 14** (ativação, imagens, vídeo, loop, troca de playlist, offline + app reaberto, início automático após reboot).
- [ ] **Validado em TV física** (TCL / Fire Stick): ainda não. O emulador não reproduz firmware/decodificadores de cada fabricante.

## Robustez (0.8+)

- **Reabrir sozinho** (`restart/`): depois de uma queda o `PlayerApp` grava o motivo e agenda um alarme (3 s, com espera crescente em quedas seguidas, teto de 5 min); um vigia confere a cada 2 min se a tela deu sinal de vida nos últimos 150 s e, se não, reabre pelo mesmo caminho do boot (`LaunchHelper`, com o overlay invisível exigido pelo Android 10+). Voltar/Início (`onUserLeaveHint`) dão 10 min de folga ao vigia. O motivo vira um aviso no histórico da TV na abertura seguinte. Teste no emulador: `adb shell am crash com.digitalsignage.player`.
- **Pouca memória** (`memory/MemoryProfile`): `ActivityManager.isLowRamDevice`, heap pequeno por app ou ≤ 1,2 GB de RAM escolhem o perfil LOW (imagens em até 1280×720 em RGB_565, um vídeo por vez, ExoPlayer e WebView soltos após o uso, buffers pequenos); `onTrimMemory` solta o que não está à vista em qualquer perfil.
- **Espaço em disco** (`ConfigSync`): antes de baixar confere se o conteúdo novo cabe (mantendo 50 MB livres); se não couber, limpa restos de downloads e, persistindo, recusa com `InsufficientSpaceException` (a playlist atual continua).
- **Vídeos**: o ExoPlayer tenta decodificadores alternativos (`setEnableDecoderFallback`) e, se nenhum servir, a mensagem diz para reenviar em H.264 de até 1080p (`ErrorMessages.playbackFailure`). `.mov` (QuickTime) toca como MP4.
