/** Compara versões "1.2.3" (partes numéricas). Devolve <0 se a < b, 0 se iguais, >0 se a > b. */
export function compareVersions(a: string, b: string): number {
  const parse = (v: string) => v.split(/[.\-+]/).map((part) => parseInt(part, 10)).map((n) => (Number.isNaN(n) ? 0 : n));
  const pa = parse(a);
  const pb = parse(b);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const diff = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (diff !== 0) {
      return diff;
    }
  }
  return 0;
}

/** True quando a TV reporta uma versão do Player menor que a mais recente publicada. Sem versão conhecida, não alerta. */
export function isOutdated(current: string | null | undefined, latest: string | null | undefined): boolean {
  if (!current || !latest) {
    return false;
  }
  return compareVersions(current, latest) < 0;
}
