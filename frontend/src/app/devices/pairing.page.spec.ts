import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { ConfigService } from '../core/config.service';
import { Device, Location } from '../core/models';
import { meFixture, tenantFixture } from '../testing/fixtures';
import { PairingPage } from './pairing.page';

const API = 'http://api.test/api/v1';

const location = (id: number, name: string): Location => ({ id, name, description: null, deviceCount: 1, onlineCount: 1, createdAt: '', updatedAt: '' });
const device = (id: number, locationId: number | null): Device => ({
  id, name: `TV ${id}`, location: null, locationId, status: 'ACTIVE', connection: 'ONLINE', groupIds: [], online: true, lastSeenAt: null,
  playerVersion: '0.14.69', currentPlaylistId: null, configVersion: 1, effectivePlaylistId: null, campaignId: null, campaignName: null, createdAt: '', updatedAt: '',
});

function setup(code = 'abc234') {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ code }) } } }],
  });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  const auth = TestBed.inject(AuthService);
  const backend = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(PairingPage);
  return { page: fixture.componentInstance, fixture, auth, backend };
}

const settle = async (fixture: { whenStable(): Promise<unknown>; detectChanges(): void }) => {
  await fixture.whenStable();
  fixture.detectChanges();
};

/** O texto da tela agora (as continuações assíncronas da página podem ter mudado o passo depois do último detectChanges). */
const text = (fixture: { detectChanges(): void; nativeElement: unknown }) => {
  fixture.detectChanges();
  return ((fixture.nativeElement as HTMLElement).textContent ?? '').replace(/\s+/g, ' ');
};

const FOUND = { code: 'ABC234', playerVersion: '0.14.69', waitingSince: '2026-10-22T10:00:00Z' };

/** Responde às consultas que a página faz quando o código é bom. */
async function flushFound(backend: HttpTestingController, fixture: { whenStable(): Promise<unknown>; detectChanges(): void }, devices: Device[] = [device(1, 3), device(2, 3), device(3, null)]) {
  await settle(fixture);
  backend.expectOne(`${API}/devices/pairing/ABC234`).flush(FOUND);
  await settle(fixture);
  backend.expectOne(`${API}/locations`).flush([location(3, 'Loja Centro'), location(4, 'Recepção')]);
  backend.expectOne((request) => request.url === `${API}/devices`).flush(devices);
  await settle(fixture);
}

describe('PairingPage (QR Code da TV)', () => {
  it('confirms the code from the QR Code and suggests a name, so an Administrator pairs without typing', async () => {
    const { page, fixture, auth, backend } = setup();
    auth.me.set(meFixture('ADMIN'));
    fixture.detectChanges();

    await flushFound(backend, fixture);

    expect(page.step()).toBe('ready');
    expect(page.code()).toBe('ABC234'); // o código vale em minúsculas, e o QR Code usa o texto em maiúsculas
    expect(page.name()).toBe('TV 4'); // a quarta TV do cliente
    expect(text(fixture)).toContain('TV encontrada');

    page.chooseLocation('3'); // o nome acompanha o local enquanto a pessoa não digitou o dela
    expect(page.name()).toBe('Loja Centro - TV 3'); // já há duas TVs nesse local

    const done = page.activate();
    const request = backend.expectOne(`${API}/devices/activate`);
    expect(request.request.body).toEqual({ activationCode: 'ABC234', name: 'Loja Centro - TV 3', locationId: 3, location: null });
    request.flush(device(9, 3));
    await done;
    await settle(fixture);

    expect(page.step()).toBe('done');
    expect(text(fixture)).toContain('TV ativada');
    backend.verify();
  });

  it('keeps the name the person typed when the location changes, and creates a new location by name', async () => {
    const { page, fixture, auth, backend } = setup();
    auth.me.set(meFixture('ADMIN'));
    fixture.detectChanges();
    await flushFound(backend, fixture, []);

    page.renamed('TV da vitrine');
    page.chooseLocation('new');
    page.newLocation.set('  Filial Norte ');
    expect(page.name()).toBe('TV da vitrine');

    const done = page.activate();
    const request = backend.expectOne(`${API}/devices/activate`);
    expect(request.request.body).toEqual({ activationCode: 'ABC234', name: 'TV da vitrine', locationId: null, location: 'Filial Norte' });
    request.flush(device(1, null));
    await done;
    backend.verify();
  });

  it('says so when the code is wrong or expired', async () => {
    const { page, fixture, auth, backend } = setup('zzzzzz');
    auth.me.set(meFixture('ADMIN'));
    fixture.detectChanges();
    await settle(fixture);

    backend.expectOne(`${API}/devices/pairing/ZZZZZZ`).flush({ error: 'Código de ativação inválido ou expirado' }, { status: 404, statusText: 'Not Found' });
    await settle(fixture);

    expect(page.step()).toBe('invalid');
    expect(text(fixture)).toContain('Código inválido ou expirado');
    backend.verify();
  });

  it('tells a user who is not an Administrator that only the Administrator can pair TVs', async () => {
    const { page, fixture, auth, backend } = setup();
    auth.me.set(meFixture('VIEWER'));
    fixture.detectChanges();
    await settle(fixture);

    backend.expectOne(`${API}/devices/pairing/ABC234`).flush({ error: 'forbidden' }, { status: 403, statusText: 'Forbidden' });
    await settle(fixture);

    expect(page.step()).toBe('forbidden');
    backend.verify();
  });

  it('lets a platform operator pick the client first and then acts as that client', async () => {
    const { page, fixture, auth, backend } = setup();
    localStorage.removeItem('signage.acting');
    auth.me.set(meFixture('PLATFORM_ADMIN'));
    fixture.detectChanges();
    await settle(fixture);

    const clients = [tenantFixture({ id: 5, name: 'Barbearia Call' }), tenantFixture({ id: 6, name: 'Pizzaria Sol' })];
    backend.expectOne(`${API}/platform/tenants`).flush(clients);
    await settle(fixture);
    expect(page.step()).toBe('client');
    expect(text(fixture)).toContain('Barbearia Call');

    const chosen = page.chooseClient(clients[0]);
    await settle(fixture);
    expect(auth.actingTenantId()).toBe(5);
    backend.expectOne(`${API}/devices/pairing/ABC234`).flush(FOUND);
    await settle(fixture);
    backend.expectOne(`${API}/locations`).flush([]);
    backend.expectOne((request) => request.url === `${API}/devices`).flush([]);
    await chosen;
    await settle(fixture);

    expect(page.step()).toBe('ready');
    localStorage.removeItem('signage.acting');
  });
});
