import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { ConfigService } from '../core/config.service';
import { PublicPrivacy } from '../core/models';
import { LegalPage } from './legal.page';
import { legalContext, privacySections, termsSections } from './legal-content';

const API = 'http://api.test/api/v1';

const INFO: PublicPrivacy = {
  companyName: 'Vyzzu',
  legalName: 'Vyzzu Tecnologia Ltda',
  legalDocument: '00.000.000/0001-00',
  contactName: 'Encarregado de Dados',
  contactEmail: 'privacidade@vyzzu.com',
  supportEmail: 'suporte@vyzzu.com',
  version: '2026-10-21',
};

function setup(legal: 'privacy' | 'terms') {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: ActivatedRoute, useValue: { data: of({ legal }) } }],
  });
  TestBed.inject(ConfigService).apiBaseUrl = 'http://api.test';
  const backend = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(LegalPage);
  fixture.detectChanges();
  return { fixture, backend, text: () => ((fixture.nativeElement as HTMLElement).textContent ?? '').replace(/\s+/g, ' ') };
}

describe('LegalPage (Política de Privacidade e Termos de Uso)', () => {
  it('shows the privacy policy with who is responsible and the officer contact, as configured by the platform', () => {
    const { fixture, backend, text } = setup('privacy');
    backend.expectOne(`${API}/public/privacy`).flush(INFO);
    fixture.detectChanges();

    expect(text()).toContain('Política de Privacidade');
    expect(text()).toContain('Versão 2026-10-21');
    expect(text()).toContain('Vyzzu Tecnologia Ltda, CNPJ 00.000.000/0001-00');
    expect(text()).toContain('Encarregado de Dados, e-mail privacidade@vyzzu.com');
    for (const topic of ['Lei nº 13.709/2018', 'controlador', 'operador', 'Transferência internacional', 'Os seus direitos', 'ANPD']) {
      expect(text(), topic).toContain(topic);
    }
    backend.verify();
  });

  it('shows the terms of use on the other route', () => {
    const { fixture, backend, text } = setup('terms');
    backend.expectOne(`${API}/public/privacy`).flush(INFO);
    fixture.detectChanges();

    expect(text()).toContain('Termos de Uso');
    expect(text()).toContain('Aceitação e objeto');
    expect(text()).toContain('Limitação de responsabilidade');
    backend.verify();
  });

  it('still shows the text, with the generic platform data, when the server does not answer', () => {
    const { fixture, backend, text } = setup('privacy');
    backend.expectOne(`${API}/public/privacy`).flush({ error: 'x' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(text()).toContain('Quem somos');
    expect(text()).toContain('o suporte do Vyzzu');
  });

  it('falls back to the support e-mail when no officer is set, and to a generic channel when nothing is set', () => {
    expect(legalContext({ ...INFO, contactName: null, contactEmail: null }).contact).toBe('e-mail suporte@vyzzu.com');
    expect(legalContext({ ...INFO, contactName: null, contactEmail: null, supportEmail: null }).contact).toContain('o suporte do Vyzzu');
    expect(legalContext({ ...INFO, legalName: null, legalDocument: null }).controller).toBe('Vyzzu');
    expect(legalContext(null).company).toBe('Vyzzu');
  });

  it('names every personal data the system really keeps and the retention periods, so the text and the code do not drift apart', () => {
    const all = JSON.stringify(privacySections(legalContext(INFO)));
    for (const fact of ['nome', 'e-mail', 'código aleatório do aparelho', '30 dias', '90 dias', '14 dias', 'hash', 'Supabase', 'Render', 'Cloudflare', 'us-east-1', 'Oregon', 'IP']) {
      expect(all, fact).toContain(fact);
    }
    expect(JSON.stringify(termsSections(legalContext(INFO)))).toContain('Privacidade e dados');
  });
});
