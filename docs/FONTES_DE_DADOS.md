# Fontes de dados dos widgets: licença, custo e o que fazer

Atualizado em 2026-10-22 (Etapa 1.2, conformidade). O servidor é quem consulta as fontes (a TV nunca fala com elas), guarda o resultado por um tempo e entrega o último dado bom se a fonte cair. **Este documento não é parecer jurídico**: registra o que foi lido nos termos publicados e o que não pôde ser confirmado.

## Resumo

| Widget | Fonte | Uso comercial? | Custo | Situação |
|---|---|---|---|---|
| Clima | **MET Norway** (Locationforecast 2.0) | Sim (CC BY 4.0 / NLOD 2.0), com citação da fonte | Grátis | ✅ em uso desde a Etapa 1.2 |
| Busca de cidades (só no painel) | **Nominatim** (OpenStreetMap) | Não é proibido, mas o serviço público é de uso moderado e pode ser retirado | Grátis | ✅ em uso, com cuidados (abaixo) |
| Cotações | **Frankfurter** (dados do Banco Central Europeu e de outros bancos centrais) | Sim | Grátis, sem chave | ✅ |
| Notícias | Feeds RSS de portais | **Só a Agência Senado tem autorização clara** | Grátis | 🟡 ver a lista abaixo |

O **Open-Meteo**, usado antes, só é gratuito para uso **não** comercial. Foi retirado.

## Clima: MET Norway

- Licença dos dados: Creative Commons BY 4.0 e Norwegian Licence for Open Government Data 2.0: uso livre, inclusive comercial, **citando a fonte**.
- Cumprido no sistema: o widget mostra "Fonte: MET Norway" no canto da tela; o painel mostra a nota de fonte no editor.
- Regras de tráfego do serviço, todas cumpridas em `WidgetDataService`:
  - `User-Agent` que identifica o aplicativo e um contato (site): `Vyzzu/1.0 (digital signage; +https://signage-painel.pages.dev)`. **Quando o Vyzzu tiver domínio próprio, trocar o endereço em `JdkWidgetHttp`** (e vale acrescentar um e-mail de contato do suporte).
  - Coordenadas com até 4 casas (usamos 2: TVs da mesma cidade dividem a mesma consulta).
  - Não consultar antes do cabeçalho `Expires` da resposta (o MET renova a cada 30 min) e perguntar com `If-Modified-Since` (o MET responde 304 sem corpo quando nada mudou).
  - Falhou? Espera 5 minutos antes de tentar de novo, e entrega o último dado bom por até 24 h.
- Volume: uma consulta a cada 30 min por cidade (≈48 por dia por cidade), muito abaixo do limite de 20 por segundo.
- Limitações conhecidas: o MET **não informa probabilidade de chuva** fora da Escandinávia, só o volume em mm; por isso o widget novo mostra "3,2 mm" ou "sem chuva". Players antigos continuam mostrando um percentual (a parte do dia com chuva prevista), até a próxima versão do Player.

## Busca de cidades: Nominatim

- Política do serviço público (uso moderado): no máximo 1 consulta por segundo, aplicativo identificado, **sem autocompletar** (só o botão "Buscar"), resultados guardados. Tudo isso é seguido: pausa de 1,1 s entre consultas, cache de 24 h, `User-Agent` identificado.
- Citação exigida: dados © colaboradores do OpenStreetMap (ODbL); a nota está no editor do widget.
- Risco: o serviço público pode limitar ou retirar o acesso de quem abusar. A busca só é usada na hora de montar o widget (poucas vezes por semana), então o risco é baixo. **Plano B** se um dia bloquear: o painel aceitar latitude e longitude digitadas, ou uma lista de cidades embutida (GeoNames, licença CC BY 4.0), sem depender de serviço externo.

## Cotações: Frankfurter

- API gratuita, sem chave, uso comercial permitido segundo a página do projeto, com recomendação de guardar em cache (o servidor já guarda por 1 h). Os dados vêm de bancos centrais (origem: Banco Central Europeu, cuja nota de direitos autorais permite uso livre citando a fonte).
- O widget já mostra "Fonte: Banco Central Europeu". Cotação de referência diária, não preço de câmbio (dito no manual).

## Notícias: o que os termos dizem de cada feed

O que consegui ler hoje, e o que não consegui. "Termos claros" = há texto público que autoriza **exibir as manchetes num serviço pago a empresas**.

| Feed | Termos claros para exibição comercial? | O que foi verificado |
|---|---|---|
| **Agência Senado** (`www12.senado.leg.br/noticias/rss`) | **Sim** (textos) | A política de uso do Senado autoriza reprodução livre, citando a Agência Senado e mantendo a informação íntegra. A restrição de uso comercial e de publicidade é só das programações da TV Senado. Foi acrescentada como fonte pronta; o widget mostra o nome da fonte. |
| **Agência Brasil** (EBC) | **Não** | A página da agência autoriza reprodução por veículos de comunicação, com a fonte citada, para fins jornalísticos; os termos de uso do portal da EBC limitam o uso a fins pessoais e sem intuito comercial, e a reprodução comercial depende de licenciamento (licenciamento@ebc.com.br). Nenhuma licença Creative Commons foi encontrada. |
| **BBC News Brasil** | **Não confirmado** | O site da BBC não pôde ser lido hoje. Os termos antigos (2005) liberavam feeds a outros sites sem cobrar o acesso ao conteúdo da BBC; os atuais não foram confirmados. Tratar como **não autorizado** até confirmar na página de termos de RSS da BBC. |
| **G1** (Globo) | **Não confirmado** | Os termos de uso da Globo não puderam ser lidos hoje e nenhuma permissão de uso comercial de feed foi encontrada. Tratar como **não autorizado**. |
| **CNN Brasil** | **Não confirmado** | A página de termos não pôde ser lida (endereço respondeu 404) e nada foi encontrado sobre RSS. Tratar como **não autorizado**. |
| **UOL Notícias** | **Não confirmado** | O site de feeds do UOL não pôde ser lido. Tratar como **não autorizado**. |
| **Feed próprio** (a pessoa informa o endereço) | Depende da pessoa | Ela responde pelo direito de usar aquele feed (próprio ou autorizado). |

**O que isso significa na prática:** o widget só mostra a manchete (título) e o nome da fonte, nunca o texto da matéria, o que reduz o risco, mas **não substitui autorização** quando o serviço é pago pelo cliente. Opções:

1. **Manter os cinco portais como estão** e deixar o risco com o dono do negócio. Custo zero, risco jurídico baixo para manchetes, mas sem respaldo nos termos.
2. **Deixar só a Agência Senado e o feed próprio ligados por padrão** e esconder os portais até haver autorização por escrito (e-mail ao setor de licenciamento de cada um; a EBC publica o contato). Custo zero. **É a que recomendo antes de vender.** Não foi feito nada nos widgets existentes: remover fontes derrubaria widgets que já estão em TV.
3. **Licenciar** (EBC tem linha de negócio de licenciamento; os demais, via contato comercial). O custo não é público.

Decisão pendente com o dono (ver `docs/PENDENCIAS.md`).

## Termos de uso da plataforma

Os termos (`/termos`) tratam do conteúdo enviado pelo cliente e da falha de serviços de terceiros, mas **não dizem** que as manchetes e os dados dos widgets pertencem às fontes. Sugestão (texto jurídico, para o dono validar): acrescentar uma cláusula dizendo que os dados exibidos nos widgets pertencem às fontes citadas e que o cliente responde pelos feeds que informar.
