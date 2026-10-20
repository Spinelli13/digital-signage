import { HttpParams } from '@angular/common/http';

import { DeviceFilters } from './models';

/** Converte os filtros da tela nos parâmetros da API (só entram os que estão preenchidos). */
export function filtersToParams(filters: DeviceFilters): HttpParams {
  let params = new HttpParams();
  if (filters.connection) {
    params = params.set('connection', filters.connection);
  }
  if (filters.location === 'none') {
    params = params.set('noLocation', 'true');
  } else if (typeof filters.location === 'number') {
    params = params.set('locationId', String(filters.location));
  }
  if (filters.groupId != null) {
    params = params.set('groupId', String(filters.groupId));
  }
  const query = filters.q?.trim();
  if (query) {
    params = params.set('q', query);
  }
  return params;
}

/** True quando há algum filtro ativo (para mostrar "limpar filtros"). */
export function hasFilters(filters: DeviceFilters): boolean {
  return filtersToParams(filters).keys().length > 0;
}
