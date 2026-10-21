# Guia da marca Vyzzu

Este é o guia da identidade visual do Vyzzu (painel, app da TV, manuais e site de downloads). A fonte de verdade no código é `frontend/src/styles.css` (cores, formas e sombras) e `scripts/gerar-icones-marca.cjs` (o símbolo). O teste `frontend/src/app/brand-tokens.spec.ts` protege as regras abaixo.

## 1. A ideia

O Vyzzu é uma plataforma de comunicação audiovisual: telas de LED, totens, widgets, vídeo, cupons, cardápios, filas e atendimento, tudo controlado num lugar só. A identidade traduz isso em três coisas: **o preto de uma tela desligada, a luz que acende nela (violeta e magenta) e a prata dos metais e dos textos de apoio**.

Não é um painel genérico: o preto domina, a cor aparece onde há interação ou identidade, e o resto é neutro.

## 2. O símbolo

O símbolo é um **V formado por linhas de tela**: a silhueta de um V cortada em cinco faixas horizontais de LED, que descem da lavanda até o magenta na ponta, sobre o preto da marca.

- **Por que assim:** as faixas lembram a varredura de uma tela e os módulos de um painel de LED; o V é a inicial; a luz que "desce" até a ponta magenta dá movimento. A silhueta é só do Vyzzu e continua legível em 16 px.
- **Onde fica:** `frontend/public/favicon.svg` (painel, manuais, site de downloads), `favicon.ico`, `apple-touch-icon.png`, o ícone e o banner do app da TV.
- **Como mudar:** o desenho é gerado por código (`scripts/gerar-icones-marca.cjs`: número de faixas, espaçamento, cores). Rode o script e todos os arquivos saem juntos, inclusive o ícone vetorial do Android.
- **Versão para fundo claro:** o próprio símbolo já traz o preto da marca como fundo (um quadrado de cantos arredondados), por isso funciona em qualquer cor de página. Sobre o preto do menu o fundo some e o V parece flutuar.

## 3. O logotipo tipográfico

O nome **Vyzzu** em Inter extra-negrito (peso 800, espaçamento apertado), com o **"zz"** na cor de destaque: lavanda (`#c4b5fd`) sobre fundos escuros e violeta escuro (`--link`) sobre fundos claros. Está num componente só, `frontend/src/app/shared/wordmark.ts` (`<app-wordmark />`), que herda a cor do texto ao redor.

Use o nome sempre escrito **Vyzzu** (V maiúsculo, o resto minúsculo).

## 4. A paleta

| Função | Cor | Código | Onde aparece |
|---|---|---|---|
| Preto da interface | o de sempre | **não mudou** (tabela da seção 5) | fundos, painéis, menu lateral |
| **Violeta elétrico** (marca) | | `#8B5CF6` | foco, seleção, ícones, detalhes, símbolo |
| Violeta de preenchimento | | `#7C3AED` | botões (texto branco, 5,7:1) |
| Violeta profundo | | `#6D28D9` | links no tema claro, botão em hover |
| **Magenta** (complemento) | | `#D946EF` | barra do item ativo do menu, ponta do símbolo, brilho de fundo do login |
| **Lavanda** (suave) | | `#C4B5FD` | destaques sobre fundo escuro, "zz" do logotipo, anel do dashboard |
| **Prata** (neutro) | | `#A1A1AA` | texto auxiliar no escuro, bordas, ícones discretos |
| Cinza muito claro | | `#F4F4F5` | texto no tema escuro, fundo do tema claro |

**Regras**
- O **violeta** é a cor de interação e de identidade. O **magenta** só entra em detalhes pequenos (nunca em áreas grandes nem em botões). A **lavanda** serve aos estados suaves e ao que fica sobre o preto.
- Há **gradientes** só em três superfícies grandes (o painel do login, o do dashboard e o do plano) e nos botões primários (de `#7C3AED` a `#6D28D9`, quase sólido). Nada de brilho neon nem sombras coloridas.
- **Sem azul.** Nenhuma cor da marca, de destaque ou decorativa é azul. O estado "informação" é prata neutra (com ícone).
- **Sem verde de marca.** O verde que sobrou tem **um único significado**: sucesso, "online", "em dia" (veja a seção 6).

## 5. O preto não mudou

Estes valores são os de **antes do rebranding**, idênticos. Estão marcados no `styles.css` com `PRETO PRESERVADO` e o teste confere um por um:

| Token | Tema claro | Tema escuro |
|---|---|---|
| Fundo da página `--bg` | (cinza claro) | `#090E0C` |
| Painéis `--panel`, `--panel-2`, `--panel-3` | (branco e cinzas claros) | `#111917`, `#16201C`, `#1C2924` |
| Linhas `--line`, `--line-strong` | (cinzas claros) | `#243029`, `#32413A` |
| Menu lateral `--sidebar`, `--sidebar-2` | `#0A0F0D`, `#101815` | `#060A08`, `#0B110F` |
| Texto preto `--text` | `#0D1612` | (texto claro) |
| Tela `--screen` | `#0B0F0D` | `#050807` |
| Cortina de janelas `--overlay` | `rgba(6,12,9,.6)` | `rgba(0,0,0,.72)` |
| Fundo do app da TV | `#070A09` | |
| Preto do QR Code | `#0C1410` | |

Observação de transparência: esses pretos de origem têm um fundo levemente esverdeado (por exemplo `#090E0C`), de quando a marca era verde. Foram mantidos porque a regra do projeto é não alterar o preto. Trocar para um preto neutro seria só mudar esses valores (e o teste que os confere).

Os cinzas **claros** do tema claro e os textos do tema escuro, que tinham um tom esverdeado, passaram a ser prata/zinco neutros (`#F4F4F5`, `#E4E4E7`, `#A1A1AA`…).

## 6. Estados

| Estado | Cor | Como não depender só da cor |
|---|---|---|
| Sucesso / online / em dia | verde (`#1D9C62`, texto `#0B5C38` sobre `#E5F4EC`) | ícone de visto, rótulo "Online", "Em dia" |
| Atenção | âmbar (`#D98A1F`, texto `#86520A` sobre `#FFF4DE`) | ícone de aviso e texto |
| Erro / offline / vencido | vermelho (`#D1465A`, texto `#9B2335` sobre `#FCECEE`) | ícone de erro e texto |
| Informação | **prata neutra** (`#404040` sobre `#F2F2F2`) | ícone "i" e texto |
| Marca / selecionado | violeta (`#6D28D9` sobre `#F5F3FF`) | ícone e rótulo |

Todos têm contraste de texto acima de 4,5:1 nos dois temas (tabela da seção 8).

## 7. O que NÃO é a marca (conteúdo dos clientes)

O conteúdo que os clientes põem nas TVs, nos painéis de LED e nos totens continua sendo deles. **Não foi mexido**: os temas e as cores dos widgets (inclusive o tema "Verde" e o acento "Azul"), os temas do gerador de QR Code (inclusive "Verde"), os vídeos, as imagens, os cardápios e as promoções. O painel só ganhou a identidade do Vyzzu nas partes administrativas.

Duas observações: o tema "Verde" dos widgets é o antigo "Verde Smartplay"; mantive as cores para não mudar o que já está nas TVs dos clientes e só tirei o nome antigo do rótulo. O acento "Azul" dos widgets é uma escolha do cliente, não da marca.

## 8. Acessibilidade: contraste

Medido por `frontend/src/app/brand-tokens.spec.ts` (e conferido à mão). AA pede 4,5:1 para texto normal.

| Par | Contraste |
|---|---|
| Claro: texto `#0D1612` sobre `#F4F4F5` | 16,8:1 |
| Claro: texto auxiliar `#6A6A73` sobre o fundo `#F4F4F5` / o cartão `#FFFFFF` | 4,9:1 / 5,4:1 |
| Claro: link `#6D28D9` sobre branco | 7,1:1 |
| Botão: branco sobre `#7C3AED` / hover `#6D28D9` | 5,7:1 / 7,1:1 |
| Escuro: texto `#F4F4F5` sobre `#090E0C` | 17,7:1 |
| Escuro: texto auxiliar `#A1A1AA` sobre `#111917` | 7,0:1 |
| Escuro: link `#A78BFA` sobre `#111917` | 6,6:1 |
| Menu: texto `#D4D4D8` / auxiliar `#A1A1AA` / destaque `#C4B5FD` sobre `#0A0F0D` | 13,1 / 7,5 / 10,5:1 |
| App da TV: texto `#F4F4F5` / auxiliar `#A1A1AA` / destaque `#C4B5FD` sobre `#070A09` | 18,1 / 7,8 / 10,8:1 |
| Estados (sucesso, erro, alerta, informação) | de 6,0 a 11,1:1 |

O violeta puro `#8B5CF6` (4,2:1 com texto branco) **não** é usado como fundo de botão com texto: o preenchimento é `#7C3AED`. O `#8B5CF6` fica em ícones, foco e detalhes, onde 3:1 basta.

## 9. Tipografia, forma e movimento

- **Fonte:** Inter (a de sempre), com o logotipo em peso 800. Títulos em 650–750, texto em 15 px.
- **Raios:** 8, 12, 16 e 22 px (`--r-sm`, `--r`, `--r-lg`, `--r-xl`). **Sombras:** 3 níveis neutros (`--sh-1..3`).
- **Foco:** anel violeta de 3 px (`--ring`), visível nos dois temas.
- **Movimento:** curto e discreto; respeita "reduzir movimento" do sistema.

## 10. Onde cada coisa vive no código

| O quê | Arquivo |
|---|---|
| Cores, formas, sombras (os dois temas) | `frontend/src/styles.css` |
| Nome do produto | `frontend/src/app/core/brand.ts` (`BRAND`) |
| Logotipo tipográfico | `frontend/src/app/shared/wordmark.ts` |
| Símbolo, favicon, ícone e banner da TV | `scripts/gerar-icones-marca.cjs` → `frontend/public/*`, `player/app/src/main/res/*` |
| Cor do navegador no celular | `index.html` e `core/theme.ts` (`theme-color`) |
| Cores e nome do app da TV | `player/app/src/main/res/values/colors.xml`, `strings.xml` |
| Manuais (PDF/HTML) e site de downloads | `scripts/build-manuals.mjs`, `scripts/release-player.mjs`, `scripts/manual-illustrations.mjs` |
| Nome da empresa nas faturas | `platform_settings.company_name` (padrão "Vyzzu", migração V22) |
| Regras protegidas por teste | `frontend/src/app/brand-tokens.spec.ts` |

Identificadores internos que **não** foram renomeados, de propósito: o pacote do app Android (`com.digitalsignage.player`: trocar faria cada TV tratar a nova versão como outro app, sem atualização automática), o objeto JavaScript dos widgets (`window.Smartplay`, contrato entre o Player e a página do widget), as chaves guardadas no navegador (`smartplay.*`, para ninguém perder a senha ou o cupom já pegos), o tipo interno de mídia dos widgets e o nome do repositório.
