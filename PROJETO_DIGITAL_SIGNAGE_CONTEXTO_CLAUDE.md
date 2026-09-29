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

### Fase 2 — Multi-dispositivo

```text
Várias TVs
Locais
Grupos
Status
```

### Fase 3 — Campanhas

```text
Agendamento
Data início/fim
Horários
Prioridade
Segmentação
```

### Fase 4 — Monitoramento

```text
Histórico
Logs
Relatórios
Alertas
```

### Fase 5 — Recursos avançados

```text
Layouts
Zonas
QR Code
Templates
Conteúdo web
```

### Fase 6 — SaaS

```text
Multi-tenant
Clientes
Permissões
Planos
Cobrança
```

---

## 20. O que NÃO fazer agora

Não implementar no MVP:

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
- [ ] ativação por código funciona.
- [x] mídia pode ser cadastrada.
- [ ] playlist pode ser criada.
- [ ] playlist pode ser associada.
- [ ] Player consegue buscar configuração.
- [ ] heartbeat funciona.

### Frontend

- [ ] Login.
- [ ] Dashboard.
- [ ] Dispositivos.
- [ ] Biblioteca.
- [ ] Playlist.
- [ ] Associação playlist/dispositivo.
- [ ] Status online/offline.

### Player

- [ ] inicia.
- [ ] exibe código.
- [ ] ativa.
- [ ] autentica.
- [ ] baixa mídia.
- [ ] mantém cache.
- [ ] reproduz imagem.
- [ ] reproduz vídeo.
- [ ] executa playlist.
- [ ] envia heartbeat.
- [ ] funciona sem internet.
- [ ] sincroniza após retorno.

---

## 22. Ordem recomendada de implementação

A IA deve seguir aproximadamente esta ordem:

1. Estrutura do repositório.
2. Backend Spring Boot mínimo.
3. `GET /health`.
4. PostgreSQL.
5. Migrations.
6. Autenticação.
7. CRUD de dispositivos.
8. Biblioteca de mídia.
9. Playlists.
10. Endpoint de configuração do Player.
11. Player mínimo.
12. Ativação.
13. Download/cache.
14. Reprodução.
15. Heartbeat.
16. Integração painel ↔ Player.
17. Teste sem internet.
18. Teste após reinicialização.
19. Deploy inicial.
20. Só depois iniciar expansão.

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
