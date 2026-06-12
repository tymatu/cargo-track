import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, firstValueFrom, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, RegisterRequest, Role, User } from './models';
import { TokenStorage } from './token-storage';

const HOME_BY_ROLE: Record<Role, string> = {
  USER: '/parcels',
  DRIVER: '/driver',
  DISPATCHER: '/dispatcher',
  ADMIN: '/admin',
};

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly storage = inject(TokenStorage);
  private readonly router = inject(Router);
  private readonly api = `${environment.apiUrl}/auth`;

  readonly currentUser = signal<User | null>(null);
  readonly isLoggedIn = computed(() => this.currentUser() !== null);

  register(request: RegisterRequest): Observable<User> {
    return this.http.post<User>(`${this.api}/register`, request);
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.api}/login`, { email, password })
      .pipe(tap((res) => this.applyAuth(res)));
  }

  /** Ротация: бэкенд гасит старый refresh и выдаёт новую пару. */
  refresh(): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.api}/refresh`, { refreshToken: this.storage.refreshToken })
      .pipe(tap((res) => this.applyAuth(res)));
  }

  logout(): void {
    const refreshToken = this.storage.refreshToken;
    if (refreshToken) {
      this.http.post(`${this.api}/logout`, { refreshToken }).subscribe();
    }
    this.forceLogout();
  }

  /** Локальный сброс сессии (например, когда refresh провалился). */
  forceLogout(): void {
    this.storage.clear();
    this.currentUser.set(null);
    this.router.navigate(['/login']);
  }

  /**
   * Восстановление сессии после F5: если токены сохранены — тянем /me.
   * Истёкший access прозрачно обновится интерсептором.
   */
  async restoreSession(): Promise<void> {
    if (!this.storage.refreshToken) {
      return;
    }
    try {
      const user = await firstValueFrom(this.http.get<User>(`${this.api}/me`));
      this.currentUser.set(user);
    } catch {
      this.storage.clear();
      this.currentUser.set(null);
    }
  }

  hasAnyRole(roles: Role[]): boolean {
    const user = this.currentUser();
    return user !== null && roles.includes(user.role);
  }

  /** Домашняя страница по роли (SDP, раздел 10.2). */
  homePath(): string {
    const user = this.currentUser();
    return user ? HOME_BY_ROLE[user.role] : '/login';
  }

  private applyAuth(res: AuthResponse): void {
    this.storage.store(res.accessToken, res.refreshToken);
    this.currentUser.set(res.user);
  }
}
