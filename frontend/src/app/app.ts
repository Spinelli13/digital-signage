import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { ErrorBanner } from './shared/error-banner';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ErrorBanner],
  template: '<app-error-banner /><router-outlet />',
})
export class App {}
