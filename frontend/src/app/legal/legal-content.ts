import { PublicPrivacy } from '../core/models';

/**
 * Os textos da Política de Privacidade e dos Termos de Uso. Ficam em português (a versão oficial, para a lei brasileira) e são
 * dados, não template: o que muda de uma instalação para outra (nome da empresa, razão social, CNPJ, canal do encarregado) vem do
 * servidor (configurado em Plataforma → Configurações). Texto-base: deve ser revisado por um advogado antes de valer como
 * documento jurídico. Mudou o texto? Suba PrivacyNotice.VERSION no backend: a versão é gravada junto da concordância de cada pessoa.
 */
export interface LegalSection {
  title: string;
  /** Cada bloco é um parágrafo (texto) ou uma lista (várias linhas). */
  blocks: (string | string[])[];
}

export interface LegalContext {
  /** O nome da plataforma, como aparece para as pessoas ("Vyzzu"). */
  company: string;
  /** "Vyzzu Tecnologia Ltda, CNPJ 00.000.000/0001-00" (o que estiver configurado), ou só o nome. */
  controller: string;
  /** O canal para falar de dados pessoais: encarregado e e-mail, ou o suporte, ou a orientação de falar com a loja. */
  contact: string;
}

export function legalContext(info: PublicPrivacy | null): LegalContext {
  const company = info?.companyName || 'Vyzzu';
  const identity = [info?.legalName, info?.legalDocument ? `CNPJ ${info.legalDocument}` : null].filter(Boolean).join(', ');
  const email = info?.contactEmail || info?.supportEmail || null;
  const who = info?.contactName ? `${info.contactName}${email ? `, e-mail ${email}` : ''}` : email ? `e-mail ${email}` : null;
  return {
    company,
    controller: identity || company,
    contact: who ?? `o suporte do ${company} (o canal de contato é informado pelo administrador da sua conta)`,
  };
}

export function privacySections(c: LegalContext): LegalSection[] {
  return [
    {
      title: '1. Quem somos e a quem esta política se aplica',
      blocks: [
        `O ${c.company} é uma plataforma de comunicação audiovisual e sinalização digital: empresas (os "clientes") controlam o que aparece em TVs, painéis de LED e totens e oferecem filas de atendimento e cupons aos seus próprios clientes. Esta política explica quais dados pessoais são tratados, para quê, por quanto tempo e como você exerce os seus direitos, conforme a Lei Geral de Proteção de Dados (Lei nº 13.709/2018, a LGPD).`,
        `Ela vale para (a) quem usa o painel, a equipe de um cliente, e (b) quem abre as páginas públicas de uma loja a partir de um QR Code (a senha da fila e o cupom). Responsável pela plataforma: ${c.controller}.`,
      ],
    },
    {
      title: '2. Nosso papel: controlador e operador',
      blocks: [
        `Dados da equipe que usa o painel (nome, e-mail e acesso): o ${c.company} é o controlador.`,
        `Dados de quem usa as páginas públicas de uma loja (o nome na fila e no cupom): a loja que exibiu o QR Code é a controladora e decide para que o dado serve; o ${c.company} é o operador e o trata em nome dela, só para prestar o serviço. Por isso o aviso nessas páginas mostra o nome da loja e o contato dela.`,
      ],
    },
    {
      title: '3. Quais dados tratamos',
      blocks: [
        [
          'Equipe do painel: nome, e-mail, papel (administrador, editor ou visualizador), situação da conta e a data de criação. A senha é guardada apenas em forma irreversível (hash); nem a nossa equipe a consegue ler.',
          'Quem pega uma senha de fila ou um cupom pelo celular: o nome, que é opcional (até 60 caracteres); a senha ou o cupom e a data e hora; um código aleatório do aparelho, guardado no navegador, usado para limitar abusos e devolver a mesma senha ou cupom a quem volta a escanear; e, quando há nome, o momento em que você concordou com o aviso e a versão do aviso que viu.',
          'Clientes (empresas): nome da empresa, plano, e-mail de contato e as faturas (valor, vencimento e forma de pagamento informada pela operação).',
          'Mensagens do sistema por e-mail: para a equipe do painel e para o contato de cobrança, enviamos o link de recuperação de senha, as boas-vindas, o aviso de fatura e, se configurado, os avisos de TV fora do ar. O link vale por tempo limitado e de uma só vez, e o servidor guarda apenas uma impressão irreversível dele, não o link em si.',
          'Dados técnicos: o endereço IP é usado só na memória do servidor, para limitar a quantidade de pedidos por hora, e não é gravado no nosso banco de dados. Os provedores de hospedagem podem manter seus próprios registros de infraestrutura.',
        ],
        'Não pedimos dados sensíveis (origem racial, saúde, religião, biometria etc.). Pedimos que você não digite esse tipo de informação no campo do nome. O conteúdo que os clientes enviam (imagens, vídeos, textos) é de responsabilidade de quem o envia; se ele tiver dados pessoais, o cliente é o controlador desses dados.',
      ],
    },
    {
      title: '4. Para que usamos os dados e em que base legal',
      blocks: [
        [
          'Cadastro, acesso ao painel e segurança da conta: execução do contrato (art. 7º, V) e legítimo interesse na segurança do serviço (art. 7º, IX).',
          'Chamar a sua senha na fila ou achar o seu cupom no caixa, usando o nome que você informou: consentimento (art. 7º, I), dado quando você digita o nome e marca que concorda. O nome é opcional: sem ele a senha e o cupom funcionam do mesmo jeito.',
          'Código aleatório do aparelho: legítimo interesse em evitar abusos e em devolver a mesma senha ou cupom (art. 7º, IX).',
          'Cobrança e suporte aos clientes: execução do contrato (art. 7º, V) e cumprimento de obrigação legal ou regulatória, quando houver (art. 7º, II).',
        ],
        `Não vendemos dados pessoais e não os usamos para publicidade de terceiros.`,
      ],
    },
    {
      title: '5. Por quanto tempo guardamos',
      blocks: [
        [
          'Nome na senha da fila: 30 dias, por padrão. A loja pode escolher de 1 a 365 dias. Passado o prazo, a senha inteira é apagada.',
          'Nome no cupom: 90 dias, por padrão (a loja escolhe de 1 a 365). Passado o prazo, o nome e a concordância são apagados e o cupom fica sem identificar ninguém.',
          'Histórico das TVs e alertas: 90 dias por padrão; detalhe de cada exibição: 7 dias por padrão (o resumo diário e o resumo por mídia ficam por um ano). Não identificam pessoas.',
          'Conta da equipe: enquanto existir; ao ser excluída pelo administrador, deixa de existir no painel.',
          'Faturas e dados de cobrança: pelo prazo que a legislação fiscal e contábil exigir.',
        ],
        'Os provedores de infraestrutura podem manter cópias de segurança por um período limitado, até serem substituídas.',
      ],
    },
    {
      title: '6. Com quem compartilhamos',
      blocks: [
        [
          'Provedores de infraestrutura (operadores): Supabase (banco de dados e arquivos), Render (servidor da aplicação) e Cloudflare (hospedagem do painel e do site de downloads).',
          'Provedor de envio de e-mail (operador), quando o envio estiver ativado: recebe o endereço de destino e o texto das mensagens do sistema, só para entregá-las.',
          'Fontes públicas dos widgets (previsão do tempo, cotações e notícias): o nosso servidor as consulta sem enviar dado de nenhuma pessoa.',
          'Autoridades, quando a lei exigir.',
        ],
        'Não compartilhamos dados pessoais com terceiros para fins de marketing.',
      ],
    },
    {
      title: '7. Transferência internacional',
      blocks: [
        'Os dados ficam em servidores nos Estados Unidos (o banco de dados no Supabase, região us-east-1, e o servidor da aplicação no Render, na região de Oregon). Essa transferência é necessária para executar o serviço contratado (LGPD, art. 33, IX, combinado com o art. 7º, V).',
      ],
    },
    {
      title: '8. Os seus direitos',
      blocks: [
        'Você pode pedir, a qualquer momento (art. 18 da LGPD): confirmação de que tratamos dados seus; acesso a eles; correção; anonimização, bloqueio ou eliminação de dados desnecessários ou tratados fora da lei; portabilidade; eliminação dos dados tratados com o seu consentimento; informação sobre com quem os compartilhamos; informação sobre a possibilidade de não consentir e as consequências; e a revogação do consentimento.',
        `Como pedir: quem informou o nome numa loja deve falar com a loja (a controladora), no contato mostrado no aviso da página; a loja localiza, copia e apaga os dados na tela "Privacidade e dados" do painel. Você também pode escrever para ${c.contact}. Respondemos em até 15 dias. Se não ficar satisfeito, pode apresentar reclamação à Autoridade Nacional de Proteção de Dados (ANPD).`,
      ],
    },
    {
      title: '9. Segurança',
      blocks: [
        [
          'Comunicação por HTTPS e senhas guardadas com hash irreversível.',
          'Acesso ao painel por papéis (administrador, editor e visualizador) e isolamento entre clientes: cada cliente só acessa os próprios dados.',
          'Os endereços dos QR Codes levam um segredo, e há limites de pedidos por hora contra abusos. A senha provisória de um novo usuário precisa ser trocada no primeiro acesso.',
        ],
        'Nenhum sistema é totalmente imune a falhas. Se ocorrer um incidente que possa gerar risco ou dano relevante, comunicaremos os clientes afetados e a ANPD em prazo razoável (LGPD, art. 48).',
      ],
    },
    {
      title: '10. Armazenamento no navegador',
      blocks: [
        'Usamos o armazenamento local do navegador apenas para o serviço funcionar: o acesso ao painel, o tema e o idioma escolhidos, e, nas páginas públicas, o código aleatório do aparelho e a senha ou o cupom que você já pegou. Não usamos cookies de publicidade nem rastreadores de terceiros, e por isso não há faixa de cookies.',
      ],
    },
    {
      title: '11. Crianças e adolescentes',
      blocks: [
        'O serviço é voltado a empresas. As páginas públicas de uma loja podem ser abertas por qualquer pessoa; pedimos que menores de 12 anos não informem o nome sem o acompanhamento de um responsável (LGPD, art. 14).',
      ],
    },
    {
      title: '12. Mudanças nesta política',
      blocks: [
        'Quando o texto mudar, a versão e a data no alto desta página mudam. A versão do aviso que cada pessoa viu fica registrada junto da concordância dela.',
      ],
    },
    {
      title: '13. Contato',
      blocks: [`Dúvidas e pedidos sobre dados pessoais: ${c.contact}.`],
    },
  ];
}

export function termsSections(c: LegalContext): LegalSection[] {
  return [
    {
      title: '1. Aceitação e objeto',
      blocks: [
        `Estes Termos de Uso regulam o acesso ao ${c.company}, plataforma de gestão de conteúdo para TVs, painéis de LED e totens, e dos serviços ligados a ela (painel, aplicativo da TV, widgets, QR Codes, filas de atendimento e cupons). Ao usar o serviço, o cliente e a sua equipe concordam com estes termos e com a Política de Privacidade. A plataforma é oferecida por ${c.controller}.`,
      ],
    },
    {
      title: '2. Cadastro e contas',
      blocks: [
        'O acesso é feito por contas da equipe do cliente, com papéis diferentes (administrador, editor e visualizador). O cliente é responsável por quem recebe acesso, por manter as senhas em sigilo e por tudo o que é feito com as suas contas. A senha provisória de um novo usuário deve ser trocada no primeiro acesso. Avise-nos se suspeitar de uso indevido de uma conta.',
      ],
    },
    {
      title: '3. Planos, preços e pagamento',
      blocks: [
        'Cada cliente usa um plano, com limites (TVs, espaço de mídia, usuários) e recursos próprios, mostrados em "Plano e cobrança". As faturas são emitidas pela operação da plataforma, com as instruções de pagamento ali indicadas. Passado o vencimento e o prazo de tolerância, o painel pode ser suspenso até a regularização. Os dados do cliente não são apagados por causa da suspensão.',
      ],
    },
    {
      title: '4. Uso permitido e conteúdo do cliente',
      blocks: [
        [
          'O conteúdo enviado ou criado pelo cliente (imagens, vídeos, textos, widgets, QR Codes, páginas indicadas) é de responsabilidade dele. O cliente declara ter os direitos necessários (autorais, de imagem, de marca) e autoriza o ' + c.company + ' a armazená-lo e exibi-lo nas TVs dele, só para prestar o serviço.',
          'É proibido usar o serviço para conteúdo ilegal, que viole direitos de terceiros, discriminatório, enganoso ou que ponha pessoas em risco; tentar acessar dados de outros clientes; contornar limites do plano; ou sobrecarregar a plataforma.',
          'Páginas públicas (senha e cupom) só devem ser usadas para o fim a que se destinam.',
        ],
      ],
    },
    {
      title: '5. Dados pessoais',
      blocks: [
        `Em relação aos dados das pessoas que usam as filas e os cupons de uma loja, o cliente (a loja) é o controlador e o ${c.company} é o operador. O cliente deve usar o serviço de acordo com a LGPD: informar as pessoas (o aviso das páginas públicas ajuda), respeitar os prazos de guarda que configurou e atender os pedidos de titulares, com a ajuda da tela "Privacidade e dados". Os detalhes estão na Política de Privacidade.`,
      ],
    },
    {
      title: '6. Disponibilidade',
      blocks: [
        'Trabalhamos para manter a plataforma disponível, mas não garantimos funcionamento ininterrupto. Pode haver manutenção, falhas de provedores e problemas de internet nos locais. As TVs guardam o conteúdo e seguem a agenda recebida quando ficam sem internet, mas o que depende de dados em tempo real (como previsão do tempo e cotações) pode ficar desatualizado.',
      ],
    },
    {
      title: '7. Propriedade intelectual',
      blocks: [
        `O software, o nome e a marca ${c.company} e o material do serviço pertencem à operação da plataforma. O cliente recebe o direito de uso enquanto o contrato valer, sem direito de copiar, revender ou fazer engenharia reversa, salvo quando a lei permitir. O conteúdo do cliente continua sendo do cliente.`,
      ],
    },
    {
      title: '8. Suspensão e encerramento',
      blocks: [
        'O cliente pode encerrar o uso quando quiser. Podemos suspender ou encerrar contas que violem estes termos ou a lei, com aviso sempre que possível. Ao encerrar, o cliente pode pedir a cópia e a exclusão dos dados dele.',
      ],
    },
    {
      title: '9. Limitação de responsabilidade',
      blocks: [
        'Na medida permitida pela lei, o serviço é prestado como está, e não respondemos por danos indiretos nem por perdas causadas por conteúdo do cliente, por falhas de internet ou de serviços de terceiros, ou por uso em desacordo com estes termos. Nada aqui limita direitos que a lei garanta ao consumidor.',
      ],
    },
    {
      title: '10. Alterações',
      blocks: ['Podemos atualizar estes termos. A versão e a data no alto da página mostram a vigente; o uso do serviço depois da mudança significa concordância com ela.'],
    },
    {
      title: '11. Lei aplicável e contato',
      blocks: [`Estes termos seguem a lei brasileira. Dúvidas: ${c.contact}.`],
    },
  ];
}
