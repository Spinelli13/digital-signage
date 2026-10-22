import { Routes } from '@angular/router';

import { authGuard } from './core/auth.guard';
import { capabilityGuard, featureGuard } from './core/role.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./login/login.page').then((m) => m.LoginPage) },
  // Recuperar a senha (e o link de boas-vindas): páginas abertas, a pessoa ainda não consegue entrar.
  { path: 'esqueci-a-senha', loadComponent: () => import('./login/forgot-password.page').then((m) => m.ForgotPasswordPage) },
  { path: 'redefinir-senha/:token', loadComponent: () => import('./login/reset-password.page').then((m) => m.ResetPasswordPage) },
  // Política de Privacidade e Termos de Uso: páginas abertas (o aviso das páginas públicas e o login apontam para elas).
  { path: 'privacidade', data: { legal: 'privacy' }, loadComponent: () => import('./legal/legal.page').then((m) => m.LegalPage) },
  { path: 'termos', data: { legal: 'terms' }, loadComponent: () => import('./legal/legal.page').then((m) => m.LegalPage) },
  // Senha pelo celular: páginas abertas a quem escaneia o QR Code da fila (sem login e fora do menu do painel).
  { path: 'fila/:token', loadComponent: () => import('./public-ticket/public-ticket.page').then((m) => m.QueueTakePage) },
  { path: 'senha/:token', loadComponent: () => import('./public-ticket/public-ticket.page').then((m) => m.TicketStatusPage) },
  // Cupom pelo celular: páginas abertas a quem escaneia o QR Code da promoção (sem login e fora do menu do painel).
  { path: 'cupom/:token', loadComponent: () => import('./public-promo/public-promo.page').then((m) => m.PromoClaimPage) },
  { path: 'meu-cupom/:token', loadComponent: () => import('./public-promo/public-promo.page').then((m) => m.MyCouponPage) },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./shell/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', loadComponent: () => import('./dashboard/dashboard.page').then((m) => m.DashboardPage) },
      { path: 'dispositivos', loadComponent: () => import('./devices/devices.page').then((m) => m.DevicesPage) },
      { path: 'parear/:code', loadComponent: () => import('./devices/pairing.page').then((m) => m.PairingPage) },
      {
        path: 'dispositivos/:id/historico',
        loadComponent: () => import('./devices/device-history.page').then((m) => m.DeviceHistoryPage),
      },
      { path: 'alertas', loadComponent: () => import('./alerts/alerts.page').then((m) => m.AlertsPage) },
      { path: 'relatorios', loadComponent: () => import('./reports/reports.page').then((m) => m.ReportsPage) },
      { path: 'campanhas', canActivate: [featureGuard('CAMPAIGNS')], loadComponent: () => import('./campaigns/campaigns.page').then((m) => m.CampaignsPage) },
      { path: 'layouts', canActivate: [featureGuard('LAYOUTS')], loadComponent: () => import('./layouts/layouts.page').then((m) => m.LayoutsPage) },
      { path: 'cupons', canActivate: [featureGuard('COUPONS')], loadComponent: () => import('./promotions/promotions.page').then((m) => m.PromotionsPage) },
      { path: 'atendimento', canActivate: [featureGuard('QUEUE')], loadComponent: () => import('./attendance/attendance.page').then((m) => m.AttendancePage) },
      { path: 'precos', canActivate: [featureGuard('PRICES')], loadComponent: () => import('./prices/prices.page').then((m) => m.PricesPage) },
      { path: 'precos/:id', canActivate: [featureGuard('PRICES')], loadComponent: () => import('./prices/price-editor.page').then((m) => m.PriceEditorPage) },
      { path: 'locais', loadComponent: () => import('./locations/locations.page').then((m) => m.LocationsPage) },
      { path: 'grupos', loadComponent: () => import('./groups/groups.page').then((m) => m.GroupsPage) },
      {
        path: 'alertar-agora',
        canActivate: [capabilityGuard('content')],
        loadComponent: () => import('./screen-alert/screen-alert.page').then((m) => m.ScreenAlertPage),
      },
      { path: 'biblioteca', loadComponent: () => import('./media/media.page').then((m) => m.MediaPage) },
      {
        path: 'equipe',
        canActivate: [capabilityGuard('admin')],
        loadComponent: () => import('./team/team.page').then((m) => m.TeamPage),
      },
      {
        path: 'dados-pessoais',
        canActivate: [capabilityGuard('admin')],
        loadComponent: () => import('./privacy/privacy.page').then((m) => m.PrivacyPage),
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
            path: 'cupons',
            loadComponent: () => import('./platform/platform-coupons.page').then((m) => m.PlatformCouponsPage),
          },
          {
            path: 'gestores',
            loadComponent: () => import('./platform/platform-managers.page').then((m) => m.PlatformManagersPage),
          },
          {
            path: 'configuracoes',
            loadComponent: () => import('./platform/platform-settings.page').then((m) => m.PlatformSettingsPage),
          },
        ],
      },
      { path: 'recurso/:code', loadComponent: () => import('./locked/locked.page').then((m) => m.LockedPage) },
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
