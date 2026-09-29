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
