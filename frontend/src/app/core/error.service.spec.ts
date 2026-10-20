import { TestBed } from '@angular/core/testing';

import { ErrorService } from './error.service';

class TestableErrorService extends ErrorService {
  time = 1_000_000;
  protected override now(): number {
    return this.time;
  }
}

describe('ErrorService', () => {
  let service: TestableErrorService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [{ provide: ErrorService, useClass: TestableErrorService }] });
    service = TestBed.inject(ErrorService) as TestableErrorService;
  });

  it('keeps an error on screen until it is dismissed', () => {
    service.show('Falhou');
    service.time += 10 * 60_000; // 10 minutos depois continua lá

    expect(service.errors().map((e) => e.message)).toEqual(['Falhou']);

    service.dismiss(service.errors()[0].id);
    expect(service.errors()).toEqual([]);
  });

  it('does not stack identical messages (a server that is down must not flood the screen)', () => {
    service.show('Sem conexão');
    service.show('Sem conexão');
    service.show('Sem conexão');

    expect(service.errors()).toHaveLength(1);
  });

  it('shows different messages together and dismisses them independently', () => {
    service.show('A');
    service.show('B');
    service.dismiss(service.errors()[0].id);

    expect(service.errors().map((e) => e.message)).toEqual(['B']);
  });

  it('does not bring a dismissed message straight back (automatic refresh)', () => {
    service.show('Sem conexão');
    service.dismiss(service.errors()[0].id);

    service.time += 5_000;
    service.show('Sem conexão');
    expect(service.errors()).toEqual([]);

    service.time += 61_000;
    service.show('Sem conexão');
    expect(service.errors()).toHaveLength(1);
  });

  it('keeps only the most recent errors', () => {
    for (let i = 1; i <= 8; i++) {
      service.show(`erro ${i}`);
    }

    expect(service.errors().map((e) => e.message)).toEqual(['erro 4', 'erro 5', 'erro 6', 'erro 7', 'erro 8']);
  });
});
