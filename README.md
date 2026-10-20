# Digital Signage

Plataforma de Digital Signage para controlar remotamente conteúdo exibido em TVs.
Contexto e decisões completas em [PROJETO_DIGITAL_SIGNAGE_CONTEXTO_CLAUDE.md](./PROJETO_DIGITAL_SIGNAGE_CONTEXTO_CLAUDE.md).

## Estrutura

```text
backend/    API REST em Java + Spring Boot
frontend/   Painel Web em Angular + TypeScript
player/     Player para Android TV / Google TV / Fire TV
docs/       Decisões e documentação do projeto (DEPLOY.md = como colocar no ar e testar na TV)
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

Lista de versões do APK e manual de instalação (TCL Android TV e Fire TV Stick): **https://signage-downloads.pages.dev** — atalho curto para o app Downloader: `signage-downloads.pages.dev/d`. O Player se atualiza sozinho a partir daí. Manual em [docs/INSTALACAO_TV.md](./docs/INSTALACAO_TV.md).

## Colocando no ar / testando na TV

Passo a passo (rede local com a TCL → túnel → nuvem gratuita → Fire Stick): **[docs/DEPLOY.md](./docs/DEPLOY.md)**.

## Status atual

MVP funcional de ponta a ponta no software (painel ↔ backend ↔ Player simulado). Falta validar o Player em hardware real — ver checklist na seção 22 do documento de contexto.
