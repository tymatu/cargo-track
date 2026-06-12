import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from './models';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() ? true : inject(Router).createUrlTree(['/login']);
};

/** Залогиненного с /login и /register уводим на его домашнюю страницу. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() ? inject(Router).createUrlTree([auth.homePath()]) : true;
};

export const roleGuard =
  (roles: Role[]): CanActivateFn =>
  () => {
    const auth = inject(AuthService);
    return auth.hasAnyRole(roles) ? true : inject(Router).createUrlTree(['/forbidden']);
  };
