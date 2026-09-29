# Player

Plataforma: **Android TV / Google TV Box**. App Kotlin single-module (`app`), tela cheia, sem bibliotecas de rede/mídia ainda (chegam nos próximos passos: ativação, download/cache e reprodução).

## Stack

```text
Gradle 8.14.5
AGP 8.13.2
Kotlin 2.1.21
compileSdk / targetSdk 36
minSdk 21
```

Versões fixadas deliberadamente na última linha estável 8.x (não a 9.x mais recente) — ver `docs/DECISIONS.md` na raiz do repo.

## Build

```bash
cd player
./gradlew assembleDebug
```

Gera `app/build/outputs/apk/debug/app-debug.apk`.

## Instalar no Android TV Box

Com o dispositivo em modo desenvolvedor (depuração USB ou por rede) e visível em `adb devices`:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Status atual

- [x] Projeto Gradle configurado, `local.properties` apontando pro SDK local.
- [x] Activity em tela cheia (immersive), mantém a tela sempre ligada.
- [x] `BroadcastReceiver` de boot (`BOOT_COMPLETED`/`QUICKBOOT_POWERON`) relançando o app.
- [x] Ícone e banner de TV (placeholders simples).
- [ ] Validado em hardware real (Android TV Box) — ainda não testado em dispositivo físico.
- [ ] Ativação por código, download/cache, reprodução, heartbeat — próximos passos.
