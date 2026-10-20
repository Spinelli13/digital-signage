import { Routes } from '@angular/router';

import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./login/login.page').then((m) => m.LoginPage) },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./shell/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', loadComponent: () => import('./dashboard/dashboard.page').then((m) => m.DashboardPage) },
      { path: 'dispositivos', loadComponent: () => import('./devices/devices.page').then((m) => m.DevicesPage) },
      { path: 'locais', loadComponent: () => import('./locations/locations.page').then((m) => m.LocationsPage) },
      { path: 'grupos', loadComponent: () => import('./groups/groups.page').then((m) => m.GroupsPage) },
      { path: 'biblioteca', loadComponent: () => import('./media/media.page').then((m) => m.MediaPage) },
      { path: 'downloads', loadComponent: () => import('./downloads/downloads.page').then((m) => m.DownloadsPage) },
      { path: 'playlists', loadComponent: () => import('./playlists/playlists.page').then((m) => m.PlaylistsPage) },
      {
        path: 'playlists/nova',
        loadComponent: () => import('./playlists/playlist-editor.page').then((m) => m.PlaylistEditorPage),
      },
      {
        path: 'playlists/:id',
        loadComponent: () => import('./playlists/playlist-editor.page').then((m) => m.PlaylistEditorPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
