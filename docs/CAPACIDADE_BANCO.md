# Capacidade do banco: quantas telas cabem

Etapa 1.3 (2026-10-22). O que mais cresce no banco é o **registro de exibição** (`play_logs`): uma linha cada vez que uma mídia vai ao ar numa TV, com hora e duração em milissegundos e o hash da corrente (a "comprovação de exibição"). Este documento mostra quanto espaço isso ocupa e quantas telas cabem no banco do plano Pro do Supabase.

## O que mudou (migração V24)

| Antes | Depois |
|---|---|
| 549 bytes por exibição (tabela + 5 índices; medido com 200 mil linhas) | **424 bytes**: os dois hashes viraram 32 bytes (`BYTEA`) em vez de 64 letras, e o índice por mídia saiu |
| Detalhe guardado 14 dias por padrão | **7 dias** para cliente novo (quem já tem um prazo salvo o mantém: nada é apagado por esta mudança) |
| Só o resumo diário por TV (exibições, tempo, último hash) por 1 ano | **Mais** o resumo diário **por mídia e campanha** por 1 ano: "quantas vezes o anúncio X rodou em cada dia", mesmo depois do detalhe ser apagado |

O detalhe linha a linha (o que prova hora e duração de cada exibição, com a corrente de hashes conferível) continua pelo prazo configurado do cliente (7 a 365 dias). Quem precisa provar um mês inteiro a um anunciante pode subir o prazo daquele cliente; o resumo por mídia já responde "quantas vezes" por um ano sem custo relevante.

## Conta

Premissas: tela ligada **24 horas** (o pior caso; uma barbearia de 10 h gasta menos da metade), 424 bytes por exibição, resumo por mídia de 20 mídias por dia (≈170 bytes por linha, 1,2 MB por ano por TV), resumo diário por TV (≈130 bytes por dia).

| Duração média de cada item | Exibições por dia | Detalhe por dia | 7 dias de detalhe | 14 dias de detalhe |
|---|---|---|---|---|
| 10 s (pior caso) | 8.640 | 3,7 MB | **25,6 MB** | 51,2 MB |
| 15 s (típico: imagens de 10 s e vídeos mais longos) | 5.760 | 2,4 MB | **17,1 MB** | 34,2 MB |
| 30 s | 2.880 | 1,2 MB | 8,5 MB | 17,1 MB |

Somando os resumos de 1 ano (≈1,3 MB por TV): **uma tela 24 h ocupa cerca de 18 MB no banco com o padrão novo (item de 15 s) e 27 MB no pior caso (10 s)**. A meta era ~20 MB por mês por tela: o padrão novo atinge no caso típico; no pior caso passa um pouco, e dá para baixar o prazo do cliente, que tem o mínimo de 7 dias.

## Quantas telas cabem no Supabase Pro

O plano Pro inclui **8 GB de banco** (o que passa disso é cobrado à parte, e o banco cresce sozinho). Reservando 25% para o resto do sistema (clientes, TVs, eventos, índices, folga do Postgres), sobram ≈ **6 GB para as exibições**.

| Cenário (tela 24 h) | MB por tela | Telas que cabem em 6 GB |
|---|---|---|
| 7 dias de detalhe, itens de 15 s (padrão novo, típico) | 18 | **≈ 340** |
| 7 dias de detalhe, itens de 10 s (pior caso) | 27 | ≈ 230 |
| 14 dias (cliente antigo), itens de 15 s | 36 | ≈ 170 |
| 14 dias (cliente antigo), itens de 10 s | 53 | ≈ 115 |
| 30 dias de detalhe, itens de 15 s (cliente que pediu prova de um mês) | 75 | ≈ 80 |

Com telas de 10 a 12 horas por dia (a maioria do comércio), os números **dobram**.

## Outros limites que chegam antes do disco

- **Memória do servidor:** o plano Starter da Render tem 512 MB. Com ~340 telas, são ~2 milhões de exibições por dia (≈ 23 por segundo, em lotes): o banco aguenta, mas o servidor passa a pedir um plano maior (Standard, 2 GB) por volta de **100 a 150 telas**. Isso vem antes de o disco acabar.
- **Mídias** não ficam no banco: ficam no Storage (100 GB no Pro, limite de 50 MB por arquivo).
- **Consultas:** o relatório linha a linha aceita no máximo 93 dias por consulta e 500 linhas por página (CSV até 100 mil); com muitas telas, o filtro por TV continua usando o índice por TV e hora.

## Como conferir no painel do Supabase

Supabase → Database → *Disk usage* mostra o uso total; o tamanho da tabela se vê com `select pg_size_pretty(pg_total_relation_size('play_logs'))`. Se o uso passar de 70% de 8 GB, as opções, em ordem de custo: baixar o prazo do detalhe dos clientes que não precisam de prova longa; aumentar o disco (cobrado por GB); exportar o detalhe antigo para arquivo e apagar do banco (antes disso, pedir OK: é apagar dados).

## Como foi medido

`CREATE TABLE` com o mesmo esquema, 200 mil linhas de uma TV de 10 s por item, `pg_total_relation_size` / linhas: esquema antigo 549 bytes, com `BYTEA` e sem o índice por mídia 424 bytes, e com `prev_hash` nulo nos registros contínuos 389 bytes (não adotado: a conferência da corrente depois do descarte do detalhe fica frágil). A migração V24 foi rodada também sobre uma cópia do banco de desenvolvimento (33 registros): os hashes voltaram idênticos, o resumo por mídia somou as mesmas 33 exibições e 630.220 ms, e o desfazer (no cabeçalho da V24) devolveu os hashes em texto.
