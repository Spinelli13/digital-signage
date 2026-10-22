# Produção paga: plano de migração, backup e monitoramento

Etapa 1.1. Escrito em 2026-10-22. **Nada aqui foi executado em produção e nada aqui gasta dinheiro até o dono fazer os passos da seção 3.** O que depende só do código (scripts de backup, fluxos do GitHub, este plano) já está pronto e testado.

## 1. Situação e decisão

| Peça | Hoje | Região |
|---|---|---|
| Servidor (Render) | plano gratuito, **dorme depois de 15 min sem uso** | Oregon |
| Banco e mídias (Supabase) | plano gratuito, Postgres 17, 14 MB de banco e 8 mídias (7,9 MB) | us-east-1 (Virgínia) |

O servidor e o banco estão em regiões diferentes (costa oeste e costa leste dos EUA): cada consulta ao banco leva dezenas de milissegundos a mais. E o plano gratuito de cada um tem limites que não servem para cliente pagante: servidor que dorme, banco que pausa por inatividade, sem cópia de segurança garantida.

**Decisão recomendada: manter o servidor onde está (Oregon) e mudar o banco para Oregon.** O motivo é o endereço do servidor: **cada TV guarda o endereço do servidor** (`signage-backend-1l0l.onrender.com`). A Render não permite trocar a região de um serviço existente, então "mover o servidor para a Virgínia" criaria um endereço novo e todas as TVs teriam de ser pareadas de novo. Já o Supabase também não muda a região de um projeto, mas o banco é invisível para as TVs: cria-se um projeto novo em **us-west-2 (Oregon)**, copiam-se os dados e troca-se a configuração do servidor. As TVs não percebem.

| Opção | TVs precisam de ajuste? | Indisponibilidade | Custo mensal (referência) |
|---|---|---|---|
| **A. Banco novo em Oregon (recomendada)** | Não | ~10 a 15 min do painel; TVs seguem tocando do cache | Supabase Pro + Render Starter |
| B. Servidor novo na Virgínia | **Sim: endereço novo, parear todas as TVs de novo** (só vale se houver domínio próprio, que esconde o endereço) | Mesma, mais o trabalho nas TVs | Igual |

## 2. Custos (valores de tabela; **você confirma nos painéis**)

| Serviço | Plano | Referência | Onde confirmar |
|---|---|---|---|
| Supabase | Pro (por organização, inclui crédito para 1 banco pequeno, 8 GB de banco, 100 GB de mídia, cópia diária por 7 dias) | US$ 25/mês | supabase.com → Organization → Billing |
| Render | Starter (não dorme) | US$ 7/mês | dashboard.render.com → o serviço → Settings → Instance Type |
| **Total** | | **≈ US$ 32/mês** + IOF/câmbio do cartão | Fatura dos dois |

Valores de tabela pública no momento em que escrevi; confira antes de contratar. A consulta do custo de **criar** um projeto novo na organização atual (plano gratuito) retornou US$ 0: a cobrança vem do **upgrade da organização para Pro**, que é uma ação sua (cartão).

## 3. O que você faz (passo a passo)

Tudo em ordem; avise "terminei o passo X" e eu continuo.

1. **Supabase → plano Pro.** supabase.com → organização *DigitalSignage* → Billing → Upgrade to Pro → cartão. (Um projeto novo em Oregon já fica dentro do Pro.)
2. **Render → Starter.** dashboard.render.com → `signage-backend` → Settings → Instance Type → *Starter* → Save. O endereço não muda e o servidor reinicia uma vez (~1 min de painel fora; as TVs seguem do cache).
3. **Cadastrar os segredos do backup** (seção 5), no GitHub: repositório → Settings → Secrets and variables → Actions. **Nunca me mande essas senhas em chat**: cadastre direto na tela.
4. Avisar para eu executar a migração do banco (seção 4). Escolha um horário de pouco movimento.
5. Depois: conferir nas telas de cobrança dos dois serviços que o valor é o esperado (critério de aceite).

## 4. Migração do banco para Oregon (eu executo, com você acordado e avisado)

**Antes do dia (ensaio, sem afetar ninguém):**

1. Criar o projeto Supabase `Signage Oregon` em **us-west-2**, Postgres 17, com uma senha forte gerada na hora (você a guarda no Supabase; eu uso só na configuração do servidor).
2. Criar o bucket privado `media` com **limite de 50 MB** e a mesma lista de tipos permitidos do atual (`image/jpeg`, `image/png`, `image/webp`, `video/mp4`, `video/quicktime`, `video/x-m4v`, `video/m4v`).
3. Restaurar nele uma cópia recente do banco (`scripts/backup/restore-db.sh`) e conferir (`verify-restore.sh`).
4. Subir o backend **no meu computador** apontando para o projeto novo e entrar no painel, listar TVs, tocar uma mídia. Se algo falhar, nada de produção foi tocado.

**No dia (janela de ~10 a 15 min, banco hoje com 14 MB):**

1. Aviso a você. O painel pode oscilar; **as TVs continuam tocando do cache** (o plano offline guarda 7 dias).
2. **Suspender** o serviço no Render (para não entrar dado novo durante a cópia).
3. Cópia final do banco: `backup-db.sh` no banco atual, restaurar no novo, `verify-restore.sh` (as contagens têm de bater exatamente, já que nada escreve).
4. Copiar as mídias: `rclone copy` do bucket antigo para o novo (os arquivos são identificados por SHA-256 nas TVs, então as TVs não baixam nada de novo).
5. No Render, atualizar as variáveis `DB_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `S3_ENDPOINT`, `S3_REGION`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`. O Flyway confere o histórico de migrações restaurado e não reaplica nada.
6. **Retomar** o serviço. Verificar: `/api/v1/health`, login no painel, lista de TVs com batimento recente, abrir uma mídia, `flyway_schema_history` na versão esperada.
7. Atualizar a política de privacidade, os termos e `docs/LGPD.md` (item 1.2): o Supabase passa de `us-east-1` para `us-west-2` (Oregon). Os dois ficam na mesma região.

**Voltar atrás (rollback):** o projeto antigo fica **intocado**. Para voltar, basta pôr as variáveis do passo 5 de volta com os valores do projeto antigo (a senha do banco e as chaves de mídia se pegam/redefinem no painel do Supabase antigo) e retomar o serviço: ~5 min. O que foi gravado no banco novo depois da virada se perde nesse caso (se já tiver passado horas, faço antes uma cópia do novo para o antigo). O projeto antigo só é pausado ou apagado **depois de 7 dias e com o seu OK explícito**.

**Indisponibilidade esperada:** 10 a 15 min do painel e das chamadas ao servidor; TVs sem efeito visível (cache). Sem perda de dados: o serviço fica suspenso durante a cópia final.

## 5. Backup agendado (banco e mídias)

O que existe (`.github/workflows/backup.yml`, todo dia às 03:00 de Brasília e sob demanda):

1. `pg_dump` do schema `public` do banco de produção, **criptografado com AES-256** (a frase fica em segredo; **sem ela a cópia não abre: guarde também no seu gerenciador de senhas**).
2. **Restaura a cópia em um Postgres 17 descartável e confere** tabela por tabela (as contagens de linhas têm de ficar entre as de antes e as de depois da cópia). Se não conferir, o fluxo falha e você recebe e-mail. Ou seja: **toda cópia é testada na hora**, não só uma vez.
3. Opcional, recomendado: copia as mídias e a cópia do banco para um **bucket privado de outro provedor** (`rclone copy`, que nunca apaga o que já foi copiado). Opções com plano gratuito: Cloudflare R2 ou Backblaze B2 (10 GB grátis; podem pedir cartão para abrir a conta). **Este repositório é público**, por isso a cópia só fica no GitHub (30 dias, criptografada) se você ligar a variável `BACKUP_ARTIFACT=true`.
4. O Supabase Pro já guarda cópias diárias por 7 dias do banco inteiro; as nossas são uma segunda cópia, fora dele e com as mídias.

Segredos a cadastrar (o fluxo não faz nada, e não falha, enquanto faltarem):

| Segredo | O que é |
|---|---|
| `BACKUP_DB_URL` | Endereço do banco (Supabase → Connect → *Session pooler*), com usuário e senha |
| `BACKUP_PASSPHRASE` | Frase longa aleatória: `openssl rand -base64 32` |
| `BACKUP_SRC_S3_ENDPOINT`, `_REGION`, `_ACCESS_KEY`, `_SECRET_KEY` | Acesso S3 ao bucket `media` (Supabase → Storage → S3 connection) |
| `BACKUP_DST_S3_ENDPOINT`, `_ACCESS_KEY`, `_SECRET_KEY`, `_BUCKET` | Bucket privado de destino (R2 ou B2) |

**Como restaurar** (de um arquivo `.dump.enc`, num banco vazio):

```bash
export BACKUP_PASSPHRASE='...'   # a mesma frase da cópia
scripts/backup/restore-db.sh vyzzu-db-AAAAMMDD-HHMMSS.dump.enc "postgresql://usuario:senha@host:5432/postgres?sslmode=require"
scripts/backup/verify-restore.sh vyzzu-db-AAAAMMDD-HHMMSS.counts.txt "postgresql://usuario:senha@host:5432/postgres?sslmode=require"
```

Para restaurar mídias: `rclone copy DST:bucket/media SRC:media`. Depois de restaurar, subir o servidor apontando para o banco novo.

**Teste de restauração documentado** (2026-10-22): numa cópia do banco de desenvolvimento (34 tabelas, 255 linhas, Postgres 16), `backup-db.sh` gerou a cópia criptografada (138 KB), `restore-db.sh` restaurou num Postgres 18 vazio e `verify-restore.sh` confirmou **34 tabelas e 255 linhas iguais**. Provas de falha: apagar uma linha restaurada faz a conferência falhar; a senha errada não abre a cópia. O `selftest.sh` repete isso a cada mudança, na CI, contra um Postgres 17. **Falta só** a primeira rodada contra o banco de produção, que depende do segredo `BACKUP_DB_URL` (seção 3, passo 3): rodando o fluxo uma vez à mão (Actions → Backup → Run workflow), o log mostra "restauração conferida".

## 6. Monitor externo de disponibilidade (gratuito)

Dois alarmes, ambos sem custo:

1. **GitHub (`.github/workflows/uptime.yml`, já pronto):** a cada 10 min consulta `/api/v1/health` (3 tentativas); se falhar, o GitHub manda e-mail para o dono do repositório. Efeito colateral bom: mantém o servidor gratuito acordado até o Starter.
2. **UptimeRobot (você cria, 3 min):** uptimerobot.com → criar conta com o seu e-mail → *Add New Monitor* → tipo **HTTP(s)** → nome "Vyzzu servidor" → URL `https://signage-backend-1l0l.onrender.com/api/v1/health` → intervalo **5 min** → em *Alert contacts* marcar o seu e-mail (e o app no celular, se quiser) → Create. Repita para o painel (`https://signage-painel.pages.dev`). O plano gratuito comporta isso.

## 7. Critérios de aceite

| Critério | Como se prova | Estado |
|---|---|---|
| Servidor não dorme | Render mostra *Starter*; `uptime.yml` sem falhas | Depende do passo 2 |
| Backup restaurado em ambiente de teste | Log do fluxo "Backup" com "restauração conferida" (e o ensaio da seção 5) | Ensaio feito; produção depende do passo 3 |
| Custo mensal confirmado por você | Faturas do Supabase e da Render | Depende dos passos 1, 2 e 5 |
| Servidor e banco na mesma região | Projeto Supabase em us-west-2 e Render em Oregon | Depende da migração (seção 4) |
