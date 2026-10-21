import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { ThemeService } from './core/theme';
import { ErrorBanner } from './shared/error-banner';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ErrorBanner],
  template: '<app-error-banner /><router-outlet />',
})
export class App {
  // Cria o serviço do tema já na abertura (ele acompanha o modo claro/escuro do aparelho).
  protected readonly theme = inject(ThemeService);
}
