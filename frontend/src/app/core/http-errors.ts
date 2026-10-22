import { HttpContextToken, HttpErrorResponse, HttpRequest } from '@angular/common/http';

import { tr, trServer } from './i18n';

/** Requisições "em segundo plano" (miniaturas etc.) que não devem gerar aviso quando falham. */
export const SILENT_ERRORS = new HttpContextToken<boolean>(() => false);

export const REQUEST_TIMEOUT_MS = 120_000;

export const ACTIONS: [method: string, path: RegExp, label: string][] = [
  ['POST', /\/auth\/login$/, 'Entrar'],
  ['POST', /\/auth\/forgot-password$/, 'Pedir o link da senha nova'],
  ['POST', /\/auth\/reset-password$/, 'Escolher a senha nova'],
  ['POST', /\/alerts\/settings\/test-email$/, 'Testar o e-mail dos avisos'],
  ['GET', /\/results$/, 'Carregar os resultados'],
  ['GET', /\/screen-alerts$/, 'Carregar os avisos em tela cheia'],
  ['POST', /\/screen-alerts$/, 'Enviar o aviso para as TVs'],
  ['POST', /\/screen-alerts\/\d+\/end$/, 'Encerrar o aviso'],
  ['GET', /\/devices$/, 'Carregar dispositivos'],
  ['POST', /\/devices\/activate$/, 'Ativar dispositivo'],
  ['PUT', /\/devices\/\d+$/, 'Salvar dispositivo'],
  ['DELETE', /\/devices\/\d+\/playlist$/, 'Remover a playlist da TV'],
  ['POST', /\/devices\/\d+\/update-check$/, 'Pedir atualização da TV'],
  ['POST', /\/devices\/update-check$/, 'Pedir atualização das TVs'],
  ['DELETE', /\/devices\/\d+$/, 'Excluir dispositivo'],
  ['GET', /\/media$/, 'Carregar a biblioteca'],
  ['POST', /\/media$/, 'Enviar mídia'],
  ['DELETE', /\/media\/\d+$/, 'Excluir mídia'],
  ['GET', /\/media\/\d+/, 'Carregar mídia'],
  ['GET', /\/playlists$/, 'Carregar playlists'],
  ['GET', /\/playlists\/\d+$/, 'Carregar playlist'],
  ['POST', /\/playlists$/, 'Criar playlist'],
  ['PUT', /\/playlists\/\d+$/, 'Salvar playlist'],
  ['DELETE', /\/playlists\/\d+$/, 'Excluir playlist'],
  ['GET', /latest\.json$|releases\.json$/, 'Carregar as versões do aplicativo'],
  ['POST', /\/devices\/bulk$/, 'Aplicar a ação às TVs'],
  ['GET', /\/locations$/, 'Carregar os locais'],
  ['POST', /\/locations$/, 'Criar local'],
  ['PUT', /\/locations\/\d+$/, 'Salvar local'],
  ['DELETE', /\/locations\/\d+$/, 'Excluir local'],
  ['GET', /\/groups$/, 'Carregar os grupos'],
  ['POST', /\/groups$/, 'Criar grupo'],
  ['PUT', /\/groups\/\d+(\/devices)?$/, 'Salvar grupo'],
  ['DELETE', /\/groups\/\d+$/, 'Excluir grupo'],
  ['GET', /\/summary$/, 'Carregar o resumo'],
  ['GET', /\/campaigns$/, 'Carregar as campanhas'],
  ['POST', /\/campaigns$/, 'Criar campanha'],
  ['PUT', /\/campaigns\/\d+$/, 'Salvar campanha'],
  ['DELETE', /\/campaigns\/\d+$/, 'Excluir campanha'],
  ['GET', /\/campaigns\/preview$/, 'Simular a agenda'],
  ['GET', /\/devices\/\d+$/, 'Carregar a TV'],
  ['GET', /\/devices\/\d+\/events$/, 'Carregar o histórico da TV'],
  ['GET', /\/alerts$/, 'Carregar os alertas'],
  ['GET', /\/alerts\/settings$/, 'Carregar as configurações dos alertas'],
  ['PUT', /\/alerts\/settings$/, 'Salvar as configurações dos alertas'],
  ['POST', /\/alerts\/settings\/test$/, 'Testar o webhook'],
  ['GET', /\/reports\/uptime$/, 'Gerar o relatório de disponibilidade'],
  ['GET', /\/reports\/plays$/, 'Gerar o relatório de exibições'],
  ['GET', /\/reports\/inventory$/, 'Gerar o inventário das TVs'],
  ['GET', /\/reports\/events$/, 'Gerar o relatório de eventos'],
  ['GET', /\/layouts$/, 'Carregar os layouts'],
  ['GET', /\/layouts\/templates$/, 'Carregar os modelos de layout'],
  ['POST', /\/layouts$/, 'Criar layout'],
  ['POST', /\/layouts\/from-template$/, 'Criar layout a partir de um modelo'],
  ['PUT', /\/layouts\/\d+$/, 'Salvar layout'],
  ['DELETE', /\/layouts\/\d+$/, 'Excluir layout'],
  ['DELETE', /\/devices\/\d+\/layout$/, 'Voltar a TV para a tela cheia'],
  ['POST', /\/media\/web$/, 'Adicionar a página da web'],
  ['POST', /\/attendance\/tickets$/, 'Emitir a senha'],
  ['POST', /\/attendance\/counters\/\d+\/call-next$/, 'Chamar a próxima senha'],
  ['POST', /\/attendance\/tickets\/\d+\/(recall|start|finish|no-show|cancel)$/, 'Atualizar a senha'],
  ['GET', /\/queues$/, 'Carregar as filas'],
  ['POST', /\/queues$/, 'Criar a fila'],
  ['PUT', /\/queues\/\d+$/, 'Salvar a fila'],
  ['DELETE', /\/queues\/\d+$/, 'Excluir a fila'],
  ['POST', /\/counters$/, 'Criar o guichê'],
  ['PUT', /\/counters\/\d+$/, 'Salvar o guichê'],
  ['DELETE', /\/counters\/\d+$/, 'Excluir o guichê'],
  ['GET', /\/auth\/me$/, 'Carregar a sua conta'],
  ['PUT', /\/auth\/password$/, 'Trocar a senha'],
  ['GET', /\/users$/, 'Carregar a equipe'],
  ['POST', /\/users$/, 'Adicionar o usuário'],
  ['PUT', /\/users\/\d+$/, 'Salvar o usuário'],
  ['DELETE', /\/users\/\d+$/, 'Excluir o usuário'],
  ['GET', /\/billing$/, 'Carregar o plano e a cobrança'],
  ['PUT', /\/billing\/profile$/, 'Salvar os dados da conta'],
  ['GET', /\/platform\/overview$/, 'Carregar a visão geral'],
  ['GET', /\/platform\/tenants$/, 'Carregar os clientes'],
  ['POST', /\/platform\/tenants$/, 'Cadastrar o cliente'],
  ['PUT', /\/platform\/tenants\/\d+$/, 'Salvar o cliente'],
  ['POST', /\/platform\/tenants\/\d+\/invoices$/, 'Criar a fatura'],
  ['GET', /\/platform\/plans$/, 'Carregar os planos'],
  ['POST', /\/platform\/plans$/, 'Criar o plano'],
  ['PUT', /\/platform\/plans\/\d+$/, 'Salvar o plano'],
  ['DELETE', /\/platform\/plans\/\d+$/, 'Excluir o plano'],
  ['GET', /\/platform\/invoices$/, 'Carregar as faturas'],
  ['POST', /\/platform\/invoices\/\d+\/pay$/, 'Registrar o pagamento'],
  ['POST', /\/platform\/invoices\/\d+\/void$/, 'Cancelar a fatura'],
  ['POST', /\/platform\/billing\/run$/, 'Rodar a cobrança'],
  ['GET', /\/platform\/settings$/, 'Carregar as configurações da plataforma'],
  ['PUT', /\/platform\/settings$/, 'Salvar as configurações da plataforma'],
];

function actionOf(request: HttpRequest<unknown>): string | null {
  const path = request.url.split('?')[0];
  const label = ACTIONS.find(([method, pattern]) => method === request.method && pattern.test(path))?.[2];
  return label === undefined ? null : tr(label);
}

function detailOf(error: HttpErrorResponse): string {
  const body = error.error as { error?: unknown } | null;
  if (body && typeof body.error === 'string' && body.error.trim()) {
    return trServer(body.error);
  }
  switch (error.status) {
    case 0:
      return tr('não foi possível conectar ao servidor. Verifique a internet; se o servidor estava inativo, aguarde cerca de 1 minuto e tente de novo.');
    case 400:
      return tr('os dados enviados são inválidos.');
    case 401:
      return tr('sua sessão expirou. Entre novamente.');
    case 403:
      return tr('você não tem permissão para esta ação.');
    case 404:
      return tr('item não encontrado (talvez já tenha sido removido).');
    case 409:
      return tr('existe outro registro que depende deste item.');
    case 413:
      return tr('o arquivo é grande demais.');
    case 429:
      return tr('muitas tentativas seguidas; aguarde um pouco.');
    default:
      return error.status >= 500
        ? tr('o servidor encontrou um erro (HTTP {status}). Tente novamente em instantes.', { status: error.status })
        : tr('erro inesperado (HTTP {status}).', { status: error.status });
  }
}

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** Mensagem completa para o usuário: "Ação: o que aconteceu". */
export function describeHttpError(error: HttpErrorResponse, request: HttpRequest<unknown>): string {
  const detail = detailOf(error);
  const action = actionOf(request);
  return action ? `${action}: ${detail}` : capitalize(detail);
}

export function describeTimeout(request: HttpRequest<unknown>): string {
  const action = actionOf(request);
  const detail = tr('o servidor demorou demais para responder. Se ele estava inativo, aguarde cerca de 1 minuto e tente de novo.');
  return action ? `${action}: ${detail}` : capitalize(detail);
}
