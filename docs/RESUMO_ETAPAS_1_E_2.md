# Resumo das Etapas 1 e 2 (22/10/2026)

Para cada item: **o que mudou**, **como testar** (no painel, em `https://signage-painel.pages.dev`) e **o que fica com você**. Cada item foi um pedido de alteração (PR) no GitHub, com testes, CI verde e descrição; as migrações do banco são reversíveis (o comando de desfazer está no cabeçalho de cada arquivo `V24` a `V31`) e **nenhuma apagou dado**.

**Importante sobre o app da TV (Player):** os itens marcados com 📺 estão prontos e testados no código, mas só chegam às TVs com uma **versão nova do Player**, que **não foi publicada**: a TV pede um OK na tela ao instalar. Diga "pode publicar" quando quiser. A etapa 3 (anunciantes e cotas, cobrança automática, 2FA e auditoria, player web, contas de agência) não foi iniciada.

---

## Etapa 1

### 1.1 Infraestrutura: plano, backup e monitor
- **O que mudou:** plano passo a passo para sair do plano grátis (Supabase Pro + Render Starter, na mesma região); scripts de backup e de restauração (testados no CI em todo pedido: copia, restaura num banco limpo e confere); rotina diária de backup e alarme de disponibilidade prontos para ligar.
- **Como testar:** no GitHub, o job **"Scripts de backup (copia, restaura e confere)"** fica verde em todo pedido. O passo a passo está em `docs/MIGRACAO_PRODUCAO.md`.
- **Com você:** contratar os planos (tem custo, não gastei nada), criar as chaves do backup e um monitor externo. **Hoje o backup ainda não roda.**

### 1.2 Licenças e conformidade
- **O que mudou:** a previsão do tempo passou para a **MET Norway** (licença que permite uso comercial, com atribuição); política de privacidade, termos de uso, aviso com concordância na coleta do nome, prazos de guarda por cliente e tela de pedidos de titulares (LGPD); os termos de cada fonte de dados estão em `docs/FONTES_DE_DADOS.md`.
- **Como testar:** abra `/privacidade` e `/termos` (sem login); em **Privacidade e dados**, veja os prazos e a busca por pessoa.
- **Com você:** razão social/CNPJ e encarregado (Plataforma → Configurações, alimenta a política); escolher quais portais de notícias ficam (opção 1, 2 ou 3 em `docs/FONTES_DE_DADOS.md`); revisão por advogado.

### 1.3 Registro de exibição menor
- **O que mudou:** o registro de cada exibição foi compactado e há um resumo diário por mídia (guardado um ano), para o banco grátis não encher (V24). Contas em `docs/CAPACIDADE_BANCO.md`.
- **Como testar:** **Relatórios → Comprovação** e **Exibições**.

### 1.4 Pareamento por QR Code 📺
- **O que mudou:** a TV nova mostra um QR Code além do código de 6 letras; no celular, a página `/parear/<código>` ativa a TV (pede login de Administrador; a equipe da plataforma escolhe o cliente).
- **Como testar:** abra `/parear/<código>` com o código mostrado numa TV nova (o QR na tela só vem com o Player novo).

### 1.5 Diagnóstico da TV 📺
- **O que mudou:** o painel pede um relatório da TV (internet, servidor, espaço, última sincronização, permissões) e o mostra no histórico; na TV, OK no controle abre o diagnóstico (V25).
- **Como testar:** **Dispositivos → histórico da TV → Pedir diagnóstico**.

### 1.6 Inventário básico 📺 (parte)
- **O que mudou:** número de série, data de instalação e nota de comodato por TV (painel), e relatório de inventário; modelo, Android e memória chegam sozinhos pelo Player novo (V26).
- **Como testar:** **Dispositivos → Editar** a TV; **Relatórios → Inventário**.

### 1.7 Primeiros passos
- **O que mudou:** no dashboard, uma lista de passos (enviar mídia, ativar TV, montar playlist...) com visto, atalho e que some ao concluir.
- **Como testar:** abra o **Dashboard** de um cliente novo.

---

## Etapa 2

### 2.1 Planos da oferta comercial
- **O que mudou:** 7 planos novos: **Start** R$ 39, **Pro** R$ 89, **Business** R$ 179, **Rede** R$ 349 e **Comodato Básico/Completo/Premium** R$ 79/129/189, com TVs, espaço, usuários, recursos e tamanho de arquivo (V27, `docs/PLANOS_E_PRECOS.md`). Nenhum cliente foi mexido; os planos antigos seguem ativos.
- **Como testar:** **Plataforma → Planos**.
- **Com você:** **TV extra a R$ 9: o modelo atual não comporta** (a fatura é o preço fixo do plano). Enquanto isso, cobre com um plano sob medida; a proposta de como implementar está no documento e aguarda seu OK. Desativar os planos antigos quando quiser.

### 2.2 E-mail do sistema
- **O que mudou:** **recuperar senha** ("Esqueci a senha" na entrada), **boas-vindas** a conta nova, **aviso de fatura** e **aviso de TV fora do ar** por e-mail (campo novo em Alertas). Funciona com Brevo ou Resend pela API (o servidor grátis do Render **bloqueia** SMTP). Sem provedor, nada quebra: só não sai e-mail (V28, `docs/EMAIL.md`).
- **Como testar (depois de ligar o provedor):** **Alertas → Aviso por e-mail → Enviar e-mail de teste**; na entrada, **Esqueci a senha**.
- **Com você:** escolher o provedor e criar a conta (o Brevo não exige domínio próprio) e criar 3 variáveis no Render (`MAIL_API`, `MAIL_API_KEY`, `MAIL_FROM`); o passo a passo está no documento. **Antes disso nenhum e-mail sai.**

### 2.3 Alertar agora 📺
- **O que mudou:** aviso em **tela cheia, por cima de tudo**, para uma TV, um grupo ou todas, com título, urgência (cor), prazo e botão **Encerrar agora**; sai sozinho no fim do prazo, até com a internet da TV caída (V29). Um clique envia; a TV percebe em até 30 s.
- **Como testar:** **Alertar agora** no menu (Editor e Administrador). Visto de verdade num emulador de Android TV.

### 2.4 Validade por item da playlist
- **O que mudou:** cada item pode valer só entre duas datas e/ou em certos dias da semana, **sem criar campanha** ("promoção de terça"); funciona **com qualquer versão do Player** (V30).
- **Como testar:** **Playlists → editar → Quando exibir** abaixo de um item.
- **Limite:** uma TV sem internet só vê a virada do dia quando reconectar.

### 2.5 Resultados do QR Code e dos cupons
- **O que mudou:** tela **Resultados** (qualquer papel): "o conteúdo rodou X vezes, Y cupons foram usados", por dia, **por TV**, por promoção, com taxa de uso e **CSV**; QR Code **por TV** (em Cupons, escolha de onde é o QR) (V31).
- **Como testar:** **Cupons →** abra uma promoção → **De onde é este QR Code?** → baixe o QR; depois **Resultados**.
- **Limite:** "escaneado" é a abertura da página da promoção, não prova que a pessoa pegou o cupom.

### 2.6 Modelos prontos por segmento
- **O que mudou:** **Modelos prontos** (Barbearia, Restaurante, Clínica, Academia, Loja): você confere os textos e um clique cria os avisos, o relógio, o clima, a tabela de preços, as playlists e o layout (só o que o plano inclui); tudo editável depois.
- **Como testar:** **Modelos prontos → Usar este modelo**.
- **Com você:** conferir os textos e preços de exemplo; só a barbearia foi desenhada com mais cuidado e **ainda sem validação com um cliente real**.

---

## O que depende de você, em ordem
1. **Publicar o Player novo** (itens 📺).
2. **Provedor de e-mail** (2.2).
3. **Infraestrutura paga e backup** (1.1).
4. **CNPJ, razão social, encarregado e portais de notícias** (1.2).
5. **TV extra R$ 9** e **desativar os planos antigos** (2.1).

O relatório técnico-comercial atualizado, com ✅🟡❌ por item, está em `docs/RELATORIO_TECNICO_COMERCIAL_VYZZU.md`.
