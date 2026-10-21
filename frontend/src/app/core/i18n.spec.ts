import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { formatDate, formatDateTimeLocal, FDatePipe, I18nService, LANG_KEY, TPipe, currentLang, currentLocale, resetLangForTests, setLang, toDate, tr, trServer } from './i18n';
import { formatPercent, stateOfConnection, timeAgo } from './format';
import { formatMoney, formatStorage, moneyInput, parseMoney, roleLabel, tenantStatusLabel, usageRows } from './saas';
import { PLAN } from '../testing/fixtures';

describe('idioma', () => {
  beforeEach(() => {
    localStorage.clear();
    resetLangForTests('pt');
  });
  afterEach(() => {
    localStorage.clear();
    resetLangForTests('pt');
  });

  describe('tr()', () => {
    it('devolve o próprio texto em português (o português é a chave)', () => {
      expect(tr('Salvar')).toBe('Salvar');
      expect(tr('Local "{name}" salvo.', { name: 'Matriz' })).toBe('Local "Matriz" salvo.');
    });

    it('traduz para o inglês e preenche os {valores}', () => {
      setLang('en');

      expect(tr('Salvar')).toBe('Save');
      expect(tr('Local "{name}" salvo.', { name: 'Matriz' })).toBe('Location "Matriz" saved.');
      expect(tr('{n} TV(s) movida(s) de local.', { n: 3 })).toBe('3 TV(s) moved to another location.');
    });

    it('mostra o português quando falta a tradução (nunca uma chave técnica) e deixa {valor} ausente como está', () => {
      setLang('en');

      expect(tr('Um texto que ninguém traduziu')).toBe('Um texto que ninguém traduziu');
      expect(tr('{n} coisas', {})).toBe('{n} coisas');
      expect(tr('Local "{name}" salvo.', { name: null })).toBe('Location "{name}" saved.');
    });
  });

  describe('trServer()', () => {
    it('não mexe nas mensagens do servidor em português', () => {
      expect(trServer('E-mail ou senha inválidos')).toBe('E-mail ou senha inválidos');
    });

    it('traduz a mensagem exata', () => {
      setLang('en');

      expect(trServer('E-mail ou senha inválidos')).toBe('Invalid email or password');
    });

    it('traduz as mensagens com nome ou número no meio, reaproveitando os valores', () => {
      setLang('en');

      expect(trServer('Já existe um local chamado "Loja Centro"')).toBe('A location named "Loja Centro" already exists');
      expect(trServer('O plano Básico permite até 5 usuário(s) e todos já estão em uso. Desative um usuário ou peça um plano maior.')).toBe(
        'The Básico plan allows up to 5 user(s) and all of them are in use. Deactivate a user or ask for a bigger plan.',
      );
      expect(trServer('Playlist não encontrada: 42')).toBe('Playlist not found: 42');
    });

    it('traduz o que o servidor grava no histórico e nos alertas', () => {
      setLang('en');

      expect(trServer('TV voltou a se conectar, depois de 5 min sem contato')).toBe('TV reconnected after 5 min without contact');
      expect(trServer('A TV "Vitrine" (Loja Centro) está sem contato com o servidor há mais de 10 min')).toBe(
        'The TV "Vitrine" (Loja Centro) has had no contact with the server for more than 10 min',
      );
      expect(trServer('A TV "Vitrine" está sem contato com o servidor há mais de 10 min')).toBe(
        'The TV "Vitrine" has had no contact with the server for more than 10 min',
      );
    });

    it('traduz os planos e modelos de fábrica, mas não o que a pessoa digitou', () => {
      setLang('en');

      expect(trServer('Básico')).toBe('Basic');
      expect(trServer('Principal + faixa lateral')).toBe('Main + side strip');
      expect(trServer('Minha loja')).toBe('Minha loja');
    });

    it('deixa como veio a mensagem que não conhece', () => {
      setLang('en');

      expect(trServer('Erro estranho do servidor')).toBe('Erro estranho do servidor');
    });
  });

  describe('troca de idioma', () => {
    it('guarda a escolha no aparelho e atualiza o idioma da página', () => {
      setLang('en');

      expect(localStorage.getItem(LANG_KEY)).toBe('en');
      expect(currentLang()).toBe('en');
      expect(currentLocale()).toBe('en-US');
      expect(document.documentElement.lang).toBe('en');

      setLang('pt');

      expect(localStorage.getItem(LANG_KEY)).toBe('pt');
      expect(currentLocale()).toBe('pt-BR');
      expect(document.documentElement.lang).toBe('pt-BR');
    });

    it('o serviço expõe os idiomas e troca pelo mesmo caminho', () => {
      const service = TestBed.inject(I18nService);

      service.set('en');

      expect(service.lang()).toBe('en');
      expect(service.locale()).toBe('en-US');
      expect(service.languages.map((l) => l.code)).toEqual(['pt', 'en']);
    });

    it('as telas se atualizam sozinhas na troca (o pipe acompanha o idioma)', async () => {
      @Component({ selector: 'app-probe', imports: [TPipe], template: `<p>{{ 'Salvar' | t }} — {{ 'Local "{name}" salvo.' | t: { name: 'X' } }}</p>` })
      class Probe {}

      const fixture = TestBed.createComponent(Probe);
      fixture.detectChanges();
      expect((fixture.nativeElement as HTMLElement).textContent).toContain('Salvar — Local "X" salvo.');

      setLang('en');
      fixture.detectChanges();
      await fixture.whenStable();

      expect((fixture.nativeElement as HTMLElement).textContent).toContain('Save — Location "X" saved.');
    });
  });

  describe('datas e números', () => {
    it('mostra um dia do calendário (sem hora) no dia certo, sem recuar um dia por causa do fuso', () => {
      expect(toDate('2026-11-05').getDate()).toBe(5);
      expect(toDate('2026-11-05').getMonth()).toBe(10);
    });

    it('formata a data no padrão do idioma', () => {
      expect(formatDate('2026-11-05')).toBe('05/11/2026');
      setLang('en');
      expect(formatDate('2026-11-05')).toBe('11/05/2026');
    });

    it('mostra um traço quando não há data', () => {
      expect(formatDate(null)).toBe('—');
      expect(formatDateTimeLocal('')).toBe('—');
    });

    it('formata data e hora', () => {
      const at = new Date(2026, 10, 5, 9, 30).toISOString();

      expect(formatDateTimeLocal(at)).toContain('05/11/2026');
      expect(formatDateTimeLocal(at)).toContain('09:30');
      setLang('en');
      expect(formatDateTimeLocal(at)).toContain('11/05/2026');
    });

    it('o pipe fdate escolhe entre dia e dia com hora', () => {
      const pipe = new FDatePipe();
      const at = new Date(2026, 10, 5, 9, 30).toISOString();

      expect(pipe.transform(at)).toBe('05/11/2026');
      expect(pipe.transform(at, 'datetime')).toContain('09:30');
    });

    it('formata dinheiro, percentual e espaço com o separador do idioma', () => {
      expect(formatMoney(4990).replace(/\s/g, ' ')).toBe('R$ 49,90');
      expect(formatPercent(99.5)).toBe('99,5%');
      expect(formatStorage(2048 * 1024 * 1024)).toBe('2,0 GB');
      expect(moneyInput(4990)).toBe('49,90');

      setLang('en');

      expect(formatMoney(4990)).toBe('R$49.90');
      expect(formatPercent(99.5)).toBe('99.5%');
      expect(formatStorage(2048 * 1024 * 1024)).toBe('2.0 GB');
      expect(moneyInput(4990)).toBe('49.90');
    });

    it('entende o valor digitado nos dois formatos', () => {
      expect(parseMoney('49,90')).toBe(4990);
      expect(parseMoney('1.249,90')).toBe(124990);
      expect(parseMoney('1,249.90')).toBe(124990);
      expect(parseMoney('49.90')).toBe(4990);
    });
  });

  describe('rótulos compartilhados', () => {
    it('papéis, situações, TVs e tempo passam para o inglês', () => {
      setLang('en');

      expect(roleLabel('ADMIN')).toBe('Administrator');
      expect(tenantStatusLabel('PAST_DUE')).toBe('Overdue');
      expect(stateOfConnection('NEVER').label).toBe('Never connected');
      expect(timeAgo(new Date(Date.now() - 5 * 60_000).toISOString())).toBe('5 min ago');
      expect(usageRows(PLAN, { devices: 2, storageBytes: 0, users: 1 })[0].text).toBe('2 of 5');
    });
  });
});
