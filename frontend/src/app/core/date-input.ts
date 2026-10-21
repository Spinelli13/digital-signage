/** "2026-12-31" (campo de data) para o instante de início ou de fim desse dia, no fuso do navegador. */
export function dateInputToIso(value: string, endOfDay: boolean): string | null {
  if (!value) {
    return null;
  }
  const date = new Date(`${value}T${endOfDay ? '23:59:59' : '00:00:00'}`);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

/** O instante guardado no servidor de volta para o campo de data. */
export function isoToDateInput(iso: string | null): string {
  if (!iso) {
    return '';
  }
  const date = new Date(iso);
  const two = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())}`;
}
