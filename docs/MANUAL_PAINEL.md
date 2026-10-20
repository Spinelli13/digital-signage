# Manual do painel

Como usar o **painel de administração** do Digital Signage no dia a dia: enviar imagens e vídeos, montar playlists, vincular TVs e acompanhar se está tudo funcionando.

> **Endereço do painel:** `https://signage-painel.pages.dev`. Funciona em computador, tablet e celular. O aplicativo da TV e os manuais ficam sempre atualizados em **Downloads e manuais** (menu à esquerda).

![Como as peças se encaixam: a biblioteca guarda as mídias, a playlist define a ordem e cada TV exibe uma playlist](manual/img/conceito-playlist.svg)

**Em resumo, o trabalho é sempre o mesmo:**

1. Enviar as mídias para a **Biblioteca** (uma vez só).
2. Montar uma **Playlist** com as mídias na ordem e com o tempo que você quiser.
3. Em **Dispositivos**, escolher qual playlist cada TV exibe.

---

## 1. Entrar no painel

1. Abra o endereço do painel no navegador.
2. Digite o **e-mail** e a **senha** de administrador e clique em **Entrar**.

![Tela de entrada do painel (captura real)](manual/img/painel-login.png)

> **Na primeira vez do dia, o painel pode demorar até 1 minuto** para responder. O servidor gratuito "dorme" quando fica sem uso e leva um tempo para acordar. É normal, e **não afeta as TVs**: elas continuam exibindo o que já baixaram.

Para sair, use o botão **Sair** no canto inferior esquerdo.

---

## 2. Conhecendo o painel

![Dashboard: visão geral com dispositivos, TVs online, mídias e playlists (captura real)](manual/img/painel-inicio.png)

O **menu à esquerda** leva a cada área:

| Menu | Para que serve |
|---|---|
| **Dashboard** | Visão geral: quantas TVs, quantas estão online, quantas aguardam ativação, e quais precisam de atenção. |
| **Dispositivos** | As TVs: ativar, dar nome e local, escolher a playlist, desativar, excluir e pedir atualização do aplicativo. |
| **Biblioteca** | Suas imagens e vídeos. |
| **Playlists** | A ordem e o tempo de cada mídia. |
| **Downloads e manuais** | O aplicativo da TV (sempre a versão mais recente) e estes manuais em PDF. |

---

## 3. Biblioteca: enviar imagens e vídeos

1. Abra **Biblioteca**.
2. Clique em **+ Enviar mídia** e escolha um ou vários arquivos.
3. Acompanhe a barra de progresso. Ao terminar, a mídia aparece na lista com uma miniatura.

![Biblioteca de mídia com as imagens enviadas (captura real)](manual/img/painel-biblioteca.png)

**Formatos aceitos**

| Tipo | Formatos | Recomendação |
|---|---|---|
| Imagem | JPG, PNG, WEBP | Proporção **16:9**, de preferência **1920×1080**. Imagens fora dessa proporção aparecem inteiras, com faixas pretas. |
| Vídeo | MP4 (vídeo H.264) | Até **1080p** e **no máximo 50 MB** por arquivo. Outros formatos (MOV, AVI, MKV, HEVC) podem não tocar. |

**Excluir uma mídia:** clique em **Excluir** no cartão da mídia. Se ela estiver em alguma playlist, o painel avisa que não dá para remover; tire-a das playlists primeiro.

---

## 4. Playlists: montar o que vai passar na TV

### 4.1 Criar uma playlist

1. Abra **Playlists** e clique em **+ Nova playlist**.
2. Dê um **nome** (ex.: "Campanha de verão") e, se quiser, uma descrição.
3. Na coluna **Biblioteca** (à direita), **clique nas mídias** para adicioná-las à sequência.
4. Ajuste cada item:
   - **Duração (s):** quantos segundos a imagem fica na tela. Para vídeos vale a duração do próprio vídeo.
   - **Setas ↑ ↓:** mudam a posição na sequência.
   - **✕:** tira o item da playlist (a mídia continua na Biblioteca).
5. Confira o **ciclo total** ("ciclo de 32s") e clique em **Publicar alterações**.

![Editor de playlist: sequência de reprodução à esquerda e biblioteca à direita (captura real)](manual/img/painel-playlist-editor.png)

> **Importante:** as mudanças só valem para as TVs depois de **Publicar alterações**. Em até 1 minuto elas baixam a novidade e passam a exibi-la.

### 4.2 Lista de playlists

Em **Playlists** você vê todas, com o número de itens, a duração do ciclo e a **versão** (sobe a cada publicação). Use **Editar** para alterar e **Excluir** para apagar. Se você excluir uma playlist que está em uso, as TVs que a usavam ficam **sem playlist**.

![Lista de playlists (captura real)](manual/img/painel-playlists.png)

---

## 5. Dispositivos: as TVs

### 5.1 Ativar uma TV nova

1. Instale o Player na TV (veja o **Manual de instalação**). Ao abrir, ela mostra um **código de 6 letras e números**.
2. No painel, abra **Dispositivos** e clique em **+ Adicionar dispositivo**.
3. Digite o **código**, dê um **nome** (ex.: "TV Recepção") e o **local** (ex.: "Loja Centro") e clique em **Ativar dispositivo**.

![Janela "Adicionar dispositivo" com o código da TV digitado (captura real)](manual/img/painel-adicionar-dispositivo.png)

O código vale por **24 horas** e só pode ser usado uma vez. Se expirar, reinicie o Player na TV para gerar outro.

### 5.2 Escolher a playlist de cada TV

Na linha da TV, use a lista da coluna **Playlist**:

- escolha uma playlist para a TV começar a exibi-la (em até 1 minuto);
- escolha **Nenhuma playlist** para tirar o conteúdo: a TV passa a mostrar "Aguardando conteúdo". A playlist em si **não** é apagada.

![Lista de dispositivos: status, último contato, versão do aplicativo e playlist de cada TV (captura real)](manual/img/painel-dispositivos.png)

### 5.3 O que significa cada status

![Significado dos status das TVs no painel](manual/img/estados-tv.svg)

### 5.4 Outras ações

| Botão | O que faz |
|---|---|
| **Editar** | Muda o nome e o local da TV. |
| **Atualizar app** | Pede que **esta TV** verifique agora se há versão nova do aplicativo (ela percebe em até 30 segundos). |
| **Atualizar todas as TVs** (no alto da página) | O mesmo pedido para todas as TVs ativas. |
| **Desativar / Reativar** | Pausa a TV (ela para de exibir) ou volta a ativá-la. |
| **Excluir** | Remove a TV. Ela esquece o conteúdo e volta a pedir um **novo código de ativação**. |

### 5.5 Atualizar o aplicativo das TVs

Quando existe uma versão nova do Player, as TVs descobrem sozinhas em até 30 minutos. Para adiantar, use **Atualizar app** (uma TV) ou **Atualizar todas as TVs**. O painel avisa que o pedido foi enviado:

![Aviso do painel depois de pedir a atualização das TVs (captura real)](manual/img/painel-atualizar-tvs.png)

> **O Android sempre pede uma confirmação na TV** antes de instalar a atualização (regra de segurança do sistema). Alguém precisa apertar **OK** no controle. Se ninguém confirmar em 10 minutos, a TV cancela e volta a exibir normalmente. Detalhes no **Manual de instalação**, seção "Atualização automática".

TVs com versão mais antiga que a última publicada aparecem marcadas com **⬆ desatualizado** na coluna "Último contato".

---

## 6. Downloads e manuais

Em **Downloads e manuais** você encontra, sempre atualizados:

- o **aplicativo da TV** (APK) na versão mais recente, com o endereço curto para digitar no app Downloader da TV;
- as versões anteriores, caso precise voltar atrás;
- os manuais em **PDF** (este e o de instalação).

![Página "Downloads e manuais" do painel (captura real)](manual/img/painel-downloads.png)

---

## 7. Avisos de erro

Quando algo dá errado, o painel mostra um aviso vermelho no alto da tela, em português, dizendo **o que aconteceu e o que fazer**. O aviso **fica na tela até você clicar em Fechar**.

![Aviso de erro do painel: o servidor não respondeu (captura real)](manual/img/painel-erro.png)

Mensagens comuns:

Todo aviso começa pela **ação** que falhou (por exemplo, "Excluir mídia: …", "Carregar dispositivos: …").

| O aviso diz | O que significa | O que fazer |
|---|---|---|
| …o servidor demorou demais para responder | O servidor gratuito estava "dormindo". | Espere cerca de **1 minuto** e tente de novo. |
| …não foi possível conectar ao servidor | Falha na sua internet ou no servidor. | Confira a conexão; se o servidor estava inativo, aguarde cerca de 1 minuto. |
| …o servidor encontrou um erro (HTTP 5xx) | Problema temporário no servidor. | Tente de novo em instantes. |
| …sua sessão expirou. Entre novamente. | Passou muito tempo logado. | Entre de novo com e-mail e senha. |
| …existe outro registro que depende deste item | Você tentou excluir algo em uso (por exemplo, uma mídia que está numa playlist). | Retire o item de onde ele está sendo usado e tente de novo. |
| Ativar dispositivo: Código de ativação inválido ou expirado | O código da TV venceu ou foi digitado errado. | Confira o código na TV; se preciso, reinicie o Player para gerar outro. |
| …o arquivo é grande demais | A mídia passa do limite de tamanho. | Comprima o vídeo (até 50 MB) e envie de novo. |

---

## 8. Boas práticas

- **Teste com uma TV primeiro**, depois replique para as outras.
- Prefira imagens **16:9 (1920×1080)** e vídeos **MP4 H.264 até 1080p**, de no máximo 50 MB.
- Dê **nomes claros** às mídias e playlists (ex.: "Promoção de verão", não "IMG_0231").
- Nas imagens, **5 a 10 segundos** costumam ser suficientes; textos longos pedem mais tempo.
- **Lembre de publicar:** alterar a playlist não muda a TV até clicar em **Publicar alterações**.
- Não precisa se preocupar com a internet da TV: ela **guarda tudo** e continua exibindo mesmo se a conexão cair.

---

## 9. Solução de problemas

| Problema | O que fazer |
|---|---|
| A TV aparece **Offline** mas está exibindo | Normal por até ~2 minutos depois de uma queda de internet. Se persistir, verifique o Wi‑Fi da TV. |
| A TV mostra **"Aguardando conteúdo"** | Ela está ativa, mas sem playlist. Escolha uma playlist para ela em **Dispositivos**. |
| Mudei a playlist e a TV não mudou | Confirme que clicou em **Publicar alterações** e aguarde até 1 minuto. |
| A TV não aparece na lista depois de digitar o código | Confira se o código está certo (6 caracteres) e dentro de 24 horas. |
| O painel não abre / "servidor demorou" | Aguarde cerca de 1 minuto e recarregue a página. |
| Vídeo não toca | Converta para **MP4 com vídeo H.264**. |
| A TV não se atualiza | Veja o **Manual de instalação**, seção "Atualização automática" (precisa liberar "instalar apps desconhecidos" e alguém confirmar na TV). |

<!-- fim-do-manual -->

## Notas para quem mantém o projeto

- Os manuais (`INSTALACAO_TV.md` e este arquivo) viram PDF/HTML em cada publicação do Player (`scripts/build-manuals.mjs`) e ficam no site de downloads.
- As capturas de tela ficam em `docs/manual/img/` (reais, do painel e do emulador de Android TV) e os esquemas são gerados por `scripts/manual-illustrations.mjs`.
