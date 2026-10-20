import { TestBed } from '@angular/core/testing';
import { ErrorHandler } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { TimeoutError } from 'rxjs';

import { ErrorService } from '../core/error.service';
import { GlobalErrorHandler } from '../core/global-error-handler';
import { ErrorBanner } from './error-banner';

describe('ErrorBanner', () => {
  it('renders each error with a Fechar button that removes only that error', async () => {
    const errors = TestBed.inject(ErrorService);
    errors.show('Primeiro erro');
    errors.show('Segundo erro');
    const fixture = TestBed.createComponent(ErrorBanner);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelectorAll('[role="alert"]')).toHaveLength(2);
    expect(root.textContent).toContain('Primeiro erro');

    (root.querySelector('button.close') as HTMLButtonElement).click();
    await fixture.whenStable();

    expect(root.querySelectorAll('[role="alert"]')).toHaveLength(1);
    expect(root.textContent).not.toContain('Primeiro erro');
    expect(root.textContent).toContain('Segundo erro');
  });

  it('shows nothing when there are no errors', async () => {
    const fixture = TestBed.createComponent(ErrorBanner);
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).querySelectorAll('[role="alert"]')).toHaveLength(0);
  });
});

describe('GlobalErrorHandler', () => {
  let handler: ErrorHandler;
  let errors: ErrorService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [{ provide: ErrorHandler, useClass: GlobalErrorHandler }] });
    handler = TestBed.inject(ErrorHandler);
    errors = TestBed.inject(ErrorService);
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
  });

  it('turns an unexpected exception into a message on screen', () => {
    handler.handleError(new TypeError('x is undefined'));

    expect(errors.errors()[0].message).toContain('erro inesperado no painel');
    expect(errors.errors()[0].message).toContain('x is undefined');
  });

  it('ignores network failures, which the HTTP interceptor already showed', () => {
    handler.handleError(new HttpErrorResponse({ status: 500 }));
    handler.handleError(new TimeoutError());

    expect(errors.errors()).toEqual([]);
  });
});
