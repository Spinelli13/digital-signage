import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ConfigService } from '../core/config.service';
import { FileSaver } from '../core/download';
import { Campaign, Device, Media, ProofDaily, ProofReport, ProofRow, ProofVerification } from '../core/models';
import { ProofReportComponent } from './proof-report';

const API = 'http://api.test/api/v1';

const ROW = (overrides: Partial<ProofRow> = {}): ProofRow => ({
  id: 1,
  deviceId: 5,
  deviceName: 'TV Vitrine',
  seq: 1,
  mediaId: 8,
  mediaName: 'Promoção de verão',
  mediaType: 'IMAGE',
  zoneName: 'Principal',
  playlistId: 3,
  campaignId: null,
  campaignName: null,
  startedAt: '2026-11-05T15:00:00.123Z',
  durationMs: 10012,
  plannedMs: 10000,
  completed: true,
  hash: 'ab'.repeat(32),
  hashValid: true,
  continuous: true,
  clockOffsetMs: 0,
  ...overrides,
});

const REPORT = (rows: ProofRow[], total = rows.length): ProofReport => ({
  from: '2026-11-05',
  to: '2026-11-05',
  timezone: 'America/Sao_Paulo',
  total,
  completed: rows.filter((r) => r.completed).length,
  totalDurationMs: rows.reduce((sum, r) => sum + r.durationMs, 0),
  devices: 1,
  offset: 0,
  limit: 100,
  rows,
});

const VERIFICATION = (ok: boolean): ProofVerification => ({
  from: '2026-11-05',
  to: '2026-11-05',
  timezone: 'America/Sao_Paulo',
  ok,
  digest: 'cd'.repeat(32),
  devices: [
    { deviceId: 5, deviceName: 'TV Vitrine', records: 2, firstSeq: 1, lastSeq: 2, missing: ok ? 0 : 1, brokenLinks: 0, hashMismatches: 0, flaggedAtIngest: 0, digest: 'ef'.repeat(32), status: ok ? 'OK' : 'ATENCAO' },
  ],
});

const DAILY: ProofDaily[] = [
  { deviceId: 5, deviceName: 'TV Vitrine', day: '2026-11-05', plays: 2, completed: 2, totalMs: 20012, firstSeq: 1, lastSeq: 2, lastHash: '12'.repeat(32) },
];

const DEVICES = [{ id: 5, name: 'TV Vitrine' }] as Device[];
const save = vi.fn();

function request(backend: HttpTestingController, path: string, params: Record<string, string> = {}): TestRequest {
  return backend.expectOne((r) => r.url === `${API}${path}` && Object.entries(params).every(([key, value]) => r.params.get(key) === value));
}

/** Abre a aba: as listas dos filtros, o relatório e o resumo diário. */
function open(rows: ProofRow[] = [ROW()], total = rows.length) {
  save.mockReset();
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), { provide: FileSaver, useValue: { save } }] });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  const backend = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(ProofReportComponent);
  fixture.componentRef.setInput('from', '2026-11-05');
  fixture.componentRef.setInput('to', '2026-11-05');
  fixture.componentRef.setInput('devices', DEVICES);
  fixture.detectChanges();
  // O modo sem zona do TestBed pode rodar a inicialização duas vezes: responde tudo o que estiver pendente.
  backend.match(`${API}/media`).forEach((r) => r.flush([{ id: 8, name: 'Promoção de verão' }] as Media[]));
  backend.match(`${API}/campaigns`).forEach((r) => r.flush([{ id: 4, name: 'Happy hour' }] as Campaign[]));
  backend.match((r) => r.url === `${API}/reports/proof`).forEach((r) => r.flush(REPORT(rows, total)));
  backend.match((r) => r.url === `${API}/reports/proof/daily`).forEach((r) => r.flush(DAILY));
  return { fixture, backend, component: fixture.componentInstance, text: () => (fixture.nativeElement as HTMLElement).textContent ?? '' };
}

describe('ProofReportComponent', () => {
  it('asks for the first 100 plays of the chosen period and shows the milliseconds of each one', async () => {
    const { fixture, text, backend } = open();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text()).toContain('Promoção de verão');
    expect(text()).toContain('10,012 s');
    expect(text()).toMatch(/00[,.]123/);
    expect(text()).toContain('prevista 10,000 s');
    expect(text()).toContain('Completa');
    expect(text()).toContain('abababababab'.slice(0, 10));
    backend.verify();
  });

  it('sends the period and the filters chosen on the screen', () => {
    const { component, backend } = open();

    component.setDevice(5);
    request(backend, '/reports/proof', { from: '2026-11-05', to: '2026-11-05', deviceId: '5', offset: '0', limit: '100' }).flush(REPORT([ROW()]));
    request(backend, '/reports/proof/daily', { deviceId: '5' }).flush(DAILY);
    component.setMedia(8);
    request(backend, '/reports/proof', { deviceId: '5', mediaId: '8' }).flush(REPORT([ROW()]));
    request(backend, '/reports/proof/daily').flush(DAILY);
    component.setCampaign(4);
    request(backend, '/reports/proof', { deviceId: '5', mediaId: '8', campaignId: '4' }).flush(REPORT([]));
    request(backend, '/reports/proof/daily').flush(DAILY);

    expect(component.rows()).toHaveLength(0);
    backend.verify();
  });

  it('loads the next plays when asked and keeps the ones already on the screen', () => {
    const { component, backend } = open([ROW({ id: 1, seq: 1 })], 3);
    expect(component.rows()).toHaveLength(1);

    component.loadMore();
    request(backend, '/reports/proof', { offset: '1', limit: '100' }).flush(REPORT([ROW({ id: 2, seq: 2 }), ROW({ id: 3, seq: 3 })], 3));

    expect(component.rows().map((r) => r.seq)).toEqual([1, 2, 3]);
    backend.verify();
  });

  it('marks a play whose chain is broken so it cannot be missed', async () => {
    const { fixture, text } = open([ROW({ hashValid: false })]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text()).toContain('Corrente com problema');
  });

  it('says when the TV clock was wrong and the server corrected the time', async () => {
    const { fixture, text } = open([ROW({ clockOffsetMs: 4200 })]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text()).toContain('relógio corrigido');
  });

  it('verifies the integrity of the period and shows the digest to keep', async () => {
    const { component, fixture, backend, text } = open();

    component.verify();
    request(backend, '/reports/proof/verify', { from: '2026-11-05', to: '2026-11-05' }).flush(VERIFICATION(true));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(text()).toContain('Corrente íntegra');
    expect(text()).toContain('cd'.repeat(32));
    backend.verify();
  });

  it('warns when the verification finds a missing record', async () => {
    const { component, fixture, backend, text } = open();

    component.verify();
    request(backend, '/reports/proof/verify').flush(VERIFICATION(false));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(text()).toContain('há registros com problema');
    expect(text()).toContain('Atenção');
    backend.verify();
  });

  it('shows the daily summary that stays for a year', async () => {
    const { fixture, text } = open();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text()).toContain('Resumo por dia');
    expect(text()).toContain('2026-11-05');
  });

  it('explains an empty period', async () => {
    const { fixture, text } = open([]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text()).toContain('Nenhuma exibição registrada neste período.');
  });

  it('downloads its own CSV with the filters and the period in the name', () => {
    const { component, backend } = open();
    component.setDevice(5);
    request(backend, '/reports/proof', { deviceId: '5' }).flush(REPORT([ROW()]));
    request(backend, '/reports/proof/daily').flush(DAILY);

    component.downloadCsv();
    const csv = request(backend, '/reports/proof', { format: 'csv', deviceId: '5', from: '2026-11-05', to: '2026-11-05' });
    csv.flush(new Blob(['x']));

    expect(save).toHaveBeenCalledWith(expect.any(Blob), 'comprovacao-2026-11-05-a-2026-11-05.csv');
    backend.verify();
  });
});
