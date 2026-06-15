import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/auth/guards';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login').then((m) => m.Login),
    canActivate: [guestGuard],
  },
  {
    path: 'register',
    loadComponent: () => import('./features/auth/register').then((m) => m.Register),
    canActivate: [guestGuard],
  },
  // Публичный трекинг — доступен без логина
  {
    path: 'track',
    loadComponent: () => import('./features/public-tracking/tracking-page').then((m) => m.TrackingPage),
  },
  {
    path: 'track/:number',
    loadComponent: () => import('./features/public-tracking/tracking-page').then((m) => m.TrackingPage),
  },
  // USER: посылки
  {
    path: 'parcels',
    canActivate: [authGuard, roleGuard(['USER', 'ADMIN'])],
    children: [
      {
        path: '',
        loadComponent: () => import('./features/user/parcel-list').then((m) => m.ParcelList),
      },
      {
        path: 'new',
        loadComponent: () => import('./features/user/parcel-create').then((m) => m.ParcelCreate),
      },
      {
        path: ':id',
        loadComponent: () => import('./features/user/parcel-detail').then((m) => m.ParcelDetailPage),
      },
    ],
  },
  {
    path: 'driver',
    canActivate: [authGuard, roleGuard(['DRIVER', 'ADMIN'])],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/driver/driver-shipment-list').then((m) => m.DriverShipmentList),
      },
      {
        path: 'shipments/:id',
        loadComponent: () =>
          import('./features/driver/driver-shipment-detail').then((m) => m.DriverShipmentDetail),
      },
    ],
  },
  {
    path: 'dispatcher',
    canActivate: [authGuard, roleGuard(['DISPATCHER', 'ADMIN'])],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/dispatcher/dispatcher-dashboard').then((m) => m.DispatcherDashboard),
      },
      {
        path: 'shipments/new',
        loadComponent: () =>
          import('./features/dispatcher/shipment-create').then((m) => m.ShipmentCreate),
      },
      {
        path: 'shipments/:id',
        loadComponent: () =>
          import('./features/dispatcher/shipment-load').then((m) => m.ShipmentLoad),
      },
    ],
  },
  {
    path: 'admin',
    canActivate: [authGuard, roleGuard(['ADMIN'])],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/admin/admin-dashboard').then((m) => m.AdminDashboardPage),
      },
      {
        path: 'users',
        loadComponent: () => import('./features/admin/admin-users').then((m) => m.AdminUsers),
      },
      {
        path: 'shipments',
        loadComponent: () => import('./features/admin/admin-shipments').then((m) => m.AdminShipments),
      },
      {
        path: 'trucks',
        loadComponent: () => import('./features/admin/admin-trucks').then((m) => m.AdminTrucks),
      },
      {
        path: 'warehouses',
        loadComponent: () =>
          import('./features/admin/admin-warehouses').then((m) => m.AdminWarehouses),
      },
      {
        path: 'audit',
        loadComponent: () => import('./features/admin/admin-audit').then((m) => m.AdminAudit),
      },
    ],
    data: { title: 'Администрирование' },
  },
  {
    path: 'forbidden',
    loadComponent: () => import('./shared/forbidden').then((m) => m.Forbidden),
  },
  { path: '**', redirectTo: '' },
];
