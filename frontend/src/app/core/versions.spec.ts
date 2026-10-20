import { compareVersions, isOutdated } from './versions';

describe('versions', () => {
  it('compares numerically, not alphabetically', () => {
    expect(compareVersions('0.10.0', '0.9.0')).toBeGreaterThan(0);
    expect(compareVersions('0.3.0', '0.3.1')).toBeLessThan(0);
    expect(compareVersions('1.0.0', '1.0.0')).toBe(0);
  });

  it('treats missing parts as zero', () => {
    expect(compareVersions('1.2', '1.2.0')).toBe(0);
    expect(compareVersions('1.2', '1.2.1')).toBeLessThan(0);
  });

  it('ignores suffixes without breaking the comparison', () => {
    expect(compareVersions('0.3.0-debug', '0.3.0')).toBe(0);
    expect(compareVersions('0.3.0-debug', '0.4.0')).toBeLessThan(0);
  });

  it('flags only devices strictly older than the latest release', () => {
    expect(isOutdated('0.2.0', '0.3.0')).toBe(true);
    expect(isOutdated('0.3.0', '0.3.0')).toBe(false);
    expect(isOutdated('0.4.0', '0.3.0')).toBe(false);
  });

  /** Uma TV que nunca reportou versão (ou sem releases.json) não deve virar alarme falso. */
  it('does not flag when either version is unknown', () => {
    expect(isOutdated(null, '0.3.0')).toBe(false);
    expect(isOutdated(undefined, '0.3.0')).toBe(false);
    expect(isOutdated('0.1.0', null)).toBe(false);
    expect(isOutdated('', '0.3.0')).toBe(false);
  });
});
