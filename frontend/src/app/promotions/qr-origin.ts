/**
 * De onde é o QR Code: uma etiqueta curta que vai no endereço (?o=...) e que os Resultados usam para contar por TV. A do servidor
 * é a mesma regra (letras minúsculas, números e hífen, até 24 caracteres); aqui a pessoa digita à vontade e o painel limpa.
 */
export const MAX_ORIGIN = 24;

/** "Balcão da Frente!" vira "balcao-da-frente": sem acento, sem símbolo, em minúsculas. */
export function cleanOrigin(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, MAX_ORIGIN)
    .replace(/-+$/, '');
}

/** A origem de uma TV: o id dela ("tv-12"). */
export function tvOrigin(deviceId: number): string {
  return `tv-${deviceId}`;
}

/** O endereço da promoção com a origem do QR Code (sem origem, o endereço geral de sempre). */
export function linkWithOrigin(base: string, origin: string): string {
  return origin ? `${base}?o=${encodeURIComponent(origin)}` : base;
}
