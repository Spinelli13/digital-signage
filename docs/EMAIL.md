# E-mail do sistema (Etapa 2.2)

O Vyzzu manda quatro tipos de e-mail. Todos são texto simples (sem imagens nem rastreadores), saem em português e **só saem se houver um provedor de e-mail configurado**. Sem provedor nada quebra: o sistema registra no log que o e-mail não saiu e segue funcionando como antes (a senha provisória continua sendo combinada pessoalmente, por exemplo).

## O que é enviado

| E-mail | Quando | Para quem |
|---|---|---|
| **Recuperar a senha** | A pessoa clica em **Esqueci a senha** na tela de entrada e digita o e-mail | O e-mail digitado, se existir uma conta ativa com ele |
| **Boas-vindas** | Uma conta nova é criada (cliente novo, usuário da equipe ou gestor da plataforma) | A pessoa da conta nova, com o link para escolher a própria senha |
| **Fatura emitida** | Uma fatura é gerada (cobrança do mês ou fatura manual) | O e-mail de cobrança do cliente; sem ele, os administradores ativos do cliente. Fatura de valor zero (coberta por cupom) não gera aviso |
| **Aviso de TV fora do ar** | Um alerta de TV sem contato (ou o que for configurado) abre e, se marcado, quando é resolvido | Os e-mails de **Alertas → Aviso por e-mail** (até 3), além do webhook que já existia |

### Segurança do link de senha

- O link leva um segredo aleatório de 256 bits. **O servidor guarda só o SHA-256 dele**: quem lê o banco não consegue usar o link.
- Vale **1 hora** na recuperação e **3 dias** nas boas-vindas, e **uma vez só**: ao trocar a senha, qualquer outro link ainda aberto da mesma pessoa deixa de valer.
- "Esqueci a senha" responde **sempre a mesma coisa**, exista ou não uma conta com o e-mail (ninguém descobre quem é cliente). Há um intervalo de 2 minutos entre pedidos do mesmo e-mail e no máximo 5 por hora, para ninguém encher a caixa de outra pessoa.
- O log guarda o endereço mascarado (`a**@empresa.com`), nunca inteiro, e nunca a chave do provedor.

## Como ligar (passo a passo para o dono)

O envio é **independente de provedor**: o servidor lê variáveis de ambiente (no Render: **Environment**). Nada de chave em arquivo, chat ou commit.

> **Atenção, plano gratuito do Render:** o Render bloqueia a saída para as portas de SMTP (25, 465 e 587) em serviços gratuitos ([aviso oficial](https://render.com/changelog/free-web-services-will-no-longer-allow-outbound-traffic-to-smtp-ports)); só voltam com um plano pago. Por isso o sistema envia pela **API HTTPS** do provedor (`MAIL_API`), que passa em qualquer plano. O SMTP (`MAIL_HOST`) continua disponível para quando o servidor estiver em plano pago.

### Opção A — Brevo (não exige domínio próprio)

1. Criar a conta em brevo.com (plano gratuito) e **validar o remetente** em *Senders, Domains & Dedicated IPs → Senders* (pode ser um e-mail seu; o Brevo manda um código de confirmação).
2. Em *SMTP & API → API Keys*, gerar uma chave.
3. No Render, em **Environment**, criar:
   - `MAIL_API` = `brevo`
   - `MAIL_API_KEY` = a chave gerada
   - `MAIL_FROM` = `Vyzzu <o-email-validado@...>` (o mesmo endereço validado no passo 1)
   - `PANEL_URL` = `https://signage-painel.pages.dev` (já existe um valor padrão pela origem do painel, mas vale deixar explícito)
4. Salvar (o Render reinicia o serviço).

### Opção B — Resend (exige domínio próprio validado para enviar a terceiros)

1. Criar a conta em resend.com, adicionar o domínio e criar no DNS os registros (SPF/DKIM) que ele indicar.
2. Gerar uma chave de API.
3. No Render: `MAIL_API` = `resend`, `MAIL_API_KEY` = a chave, `MAIL_FROM` = `Vyzzu <avisos@seudominio.com>`.

Sem domínio, o Resend só entrega para o e-mail da própria conta (serve para testar, não para clientes).

### Opção C — SMTP (só em hospedagem paga)

`MAIL_HOST` (servidor do provedor), `MAIL_PORT` (587), `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`; `MAIL_STARTTLS=true` (padrão). Serve para qualquer provedor que ofereça SMTP.

Limites e regras dos planos gratuitos mudam: **confira no site do provedor** antes de escolher.

## Como testar depois de ligar

1. **Alertas → Aviso por e-mail**: digite o seu e-mail, **Salvar configurações** e depois **Enviar e-mail de teste**. O aviso amarelo "O envio de e-mail ainda não foi ativado" some quando o servidor tem provedor.
2. Na tela de entrada, **Esqueci a senha** com a sua própria conta: o link deve chegar em instantes (olhe o spam na primeira vez).
3. Se nada chegar, o log do Render mostra o motivo, sem dado sensível: `E-mail '…' para a**@… falhou: …HTTP 401…` (chave errada), `HTTP 400` (remetente não validado), `provedor ainda não está configurado` (variáveis ausentes).

## Onde está no código

- `mail/` — `EmailService` (nunca derruba quem pediu: devolve se saiu), `EmailTemplates` (os textos), `MailConfig` (escolhe API ou SMTP pelas variáveis), `ApiEmailSender` (Resend e Brevo por HTTPS), `EmailSender` (a interface para trocar de provedor e para os testes).
- `auth/PasswordResetService` — recuperação e boas-vindas; tabela `password_resets` (migração V28).
- `billing/InvoiceMailer` — aviso de fatura. `monitoring/AlertNotifier` — aviso de TV fora do ar.
- Painel: `login/forgot-password.page.ts`, `login/reset-password.page.ts` e o campo em `alerts/alerts.page.ts`.
