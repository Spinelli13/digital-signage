# Vyzzu

Vyzzu é uma plataforma de comunicação audiovisual e sinalização digital (digital signage) para controlar remotamente conteúdo exibido em TVs, painéis de LED e totens. A identidade visual está em [docs/MARCA_VYZZU.md](./docs/MARCA_VYZZU.md).
Contexto e decisões completas em [PROJETO_DIGITAL_SIGNAGE_CONTEXTO_CLAUDE.md](./PROJETO_DIGITAL_SIGNAGE_CONTEXTO_CLAUDE.md).

## Estrutura

```text
backend/    API REST em Java + Spring Boot
frontend/   Painel Web em Angular + TypeScript
player/     Player para Android TV / Google TV / Fire TV
docs/       Decisões e documentação do projeto (DEPLOY.md = como colocar no ar e testar na TV; DESIGN_REVIEW.md = guia de UX/UI do painel)
```

## Rodando tudo localmente

Backend sem Docker (Postgres embutido; dados em `backend/data/dev-pg`):

```bash
cd backend
mvn -q test-compile exec:java -Dexec.mainClass=com.digitalsignage.backend.DevApplication -Dexec.classpathScope=test
```

Ou com Docker (Postgres real):

```bash
cp .env.example .env
docker compose up --build
```

Painel: `cd frontend && npm install && npm start` → http://localhost:4200 (login padrão em `.env.example`).

Health check: `GET http://localhost:8080/api/v1/health`

## Testes (rodam também no GitHub Actions a cada push)

```bash
cd backend  && mvn test                       # API + regressões, Postgres real embutido (sem Docker)
cd frontend && npm test -- --watch=false      # painel
cd player   && ./gradlew testDebugUnitTest    # Player (JVM)
```

Cada bug corrigido no projeto tem um teste de regressão; a lista está em [docs/DECISIONS.md](./docs/DECISIONS.md).

## Baixar o Player e instalar na TV

Último APK, lista de versões e **manuais em PDF ilustrados** (instalação na TV TCL/Fire TV Stick e uso do painel): **https://signage-downloads.pages.dev** (também no menu **Downloads e manuais** do painel) — atalho curto para o app Downloader: `signage-downloads.pages.dev/d`. O Player se atualiza sozinho a partir daí (a TV precisa de um OK do Android) e o painel pode pedir a atualização na hora. Um único APK (Android 5.0+) atende todos os aparelhos. Fontes dos manuais: [docs/INSTALACAO_TV.md](./docs/INSTALACAO_TV.md) e [docs/MANUAL_PAINEL.md](./docs/MANUAL_PAINEL.md).

## Colocando no ar / testando na TV

Passo a passo (rede local com a TCL → túnel → nuvem gratuita → Fire Stick): **[docs/DEPLOY.md](./docs/DEPLOY.md)**.

## Status atual

MVP funcional de ponta a ponta no software (painel ↔ backend ↔ Player simulado). Falta validar o Player em hardware real — ver checklist na seção 22 do documento de contexto.
