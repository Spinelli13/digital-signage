import {
  ZONE_COLORS,
  ZoneDraft,
  addZone,
  blankDrafts,
  draftsFromLayout,
  draftsFromTemplate,
  overlaps,
  removeZone,
  setMain,
  toLayoutRequest,
  validateZones,
  zoneColor,
} from './layout-form';

let key = 1000;
const zone = (name: string, x: number, y: number, width: number, height: number, main = false, playlistId: number | null = null): ZoneDraft => ({
  key: key++,
  name,
  x,
  y,
  width,
  height,
  main,
  playlistId,
});

describe('validateZones', () => {
  it('accepts a full-screen zone and zones that only touch', () => {
    expect(validateZones(blankDrafts())).toBeNull();
    expect(validateZones([zone('A', 0, 0, 50, 100, true), zone('B', 50, 0, 50, 50), zone('C', 50, 50, 50, 50)])).toBeNull();
  });

  it('needs exactly one main zone', () => {
    const message = 'Escolha exatamente uma zona principal (a que exibe a playlist da TV)';
    expect(validateZones([zone('A', 0, 0, 100, 100)])).toBe(message);
    expect(validateZones([zone('A', 0, 0, 50, 100, true), zone('B', 50, 0, 50, 100, true)])).toBe(message);
    expect(validateZones([])).toBe('O layout precisa de pelo menos uma zona');
  });

  it('refuses zones outside the screen, too small or with fractions', () => {
    expect(validateZones([zone('A', 60, 0, 50, 100, true)])).toBe('A zona "A" passa do limite da tela (100%)');
    expect(validateZones([zone('A', -5, 0, 50, 100, true)])).toBe('A zona "A" começa fora da tela');
    expect(validateZones([zone('A', 0, 0, 4, 100, true)])).toBe('A zona "A" é pequena demais (mínimo 5% de largura e de altura)');
    expect(validateZones([zone('A', 0.5, 0, 50, 100, true)])).toBe('A zona "A" precisa de números inteiros na posição e no tamanho');
    expect(validateZones([zone('A', Number.NaN, 0, 50, 100, true)])).toBe('A zona "A" precisa de números inteiros na posição e no tamanho');
  });

  it('says which zones overlap', () => {
    expect(validateZones([zone('Principal', 0, 0, 80, 100, true), zone('Lateral', 75, 0, 25, 100)])).toBe(
      'As zonas "Principal" e "Lateral" se sobrepõem',
    );
  });

  it('refuses blank and repeated names and more than six zones', () => {
    expect(validateZones([zone('  ', 0, 0, 100, 100, true)])).toBe('Dê um nome a cada zona');
    expect(validateZones([zone('Igual', 0, 0, 50, 100, true), zone('igual', 50, 0, 50, 100)])).toBe('Cada zona precisa de um nome diferente');
    const many = [zone('Z0', 0, 0, 10, 10, true), ...[1, 2, 3, 4, 5, 6].map((i) => zone(`Z${i}`, i * 10, 0, 10, 10))];
    expect(validateZones(many)).toBe('O layout pode ter no máximo 6 zonas');
  });
});

describe('overlaps', () => {
  it('treats shared edges as not overlapping and containment as overlapping', () => {
    expect(overlaps(zone('A', 0, 0, 50, 50), zone('B', 50, 0, 50, 50))).toBe(false);
    expect(overlaps(zone('A', 0, 0, 50, 50), zone('B', 49, 0, 50, 50))).toBe(true);
    expect(overlaps(zone('A', 10, 10, 50, 50), zone('B', 20, 20, 10, 10))).toBe(true);
  });
});

describe('editing helpers', () => {
  it('keeps exactly one main zone and drops its fixed playlist', () => {
    const zones = [zone('A', 0, 0, 50, 100, true), zone('B', 50, 0, 50, 100, false, 7)];

    const result = setMain(zones, zones[1].key);

    expect(result.map((z) => z.main)).toEqual([false, true]);
    expect(result[1].playlistId).toBeNull();
  });

  it('hands the main role to the first remaining zone when the main one is removed', () => {
    const zones = [zone('A', 0, 0, 50, 100, true), zone('B', 50, 0, 50, 100, false, 7)];

    const result = removeZone(zones, zones[0].key);

    expect(result).toHaveLength(1);
    expect(result[0].main).toBe(true);
    expect(result[0].playlistId).toBeNull();
  });

  it('adds a new zone in free space without overlapping anything', () => {
    const zones = [zone('Principal', 0, 0, 75, 100, true)];

    const result = addZone(zones)!;

    expect(result).toHaveLength(2);
    expect(result[1].main).toBe(false);
    expect(overlaps(result[0], result[1])).toBe(false);
    expect(validateZones(result)).toBeNull();
  });

  it('gives each new zone a different name', () => {
    let zones = blankDrafts();
    zones = [{ ...zones[0], width: 50, height: 50 }];

    for (let i = 0; i < 3; i++) {
      zones = addZone(zones)!;
    }

    expect(new Set(zones.map((z) => z.name)).size).toBe(4);
    expect(validateZones(zones)).toBeNull();
  });

  it('says there is no room when the screen is full or the limit is reached', () => {
    expect(addZone(blankDrafts())).toBeNull(); // a principal já ocupa tudo
    const six = [zone('Z0', 0, 0, 10, 10, true), ...[1, 2, 3, 4, 5].map((i) => zone(`Z${i}`, i * 10, 0, 10, 10))];
    expect(addZone(six)).toBeNull();
  });
});

describe('conversions', () => {
  it('turns drafts into the request, trimming text and clearing the playlist of the main zone', () => {
    const zones = [zone(' Principal ', 0, 0, 75, 100, true, 5), zone('Lateral', 75, 0, 25, 100, false, 9)];

    const request = toLayoutRequest('  Meu layout ', '  ', zones);

    expect(request.name).toBe('Meu layout');
    expect(request.description).toBeNull();
    expect(request.zones[0]).toEqual({ name: 'Principal', x: 0, y: 0, width: 75, height: 100, main: true, playlistId: null });
    expect(request.zones[1].playlistId).toBe(9);
  });

  it('builds drafts from a layout and from a template with unique keys', () => {
    const fromLayout = draftsFromLayout({ zones: [{ id: 1, name: 'A', x: 0, y: 0, width: 100, height: 100, main: true, playlistId: null }] });
    const fromTemplate = draftsFromTemplate({
      zones: [
        { name: 'A', x: 0, y: 0, width: 50, height: 100, main: true, playlistId: null },
        { name: 'B', x: 50, y: 0, width: 50, height: 100, main: false, playlistId: null },
      ],
    });

    expect(fromLayout[0].name).toBe('A');
    expect(new Set([...fromLayout, ...fromTemplate].map((z) => z.key)).size).toBe(3);
  });

  it('colours the main zone first and cycles through the palette for the others', () => {
    const zones = [{ main: false }, { main: true }, { main: false }];

    expect(zoneColor(zones, 1)).toBe(ZONE_COLORS[0]);
    expect(zoneColor(zones, 0)).toBe(ZONE_COLORS[1]);
    expect(zoneColor(zones, 2)).toBe(ZONE_COLORS[2]);
  });
});
