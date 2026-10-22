import { currentLocale, tr } from '../core/i18n';
import { ResultsDay, ResultsOrigin } from '../core/models';

/** 12345 vira "12.345" (português) ou "12,345" (inglês). */
export function formatCount(value: number): string {
  return value.toLocaleString(currentLocale());
}

/** Quantos por cento dos cupons pegos foram usados; sem cupom pego não há taxa (nulo, e a tela mostra um traço). */
export function usageRate(claimed: number, redeemed: number): number | null {
  return claimed > 0 ? Math.round((redeemed / claimed) * 1000) / 10 : null;
}

/** "42,9%" ou "—" quando não há cupom pego. */
export function formatRate(claimed: number, redeemed: number): string {
  const rate = usageRate(claimed, redeemed);
  return rate === null ? '—' : `${rate.toLocaleString(currentLocale(), { minimumFractionDigits: 1, maximumFractionDigits: 1 })}%`;
}

/** O nome de um QR Code que não é de uma TV: o geral (origem vazia) ou a etiqueta que a pessoa escolheu. */
export function originLabel(origin: ResultsOrigin): string {
  return origin.origin === '' ? tr('QR Code geral (sem origem)') : tr('QR Code: {name}', { name: origin.origin });
}

/** Altura de cada barra (0 a 100) em relação ao maior valor; tudo zero = barras vazias. */
export function barHeights(values: number[]): number[] {
  const max = Math.max(0, ...values);
  return values.map((value) => (max === 0 ? 0 : Math.max(value > 0 ? 4 : 0, Math.round((value / max) * 100))));
}

/** Os dias em que alguma coisa aconteceu, do mais recente para o mais antigo (a tabela não precisa listar dias vazios). */
export function activeDays(days: ResultsDay[]): ResultsDay[] {
  return days.filter((day) => day.plays + day.scans + day.claimed + day.redeemed > 0).reverse();
}

/** A frase do topo: o conteúdo rodou X vezes (em N TVs) e, havendo cupons, Y pessoas escanearam e Z cupons foram usados. */
export function headline(plays: number, tvs: number, scans: number, claimed: number, redeemed: number, coupons: boolean): string[] {
  const lines: string[] = [];
  lines.push(
    plays === 0
      ? tr('Ainda não há exibições registradas neste período.')
      : tr('O seu conteúdo rodou {plays} vez(es) em {tvs} TV(s).', { plays: formatCount(plays), tvs }),
  );
  if (coupons && (scans > 0 || claimed > 0 || redeemed > 0)) {
    lines.push(tr('{scans} escaneamento(s) do QR Code, {claimed} cupom(ns) pego(s) e {redeemed} usado(s).', {
      scans: formatCount(scans),
      claimed: formatCount(claimed),
      redeemed: formatCount(redeemed),
    }));
  }
  return lines;
}
