import { Routes } from '@angular/router';

import { authGuard } from './core/auth.guard';
import { capabilityGuard } from './core/role.guard';

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
      {
        path: 'dispositivos/:id/historico',
        loadComponent: () => import('./devices/device-history.page').then((m) => m.DeviceHistoryPage),
      },
      { path: 'alertas', loadComponent: () => import('./alerts/alerts.page').then((m) => m.AlertsPage) },
      { path: 'relatorios', loadComponent: () => import('./reports/reports.page').then((m) => m.ReportsPage) },
      { path: 'campanhas', loadComponent: () => import('./campaigns/campaigns.page').then((m) => m.CampaignsPage) },
      { path: 'layouts', loadComponent: () => import('./layouts/layouts.page').then((m) => m.LayoutsPage) },
      { path: 'atendimento', loadComponent: () => import('./attendance/attendance.page').then((m) => m.AttendancePage) },
      { path: 'precos', loadComponent: () => import('./prices/prices.page').then((m) => m.PricesPage) },
      { path: 'precos/:id', loadComponent: () => import('./prices/price-editor.page').then((m) => m.PriceEditorPage) },
      { path: 'locais', loadComponent: () => import('./locations/locations.page').then((m) => m.LocationsPage) },
      { path: 'grupos', loadComponent: () => import('./groups/groups.page').then((m) => m.GroupsPage) },
      { path: 'biblioteca', loadComponent: () => import('./media/media.page').then((m) => m.MediaPage) },
      {
        path: 'equipe',
        canActivate: [capabilityGuard('admin')],
        loadComponent: () => import('./team/team.page').then((m) => m.TeamPage),
      },
      {
        path: 'plano',
        canActivate: [capabilityGuard('admin')],
        loadComponent: () => import('./billing/billing.page').then((m) => m.BillingPage),
      },
      {
        path: 'plataforma',
        canActivate: [capabilityGuard('platform')],
        children: [
          {
            path: '',
            pathMatch: 'full',
            loadComponent: () => import('./platform/platform-overview.page').then((m) => m.PlatformOverviewPage),
          },
          {
            path: 'clientes',
            loadComponent: () => import('./platform/platform-clients.page').then((m) => m.PlatformClientsPage),
          },
          {
            path: 'planos',
            loadComponent: () => import('./platform/platform-plans.page').then((m) => m.PlatformPlansPage),
          },
          {
            path: 'faturas',
            loadComponent: () => import('./platform/platform-invoices.page').then((m) => m.PlatformInvoicesPage),
          },
          {
            path: 'configuracoes',
            loadComponent: () => import('./platform/platform-settings.page').then((m) => m.PlatformSettingsPage),
          },
        ],
      },
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
