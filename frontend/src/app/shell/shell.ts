import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <aside class="side">
      <div class="brand"><span class="logo">▶</span> Digital Signage</div>
      <nav>
        <a routerLink="/dashboard" routerLinkActive="active">▦ Dashboard</a>
        <a routerLink="/dispositivos" routerLinkActive="active">📺 Dispositivos</a>
        <a routerLink="/biblioteca" routerLinkActive="active">🖼 Biblioteca</a>
        <a routerLink="/playlists" routerLinkActive="active">☷ Playlists</a>
      </nav>
      <button class="logout" (click)="logout()">Sair</button>
    </aside>
    <main><router-outlet /></main>
  `,
  styles: `
    :host { display: grid; grid-template-columns: 240px 1fr; min-height: 100vh; }
    .side { background: var(--sidebar); color: #fff; padding: 22px 14px; display: flex; flex-direction: column; gap: 22px; }
    .brand { font-weight: 700; font-size: 17px; padding: 0 10px; display: flex; align-items: center; gap: 10px; }
    .logo { background: var(--blue); border-radius: 8px; padding: 4px 8px; font-size: 13px; }
    nav { display: flex; flex-direction: column; gap: 4px; }
    nav a { color: #c9d2e6; padding: 11px 12px; border-radius: 10px; }
    nav a:hover { background: rgba(255, 255, 255, 0.07); }
    nav a.active { background: var(--blue); color: #fff; }
    .logout { margin-top: auto; background: transparent; color: #c9d2e6; border: 1px solid rgba(255, 255, 255, 0.2); border-radius: 10px; padding: 10px; }
    main { padding: 28px; min-width: 0; }
    @media (max-width: 820px) {
      :host { grid-template-columns: 1fr; }
      .side { flex-direction: row; align-items: center; flex-wrap: wrap; padding: 12px; gap: 10px; }
      nav { flex-direction: row; flex-wrap: wrap; }
      .logout { margin: 0 0 0 auto; }
      main { padding: 16px; }
    }
  `,
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  logout(): void {
    this.auth.logout();
    void this.router.navigate(['/login']);
  }
}
