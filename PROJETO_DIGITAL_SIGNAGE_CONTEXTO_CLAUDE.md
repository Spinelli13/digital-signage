# Digital Signage — Contexto de Desenvolvimento para Claude

## 1. Objetivo

Criar uma plataforma de Digital Signage para controlar remotamente conteúdos exibidos em TVs.

O produto final terá dois componentes:

1. **Painel Web administrativo**: cadastra dispositivos, mídias, playlists, campanhas e acompanha status.
2. **Player**: executa próximo/na TV, baixa e armazena conteúdo, reproduz a programação e envia seu status ao backend.

Fluxo principal:

```text
Administrador
    ↓
Painel Web
    ↓
Backend/API
    ↓
Player
    ↓
TV
```

A internet deve servir principalmente para sincronização. A reprodução deve continuar funcionando quando a internet cair.

---

## 2. Escopo inicial — MVP

Começar deliberadamente pequeno:

- 1 administrador;
- 1 TV;
- 1 Player;
- 1 local;
- 1 playlist;
- imagens e vídeos;
- painel Web;
- ativação do Player por código;
- download/cache local;
- reprodução em loop;
- heartbeat;
- sincronização;
- funcionamento offline.

### Critério de sucesso

Deve ser possível:

1. entrar no painel;
2. cadastrar uma TV/dispositivo;
3. abrir o Player e obter um código;
4. vincular o código no painel;
5. enviar uma imagem/vídeo;
6. criar uma playlist;
7. associar a playlist à TV;
8. o Player baixar o conteúdo;
9. a TV reproduzir a playlist;
10. alterar a playlist no painel;
11. o Player sincronizar;
12. continuar reproduzindo conteúdo já baixado sem internet.

**Não tentar implementar a plataforma completa antes desse fluxo funcionar.**

---

## 3. Tecnologias

### Backend

**Java + Spring Boot**.

Responsabilidades:

- REST API;
- autenticação;
- dispositivos;
- mídias;
- playlists;
- configuração do Player;
- heartbeat;
- sincronização;
- futuramente WebSocket.

Preferir Spring idiomático, código simples e módulos com responsabilidades claras.

### Frontend

**Angular + TypeScript**.

O painel deve ser responsivo e funcionar em desktop, tablet e celular.

### Banco

**PostgreSQL**.

### Mídias

Não armazenar arquivos grandes no PostgreSQL.

Durante desenvolvimento pode haver armazenamento local. Na evolução usar Object Storage compatível com S3.

### Infraestrutura

Usar Docker/Docker Compose para desenvolvimento.

O projeto deve poder começar gratuitamente ou dentro de planos gratuitos, mas não assumir que um plano gratuito específico permanecerá disponível. Validar limites atuais dos provedores antes do deploy público.

---

## 4. Hardware / Player

Smart TV não deve ser requisito obrigatório.

Estratégia preferencial:

```text
TV
 +
Android TV / Google TV Box
 +
Player
```

Assim a TV funciona como tela e a inteligência fica no Player.

Fire TV também é uma possibilidade, mas não implementar várias plataformas no MVP.

### Decisão de Player

Antes de produção, fazer uma prova técnica em uma única plataforma e validar:

- reprodução MP4;
- imagens;
- tela cheia;
- armazenamento local;
- inicialização automática;
- recuperação após reinicialização;
- funcionamento offline;
- comunicação HTTPS com backend.

Depois homologar essa plataforma como Player inicial.

O painel administrativo não deve rodar na TV. A TV deve mostrar somente a publicidade/conteúdo, exceto telas de ativação/configuração.

---

## 5. Ativação

Primeiro acesso do Player:

```text
DIGITAL SIGNAGE

Código de ativação:
8F32A9

Aguardando ativação...
```

No painel:

```text
Dispositivos
→ Adicionar dispositivo
→ código 8F32A9
→ nome "TV Recepção"
→ local "Loja Centro"
```

Após ativação o Player recebe suas credenciais.

O Player não deve usar usuário/senha administrativo.

Usar identidade própria:

```text
device_id
device_token
```

Tokens devem poder ser revogados.

---

## 6. Funcionalidades do painel MVP

### Dashboard

Mostrar:

- quantidade de dispositivos;
- online/offline;
- última comunicação;
- playlist atual;
- atividades recentes.

### Dispositivos

Campos iniciais:

```text
id
nome
código/identidade
local
status
último heartbeat
playlist atual
versão do Player
```

Ações:

- cadastrar;
- editar;
- ativar/desativar;
- remover;
- visualizar detalhes.

### Biblioteca

Upload:

- JPG;
- PNG;
- WEBP;
- MP4.

Metadados:

```text
id
nome
tipo
mime_type
storage_key/url
tamanho
duração
checksum
created_at
updated_at
```

### Playlists

Criar playlist e ordenar conteúdos.

Exemplo:

```text
01 Promoção X       10s
02 Vídeo             30s
03 Oferta            10s
```

Imagens têm duração configurável. Vídeos podem usar sua duração natural.

---

## 7. Funcionamento do Player

Fluxo:

```text
Player inicia
   ↓
lê configuração local
   ↓
começa reprodução com o cache disponível
   ↓
tenta conexão com backend
   ↓
verifica versão da configuração
   ↓
baixa arquivos ausentes
   ↓
atualiza cache
   ↓
reproduz nova programação
```

O Player nunca deve apagar uma versão válida antes de validar a nova versão.

### Cache

Conceitualmente:

```text
cache/
├── config.json
└── media/
    ├── arquivo1.jpg
    ├── arquivo2.mp4
    └── arquivo3.png
```

Usar checksum para verificar integridade.

---

## 8. Offline

Requisito obrigatório.

Se a internet cair:

```text
Internet ❌
    ↓
Cache local
    ↓
Playlist atual
    ↓
Reprodução continua
```

Quando voltar:

```text
Internet ✅
    ↓
verifica versão
    ↓
baixa alterações
    ↓
atualiza cache
```

---

## 9. Heartbeat

O Player deve informar periodicamente que está funcionando.

Exemplo:

```http
POST /api/v1/devices/{id}/heartbeat
```

Payload possível:

```json
{
  "device_id": "abc123",
  "player_version": "0.1.0",
  "current_playlist_id": "playlist-001",
  "current_media_id": "media-003"
}
```

Backend armazena `last_seen_at`.

Status pode ser calculado pelo tempo desde o último heartbeat.

---

## 10. Sincronização

No MVP usar **polling**, não WebSocket.

Exemplo:

```http
GET /api/v1/devices/{id}/config
```

O backend deve retornar uma versão da configuração.

Exemplo:

```text
config_version = 15
```

Se o Player possui 14, sincroniza. Se possui 15, não baixa tudo novamente.

Depois do MVP, WebSocket pode ser usado para atualização quase imediata.

---

## 11. API inicial

Versionar:

```text
/api/v1
```

### Health

```http
GET /api/v1/health
```

### Auth

```http
POST /api/v1/auth/login
```

### Devices

```http
GET    /api/v1/devices
POST   /api/v1/devices
GET    /api/v1/devices/{id}
PUT    /api/v1/devices/{id}
DELETE /api/v1/devices/{id}
POST   /api/v1/devices/activate
POST   /api/v1/devices/{id}/heartbeat
GET    /api/v1/devices/{id}/config
```

### Media

```http
GET    /api/v1/media
POST   /api/v1/media
GET    /api/v1/media/{id}
DELETE /api/v1/media/{id}
```

### Playlists

```http
GET    /api/v1/playlists
POST   /api/v1/playlists
GET    /api/v1/playlists/{id}
PUT    /api/v1/playlists/{id}
DELETE /api/v1/playlists/{id}
```

---

## 12. Banco inicial

### users

```text
id
name
email
password_hash
created_at
updated_at
```

### devices

```text
id
name
activation_code
device_token_hash
location
status
last_seen_at
player_version
current_playlist_id
config_version
created_at
updated_at
```

### media

```text
id
name
type
mime_type
storage_key
file_size
duration_seconds
checksum
created_at
updated_at
```

### playlists

```text
id
name
description
config_version
created_at
updated_at
```

### playlist_items

```text
id
playlist_id
media_id
position
duration_seconds
created_at
```

### device_playlists

```text
device_id
playlist_id
created_at
updated_at
```

---

## 13. Estrutura sugerida do repositório

```text
digital-signage/
├── backend/
│   ├── src/main/java/com/digitalsignage/backend/
│   │   ├── auth/
│   │   ├── devices/
│   │   ├── media/
│   │   ├── playlists/
│   │   ├── heartbeat/
│   │   ├── storage/
│   │   └── config/
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/
│   ├── Dockerfile
│   └── pom.xml
│
├── frontend/
│   ├── src/app/
│   │   ├── components/
│   │   ├── layouts/
│   │   ├── pages/
│   │   │   ├── dashboard/
│   │   │   ├── devices/
│   │   │   ├── media/
│   │   │   └── playlists/
│   │   ├── services/
│   │   └── models/
│   ├── package.json
│   └── angular.json
│
├── player/
│   ├── activation/
│   ├── api/
│   ├── config/
│   ├── downloader/
│   ├── cache/
│   ├── playlist/
│   ├── heartbeat/
│   └── playback/
│
├── docs/
├── docker-compose.yml
├── .env.example
└── README.md
```

A implementação concreta do Player pode mudar conforme a plataforma escolhida.

---

## 14. Interface

O protótipo de interface já criado serve como referência de UX.

Menu planejado:

```text
Dashboard
Dispositivos
Biblioteca
Playlists
Campanhas
Pontos & Locais
Relatórios
Configurações
```

No MVP implementar principalmente:

```text
Dashboard
Dispositivos
Biblioteca
Playlists
Configurações básicas
```

Campanhas, locais avançados e relatórios podem ficar para depois.

### Princípios da interface

- simples;
- profissional;
- poucos passos para publicar;
- status da TV evidente;
- feedback de sincronização;
- fácil identificação de erros.

---

## 15. Dashboard MVP

Exemplo:

```text
DIGITAL SIGNAGE

1 dispositivo

🟢 TV Recepção
Última comunicação: agora

Playlist:
Institucional + Promoções
```

---

## 16. Fluxo completo da primeira demonstração

### 1. Administrador entra no painel

### 2. Cadastra dispositivo

```text
TV Recepção
```

### 3. Player exibe código

```text
8F32A9
```

### 4. Administrador ativa o código

### 5. Envia:

```text
promocao.jpg
video.mp4
```

### 6. Cria:

```text
Playlist Recepção
```

### 7. Adiciona as mídias

### 8. Associa a playlist à TV

### 9. Player baixa os arquivos

### 10. TV reproduz

### 11. Administrador altera a playlist

### 12. Player sincroniza

### 13. Internet é desligada

### 14. TV continua reproduzindo o cache

Esse fluxo é a principal demonstração técnica.

---

## 17. Desenvolvimento gratuito inicialmente

Objetivo:

```text
Desenvolvimento local
        ↓
Docker
        ↓
Teste real
        ↓
Deploy gratuito/baixo custo
        ↓
Validação com cliente
        ↓
Escala
```

Durante desenvolvimento:

- PostgreSQL local;
- armazenamento local;
- backend Spring Boot local;
- frontend Angular local;
- Player em equipamento de teste.

No deploy, separar:

```text
Frontend
Backend
PostgreSQL
Storage
```

Validar preços e limites atuais dos provedores antes de escolher hospedagem, pois planos gratuitos mudam.

---

## 18. Docker

Criar ambiente reproduzível.

Exemplo:

```text
docker compose up
```

Serviços principais:

```text
backend
postgres
```

Frontend pode rodar com:

```bash
npm run dev
```

Criar `.env.example`.

Nunca versionar `.env` com segredos.

---

## 19. Roadmap

### Fase 1 — MVP

```text
1 TV
1 Player
1 administrador
Mídias
Playlist
Ativação
Cache
Heartbeat
Sincronização
```

### Fase 2 — Multi-dispositivo ✅ (concluída em 2026-10-20)

```text
[x] Várias TVs (filtros, busca, seleção e ações em massa)
[x] Locais (Pontos & Locais)
[x] Grupos
[x] Status (online/offline/nunca conectou/pendente/desativada, calculado no servidor; dashboard)
```

Detalhes em [docs/DECISIONS.md](./docs/DECISIONS.md) (parte 5).

### Fase 3 — Campanhas ✅ (concluída em 2026-10-20)

```text
[x] Agendamento (resolvido no servidor a cada heartbeat; o Player não muda)
[x] Data início/fim (inclusivas)
[x] Horários (dias da semana, janela diária, fuso por campanha, atravessa a meia-noite)
[x] Prioridade (e desempate por alvo mais específico, depois a mais nova)
[x] Segmentação (todas as TVs, TV, local ou grupo; simulador no painel)
```

Detalhes em [docs/DECISIONS.md](./docs/DECISIONS.md) (parte 6).

### Fase 4 — Monitoramento ✅ (concluída em 2026-10-20)

```text
[x] Histórico (linha do tempo de cada TV: ativação, conexão, queda, conteúdo, versão; o servidor registra sozinho)
[x] Logs (erros e avisos relatados pelo Player 0.5 dentro do heartbeat, com fila e entrega em duas etapas)
[x] Relatórios (disponibilidade, exibições por mídia/TV/dia, inventário e eventos; CSV para o Excel)
[x] Alertas (TV sem contato, nunca conectou, erros repetidos; webhook https com proteção contra SSRF)
```

Detalhes em [docs/DECISIONS.md](./docs/DECISIONS.md) (parte 7).

### Fase 5 — Recursos avançados ✅ (concluída em 2026-10-20)

```text
[x] Layouts (divisão da tela; a TV sem layout continua em tela cheia, como sempre)
[x] Zonas (retângulos em % da tela, cada uma com a sua playlist; a principal exibe a da TV e das campanhas)
[x] QR Code (gerado no painel como imagem; o Player não muda para ele)
[x] Templates (seis modelos de layout prontos)
[x] Conteúdo web (páginas https exibidas por WebView no Player 0.6)
```

Detalhes em [docs/DECISIONS.md](./docs/DECISIONS.md) (parte 8).

### Fase 6 — SaaS ✅ (concluída em 2026-10-20)

```text
[x] Multi-tenant (um banco, `tenant_id` em cada tabela de negócio, isolamento automático com @TenantId do Hibernate)
[x] Clientes (a operação cadastra, edita, suspende e "entra" no painel do cliente para dar suporte)
[x] Permissões (Visualizador, Editor, Administrador e Operador da plataforma; equipe gerenciada pelo Administrador)
[x] Planos (preço e limites de TVs, espaço de mídias e usuários; o servidor recusa o que passa do limite)
[x] Cobrança (faturas, ciclo automático a cada hora, atraso e suspensão; pagamento registrado à mão — SEM gateway de pagamento nem e-mails)
```

Detalhes em [docs/DECISIONS.md](./docs/DECISIONS.md) (parte 9) e no manual do painel (capítulos 11 a 13).

### Experiência do painel ✅ (pedido pelo dono em 2026-10-20, depois da Fase 6)

```text
[x] Marca nova: verde escuro + prata + preto (painel, logo, Player, manuais)
[x] Modo escuro (automático, claro ou escuro; guardado no aparelho)
[x] Uso no celular (menu em gaveta, atalhos na base, tabelas em cartões, janelas em folha)
[x] Idioma: português (nativo) e inglês, com troca na hora; teste que reprova texto sem tradução
```

Detalhes em [docs/DECISIONS.md](./docs/DECISIONS.md) (parte 10) e no manual do painel (seções 2.1 a 2.3).

### Revisão de UX/UI do painel ✅ (pedido pelo dono em 2026-10-21)

```text
[x] Diagnóstico de UX/UI e pesquisa (docs/DESIGN_REVIEW.md)
[x] Sistema de design: ícones Lucide embutidos (sem emoji), Inter, selos, estados vazios, esqueleto, menu "mais ações"
[x] Todas as telas refeitas (menu agrupado, alertas no menu, dashboard com saúde da rede e primeiros passos, tabelas com uma ação + menu)
[x] Contraste AA medido e corrigido; conferência em 1280 e 390 px, claro e escuro
[x] Teste que reprova emoji e símbolo decorativo no painel
```

Detalhes em [docs/DESIGN_REVIEW.md](./docs/DESIGN_REVIEW.md) e [docs/DECISIONS.md](./docs/DECISIONS.md) (parte 11).

---

## 20. O que NÃO fazer agora

Não implementar no MVP (lista original; **atualização:** múltiplas zonas, multi-tenant, cobrança, assinatura e usuários com papéis foram implementados depois, nas fases 5 e 6; o restante segue fora do escopo):

- multi-tenant completo;
- cobrança;
- assinatura;
- marketplace;
- múltiplos usuários complexos;
- mapas reais;
- analytics avançado;
- métricas de audiência;
- reconhecimento facial;
- sensores;
- editor drag-and-drop;
- múltiplas zonas;
- suporte simultâneo a Samsung/LG/Roku/Android/Fire TV;
- app administrativo Android/iOS;
- WebSocket antes de o polling funcionar;
- arquitetura distribuída desnecessária.

**Regra: primeiro fazer uma TV funcionar perfeitamente.**

---

## 21. Critérios de aceite

### Backend

- [x] Spring Boot inicia.
- [x] PostgreSQL conecta.
- [x] migrations funcionam.
- [x] login funciona.
- [x] dispositivo pode ser criado.
- [x] ativação por código funciona.
- [x] mídia pode ser cadastrada.
- [x] playlist pode ser criada.
- [x] playlist pode ser associada.
- [x] Player consegue buscar configuração.
- [x] heartbeat funciona.

### Frontend

- [x] Login.
- [x] Dashboard.
- [x] Dispositivos.
- [x] Biblioteca.
- [x] Playlist.
- [x] Associação playlist/dispositivo.
- [x] Status online/offline.

### Player

- [x] inicia.
- [x] exibe código.
- [x] ativa.
- [x] autentica.
- [x] baixa mídia.
- [x] mantém cache.
- [x] reproduz imagem.
- [x] reproduz vídeo.
- [x] executa playlist.
- [x] envia heartbeat.
- [x] funciona sem internet.
- [x] sincroniza após retorno.

> Verificado no painel publicado (Cloudflare Pages) + backend (Render) + banco (Supabase) e no **emulador de Android TV 14**. Ainda **não** verificado em TV física (TCL, Fire Stick) nem o upload de vídeos grandes (>50 MB: limite do plano gratuito do Supabase). Itens do painel que o escopo cita e ainda faltam: "atividades recentes" no dashboard e "visualizar detalhes" do dispositivo.

---

## 22. Ordem recomendada de implementação

A IA deve seguir aproximadamente esta ordem:

1. [x] Estrutura do repositório.
2. [x] Backend Spring Boot mínimo.
3. [x] `GET /health`.
4. [x] PostgreSQL.
5. [x] Migrations.
6. [x] Autenticação.
7. [x] CRUD de dispositivos.
8. [x] Biblioteca de mídia.
9. [x] Playlists.
10. [x] Endpoint de configuração do Player.
11. [x] Player mínimo (compilado e validado — instalação em hardware real ainda pendente).
12. [x] Ativação (código na TV + token do aparelho; validado no software, falta na TV física).
13. [x] Download/cache (checksum SHA-256, troca atômica; validado em testes JVM, falta na TV física).
14. [x] Reprodução (imagens + vídeo MP4; **verificada no emulador de Android TV 14**; falta confirmar na TCL/Fire Stick reais).
15. [x] Heartbeat.
16. [x] Integração painel ↔ Player (painel Angular + API; fluxo conferido no navegador contra o backend real).
17. [x] Teste sem internet (backend parado + app reaberto → toca o cache; verificado no emulador; falta confirmar na TV real).
18. [x] Teste após reinicialização (verificado no emulador com a permissão "exibir sobre outros apps" + overlay invisível; ver docs/DEPLOY.md; falta confirmar na TV real).
19. [~] Deploy inicial (guia completo em [docs/DEPLOY.md](./docs/DEPLOY.md): rede local → túnel → nuvem gratuita; **nada foi publicado ainda**).
20. [ ] Só depois iniciar expansão.

Transversal: testes de regressão automatizados (backend 177, painel 145, Player 86) rodando no GitHub Actions a cada push. APK publicado numa lista de downloads com atualização automática pelo site (sem Google Play), pedido de atualização pelo painel e um único APK para Android 5.0+; manuais em PDF ilustrados (docs/INSTALACAO_TV.md e docs/MANUAL_PAINEL.md) publicados junto com cada versão; erros tratados e exibidos nos dois clientes (painel: até fechar; TV: 10 s).

Detalhes de cada etapa concluída (decisões tomadas, bugs encontrados e corrigidos) ficam em [docs/DECISIONS.md](./docs/DECISIONS.md).

---

## 23. Princípios para a IA de desenvolvimento

### Não aumentar o escopo sem necessidade

Se uma solução simples atende o MVP, escolher a simples.

### Não reescrever código funcional sem motivo

Preservar funcionalidades existentes.

### Fazer mudanças pequenas e testáveis

Evitar grandes alterações simultâneas.

### Priorizar integração real

O objetivo não é apenas criar telas bonitas.

O objetivo é fazer:

```text
Painel → Backend → Player → TV
```

funcionar.

### Segurança

Nunca colocar secrets no código.

### Documentação

Registrar decisões importantes no `/docs`.

### Compatibilidade futura

Estruturar o núcleo para várias TVs, mas não implementar a complexidade antes da necessidade.

---

## 24. Arquitetura futura

```text
                    ┌────────────────────┐
                    │      PAINEL WEB     │
                    │ Angular + TypeScript│
                    └──────────┬─────────┘
                               │ HTTPS
                               v
                    ┌────────────────────┐
                    │ BACKEND JAVA/SPRING │
                    │                    │
                    │ REST API           │
                    │ Auth               │
                    │ Devices            │
                    │ Media              │
                    │ Playlists          │
                    │ Campaigns          │
                    │ WebSocket          │
                    └───────┬───────┬────┘
                            │       │
                 ┌──────────┘       └──────────┐
                 v                             v
          ┌──────────────┐              ┌──────────────┐
          │ PostgreSQL   │              │ Object       │
          │              │              │ Storage      │
          └──────────────┘              └──────┬───────┘
                                               │
                      ┌────────────────────────┼──────────┐
                      v                        v          v
                 📺 Player 1              📺 Player 2   📺 Player N
```

No MVP:

```text
Angular
  ↓
Spring Boot API
  ↓
PostgreSQL
  ↓
1 Player
```

---

## 25. Contexto comercial

O produto poderá futuramente atender:

- lojas;
- filiais;
- recepções;
- restaurantes;
- clínicas;
- órgãos públicos;
- empresas;
- redes de divulgação;
- pontos de atendimento.

A longo prazo, uma campanha poderá ser direcionada a:

```text
uma TV
um local
um grupo
todas as TVs
```

por:

```text
data
horário
prioridade
frequência
```

Isso permite transformar o projeto em uma plataforma comercial de Digital Signage.

---

# INSTRUÇÃO FINAL PARA A IA

Este documento é o contexto inicial do projeto.

**Não comece implementando todas as funcionalidades descritas no roadmap.**

Comece pelo MVP de **uma única TV**.

A prioridade é provar o fluxo:

```text
UPLOAD
  ↓
PLAYLIST
  ↓
PUBLICAÇÃO
  ↓
SINCRONIZAÇÃO
  ↓
CACHE
  ↓
REPRODUÇÃO NA TV
```

O primeiro objetivo técnico é conseguir controlar uma TV real a partir do painel Web.

Depois que esse fluxo estiver funcionando e testado, evoluir gradualmente para múltiplos dispositivos, locais, campanhas, agendamento e recursos avançados.

**Stack principal definida:**

```text
Backend: Java + Spring Boot
Frontend: Angular + TypeScript
Database: PostgreSQL
Player: Android TV/Google TV Box — plataforma homologada para a prova técnica
Comunicação inicial: REST + polling
Armazenamento inicial: local / Object Storage posteriormente
Infraestrutura: Docker
```

**Não adicionar complexidade arquitetural sem necessidade.**

A regra do projeto é:

> **Primeiro uma TV funcionando perfeitamente. Depois uma rede de TVs.**
