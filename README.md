# Digital Signage

Plataforma de Digital Signage para controlar remotamente conteúdo exibido em TVs.
Contexto e decisões completas em [PROJETO_DIGITAL_SIGNAGE_CONTEXTO_CLAUDE.md](./PROJETO_DIGITAL_SIGNAGE_CONTEXTO_CLAUDE.md).

## Estrutura

```text
backend/    API REST em Java + Spring Boot
frontend/   Painel Web em Angular + TypeScript
player/     Player para Android TV / Google TV Box
docs/       Decisões e documentação do projeto
```

## Rodando o backend

Local, sem Docker (requer Java 21 e Maven):

```bash
cd backend
mvn spring-boot:run
```

Com Docker:

```bash
cp .env.example .env
docker compose up --build
```

Health check: `GET http://localhost:8080/api/v1/health`

## Status atual

MVP em andamento — ver [docs/DECISIONS.md](./docs/DECISIONS.md) e a seção 22 do documento de contexto para a ordem de implementação.
