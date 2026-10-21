import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { ServerStatusService } from './core/server-status.service';
import { ThemeService } from './core/theme';
import { ErrorBanner } from './shared/error-banner';
import { ServerStatusBanner } from './shared/server-status';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ErrorBanner, ServerStatusBanner],
  template: '<app-error-banner /><app-server-status /><router-outlet />',
})
export class App {
  // Cria o serviço do tema já na abertura (ele acompanha o modo claro/escuro do aparelho).
  protected readonly theme = inject(ThemeService);

  constructor() {
    // O servidor gratuito dorme sem uso: pergunta já na abertura (inclusive na tela de entrada) e avisa se estiver acordando.
    inject(ServerStatusService).probe();
  }
}
