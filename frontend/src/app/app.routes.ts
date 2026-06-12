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
  // Домашние разделы ролей — наполняются в следующих фазах
  {
    path: 'parcels',
    loadComponent: () => import('./shared/coming-soon').then((m) => m.ComingSoon),
    canActivate: [authGuard, roleGuard(['USER'])],
    data: { title: 'Мои посылки' },
  },
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
