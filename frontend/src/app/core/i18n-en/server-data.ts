/**
 * Inglês de textos que o SERVIDOR grava ou devolve em português como dado do sistema: modelos de layout, planos de
 * fábrica, mensagens do histórico das TVs e dos alertas. Casados em tempo de execução por trServer (veja i18n.ts).
 * O que a PESSOA digitou (nome de uma TV, de uma playlist, de um local…) nunca é traduzido.
 */
export const LAYOUT_TEMPLATES: Record<string, string> = {
  'Tela cheia': 'Full screen',
  'Um único conteúdo ocupando a tela toda (o comportamento de sempre).': 'A single content item filling the whole screen (the usual behavior).',
  'Principal + faixa lateral': 'Main + side strip',
  'Conteúdo principal à esquerda e uma faixa à direita (avisos, promoções, QR Code).':
    'Main content on the left and a strip on the right (notices, promotions, QR Code).',
  'Principal + rodapé': 'Main + footer',
  'Conteúdo principal em cima e uma faixa embaixo (avisos, horários, notícias).':
    'Main content on top and a strip at the bottom (notices, schedules, news).',
  'Principal + lateral + rodapé': 'Main + side + footer',
  'Conteúdo principal, uma faixa à direita e um rodapé.': 'Main content, a strip on the right and a footer.',
  'Duas metades': 'Two halves',
  'A tela dividida ao meio: dois conteúdos lado a lado.': 'The screen split in half: two contents side by side.',
  'Quatro quadros': 'Four quadrants',
  'Quatro conteúdos iguais (bom para painéis de informação).': 'Four equal contents (good for information boards).',
};

export const PLAN_TEXT: Record<string, string> = {
  'Interno (sem limites)': 'Internal (no limits)',
  'Uso próprio da operação: sem limites e sem cobrança.': "The operator's own use: no limits and no billing.",
  Grátis: 'Free',
  'Para conhecer: 2 TVs.': 'To try it out: 2 TVs.',
  Básico: 'Basic',
  'Pequenos negócios: até 5 TVs.': 'Small businesses: up to 5 TVs.',
  Profissional: 'Professional',
  'Várias lojas: até 25 TVs.': 'Several stores: up to 25 TVs.',
};

export const MONITORING_MESSAGES: Record<string, string> = {
  'TV ativada com o nome "{name}"': 'TV activated with the name "{name}"',
  'TV desativada no painel': 'TV disabled in the panel',
  'TV reativada no painel': 'TV re-enabled in the panel',
  'Layout da tela removido: a TV voltou para a tela cheia': 'Screen layout removed: the TV went back to full screen',
  'Layout da tela: "{name}"': 'Screen layout: "{name}"',
  'Pedido de verificação de atualização do Player enviado à TV': 'Player update check request sent to the TV',
  'TV ficou sem contato com o servidor': 'TV lost contact with the server',
  'Primeiro contato da TV com o servidor': "The TV's first contact with the server",
  'TV voltou a se conectar, depois de {time} sem contato': 'TV reconnected after {time} without contact',
  'Player atualizado de {from} para {to}': 'Player updated from {from} to {to}',
  'A TV ficou sem playlist para exibir': 'The TV has no playlist to show',
  'Exibindo a playlist "{playlist}" da campanha "{campaign}"': 'Showing the playlist "{playlist}" from the campaign "{campaign}"',
  'Exibindo a playlist "{playlist}"': 'Showing the playlist "{playlist}"',

  // alertas (a TV com e sem local)
  'A TV "{name}" está sem contato com o servidor há mais de {time}': 'The TV "{name}" has had no contact with the server for more than {time}',
  'A TV "{name}" ({location}) está sem contato com o servidor há mais de {time}':
    'The TV "{name}" ({location}) has had no contact with the server for more than {time}',
  'A TV "{name}" foi ativada, mas ainda não se conectou ao servidor (há mais de {time})':
    'The TV "{name}" was activated but has not connected to the server yet (more than {time} ago)',
  'A TV "{name}" ({location}) foi ativada, mas ainda não se conectou ao servidor (há mais de {time})':
    'The TV "{name}" ({location}) was activated but has not connected to the server yet (more than {time} ago)',
  'A TV "{name}" relatou {n} erros nos últimos {time}': 'The TV "{name}" reported {n} errors in the last {time}',
  'A TV "{name}" ({location}) relatou {n} erros nos últimos {time}': 'The TV "{name}" ({location}) reported {n} errors in the last {time}',
};

export const SERVER_DATA: Record<string, string> = { ...LAYOUT_TEMPLATES, ...PLAN_TEXT, ...MONITORING_MESSAGES };
