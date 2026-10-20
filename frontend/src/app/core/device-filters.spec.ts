import { filtersToParams, hasFilters } from './device-filters';

describe('filtersToParams', () => {
  it('sends nothing when no filter is set', () => {
    expect(filtersToParams({}).keys()).toEqual([]);
    expect(filtersToParams({ connection: '', location: null, groupId: null, q: '  ' }).keys()).toEqual([]);
  });

  it('sends only the filters that are set', () => {
    const params = filtersToParams({ connection: 'OFFLINE', location: 4, groupId: 10, q: ' recepção ' });

    expect(params.get('connection')).toBe('OFFLINE');
    expect(params.get('locationId')).toBe('4');
    expect(params.get('groupId')).toBe('10');
    expect(params.get('q')).toBe('recepção');
  });

  it('turns "sem local" into noLocation instead of an id', () => {
    const params = filtersToParams({ location: 'none' });

    expect(params.get('noLocation')).toBe('true');
    expect(params.has('locationId')).toBe(false);
  });

  /** O id 0 não existe, mas um grupo com id baixo nunca pode ser tratado como "sem filtro" por engano. */
  it('treats a group id of 0 as a real filter, not as empty', () => {
    expect(filtersToParams({ groupId: 0 }).get('groupId')).toBe('0');
  });
});

describe('hasFilters', () => {
  it('tells whether any filter is active', () => {
    expect(hasFilters({})).toBe(false);
    expect(hasFilters({ q: 'tv' })).toBe(true);
    expect(hasFilters({ location: 'none' })).toBe(true);
    expect(hasFilters({ connection: 'ONLINE' })).toBe(true);
  });
});
