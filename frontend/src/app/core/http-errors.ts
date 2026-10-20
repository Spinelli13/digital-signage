import { HttpContextToken, HttpErrorResponse, HttpRequest } from '@angular/common/http';

/** Requisições "em segundo plano" (miniaturas etc.) que não devem gerar aviso quando falham. */
export const SILENT_ERRORS = new HttpContextToken<boolean>(() => false);

export const REQUEST_TIMEOUT_MS = 120_000;

const ACTIONS: [method: string, path: RegExp, label: string][] = [
  ['POST', /\/auth\/login$/, 'Entrar'],
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
];

function actionOf(request: HttpRequest<unknown>): string | null {
  const path = request.url.split('?')[0];
  return ACTIONS.find(([method, pattern]) => method === request.method && pattern.test(path))?.[2] ?? null;
}

function detailOf(error: HttpErrorResponse): string {
  const body = error.error as { error?: unknown } | null;
  if (body && typeof body.error === 'string' && body.error.trim()) {
    return body.error;
  }
  switch (error.status) {
    case 0:
      return 'não foi possível conectar ao servidor. Verifique a internet; se o servidor estava inativo, aguarde cerca de 1 minuto e tente de novo.';
    case 400:
      return 'os dados enviados são inválidos.';
    case 401:
      return 'sua sessão expirou. Entre novamente.';
    case 403:
      return 'você não tem permissão para esta ação.';
    case 404:
      return 'item não encontrado (talvez já tenha sido removido).';
    case 409:
      return 'existe outro registro que depende deste item.';
    case 413:
      return 'o arquivo é grande demais.';
    case 429:
      return 'muitas tentativas seguidas; aguarde um pouco.';
    default:
      return error.status >= 500
        ? `o servidor encontrou um erro (HTTP ${error.status}). Tente novamente em instantes.`
        : `erro inesperado (HTTP ${error.status}).`;
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
  const detail =
    'o servidor demorou demais para responder. Se ele estava inativo, aguarde cerca de 1 minuto e tente de novo.';
  return action ? `${action}: ${detail}` : capitalize(detail);
}
