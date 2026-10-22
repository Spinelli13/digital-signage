# Planos e preços da oferta comercial

Etapa 2.1 (2026-10-22). A migração **V27** acrescenta os planos abaixo. **Nenhum cliente existente muda de plano**, e os planos antigos (Grátis, Básico e Profissional) continuam como estavam e ativos: quando a operação quiser parar de oferecê-los, desativa em **Plataforma > Planos**. Tudo (preço, limites e recursos) se edita no painel, por plano.

## A oferta

| Plano (código) | TVs | Espaço | Arquivo | Equipe | Recursos | Preço/mês |
|---|---|---|---|---|---|---|
| **Start** (`start`) | 2 | 3 GB | 50 MB | 2 | conteúdo e widgets | R$ 39 |
| **Pro** (`pro`) | 5 | 10 GB | 50 MB | 5 | + fila de senhas, cupons com QR Code, tabelas de preços | R$ 89 |
| **Business** (`business`) | 15 | 30 GB | 50 MB | 15 | todos | R$ 179 |
| **Rede** (`rede`) | 40 | 80 GB | 50 MB | 40 | todos | R$ 349 |
| **Comodato Básico** (`comodato-basico`) | 1 por contrato | 3 GB | 50 MB | 2 | os do Pro | R$ 79 |
| **Comodato Completo** (`comodato-completo`) | 1 por contrato | 3 GB | 50 MB | 2 | os do Pro | R$ 129 |
| **Comodato Premium** (`comodato-premium`) | 1 por contrato | 3 GB | 50 MB | 2 | os do Pro | R$ 189 |

- "Conteúdo" (mídias, playlists, locais, grupos, alertas e equipe) faz parte de qualquer plano e só tem limite de quantidade. Os recursos opcionais do sistema são: campanhas, layouts com zonas, widgets, tabelas de preços, fila de atendimento, comprovação de exibição e cupons. **Start** inclui só widgets; **Pro** e os **de comodato** incluem widgets, tabelas de preços, fila de atendimento e cupons; **Business** e **Rede** incluem todos (campanhas, layouts e comprovação de exibição também).
- A coluna **Equipe** (usuários do painel) não estava no pedido; os valores acima são uma suposição razoável e se ajustam em Plataforma > Planos.
- Os três planos de **comodato** são para **aluguel de equipamento**: não vendemos TV nem stick. O que muda entre eles é o que a mensalidade cobre (TV do cliente, kit TV e stick, TV nova de 32 pol), dito na descrição de cada um. O controle de qual equipamento está em qual cliente é o inventário (item 1.6: número de série, instalação e observação de comodato).
- **Tamanho de arquivo:** todos os planos (os novos e os antigos, que estavam vazios) passam a ter **50 MB**, que é o limite real do armazenamento de mídias (o bucket recusa arquivos maiores). Antes o painel dizia "sem limite" e o envio falhava no servidor de arquivos, com uma mensagem confusa; agora o painel avisa antes, com a mesma conta. Nenhum arquivo que já coubesse é recusado.
- O plano **Interno** (uso próprio) continua sem limite de TVs, de espaço e de equipe.

## TV extra a R$ 9 por mês: o modelo atual comporta?

**Não comporta.** Hoje o preço de uma fatura é o preço fixo do plano (`BillingService`: `amount = plan.getPriceCents()`, menos cupom) e o limite de TVs é um teto duro (`PlanLimits.requireRoomForDevice` recusa a TV a mais). Não existe "TV extra" nem preço por TV.

**O que dá para fazer hoje, sem código** (serve para o primeiro cliente): criar em Plataforma > Planos um plano sob medida para o cliente (por exemplo "Pro + 2 TVs", com 7 TVs e o preço somado: R$ 89 + 2 × R$ 9 = R$ 107) e colocar o cliente nele. É manual, mas é correto, e as faturas já saem com o valor certo.

**Proposta mais simples para automatizar** (não implementada; peço o seu OK antes, porque mexe em cobrança):

1. `plans.extra_device_cents` (preço da TV extra daquele plano; 0 = o plano não aceita TV extra) e `tenants.extra_devices` (quantas TVs extras o cliente contratou, a operação ajusta em Plataforma > Clientes).
2. Limite de TVs do cliente = limite do plano + TVs extras (limite nulo continua sem limite).
3. Valor da fatura = preço do plano + TVs extras × preço da TV extra (menos cupom, como hoje), com a linha "+ N TV(s) extra" na descrição da fatura.
4. A cobrança continua **manual** (a automática é da Etapa 3): a operação conta as TVs extras e a fatura sai com o valor certo.

Impacto: uma migração (duas colunas com valor 0 por padrão, sem mexer em nenhum cliente), `PlanLimits`, `BillingService`, os dois formulários da plataforma e testes. Decisões suas: se o preço extra é R$ 9 em todos os planos ou só em alguns, e se o plano de comodato aceita TV extra (hoje é "1 TV por contrato").

## Como conferir

Plataforma > Planos: os sete planos novos aparecem depois dos antigos, com preço, limites e recursos. Criar um cliente num plano novo e conferir no painel dele: o menu mostra só os recursos do plano (por exemplo, o Start não mostra a fila de atendimento) e a tela "Plano e cobrança" mostra os limites.
