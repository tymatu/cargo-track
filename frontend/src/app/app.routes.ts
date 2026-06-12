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
    canActivate: [authGuard, roleGuard(['USER'])],
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
  // Разделы остальных ролей — наполняются в следующих фазах
  {
    path: 'driver',
    loadComponent: () => import('./shared/coming-soon').then((m) => m.ComingSoon),
    canActivate: [authGuard, roleGuard(['DRIVER'])],
    data: { title: 'Мои рейсы' },
  },
  {
    path: 'dispatcher',
    loadComponent: () => import('./shared/coming-soon').then((m) => m.ComingSoon),
    canActivate: [authGuard, roleGuard(['DISPATCHER', 'ADMIN'])],
    data: { title: 'Диспетчерская' },
  },
  {
    path: 'admin',
    loadComponent: () => import('./shared/coming-soon').then((m) => m.ComingSoon),
    canActivate: [authGuard, roleGuard(['ADMIN'])],
    data: { title: 'Администрирование' },
  },
  {
    path: 'forbidden',
    loadComponent: () => import('./shared/forbidden').then((m) => m.Forbidden),
  },
  { path: '**', redirectTo: '' },
];
